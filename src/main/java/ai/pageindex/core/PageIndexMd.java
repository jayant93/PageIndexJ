package ai.pageindex.core;

import ai.pageindex.util.OpenAIClient;
import ai.pageindex.util.PdfParser;
import ai.pageindex.util.TreeUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Markdown indexing engine.
 * Full Java translation of pageindex/page_index_md.py.
 */
public class PageIndexMd {

    private final OpenAIClient ai;

    public PageIndexMd(OpenAIClient ai) {
        this.ai = ai;
    }

    // =========================================================================
    // 1. Markdown parsing
    // =========================================================================

    /**
     * Result of extracting nodes from markdown.
     */
    public record ExtractionResult(List<Map<String, Object>> nodeList, List<String> lines) {}

    /**
     * Extract header nodes from markdown content.
     * Mirrors extract_nodes_from_markdown(markdown_content).
     */
    public ExtractionResult extractNodesFromMarkdown(String markdownContent) {
        Pattern headerPattern = Pattern.compile("^(#{1,6})\\s+(.+)$");
        Pattern codeBlockPattern = Pattern.compile("^```");

        List<Map<String, Object>> nodeList = new ArrayList<>();
        List<String> lines = Arrays.asList(markdownContent.split("\n", -1));

        boolean inCodeBlock = false;
        for (int lineNum = 1; lineNum <= lines.size(); lineNum++) {
            String line = lines.get(lineNum - 1);
            String stripped = line.strip();

            if (codeBlockPattern.matcher(stripped).find()) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (stripped.isEmpty()) continue;

            if (!inCodeBlock) {
                Matcher m = headerPattern.matcher(stripped);
                if (m.matches()) {
                    String title = m.group(2).strip();
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("node_title", title);
                    node.put("line_num", lineNum);
                    nodeList.add(node);
                }
            }
        }
        return new ExtractionResult(nodeList, lines);
    }

    /**
     * Enrich each node with its level, text content, and line_num.
     * Mirrors extract_node_text_content(node_list, markdown_lines).
     */
    public List<Map<String, Object>> extractNodeTextContent(
            List<Map<String, Object>> nodeList, List<String> markdownLines) {

        Pattern headerPattern = Pattern.compile("^(#{1,6})");
        List<Map<String, Object>> allNodes = new ArrayList<>();

        for (Map<String, Object> node : nodeList) {
            int lineNum = (int) node.get("line_num");
            String lineContent = markdownLines.get(lineNum - 1);
            Matcher m = headerPattern.matcher(lineContent);
            if (!m.find()) {
                System.out.println("Warning: Line " + lineNum + " is not a valid header: " + lineContent);
                continue;
            }
            Map<String, Object> processed = new LinkedHashMap<>();
            processed.put("title", node.get("node_title"));
            processed.put("line_num", lineNum);
            processed.put("level", m.group(1).length());
            allNodes.add(processed);
        }

        // Assign text slices
        for (int i = 0; i < allNodes.size(); i++) {
            Map<String, Object> n = allNodes.get(i);
            int startLine = (int) n.get("line_num") - 1; // 0-based
            int endLine = (i + 1 < allNodes.size())
                    ? (int) allNodes.get(i + 1).get("line_num") - 1
                    : markdownLines.size();
            String text = String.join("\n", markdownLines.subList(startLine, endLine)).strip();
            n.put("text", text);
        }
        return allNodes;
    }

    /**
     * Calculate cumulative token counts per node (including descendants).
     * Mirrors update_node_list_with_text_token_count(node_list, model).
     */
    public List<Map<String, Object>> updateNodeListWithTextTokenCount(
            List<Map<String, Object>> nodeList, String model) {

        List<Map<String, Object>> result = new ArrayList<>(nodeList);

        // Process from end to start so children are counted before parents
        for (int i = result.size() - 1; i >= 0; i--) {
            Map<String, Object> current = result.get(i);
            int currentLevel = (int) current.get("level");
            String totalText = (String) current.getOrDefault("text", "");

            // Add all descendant text
            for (int j = i + 1; j < result.size(); j++) {
                if ((int) result.get(j).get("level") <= currentLevel) break;
                String childText = (String) result.get(j).getOrDefault("text", "");
                if (childText != null && !childText.isBlank())
                    totalText += "\n" + childText;
            }
            current.put("text_token_count", PdfParser.countTokens(totalText, model));
        }
        return result;
    }

