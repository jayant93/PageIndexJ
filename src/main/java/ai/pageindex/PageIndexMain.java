package ai.pageindex;

import ai.pageindex.config.ConfigLoader;
import ai.pageindex.config.PageIndexConfig;
import ai.pageindex.core.PageIndexMd;
import ai.pageindex.util.OpenAIClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.Map;

/**
 * CLI entry point for PageIndexJ.
 * Mirrors run_pageindex.py.
 *
 * Usage:
 *   java -jar pageindexj.jar --pdf_path /path/to/doc.pdf [options]
 *   java -jar pageindexj.jar --md_path /path/to/doc.md [options]
 *
 * Options:
 *   --model                    OpenAI model (default: gpt-4o-2024-11-20)
 *   --toc-check-pages          Pages to check for TOC (default: 20)
 *   --max-pages-per-node       Max pages per node (default: 10)
 *   --max-tokens-per-node      Max tokens per node (default: 20000)
 *   --if-add-node-id           yes/no (default: yes)
 *   --if-add-node-summary      yes/no (default: yes)
 *   --if-add-doc-description   yes/no (default: no)
 *   --if-add-node-text         yes/no (default: no)
 *   --if-thinning              yes/no for markdown (default: no)
 *   --thinning-threshold       min tokens for thinning (default: 5000)
 *   --summary-token-threshold  token threshold for summaries in markdown (default: 200)
 */
public class PageIndexMain {

    public static void main(String[] args) throws Exception {
        // ---- Parse arguments -----------------------------------------------
        String pdfPath = null;
        String mdPath = null;
        String model = null;
        Integer tocCheckPages = null;
        Integer maxPagesPerNode = null;
        Integer maxTokensPerNode = null;
        String ifAddNodeId = null;
        String ifAddNodeSummary = null;
        String ifAddDocDescription = null;
        String ifAddNodeText = null;
        boolean ifThinning = false;
        int thinningThreshold = 5000;
        int summaryTokenThreshold = 200;

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--pdf_path"               -> pdfPath = args[++i];
                case "--md_path"                -> mdPath = args[++i];
                case "--model"                  -> model = args[++i];
                case "--toc-check-pages"        -> tocCheckPages = Integer.parseInt(args[++i]);
                case "--max-pages-per-node"     -> maxPagesPerNode = Integer.parseInt(args[++i]);
                case "--max-tokens-per-node"    -> maxTokensPerNode = Integer.parseInt(args[++i]);
                case "--if-add-node-id"         -> ifAddNodeId = args[++i];
                case "--if-add-node-summary"    -> ifAddNodeSummary = args[++i];
                case "--if-add-doc-description" -> ifAddDocDescription = args[++i];
                case "--if-add-node-text"       -> ifAddNodeText = args[++i];
                case "--if-thinning"            -> ifThinning = "yes".equalsIgnoreCase(args[++i]);
                case "--thinning-threshold"     -> thinningThreshold = Integer.parseInt(args[++i]);
                case "--summary-token-threshold"-> summaryTokenThreshold = Integer.parseInt(args[++i]);
            }
        }

        // ---- Validate -------------------------------------------------------
        if (pdfPath == null && mdPath == null) {
            System.err.println("Error: Either --pdf_path or --md_path must be specified");
            System.exit(1);
        }
        if (pdfPath != null && mdPath != null) {
            System.err.println("Error: Only one of --pdf_path or --md_path can be specified");
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        new File("results").mkdirs();

        // ---- PDF path -------------------------------------------------------
        if (pdfPath != null) {
            if (!pdfPath.toLowerCase().endsWith(".pdf")) {
                System.err.println("Error: PDF file must have .pdf extension");
                System.exit(1);
            }
            if (!new File(pdfPath).isFile()) {
                System.err.println("Error: PDF file not found: " + pdfPath);
                System.exit(1);
            }

            PageIndexConfig userOpt = new PageIndexConfig();
            if (model != null)              userOpt.model = model;
            if (tocCheckPages != null)      userOpt.tocCheckPageNum = tocCheckPages;
            if (maxPagesPerNode != null)    userOpt.maxPageNumEachNode = maxPagesPerNode;
            if (maxTokensPerNode != null)   userOpt.maxTokenNumEachNode = maxTokensPerNode;
            if (ifAddNodeId != null)        userOpt.ifAddNodeId = ifAddNodeId;
            if (ifAddNodeSummary != null)   userOpt.ifAddNodeSummary = ifAddNodeSummary;
            if (ifAddDocDescription != null) userOpt.ifAddDocDescription = ifAddDocDescription;
            if (ifAddNodeText != null)      userOpt.ifAddNodeText = ifAddNodeText;

            PageIndexConfig opt = new ConfigLoader().load(userOpt);

            Map<String, Object> result = PageIndex.pageIndexMain(pdfPath, opt);
            System.out.println("Parsing done, saving to file...");

            String pdfName = new File(pdfPath).getName().replaceAll("\\.pdf$", "");
            String outputFile = "results/" + pdfName + "_structure.json";
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(outputFile), result);
            System.out.println("Tree structure saved to: " + outputFile);

        // ---- Markdown path --------------------------------------------------
        } else {
            String mdLower = mdPath.toLowerCase();
            if (!mdLower.endsWith(".md") && !mdLower.endsWith(".markdown")) {
                System.err.println("Error: Markdown file must have .md or .markdown extension");
                System.exit(1);
            }
            if (!new File(mdPath).isFile()) {
                System.err.println("Error: Markdown file not found: " + mdPath);
                System.exit(1);
            }

            System.out.println("Processing markdown file...");

            PageIndexConfig userOpt = new PageIndexConfig();
            if (model != null)              userOpt.model = model;
            if (ifAddNodeSummary != null)   userOpt.ifAddNodeSummary = ifAddNodeSummary;
            if (ifAddDocDescription != null) userOpt.ifAddDocDescription = ifAddDocDescription;
            if (ifAddNodeText != null)      userOpt.ifAddNodeText = ifAddNodeText;
            if (ifAddNodeId != null)        userOpt.ifAddNodeId = ifAddNodeId;

            PageIndexConfig opt = new ConfigLoader().load(userOpt);

            OpenAIClient ai = new OpenAIClient();
            PageIndexMd mdIndexer = new PageIndexMd(ai);

            Map<String, Object> result = mdIndexer.mdToTree(
                    mdPath,
                    ifThinning,
                    thinningThreshold,
                    opt.ifAddNodeSummary,
                    summaryTokenThreshold,
                    opt.model,
                    opt.ifAddDocDescription,
                    opt.ifAddNodeText,
                    opt.ifAddNodeId
            );

            System.out.println("Parsing done, saving to file...");
            String mdName = new File(mdPath).getName().replaceAll("\\.(md|markdown)$", "");
            String outputFile = "results/" + mdName + "_structure.json";
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(outputFile), result);
            System.out.println("Tree structure saved to: " + outputFile);
        }
    }
}
