package ai.pageindex;

import ai.pageindex.config.ConfigLoader;
import ai.pageindex.config.PageIndexConfig;
import ai.pageindex.core.PageIndexPdf;
import ai.pageindex.util.JsonLogger;
import ai.pageindex.util.OpenAIClient;
import ai.pageindex.util.PdfParser;
import ai.pageindex.util.TreeUtils;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Public API for PageIndexJ.
 * Mirrors page_index_main() and page_index() from pageindex/page_index.py.
 *
 * Usage:
 *   Map<String, Object> result = PageIndex.pageIndexMain("/path/to/file.pdf", config);
 */
public class PageIndex {

    /**
     * Full pipeline: parse PDF → build tree → optionally enrich → return result map.
     * Mirrors page_index_main(doc, opt) in page_index.py.
     *
     * @param pdfPath  absolute path to the PDF file
     * @param opt      configuration (null = all defaults from config.yaml)
     * @return map with "doc_name", optional "doc_description", and "structure"
     */
    public static Map<String, Object> pageIndexMain(String pdfPath, PageIndexConfig opt) throws IOException {
        if (opt == null) opt = new ConfigLoader().load();
        OpenAIClient ai = new OpenAIClient(opt.baseUrl, opt.numCtx > 0 ? opt.numCtx : 8192);
        return pageIndexMain(pdfPath, opt, ai);
    }

    /**
     * Full pipeline with a pre-built OpenAIClient (e.g. a rotating free-cloud pool).
     */
    public static Map<String, Object> pageIndexMain(String pdfPath, PageIndexConfig opt, OpenAIClient ai) throws IOException {
        // Validate input
        File f = new File(pdfPath);
        if (!f.isFile() || !pdfPath.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Expected a valid PDF file path: " + pdfPath);
        }

        if (opt == null) opt = new ConfigLoader().load();

        JsonLogger logger = new JsonLogger(pdfPath);
        System.out.println("Parsing PDF...");
        List<PdfParser.PageEntry> pageList = PdfParser.getPageTokens(pdfPath, opt.model);

        logger.info("total_page_number: " + pageList.size());
        int totalTokens = pageList.stream().mapToInt(PdfParser.PageEntry::tokenCount).sum();
        logger.info("total_token: " + totalTokens);

        PageIndexPdf indexer = new PageIndexPdf(ai);

        // Build the tree
        List<Map<String, Object>> structure = indexer.treeParser(pageList, opt, logger);

        // Enrich: node IDs
        if ("yes".equals(opt.ifAddNodeId)) {
            TreeUtils.writeNodeId(structure, 0);
        }

        // Enrich: node text
        if ("yes".equals(opt.ifAddNodeText)) {
            TreeUtils.addNodeText(structure, pageList);
        }

        // Enrich: summaries
        if ("yes".equals(opt.ifAddNodeSummary)) {
            if (!"yes".equals(opt.ifAddNodeText)) {
                TreeUtils.addNodeText(structure, pageList);
            }
            indexer.generateSummariesForStructure(structure, opt.model);
            if (!"yes".equals(opt.ifAddNodeText)) {
                TreeUtils.removeNodeText(structure);
            }

            // Enrich: doc description
            if ("yes".equals(opt.ifAddDocDescription)) {
                String docDescription = indexer.generateDocDescription(structure, opt.model);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("doc_name", f.getName());
                result.put("doc_description", docDescription);
                result.put("structure", structure);
                return result;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("doc_name", f.getName());
        result.put("structure", structure);
        return result;
    }

    /**
     * Convenience overload with individual parameter overrides.
     * Mirrors page_index(doc, model, ...) in page_index.py.
     */
    public static Map<String, Object> pageIndex(
            String pdfPath,
            String model,
            Integer tocCheckPageNum,
            Integer maxPageNumEachNode,
            Integer maxTokenNumEachNode,
            String ifAddNodeId,
            String ifAddNodeSummary,
            String ifAddDocDescription,
            String ifAddNodeText) throws IOException {

        PageIndexConfig userOpt = new PageIndexConfig();
        if (model != null) userOpt.model = model;
        if (tocCheckPageNum != null) userOpt.tocCheckPageNum = tocCheckPageNum;
        if (maxPageNumEachNode != null) userOpt.maxPageNumEachNode = maxPageNumEachNode;
        if (maxTokenNumEachNode != null) userOpt.maxTokenNumEachNode = maxTokenNumEachNode;
        if (ifAddNodeId != null) userOpt.ifAddNodeId = ifAddNodeId;
        if (ifAddNodeSummary != null) userOpt.ifAddNodeSummary = ifAddNodeSummary;
        if (ifAddDocDescription != null) userOpt.ifAddDocDescription = ifAddDocDescription;
        if (ifAddNodeText != null) userOpt.ifAddNodeText = ifAddNodeText;

        PageIndexConfig merged = new ConfigLoader().load(userOpt);
        return pageIndexMain(pdfPath, merged);
    }

    /**
     * Convenience: run with all defaults.
     */
    public static Map<String, Object> pageIndex(String pdfPath) throws IOException {
        return pageIndexMain(pdfPath, new ConfigLoader().load());
    }

    /**
     * Convert the result Map to a pretty JSON string.
     */
    public static String toJson(Map<String, Object> result) throws Exception {
        ObjectNode root = TreeUtils.MAPPER.createObjectNode();
        result.forEach((k, v) -> {
            if (v instanceof String s) root.put(k, s);
            else if (v instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                ArrayNode arr = TreeUtils.toJsonArray((List<Map<String, Object>>) list);
                root.set(k, arr);
            }
        });
        return TreeUtils.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }
}