    /**
     * Collapse small nodes into their parents to avoid over-fragmentation.
     * Mirrors tree_thinning_for_index(node_list, min_node_token, model).
     */
    public List<Map<String, Object>> treeThinningForIndex(
            List<Map<String, Object>> nodeList, int minNodeToken, String model) {

        List<Map<String, Object>> result = new ArrayList<>(nodeList);
        Set<Integer> toRemove = new HashSet<>();

        for (int i = result.size() - 1; i >= 0; i--) {
            if (toRemove.contains(i)) continue;
            Map<String, Object> current = result.get(i);
            int currentLevel = (int) current.get("level");
            int totalTokens = (int) current.getOrDefault("text_token_count", 0);

            if (totalTokens < minNodeToken) {
                // Collect all direct+indirect children
                List<Integer> childrenIndices = new ArrayList<>();
                for (int j = i + 1; j < result.size(); j++) {
                    if ((int) result.get(j).get("level") <= currentLevel) break;
                    childrenIndices.add(j);
                }

                // Merge children text into current node
                StringBuilder merged = new StringBuilder((String) current.getOrDefault("text", ""));
                for (int ci : childrenIndices) {
                    if (toRemove.contains(ci)) continue;
                    String childText = (String) result.get(ci).getOrDefault("text", "");
                    if (childText != null && !childText.isBlank()) {
                        if (merged.length() > 0 && !merged.toString().endsWith("\n")) merged.append("\n\n");
                        merged.append(childText);
                    }
                    toRemove.add(ci);
                }
                current.put("text", merged.toString());
                current.put("text_token_count", PdfParser.countTokens(merged.toString(), model));
            }
        }

        // Remove collapsed nodes (descending order to preserve indices)
        List<Integer> sortedRemove = new ArrayList<>(toRemove);
        sortedRemove.sort(Collections.reverseOrder());
        for (int idx : sortedRemove) result.remove(idx);

        return result;
    }

    /**
     * Build a nested tree from a flat ordered list of nodes.
     * Mirrors build_tree_from_nodes(node_list).
     */
    public List<Map<String, Object>> buildTreeFromNodes(List<Map<String, Object>> nodeList) {
        if (nodeList.isEmpty()) return List.of();

        Deque<Object[]> stack = new ArrayDeque<>(); // [node, level]
        List<Map<String, Object>> rootNodes = new ArrayList<>();
        int nodeCounter = 1;

        for (Map<String, Object> node : nodeList) {
            int currentLevel = (int) node.get("level");
            Map<String, Object> treeNode = new LinkedHashMap<>();
            treeNode.put("title", node.get("title"));
            treeNode.put("node_id", String.format("%04d", nodeCounter++));
            treeNode.put("text", node.get("text"));
            treeNode.put("line_num", node.get("line_num"));
            treeNode.put("nodes", new ArrayList<Map<String, Object>>());

            // Pop stack until we find parent
            while (!stack.isEmpty() && (int) ((Object[]) stack.peek())[1] >= currentLevel) {
                stack.pop();
            }

            if (stack.isEmpty()) {
                rootNodes.add(treeNode);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> parent = (Map<String, Object>) ((Object[]) stack.peek())[0];
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> siblings = (List<Map<String, Object>>) parent.get("nodes");
                siblings.add(treeNode);
            }
            stack.push(new Object[]{treeNode, currentLevel});
        }
        return rootNodes;
    }

    /**
     * Remove empty "nodes" lists and strip unwanted fields for clean output.
     * Mirrors clean_tree_for_output(tree_nodes).
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> cleanTreeForOutput(List<Map<String, Object>> treeNodes) {
        List<Map<String, Object>> cleaned = new ArrayList<>();
        for (Map<String, Object> node : treeNodes) {
            Map<String, Object> cleanNode = new LinkedHashMap<>();
            cleanNode.put("title", node.get("title"));
            cleanNode.put("node_id", node.get("node_id"));
            cleanNode.put("text", node.get("text"));
            cleanNode.put("line_num", node.get("line_num"));

            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("nodes");
            if (children != null && !children.isEmpty()) {
                cleanNode.put("nodes", cleanTreeForOutput(children));
            }
            cleaned.add(cleanNode);
        }
        return cleaned;
    }

    // =========================================================================
    // 2. Summary generation
    // =========================================================================

    /**
     * Generate or pass-through summary for a single node based on token count.
     * Mirrors get_node_summary(node, summary_token_threshold, model).
     */
    public String getNodeSummary(Map<String, Object> node, int summaryTokenThreshold, String model) {
        String text = (String) node.getOrDefault("text", "");
        int tokens = PdfParser.countTokens(text, model);
        if (tokens < summaryTokenThreshold) return text;

        String prompt = """
                You are given a part of a document, your task is to generate a description of the partial document about what are main points covered in the partial document.

                Partial Document Text: %s

                Directly return the description, do not include any other text.""".formatted(text);
        return ai.call(model, prompt);
    }

