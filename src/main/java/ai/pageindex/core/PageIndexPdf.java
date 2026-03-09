package ai.pageindex.core;

import ai.pageindex.config.PageIndexConfig;
import ai.pageindex.util.*;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Core PDF indexing engine.
 * Full Java translation of pageindex/page_index.py.
 *
 * All async methods return CompletableFuture and use OpenAIClient.EXECUTOR
 * for parallel LLM calls (mirrors Python asyncio.gather).
 */
public class PageIndexPdf {

    private final OpenAIClient ai;

    public PageIndexPdf(OpenAIClient ai) {
        this.ai = ai;
    }

    // =========================================================================
    // 1. Title appearance checks
    // =========================================================================

    /**
     * Check if a section title appears on its claimed page.
     * Mirrors check_title_appearance(item, page_list, start_index, model).
     */
    public CompletableFuture<Map<String, Object>> checkTitleAppearance(
            Map<String, Object> item, List<PdfParser.PageEntry> pageList,
            int startIndex, String model) {

        return CompletableFuture.supplyAsync(() -> {
            String title = (String) item.get("title");
            Object piObj = item.get("physical_index");
            if (piObj == null) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("list_index", item.getOrDefault("list_index", -1));
                r.put("answer", "no");
                r.put("title", title);
                r.put("page_number", null);
                return r;
            }
            int pageNumber = TreeUtils.toInt(piObj, -1);
            int listIdx = pageNumber - startIndex;
            if (listIdx < 0 || listIdx >= pageList.size()) {
                return Map.of("list_index", item.getOrDefault("list_index", -1),
                        "answer", "no", "title", title, "page_number", pageNumber);
            }
            String pageText = pageList.get(listIdx).text();
            String prompt = """
                    Your job is to check if the given section appears or starts in the given page_text.

                    Note: do fuzzy matching, ignore any space inconsistency in the page_text.

                    The given section title is %s.
                    The given page_text is %s.

                    Reply format:
                    {
                        "thinking": <why do you think the section appears or starts in the page_text>
                        "answer": "yes or no" (yes if the section appears or starts in the page_text, no otherwise)
                    }
                    Directly return the final JSON structure. Do not output anything else."""
                    .formatted(title, pageText);

            String response = ai.call(model, prompt);
            JsonNode json = TreeUtils.extractJson(response);
            String answer = json.has("answer") ? json.get("answer").asText("no") : "no";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("list_index", item.getOrDefault("list_index", -1));
            result.put("answer", answer);
            result.put("title", title);
            result.put("page_number", pageNumber);
            return result;
        }, OpenAIClient.EXECUTOR);
    }

    /**
     * Check if a title appears at the start of a page (for appear_start field).
     * Mirrors check_title_appearance_in_start(title, page_text, model, logger).
     */
    public String checkTitleAppearanceInStart(String title, String pageText, String model) {
        String prompt = """
                Your job is to check if the given section title appears at the start of the given page_text.
                The section title should appear at the very beginning of the page, before the main content of the section.
                Note: do fuzzy matching, ignore any space inconsistency in the page_text.
                Reply format:
                {
                    "thinking": <why do you think the section title appears at the start of the page_text>
                    "answer": "yes or no"
                }
                The given section title is %s.
                The given page_text is %s.
                Directly return the final JSON structure. Do not output anything else."""
                .formatted(title, pageText);
        String response = ai.call(model, prompt);
        JsonNode json = TreeUtils.extractJson(response);
        return json.has("answer") ? json.get("answer").asText("no") : "no";
    }

    /**
     * Concurrently check all items whether their title appears at page start.
     * Mirrors check_title_appearance_in_start_concurrent in page_index.py.
     */
    public List<Map<String, Object>> checkTitleAppearanceInStartConcurrent(
            List<Map<String, Object>> structure, List<PdfParser.PageEntry> pageList,
            String model, JsonLogger logger) {

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map<String, Object> item : structure) {
            Object piObj = item.get("physical_index");
            if (piObj == null) { item.put("appear_start", "no"); continue; }
            int pageNumber = TreeUtils.toInt(piObj, -1);
            int listIdx = pageNumber - 1; // start_index assumed 1 here
            if (listIdx < 0 || listIdx >= pageList.size()) { item.put("appear_start", "no"); continue; }

            String pageText = pageList.get(listIdx).text();
            String title = (String) item.get("title");
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                String result = checkTitleAppearanceInStart(title, pageText, model);
                item.put("appear_start", result);
            }, OpenAIClient.EXECUTOR);
            futures.add(f);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return structure;
    }

    // =========================================================================
    // 2. TOC Detection
    // =========================================================================

    /**
     * Detect if a single page contains a table of contents.
     * Mirrors toc_detector_single_page(content, model).
     */
    public String tocDetectorSinglePage(String content, String model) {
        String prompt = """
                Your job is to detect if there is a table of content provided in the given text.

                Given text: %s

                return the following JSON format:
                {
                    "thinking": <why do you think there is a table of content in the given text>
                    "toc_detected": "<yes or no>",
                }

                Directly return the final JSON structure. Do not output anything else.
                Please note: abstract,summary, notation list, figure list, table list, etc. are not table of contents."""
                .formatted(content);
        String response = ai.call(model, prompt);
        JsonNode json = TreeUtils.extractJson(response);
        return json.has("toc_detected") ? json.get("toc_detected").asText("no") : "no";
    }

    /** Mirrors check_if_toc_transformation_is_complete(content, toc, model). */
    public String checkIfTocTransformationIsComplete(String content, String toc, String model) {
        String prompt = """
                You are given a raw table of contents and a  table of contents.
                Your job is to check if the  table of contents is complete.

                Reply format:
                {
                    "thinking": <why do you think the cleaned table of contents is complete or not>
                    "completed": "yes" or "no"
                }
                Directly return the final JSON structure. Do not output anything else.
                Raw Table of contents:
                %s
                Cleaned Table of contents:
                %s""".formatted(content, toc);
        String response = ai.call(model, prompt);
        JsonNode json = TreeUtils.extractJson(response);
        return json.has("completed") ? json.get("completed").asText("no") : "no";
    }

    /**
     * Extract the full TOC text from raw page content, continuing generation if needed.
     * Mirrors extract_toc_content(content, model).
     */
    public String extractTocContent(String content, String model) {
        String prompt = """
                Your job is to extract the full table of contents from the given text, replace ... with :

                Given text: %s

                Directly return the full table of contents content. Do not output anything else."""
                .formatted(content);

        OpenAIClient.CompletionResult result = ai.callWithFinishReason(model, prompt, null);
        String response = result.content();

        if ("yes".equals(checkIfTocTransformationIsComplete(content, response, model))
                && "finished".equals(result.finishReason())) {
            return response;
        }

        // Continue generation loop
        int attempts = 0;
        List<Map<String, String>> history = new ArrayList<>();
        history.add(Map.of("role", "user", "content", prompt));
        history.add(Map.of("role", "assistant", "content", response));

        while (attempts < 5) {
            String continuePrompt = "please continue the generation of table of contents , directly output the remaining part of the structure";
            OpenAIClient.CompletionResult cont = ai.callWithFinishReason(model, continuePrompt, history);
            response = response + cont.content();
            if ("yes".equals(checkIfTocTransformationIsComplete(content, response, model))
                    && "finished".equals(cont.finishReason())) {
                return response;
            }
            history.add(Map.of("role", "assistant", "content", cont.content()));
            attempts++;
        }
        throw new RuntimeException("Failed to complete table of contents after maximum retries");
    }

    /**
     * Detect if the TOC includes page numbers.
     * Mirrors detect_page_index(toc_content, model).
     */
    public String detectPageIndex(String tocContent, String model) {
        System.out.println("start detect_page_index");
        String prompt = """
                You will be given a table of contents.

                Your job is to detect if there are page numbers/indices given within the table of contents.

                Given text: %s

                Reply format:
                {
                    "thinking": <why do you think there are page numbers/indices given within the table of contents>
                    "page_index_given_in_toc": "<yes or no>"
                }
                Directly return the final JSON structure. Do not output anything else."""
                .formatted(tocContent);
        String response = ai.call(model, prompt);
        JsonNode json = TreeUtils.extractJson(response);
        return json.has("page_index_given_in_toc") ? json.get("page_index_given_in_toc").asText("no") : "no";
    }

    /**
     * Extract structured TOC from TOC pages and detect if it has page numbers.
     * Mirrors toc_extractor(page_list, toc_page_list, model).
     */
    public Map<String, Object> tocExtractor(List<PdfParser.PageEntry> pageList,
                                             List<Integer> tocPageList, String model) {
        StringBuilder sb = new StringBuilder();
        for (int idx : tocPageList) sb.append(pageList.get(idx).text());
        String tocContent = sb.toString();

        // Replace long dot sequences with ": "
        tocContent = tocContent.replaceAll("\\.{5,}", ": ")
                               .replaceAll("(?:\\. ){5,}\\.?", ": ");

        String hasPageIndex = detectPageIndex(tocContent, model);
        return Map.of("toc_content", tocContent, "page_index_given_in_toc", hasPageIndex);
    }

    /**
     * Transform raw TOC text into a structured JSON list.
     * Mirrors toc_transformer(toc_content, model).
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> tocTransformer(String tocContent, String model) {
        System.out.println("start toc_transformer");
        String initPrompt = """
                You are given a table of contents, You job is to transform the whole table of content into a JSON format included table_of_contents.

                structure is the numeric system which represents the index of the hierarchy section in the table of contents. For example, the first section has structure index 1, the first subsection has structure index 1.1, the second subsection has structure index 1.2, etc.

                The response should be in the following JSON format:
                {
                "table_of_contents: [
                    {
                        "structure": <structure index, "x.x.x" or None> (string),
                        "title": <title of the section>,
                        "page": <page number or None>,
                    },
                    ...
                    ],
                }
                You should transform the full table of contents in one go.
                Directly return the final JSON structure, do not output anything else.""";

        String prompt = initPrompt + "\n Given table of contents\n:" + tocContent;
        OpenAIClient.CompletionResult result = ai.callWithFinishReason(model, prompt, null);
        String lastComplete = result.content();

        String isComplete = checkIfTocTransformationIsComplete(tocContent, lastComplete, model);
        if ("yes".equals(isComplete) && "finished".equals(result.finishReason())) {
            JsonNode json = TreeUtils.extractJson(lastComplete);
            List<Map<String, Object>> toc = jsonArrayToList(json.get("table_of_contents"));
            convertPageToInt(toc);
            return toc;
        }

        // Continue generation loop
        lastComplete = TreeUtils.stripJsonFence(lastComplete);
        int attempts = 0;
        while (attempts < 5) {
            int pos = lastComplete.lastIndexOf('}');
            if (pos != -1) lastComplete = lastComplete.substring(0, Math.min(pos + 2, lastComplete.length()));

            String continuePrompt = """
                    Your task is to continue the table of contents json structure, directly output the remaining part of the json structure.
                    The response should be in the following JSON format:

                    The raw table of contents json structure is:
                    %s

                    The incomplete transformed table of contents json structure is:
                    %s

                    Please continue the json structure, directly output the remaining part of the json structure."""
                    .formatted(tocContent, lastComplete);

            OpenAIClient.CompletionResult cont = ai.callWithFinishReason(model, continuePrompt, null);
            String newComplete = cont.content();
            if (newComplete.startsWith("```json")) newComplete = TreeUtils.stripJsonFence(newComplete);
            lastComplete = lastComplete + newComplete;

            isComplete = checkIfTocTransformationIsComplete(tocContent, lastComplete, model);
            if ("yes".equals(isComplete) && "finished".equals(cont.finishReason())) break;
            attempts++;
        }

        try {
            JsonNode json = TreeUtils.MAPPER.readTree(lastComplete);
            List<Map<String, Object>> toc = jsonArrayToList(json.get("table_of_contents"));
            convertPageToInt(toc);
            return toc;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse TOC JSON: " + e.getMessage());
        }
    }

    /**
     * Add physical_index (actual PDF page number) to TOC items by reading pages.
     * Mirrors toc_index_extractor(toc, content, model).
     */
    public List<Map<String, Object>> tocIndexExtractor(
            List<Map<String, Object>> toc, String content, String model) {
        System.out.println("start toc_index_extractor");
        String prompt = """
                You are given a table of contents in a json format and several pages of a document, your job is to add the physical_index to the table of contents in the json format.

                The provided pages contains tags like <physical_index_X> and <physical_index_X> to indicate the physical location of the page X.

                The structure variable is the numeric system which represents the index of the hierarchy section in the table of contents. For example, the first section has structure index 1, the first subsection has structure index 1.1, the second subsection has structure index 1.2, etc.

                The response should be in the following JSON format:
                [
                    {
                        "structure": <structure index, "x.x.x" or None> (string),
                        "title": <title of the section>,
                        "physical_index": "<physical_index_X>" (keep the format)
                    },
                    ...
                ]

                Only add the physical_index to the sections that are in the provided pages.
                If the section is not in the provided pages, do not add the physical_index to it.
                Directly return the final JSON structure. Do not output anything else.""";

        try {
            prompt = prompt + "\nTable of contents:\n" + TreeUtils.MAPPER.writeValueAsString(toc)
                    + "\nDocument pages:\n" + content;
        } catch (Exception e) {
            prompt = prompt + "\nTable of contents:\n" + toc + "\nDocument pages:\n" + content;
        }

        String response = ai.call(model, prompt);
        JsonNode json = TreeUtils.extractJson(response);
        return jsonArrayToList(json);
    }

    /**
     * Find pages in the PDF that contain a table of contents.
     * Mirrors find_toc_pages(start_page_index, page_list, opt, logger).
     */
    public List<Integer> findTocPages(int startPageIndex, List<PdfParser.PageEntry> pageList,
                                       PageIndexConfig opt, JsonLogger logger) {
        System.out.println("start find_toc_pages");
        boolean lastPageIsYes = false;
        List<Integer> tocPageList = new ArrayList<>();
        int i = startPageIndex;

        while (i < pageList.size()) {
            if (i >= opt.tocCheckPageNum && !lastPageIsYes) break;
            String detected = tocDetectorSinglePage(pageList.get(i).text(), opt.model);
            if ("yes".equals(detected)) {
                if (logger != null) logger.info("Page " + i + " has toc");
                tocPageList.add(i);
                lastPageIsYes = true;
            } else if ("no".equals(detected) && lastPageIsYes) {
                if (logger != null) logger.info("Found the last page with toc: " + (i - 1));
                break;
            }
            i++;
        }

        if (tocPageList.isEmpty() && logger != null) logger.info("No toc found");
        return tocPageList;
    }

    /**
     * Remove "page_number" fields from tree nodes.
     * Mirrors remove_page_number(data).
     */
    @SuppressWarnings("unchecked")
    public static void removePageNumber(Object data) {
        if (data instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).remove("page_number");
            for (Object val : ((Map<?, ?>) map).values()) removePageNumber(val);
        } else if (data instanceof List<?> list) {
            for (Object item : list) removePageNumber(item);
        }
    }

    /**
     * Match TOC page numbers to physical indices to compute page offset.
     * Mirrors extract_matching_page_pairs and calculate_page_offset.
     */
    public List<Map<String, Object>> extractMatchingPagePairs(
            List<Map<String, Object>> tocPage,
            List<Map<String, Object>> tocPhysicalIndex,
            int startPageIndex) {
        List<Map<String, Object>> pairs = new ArrayList<>();
        for (Map<String, Object> phyItem : tocPhysicalIndex) {
            for (Map<String, Object> pageItem : tocPage) {
                if (Objects.equals(phyItem.get("title"), pageItem.get("title"))) {
                    Object pi = phyItem.get("physical_index");
                    if (pi != null) {
                        int physIdx = TreeUtils.toInt(pi, -1);
                        if (physIdx >= startPageIndex) {
                            Map<String, Object> pair = new LinkedHashMap<>();
                            pair.put("title", phyItem.get("title"));
                            pair.put("page", pageItem.get("page"));
                            pair.put("physical_index", physIdx);
                            pairs.add(pair);
                        }
                    }
                }
            }
        }
        return pairs;
    }

    public Integer calculatePageOffset(List<Map<String, Object>> pairs) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Map<String, Object> pair : pairs) {
            try {
                int pi = TreeUtils.toInt(pair.get("physical_index"), -1);
                int pg = TreeUtils.toInt(pair.get("page"), -1);
                if (pi < 0 || pg < 0) continue;
                int diff = pi - pg;
                counts.merge(diff, 1, Integer::sum);
            } catch (Exception ignored) {}
        }
        if (counts.isEmpty()) return null;
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue()).get().getKey();
    }

    public List<Map<String, Object>> addPageOffsetToTocJson(
            List<Map<String, Object>> data, Integer offset) {
        if (offset == null) return data;
        for (Map<String, Object> item : data) {
            Object page = item.get("page");
            if (page instanceof Integer pg) {
                item.put("physical_index", pg + offset);
                item.remove("page");
            }
        }
        return data;
    }

    // =========================================================================
    // 3. Page grouping
    // =========================================================================

    /**
     * Group page texts into chunks that fit within max_tokens.
     * Mirrors page_list_to_group_text(page_contents, token_lengths, max_tokens, overlap_page).
     */
    public List<String> pageListToGroupText(List<String> pageContents,
                                             List<Integer> tokenLengths,
                                             int maxTokens) {
        int numTokens = tokenLengths.stream().mapToInt(Integer::intValue).sum();
        if (numTokens <= maxTokens) {
            return List.of(String.join("", pageContents));
        }

        List<String> subsets = new ArrayList<>();
        List<String> currentSubset = new ArrayList<>();
        int currentCount = 0;
        int overlapPage = 1;

        int expectedParts = (int) Math.ceil((double) numTokens / maxTokens);
        int avgTokensPerPart = (int) Math.ceil(((double) numTokens / expectedParts + maxTokens) / 2);

        for (int i = 0; i < pageContents.size(); i++) {
            String pageContent = pageContents.get(i);
            int pageTokens = tokenLengths.get(i);
            if (currentCount + pageTokens > avgTokensPerPart) {
                subsets.add(String.join("", currentSubset));
                int overlapStart = Math.max(i - overlapPage, 0);
                currentSubset = new ArrayList<>(pageContents.subList(overlapStart, i));
                currentCount = tokenLengths.subList(overlapStart, i).stream().mapToInt(Integer::intValue).sum();
            }
            currentSubset.add(pageContent);
            currentCount += pageTokens;
        }
        if (!currentSubset.isEmpty()) subsets.add(String.join("", currentSubset));
        System.out.println("divide page_list to groups " + subsets.size());
        return subsets;
    }

    // =========================================================================
    // 4. TOC generation (no existing TOC)
    // =========================================================================

    /**
     * Generate initial TOC structure from first page group.
     * Mirrors generate_toc_init(part, model).
     */
    public List<Map<String, Object>> generateTocInit(String part, String model) {
        System.out.println("start generate_toc_init");
        String prompt = """
                You are an expert in extracting hierarchical tree structure, your task is to generate the tree structure of the document.

                The structure variable is the numeric system which represents the index of the hierarchy section in the table of contents. For example, the first section has structure index 1, the first subsection has structure index 1.1, the second subsection has structure index 1.2, etc.

                For the title, you need to extract the original title from the text, only fix the space inconsistency.

                The provided text contains tags like <physical_index_X> and <physical_index_X> to indicate the start and end of page X.

                For the physical_index, you need to extract the physical index of the start of the section from the text. Keep the <physical_index_X> format.

                The response should be in the following format.
                    [
                        {
                            "structure": <structure index, "x.x.x"> (string),
                            "title": <title of the section, keep the original title>,
                            "physical_index": "<physical_index_X> (keep the format)"
                        },

                    ],


                Directly return the final JSON structure. Do not output anything else."""
                + "\nGiven text\n:" + part;

        OpenAIClient.CompletionResult result = ai.callWithFinishReason(model, prompt, null);
        if ("error".equals(result.finishReason())) {
            System.out.println("generate_toc_init: LLM call failed, returning empty structure");
            return new ArrayList<>();
        }
        try {
            return jsonArrayToList(TreeUtils.extractJson(result.content()));
        } catch (Exception e) {
            System.out.println("generate_toc_init: failed to parse response, returning empty structure");
            return new ArrayList<>();
        }
    }

    /**
     * Continue TOC structure generation for subsequent page groups.
     * Mirrors generate_toc_continue(toc_content, part, model).
     */
    public List<Map<String, Object>> generateTocContinue(
            List<Map<String, Object>> tocContent, String part, String model) {
        System.out.println("start generate_toc_continue");
        String prompt = """
                You are an expert in extracting hierarchical tree structure.
                You are given a tree structure of the previous part and the text of the current part.
                Your task is to continue the tree structure from the previous part to include the current part.

                The structure variable is the numeric system which represents the index of the hierarchy section in the table of contents. For example, the first section has structure index 1, the first subsection has structure index 1.1, the second subsection has structure index 1.2, etc.

                For the title, you need to extract the original title from the text, only fix the space inconsistency.

                The provided text contains tags like <physical_index_X> and <physical_index_X> to indicate the start and end of page X.

                For the physical_index, you need to extract the physical index of the start of the section from the text. Keep the <physical_index_X> format.

                The response should be in the following format.
                    [
                        {
                            "structure": <structure index, "x.x.x"> (string),
                            "title": <title of the section, keep the original title>,
                            "physical_index": "<physical_index_X> (keep the format)"
                        },
                        ...
                    ]

                Directly return the additional part of the final JSON structure. Do not output anything else.""";

        try {
            prompt = prompt + "\nGiven text\n:" + part
                    + "\nPrevious tree structure\n:" + TreeUtils.MAPPER.writeValueAsString(tocContent);
        } catch (Exception e) {
            prompt = prompt + "\nGiven text\n:" + part + "\nPrevious tree structure\n:" + tocContent;
        }

        OpenAIClient.CompletionResult result = ai.callWithFinishReason(model, prompt, null);
        if ("finished".equals(result.finishReason())) {
            return jsonArrayToList(TreeUtils.extractJson(result.content()));
        }
        throw new RuntimeException("finish reason: " + result.finishReason());
    }

    /**
     * Add physical page numbers to TOC items for each page group.
     * Mirrors add_page_number_to_toc(part, structure, model).
     */
    public List<Map<String, Object>> addPageNumberToToc(String part,
                                                         List<Map<String, Object>> structure,
                                                         String model) {
        String fillPrompt = """
                You are given an JSON structure of a document and a partial part of the document. Your task is to check if the title that is described in the structure is started in the partial given document.

                The provided text contains tags like <physical_index_X> and <physical_index_X> to indicate the physical location of the page X.

                If the full target section starts in the partial given document, insert the given JSON structure with the "start": "yes", and "start_index": "<physical_index_X>".

                If the full target section does not start in the partial given document, insert "start": "no",  "start_index": None.

                The response should be in the following format.
                    [
                        {
                            "structure": <structure index, "x.x.x" or None> (string),
                            "title": <title of the section>,
                            "start": "<yes or no>",
                            "physical_index": "<physical_index_X> (keep the format)" or None
                        },
                        ...
                    ]
                The given structure contains the result of the previous part, you need to fill the result of the current part, do not change the previous result.
                Directly return the final JSON structure. Do not output anything else.""";

        try {
            String structJson = TreeUtils.MAPPER.writeValueAsString(structure);
            String prompt = fillPrompt + "\n\nCurrent Partial Document:\n" + part + "\n\nGiven Structure\n" + structJson;
            String response = ai.call(model, prompt);
            JsonNode json = TreeUtils.extractJson(response);
            List<Map<String, Object>> result = jsonArrayToList(json);
            result.forEach(item -> item.remove("start"));
            return result;
        } catch (Exception e) {
            throw new RuntimeException("addPageNumberToToc failed: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 5. Processing modes
    // =========================================================================

    /**
     * Process document with no TOC — generate structure from scratch.
     * Mirrors process_no_toc(page_list, start_index, model, logger).
     */
    public List<Map<String, Object>> processNoToc(List<PdfParser.PageEntry> pageList,
                                                   int startIndex, String model, JsonLogger logger) {
        List<String> pageContents = new ArrayList<>();
        List<Integer> tokenLengths = new ArrayList<>();

        for (int pageIndex = startIndex; pageIndex < startIndex + pageList.size(); pageIndex++) {
            String pageText = "<physical_index_" + pageIndex + ">\n"
                    + pageList.get(pageIndex - startIndex).text()
                    + "\n<physical_index_" + pageIndex + ">\n\n";
            pageContents.add(pageText);
            tokenLengths.add(PdfParser.countTokens(pageText, model));
        }

        List<String> groupTexts = pageListToGroupText(pageContents, tokenLengths, 20000);
        logger.info("len(group_texts): " + groupTexts.size());

        List<Map<String, Object>> toc = generateTocInit(groupTexts.get(0), model);
        for (int i = 1; i < groupTexts.size(); i++) {
            List<Map<String, Object>> additional = generateTocContinue(toc, groupTexts.get(i), model);
            toc.addAll(additional);
        }
        logger.info("generate_toc: " + toc);

        TreeUtils.convertPhysicalIndicesToInt(toc);
        logger.info("convert_physical_index_to_int: " + toc);
        return toc;
    }

    /**
     * Process document where TOC exists but has no page numbers.
     * Mirrors process_toc_no_page_numbers(toc_content, toc_page_list, page_list, ...).
     */
    public List<Map<String, Object>> processTocNoPageNumbers(
            String tocContent, List<Integer> tocPageList,
            List<PdfParser.PageEntry> pageList, int startIndex,
            String model, JsonLogger logger) {

        List<Map<String, Object>> toc = tocTransformer(tocContent, model);
        logger.info("toc_transformer: " + toc);

        List<String> pageContents = new ArrayList<>();
        List<Integer> tokenLengths = new ArrayList<>();
        for (int pageIndex = startIndex; pageIndex < startIndex + pageList.size(); pageIndex++) {
            String pageText = "<physical_index_" + pageIndex + ">\n"
                    + pageList.get(pageIndex - startIndex).text()
                    + "\n<physical_index_" + pageIndex + ">\n\n";
            pageContents.add(pageText);
            tokenLengths.add(PdfParser.countTokens(pageText, model));
        }

        List<String> groupTexts = pageListToGroupText(pageContents, tokenLengths, 20000);
        logger.info("len(group_texts): " + groupTexts.size());

        List<Map<String, Object>> tocWithPageNumber = new ArrayList<>(toc);
        for (String groupText : groupTexts) {
            tocWithPageNumber = addPageNumberToToc(groupText, tocWithPageNumber, model);
        }
        logger.info("add_page_number_to_toc: " + tocWithPageNumber);
        TreeUtils.convertPhysicalIndicesToInt(tocWithPageNumber);
        logger.info("convert_physical_index_to_int: " + tocWithPageNumber);
        return tocWithPageNumber;
    }

    /**
     * Process document where TOC exists with page numbers.
     * Mirrors process_toc_with_page_numbers(toc_content, toc_page_list, page_list, ...).
     */
    public List<Map<String, Object>> processTocWithPageNumbers(
            String tocContent, List<Integer> tocPageList,
            List<PdfParser.PageEntry> pageList, int tocCheckPageNum,
            String model, JsonLogger logger) {

        List<Map<String, Object>> tocWithPageNumber = tocTransformer(tocContent, model);
        logger.info("toc_with_page_number: " + tocWithPageNumber);

        List<Map<String, Object>> tocNoPageNumber = deepCopyList(tocWithPageNumber);
        removePageNumber(tocNoPageNumber);

        int startPageIndex = tocPageList.get(tocPageList.size() - 1) + 1;
        StringBuilder mainContent = new StringBuilder();
        for (int pi = startPageIndex; pi < Math.min(startPageIndex + tocCheckPageNum, pageList.size()); pi++) {
            mainContent.append("<physical_index_").append(pi + 1).append(">\n")
                    .append(pageList.get(pi).text()).append("\n")
                    .append("<physical_index_").append(pi + 1).append(">\n\n");
        }

        List<Map<String, Object>> tocWithPhysicalIndex =
                tocIndexExtractor(tocNoPageNumber, mainContent.toString(), model);
        logger.info("toc_with_physical_index: " + tocWithPhysicalIndex);

        TreeUtils.convertPhysicalIndicesToInt(tocWithPhysicalIndex);
        logger.info("toc_with_physical_index (converted): " + tocWithPhysicalIndex);

        List<Map<String, Object>> matchingPairs =
                extractMatchingPagePairs(tocWithPageNumber, tocWithPhysicalIndex, startPageIndex);
        logger.info("matching_pairs: " + matchingPairs);

        Integer offset = calculatePageOffset(matchingPairs);
        logger.info("offset: " + offset);

        tocWithPageNumber = addPageOffsetToTocJson(tocWithPageNumber, offset);
        logger.info("toc_with_page_number (offset): " + tocWithPageNumber);

        tocWithPageNumber = processNonePageNumbers(tocWithPageNumber, pageList, 1, model);
        logger.info("toc_with_page_number (none fixed): " + tocWithPageNumber);
        return tocWithPageNumber;
    }

    /**
     * Fix items in the TOC that have no physical_index (page=null after offset).
     * Mirrors process_none_page_numbers(toc_items, page_list, start_index, model).
     */
    public List<Map<String, Object>> processNonePageNumbers(
            List<Map<String, Object>> tocItems, List<PdfParser.PageEntry> pageList,
            int startIndex, String model) {

        for (int i = 0; i < tocItems.size(); i++) {
            Map<String, Object> item = tocItems.get(i);
            if (!item.containsKey("physical_index")) {
                // Find prev physical_index
                int prevPhysicalIndex = 0;
                for (int j = i - 1; j >= 0; j--) {
                    Object pi = tocItems.get(j).get("physical_index");
                    if (pi != null) { prevPhysicalIndex = TreeUtils.toInt(pi, 0); break; }
                }
                // Find next physical_index
                int nextPhysicalIndex = -1;
                for (int j = i + 1; j < tocItems.size(); j++) {
                    Object pi = tocItems.get(j).get("physical_index");
                    if (pi != null) { nextPhysicalIndex = TreeUtils.toInt(pi, -1); break; }
                }

                StringBuilder pageContents = new StringBuilder();
                for (int pi = prevPhysicalIndex; pi <= nextPhysicalIndex; pi++) {
                    int listIdx = pi - startIndex;
                    if (listIdx >= 0 && listIdx < pageList.size()) {
                        pageContents.append("<physical_index_").append(pi).append(">\n")
                                .append(pageList.get(listIdx).text()).append("\n")
                                .append("<physical_index_").append(pi).append(">\n\n");
                    }
                }

                Map<String, Object> itemCopy = new LinkedHashMap<>(item);
                itemCopy.remove("page");
                List<Map<String, Object>> result =
                        addPageNumberToToc(pageContents.toString(), List.of(itemCopy), model);
                if (!result.isEmpty()) {
                    Object piVal = result.get(0).get("physical_index");
                    if (piVal instanceof String s && s.startsWith("<physical_index")) {
                        item.put("physical_index", TreeUtils.parsePhysicalIndex(s));
                        item.remove("page");
                    }
                }
            }
        }
        return tocItems;
    }

    // =========================================================================
    // 6. TOC checking orchestrator
    // =========================================================================

    /**
     * Scan the first N pages for a TOC, returning its content and type.
     * Mirrors check_toc(page_list, opt).
     */
    public Map<String, Object> checkToc(List<PdfParser.PageEntry> pageList, PageIndexConfig opt) {
        List<Integer> tocPageList = findTocPages(0, pageList, opt, null);
        if (tocPageList.isEmpty()) {
            System.out.println("no toc found");
            return Map.of("toc_content", "", "toc_page_list", List.of(), "page_index_given_in_toc", "no");
        }
        System.out.println("toc found");
        Map<String, Object> tocJson = tocExtractor(pageList, tocPageList, opt.model);

        if ("yes".equals(tocJson.get("page_index_given_in_toc"))) {
            System.out.println("index found");
            return Map.of("toc_content", tocJson.get("toc_content"),
                    "toc_page_list", tocPageList, "page_index_given_in_toc", "yes");
        }

        // Search further for TOC with page numbers
        int currentStart = tocPageList.get(tocPageList.size() - 1) + 1;
        while (!"yes".equals(tocJson.get("page_index_given_in_toc"))
                && currentStart < pageList.size()
                && currentStart < opt.tocCheckPageNum) {

            List<Integer> additional = findTocPages(currentStart, pageList, opt, null);
            if (additional.isEmpty()) break;

            Map<String, Object> additionalJson = tocExtractor(pageList, additional, opt.model);
            if ("yes".equals(additionalJson.get("page_index_given_in_toc"))) {
                System.out.println("index found");
                return Map.of("toc_content", additionalJson.get("toc_content"),
                        "toc_page_list", additional, "page_index_given_in_toc", "yes");
            }
            currentStart = additional.get(additional.size() - 1) + 1;
        }

        System.out.println("index not found");
        return Map.of("toc_content", tocJson.getOrDefault("toc_content", ""),
                "toc_page_list", tocPageList, "page_index_given_in_toc", "no");
    }

    // =========================================================================
    // 7. TOC verification and fixing
    // =========================================================================

    /**
     * Fix a single incorrect TOC item by searching the surrounding page range.
     * Mirrors single_toc_item_index_fixer(section_title, content, model).
     */
    public Integer singleTocItemIndexFixer(String sectionTitle, String content, String model) {
        String prompt = """
                You are given a section title and several pages of a document, your job is to find the physical index of the start page of the section in the partial document.

                The provided pages contains tags like <physical_index_X> and <physical_index_X> to indicate the physical location of the page X.

                Reply in a JSON format:
                {
                    "thinking": <explain which page, started and closed by <physical_index_X>, contains the start of this section>,
                    "physical_index": "<physical_index_X>" (keep the format)
                }
                Directly return the final JSON structure. Do not output anything else."""
                + "\nSection Title:\n" + sectionTitle + "\nDocument pages:\n" + content;
        String response = ai.call(model, prompt);
        JsonNode json = TreeUtils.extractJson(response);
        String piStr = json.has("physical_index") ? json.get("physical_index").asText() : null;
        return TreeUtils.parsePhysicalIndex(piStr);
    }

    /**
     * Fix incorrectly placed TOC items concurrently, then verify corrections.
     * Mirrors fix_incorrect_toc(...) in page_index.py.
     */
    public FixResult fixIncorrectToc(
            List<Map<String, Object>> tocWithPageNumber,
            List<PdfParser.PageEntry> pageList,
            List<Map<String, Object>> incorrectResults,
            int startIndex, String model, JsonLogger logger) {

        System.out.println("start fix_incorrect_toc with " + incorrectResults.size() + " incorrect results");
        Set<Integer> incorrectIndices = incorrectResults.stream()
                .map(r -> TreeUtils.toInt(r.get("list_index"), -1))
                .collect(Collectors.toSet());
        int endIndex = pageList.size() + startIndex - 1;

        // Process each incorrect item concurrently
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        for (Map<String, Object> incorrectItem : incorrectResults) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                int listIndex = TreeUtils.toInt(incorrectItem.get("list_index"), -1);
                if (listIndex < 0 || listIndex >= tocWithPageNumber.size()) {
                    return Map.of("list_index", listIndex, "title", incorrectItem.getOrDefault("title", ""),
                            "physical_index", incorrectItem.getOrDefault("physical_index", (Object) null),
                            "is_valid", false);
                }

                // Find prev correct
                int prevCorrect = startIndex - 1;
                for (int i = listIndex - 1; i >= 0; i--) {
                    if (!incorrectIndices.contains(i)) {
                        Object pi = tocWithPageNumber.get(i).get("physical_index");
                        if (pi != null) { prevCorrect = TreeUtils.toInt(pi, prevCorrect); break; }
                    }
                }

                // Find next correct
                int nextCorrect = endIndex;
                for (int i = listIndex + 1; i < tocWithPageNumber.size(); i++) {
                    if (!incorrectIndices.contains(i)) {
                        Object pi = tocWithPageNumber.get(i).get("physical_index");
                        if (pi != null) { nextCorrect = TreeUtils.toInt(pi, nextCorrect); break; }
                    }
                }

                StringBuilder pageContents = new StringBuilder();
                for (int pi = prevCorrect; pi <= nextCorrect; pi++) {
                    int idx = pi - startIndex;
                    if (idx >= 0 && idx < pageList.size()) {
                        pageContents.append("<physical_index_").append(pi).append(">\n")
                                .append(pageList.get(idx).text()).append("\n")
                                .append("<physical_index_").append(pi).append(">\n\n");
                    }
                }

                String title = (String) incorrectItem.get("title");
                Integer fixedIndex = singleTocItemIndexFixer(title, pageContents.toString(), model);

                // Verify the fix
                Map<String, Object> checkItem = new LinkedHashMap<>(incorrectItem);
                checkItem.put("physical_index", fixedIndex);
                Map<String, Object> checkResult =
                        checkTitleAppearance(checkItem, pageList, startIndex, model).join();

                Map<String, Object> res = new LinkedHashMap<>();
                res.put("list_index", listIndex);
                res.put("title", title);
                res.put("physical_index", fixedIndex);
                res.put("is_valid", "yes".equals(checkResult.get("answer")));
                return res;
            }, OpenAIClient.EXECUTOR));
        }

        List<Map<String, Object>> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Map<String, Object>> invalidResults = new ArrayList<>();
        for (Map<String, Object> result : results) {
            boolean valid = Boolean.TRUE.equals(result.get("is_valid"));
            int idx = TreeUtils.toInt(result.get("list_index"), -1);
            if (valid && idx >= 0 && idx < tocWithPageNumber.size()) {
                tocWithPageNumber.get(idx).put("physical_index", result.get("physical_index"));
            } else {
                Map<String, Object> inv = new LinkedHashMap<>();
                inv.put("list_index", result.get("list_index"));
                inv.put("title", result.get("title"));
                inv.put("physical_index", result.get("physical_index"));
                invalidResults.add(inv);
            }
        }

        logger.info("invalid_results: " + invalidResults);
        return new FixResult(tocWithPageNumber, invalidResults);
    }

    /** Retry fix_incorrect_toc up to maxAttempts times. */
    public FixResult fixIncorrectTocWithRetries(
            List<Map<String, Object>> tocWithPageNumber,
            List<PdfParser.PageEntry> pageList,
            List<Map<String, Object>> incorrectResults,
            int startIndex, int maxAttempts, String model, JsonLogger logger) {

        System.out.println("start fix_incorrect_toc");
        List<Map<String, Object>> currentToc = tocWithPageNumber;
        List<Map<String, Object>> currentIncorrect = incorrectResults;
        int attempt = 0;

        while (!currentIncorrect.isEmpty()) {
            System.out.println("Fixing " + currentIncorrect.size() + " incorrect results");
            FixResult fix = fixIncorrectToc(currentToc, pageList, currentIncorrect, startIndex, model, logger);
            currentToc = fix.toc();
            currentIncorrect = fix.incorrectResults();
            attempt++;
            if (attempt >= maxAttempts) {
                logger.info("Maximum fix attempts reached");
                break;
            }
        }
        return new FixResult(currentToc, currentIncorrect);
    }

    public record FixResult(List<Map<String, Object>> toc, List<Map<String, Object>> incorrectResults) {}

    /**
     * Concurrently verify all TOC items against the actual PDF pages.
     * Mirrors verify_toc(page_list, list_result, start_index, N, model).
     */
    public VerifyResult verifyToc(List<PdfParser.PageEntry> pageList,
                                   List<Map<String, Object>> listResult,
                                   int startIndex, Integer N, String model) {
        System.out.println("start verify_toc");

        // Find last non-null physical_index
        Integer lastPhysical = null;
        for (int i = listResult.size() - 1; i >= 0; i--) {
            Object pi = listResult.get(i).get("physical_index");
            if (pi != null) { lastPhysical = TreeUtils.toInt(pi, -1); break; }
        }
        if (lastPhysical == null || lastPhysical < pageList.size() / 2.0) {
            return new VerifyResult(0.0, List.of());
        }

        // Determine sample
        List<Integer> sampleIndices;
        if (N == null) {
            sampleIndices = new ArrayList<>();
            for (int i = 0; i < listResult.size(); i++) sampleIndices.add(i);
        } else {
            int n = Math.min(N, listResult.size());
            List<Integer> all = new ArrayList<>();
            for (int i = 0; i < listResult.size(); i++) all.add(i);
            Collections.shuffle(all);
            sampleIndices = all.subList(0, n);
        }

        // Build indexed sample, skip null physical_index
        List<Map<String, Object>> indexedSample = new ArrayList<>();
        for (int idx : sampleIndices) {
            Map<String, Object> item = listResult.get(idx);
            if (item.get("physical_index") != null) {
                Map<String, Object> copy = new LinkedHashMap<>(item);
                copy.put("list_index", idx);
                indexedSample.add(copy);
            }
        }

        // Concurrent checks
        List<CompletableFuture<Map<String, Object>>> futures = indexedSample.stream()
                .map(item -> checkTitleAppearance(item, pageList, startIndex, model))
                .collect(Collectors.toList());
        List<Map<String, Object>> results = futures.stream()
                .map(CompletableFuture::join).collect(Collectors.toList());

        int correct = 0;
        List<Map<String, Object>> incorrect = new ArrayList<>();
        for (Map<String, Object> r : results) {
            if ("yes".equals(r.get("answer"))) correct++;
            else incorrect.add(r);
        }
        double accuracy = results.isEmpty() ? 0.0 : (double) correct / results.size();
        System.out.printf("accuracy: %.2f%%%n", accuracy * 100);
        return new VerifyResult(accuracy, incorrect);
    }

    public record VerifyResult(double accuracy, List<Map<String, Object>> incorrectResults) {}

    // =========================================================================
    // 8. Meta processor
    // =========================================================================

    /**
     * Dispatch to the correct processing mode, then verify and fix.
     * Mirrors meta_processor(page_list, mode, ...) in page_index.py.
     */
    public List<Map<String, Object>> metaProcessor(
            List<PdfParser.PageEntry> pageList, String mode,
            String tocContent, List<Integer> tocPageList,
            int startIndex, PageIndexConfig opt, JsonLogger logger) {

        System.out.println(mode);
        System.out.println("start_index: " + startIndex);

        List<Map<String, Object>> toc;
        if ("process_toc_with_page_numbers".equals(mode)) {
            try {
                toc = processTocWithPageNumbers(tocContent, tocPageList, pageList,
                        opt.tocCheckPageNum, opt.model, logger);
            } catch (Exception e) {
                System.out.println("process_toc_with_page_numbers failed (" + e.getMessage() + "), falling back to process_toc_no_page_numbers");
                return metaProcessor(pageList, "process_toc_no_page_numbers",
                        tocContent, tocPageList, startIndex, opt, logger);
            }
        } else if ("process_toc_no_page_numbers".equals(mode)) {
            try {
                toc = processTocNoPageNumbers(tocContent, tocPageList, pageList,
                        startIndex, opt.model, logger);
            } catch (Exception e) {
                System.out.println("process_toc_no_page_numbers failed (" + e.getMessage() + "), falling back to process_no_toc");
                return metaProcessor(pageList, "process_no_toc",
                        null, null, startIndex, opt, logger);
            }
        } else {
            toc = processNoToc(pageList, startIndex, opt.model, logger);
        }

        // Filter null physical_index
        toc = toc.stream().filter(i -> i.get("physical_index") != null).collect(Collectors.toList());
        toc = TreeUtils.validateAndTruncatePhysicalIndices(toc, pageList.size(), startIndex);

        VerifyResult vr = verifyToc(pageList, toc, startIndex, null, opt.model);
        logger.info("accuracy: " + vr.accuracy() + " incorrect: " + vr.incorrectResults().size());

        if (vr.accuracy() == 1.0 && vr.incorrectResults().isEmpty()) return toc;

        if (vr.accuracy() > 0.4 && !vr.incorrectResults().isEmpty()) {
            FixResult fr = fixIncorrectTocWithRetries(toc, pageList, vr.incorrectResults(),
                    startIndex, 3, opt.model, logger);
            return fr.toc();
        }

        // Fall back to less structured mode
        if ("process_toc_with_page_numbers".equals(mode)) {
            return metaProcessor(pageList, "process_toc_no_page_numbers",
                    tocContent, tocPageList, startIndex, opt, logger);
        } else if ("process_toc_no_page_numbers".equals(mode)) {
            return metaProcessor(pageList, "process_no_toc",
                    null, null, startIndex, opt, logger);
        }
        // All modes exhausted — return best-effort result rather than crashing.
        // This can happen with small local models that produce low-accuracy output.
        System.out.println("Warning: verification accuracy " + vr.accuracy() + " below threshold — returning best-effort result");
        return toc;
    }

    // =========================================================================
    // 9. Recursive large-node splitting
    // =========================================================================

    /**
     * If a node spans too many pages/tokens, recursively sub-index it.
     * Mirrors process_large_node_recursively(node, page_list, opt, logger).
     */
    @SuppressWarnings("unchecked")
    public void processLargeNodeRecursively(Map<String, Object> node,
                                             List<PdfParser.PageEntry> pageList,
                                             PageIndexConfig opt, JsonLogger logger) {
        int start = TreeUtils.toInt(node.get("start_index"), 1);
        int end = TreeUtils.toInt(node.get("end_index"), pageList.size());

        List<PdfParser.PageEntry> nodePagesSlice = pageList.subList(start - 1, Math.min(end, pageList.size()));
        int tokenNum = nodePagesSlice.stream().mapToInt(PdfParser.PageEntry::tokenCount).sum();

        if ((end - start) > opt.maxPageNumEachNode && tokenNum >= opt.maxTokenNumEachNode) {
            System.out.println("large node: " + node.get("title") + " start: " + start + " end: " + end);

            List<Map<String, Object>> nodeTocTree = metaProcessor(nodePagesSlice,
                    "process_no_toc", null, null, start, opt, logger);
            nodeTocTree = checkTitleAppearanceInStartConcurrent(nodeTocTree, pageList, opt.model, logger);
            List<Map<String, Object>> valid = nodeTocTree.stream()
                    .filter(i -> i.get("physical_index") != null).collect(Collectors.toList());

            String nodeTitle = ((String) node.getOrDefault("title", "")).strip();
            if (!valid.isEmpty() && nodeTitle.equals(((String) valid.get(0).getOrDefault("title", "")).strip())) {
                node.put("nodes", TreeUtils.postProcessing(valid.subList(1, valid.size()), end));
                if (valid.size() > 1)
                    node.put("end_index", TreeUtils.toInt(valid.get(1).get("start_index"), end));
            } else {
                node.put("nodes", TreeUtils.postProcessing(valid, end));
                if (!valid.isEmpty())
                    node.put("end_index", TreeUtils.toInt(valid.get(0).get("start_index"), end));
            }
        }

        // Recurse into children
        Object children = node.get("nodes");
        if (children instanceof List<?> list && !((List<?>) list).isEmpty()) {
            List<CompletableFuture<Void>> futures = ((List<Map<String, Object>>) list).stream()
                    .map(child -> CompletableFuture.runAsync(
                            () -> processLargeNodeRecursively(child, pageList, opt, logger),
                            OpenAIClient.EXECUTOR))
                    .collect(Collectors.toList());
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    // =========================================================================
    // 10. Main tree parser
    // =========================================================================

    /**
     * Build the full hierarchical tree from a PDF's page list.
     * Mirrors tree_parser(page_list, opt, doc, logger).
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> treeParser(List<PdfParser.PageEntry> pageList,
                                                  PageIndexConfig opt, JsonLogger logger) {
        Map<String, Object> checkTocResult = checkToc(pageList, opt);
        logger.info(checkTocResult.toString());

        String tocContent = (String) checkTocResult.get("toc_content");
        List<Integer> tocPageList = (List<Integer>) checkTocResult.get("toc_page_list");
        String hasPageIndex = (String) checkTocResult.get("page_index_given_in_toc");

        List<Map<String, Object>> tocWithPageNumber;
        if (tocContent != null && !tocContent.isBlank() && "yes".equals(hasPageIndex)) {
            tocWithPageNumber = metaProcessor(pageList, "process_toc_with_page_numbers",
                    tocContent, tocPageList, 1, opt, logger);
        } else {
            tocWithPageNumber = metaProcessor(pageList, "process_no_toc",
                    null, null, 1, opt, logger);
        }

        tocWithPageNumber = TreeUtils.addPrefaceIfNeeded(tocWithPageNumber);
        tocWithPageNumber = checkTitleAppearanceInStartConcurrent(tocWithPageNumber, pageList, opt.model, logger);

        List<Map<String, Object>> validItems = tocWithPageNumber.stream()
                .filter(i -> i.get("physical_index") != null).collect(Collectors.toList());

        List<Map<String, Object>> tocTree = TreeUtils.postProcessing(validItems, pageList.size());

        List<CompletableFuture<Void>> futures = tocTree.stream()
                .map(node -> CompletableFuture.runAsync(
                        () -> processLargeNodeRecursively(node, pageList, opt, logger),
                        OpenAIClient.EXECUTOR))
                .collect(Collectors.toList());
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return tocTree;
    }

    // =========================================================================
    // 11. Node summary generation
    // =========================================================================

    /**
     * Generate LLM summaries for all nodes in the tree concurrently.
     * Mirrors generate_summaries_for_structure in utils.py.
     */
    public void generateSummariesForStructure(List<Map<String, Object>> structure, String model) {
        List<Map<String, Object>> nodes = TreeUtils.structureToList(structure);
        List<CompletableFuture<Void>> futures = nodes.stream()
                .map(node -> CompletableFuture.runAsync(() -> {
                    String text = (String) node.get("text");
                    if (text == null || text.isBlank()) return;
                    String prompt = """
                            You are given a part of a document, your task is to generate a description of the partial document about what are main points covered in the partial document.

                            Partial Document Text: %s

                            Directly return the description, do not include any other text.""".formatted(text);
                    String summary = ai.call(model, prompt);
                    node.put("summary", summary);
                }, OpenAIClient.EXECUTOR))
                .collect(Collectors.toList());
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Generate a one-sentence description for the whole document.
     * Mirrors generate_doc_description in utils.py.
     */
    public String generateDocDescription(Object structure, String model) {
        String prompt = """
                Your are an expert in generating descriptions for a document.
                You are given a structure of a document. Your task is to generate a one-sentence description for the document, which makes it easy to distinguish the document from other documents.

                Document Structure: %s

                Directly return the description, do not include any other text."""
                .formatted(structure);
        return ai.call(model, prompt);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private List<Map<String, Object>> deepCopyList(List<Map<String, Object>> list) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> item : list) copy.add(new LinkedHashMap<>(item));
        return copy;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> jsonArrayToList(JsonNode node) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (node == null || !node.isArray()) return result;
        for (JsonNode elem : node) {
            Map<String, Object> map = new LinkedHashMap<>();
            elem.fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                if (v.isNull()) map.put(e.getKey(), null);
                else if (v.isInt()) map.put(e.getKey(), v.intValue());
                else if (v.isLong()) map.put(e.getKey(), (int) v.longValue());
                else if (v.isTextual()) map.put(e.getKey(), v.asText());
                else if (v.isBoolean()) map.put(e.getKey(), v.booleanValue());
                else map.put(e.getKey(), v.asText());
            });
            result.add(map);
        }
        return result;
    }

    private void convertPageToInt(List<Map<String, Object>> list) {
        for (Map<String, Object> item : list) {
            Object page = item.get("page");
            if (page instanceof String s) {
                try { item.put("page", Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
            }
        }
    }
}
