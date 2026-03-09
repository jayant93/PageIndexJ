package ai.pageindex.web;

import ai.pageindex.util.OpenAIClient;
import ai.pageindex.util.TreeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final JobService jobService;

    public QueryController(JobService jobService) {
        this.jobService = jobService;
    }

    public record QueryRequest(String docName, String question, String model, int numCtx, String tier) {}
    public record QueryResponse(String answer, List<String> sources) {}

    @PostMapping("/query")
    public QueryResponse query(@RequestBody QueryRequest req) throws Exception {
        Map<String, Object> structure = jobService.loadStructure(req.docName());
        List<Map<String, Object>> pages = jobService.loadPages(req.docName());

        OpenAIClient ai;
        String model;
        if ("free".equals(req.tier())) {
            ai = OpenAIClient.freeCloudPool();
            model = "gpt-4o-mini"; // placeholder — pool ignores this
        } else {
            String resolvedUrl = ModelRegistry.resolveBaseUrl(req.model());
            ai = new OpenAIClient(resolvedUrl, req.numCtx() > 0 ? req.numCtx() : 8192);
            model = req.model() != null && !req.model().isBlank() ? req.model() : "qwen2.5:7b";
        }

        // Step 1: Find relevant sections using the TOC structure
        String tocSummary = buildTocSummary(structure);
        String sectionPrompt = """
                You are given a document table of contents. Select the 1-3 most relevant sections for answering the question.

                Document TOC:
                %s

                Question: %s

                Return a JSON array of section titles: ["title1", "title2"]
                Return only the JSON array, nothing else."""
                .formatted(tocSummary, req.question());

        String sectionResponse = ai.call(model, sectionPrompt);
        List<String> selectedSections = parseStringArray(sectionResponse);

        // Step 2: Build context from page text or TOC
        String context;
        if (!pages.isEmpty()) {
            context = buildContextFromPages(structure, pages, selectedSections);
        } else {
            context = "Document structure:\n" + tocSummary;
        }

        // Step 3: Answer the question
        String answerPrompt = """
                Answer the question based on the provided context from the document.
                Be concise and specific. If the answer is not in the context, say so.

                Context:
                %s

                Question: %s

                Answer:""".formatted(context, req.question());

        String answer = ai.call(model, answerPrompt);
        return new QueryResponse(answer, selectedSections);
    }

    @SuppressWarnings("unchecked")
    private String buildTocSummary(Map<String, Object> structure) {
        StringBuilder sb = new StringBuilder();
        Object sections = structure.get("structure");
        if (sections instanceof List<?> list) {
            flattenToc((List<Map<String, Object>>) list, sb, 0);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void flattenToc(List<Map<String, Object>> nodes, StringBuilder sb, int depth) {
        for (Map<String, Object> node : nodes) {
            String indent = "  ".repeat(depth);
            String title = (String) node.getOrDefault("title", "Untitled");
            Object start = node.get("start_index");
            Object end = node.get("end_index");
            sb.append(indent).append("- ").append(title);
            if (start != null) sb.append(" (pages ").append(start).append("-").append(end).append(")");
            sb.append("\n");
            Object children = node.get("nodes");
            if (children instanceof List<?> childList) {
                flattenToc((List<Map<String, Object>>) childList, sb, depth + 1);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String buildContextFromPages(Map<String, Object> structure,
                                          List<Map<String, Object>> pages,
                                          List<String> selectedSections) {
        // Find page ranges for selected sections
        List<Map<String, Object>> allNodes = new ArrayList<>();
        Object sections = structure.get("structure");
        if (sections instanceof List<?> list) {
            collectNodes((List<Map<String, Object>>) list, allNodes);
        }

        StringBuilder context = new StringBuilder();
        for (String sectionTitle : selectedSections) {
            for (Map<String, Object> node : allNodes) {
                String title = (String) node.getOrDefault("title", "");
                if (title.equalsIgnoreCase(sectionTitle) || title.contains(sectionTitle)) {
                    int start = toInt(node.get("start_index"), 0);
                    int end = toInt(node.get("end_index"), start);
                    context.append("=== ").append(title).append(" ===\n");
                    for (Map<String, Object> page : pages) {
                        int idx = toInt(page.get("index"), -1);
                        if (idx >= start - 1 && idx <= end) {
                            context.append(page.get("text")).append("\n");
                        }
                    }
                    context.append("\n");
                    break;
                }
            }
        }
        return context.isEmpty() ? buildTocSummary(structure) : context.toString();
    }

    @SuppressWarnings("unchecked")
    private void collectNodes(List<Map<String, Object>> nodes, List<Map<String, Object>> all) {
        for (Map<String, Object> node : nodes) {
            all.add(node);
            Object children = node.get("nodes");
            if (children instanceof List<?> list) {
                collectNodes((List<Map<String, Object>>) list, all);
            }
        }
    }

    private List<String> parseStringArray(String response) {
        try {
            JsonNode arr = TreeUtils.extractJson(response);
            List<String> result = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) result.add(n.asText());
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private int toInt(Object val, int def) {
        if (val == null) return def;
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return def; }
    }
}