    /**
     * Generate summaries for all nodes concurrently.
     * Mirrors generate_summaries_for_structure_md(structure, summary_token_threshold, model).
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> generateSummariesForStructureMd(
            List<Map<String, Object>> structure, int summaryTokenThreshold, String model) {

        List<Map<String, Object>> nodes = TreeUtils.structureToList(structure);
        List<CompletableFuture<Void>> futures = nodes.stream()
                .map(node -> CompletableFuture.runAsync(() -> {
                    String summary = getNodeSummary(node, summaryTokenThreshold, model);
                    List<Map<String, Object>> children =
                            (List<Map<String, Object>>) node.get("nodes");
                    boolean isLeaf = children == null || children.isEmpty();
                    if (isLeaf) node.put("summary", summary);
                    else node.put("prefix_summary", summary);
                }, OpenAIClient.EXECUTOR))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return structure;
    }

    // =========================================================================
    // 3. Main entry point
    // =========================================================================

    /**
     * Convert a Markdown file to a PageIndex tree structure.
     * Mirrors md_to_tree(md_path, if_thinning, min_token_threshold, ...).
     */
    public Map<String, Object> mdToTree(
            String mdPath,
            boolean ifThinning,
            int minTokenThreshold,
            String ifAddNodeSummary,
            int summaryTokenThreshold,
            String model,
            String ifAddDocDescription,
            String ifAddNodeText,
            String ifAddNodeId) throws IOException {

        String markdownContent = new String(Files.readAllBytes(Paths.get(mdPath)));

        System.out.println("Extracting nodes from markdown...");
        ExtractionResult extraction = extractNodesFromMarkdown(markdownContent);

        System.out.println("Extracting text content from nodes...");
        List<Map<String, Object>> nodesWithContent =
                extractNodeTextContent(extraction.nodeList(), extraction.lines());

        if (ifThinning) {
            nodesWithContent = updateNodeListWithTextTokenCount(nodesWithContent, model);
            System.out.println("Thinning nodes...");
            nodesWithContent = treeThinningForIndex(nodesWithContent, minTokenThreshold, model);
        }

        System.out.println("Building tree from nodes...");
        List<Map<String, Object>> treeStructure = buildTreeFromNodes(nodesWithContent);

        if ("yes".equals(ifAddNodeId)) {
            TreeUtils.writeNodeId(treeStructure, 0);
        }

        System.out.println("Formatting tree structure...");

        if ("yes".equals(ifAddNodeSummary)) {
            System.out.println("Generating summaries for each node...");
            treeStructure = generateSummariesForStructureMd(
                    treeStructure, summaryTokenThreshold, model);

            if ("no".equals(ifAddNodeText)) {
                removeTextFromTree(treeStructure);
            }

            if ("yes".equals(ifAddDocDescription)) {
                System.out.println("Generating document description...");
                String docDescription = generateDocDescription(treeStructure, model);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("doc_name", Paths.get(mdPath).getFileName().toString()
                        .replaceAll("\\.(md|markdown)$", ""));
                result.put("doc_description", docDescription);
                result.put("structure", treeStructure);
                return result;
            }
        } else {
            if ("no".equals(ifAddNodeText)) {
                removeTextFromTree(treeStructure);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("doc_name", Paths.get(mdPath).getFileName().toString()
                .replaceAll("\\.(md|markdown)$", ""));
        result.put("structure", treeStructure);
        return result;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    @SuppressWarnings("unchecked")
    private void removeTextFromTree(List<Map<String, Object>> tree) {
        for (Map<String, Object> node : tree) {
            node.remove("text");
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("nodes");
            if (children != null) removeTextFromTree(children);
        }
    }

    private String generateDocDescription(List<Map<String, Object>> structure, String model) {
        String prompt = """
                Your are an expert in generating descriptions for a document.
                You are given a structure of a document. Your task is to generate a one-sentence description for the document, which makes it easy to distinguish the document from other documents.

                Document Structure: %s

                Directly return the description, do not include any other text."""
                .formatted(structure);
        return ai.call(model, prompt);
    }
}
