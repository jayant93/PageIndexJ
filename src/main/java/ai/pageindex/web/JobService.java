package ai.pageindex.web;

import ai.pageindex.PageIndex;
import ai.pageindex.config.ConfigLoader;
import ai.pageindex.config.PageIndexConfig;
import ai.pageindex.core.PageIndexMd;
import ai.pageindex.util.OpenAIClient;
import ai.pageindex.util.PdfParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

@Service
public class JobService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESULTS_DIR = "results";

    private final Map<String, IndexJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public JobService() {
        new File(RESULTS_DIR).mkdirs();
    }

    /**
     * Start a new indexing job and return its ID.
     *
     * @param tier "free" uses the rotating multi-provider pool with page/use limits;
     *             "paid" uses the model selected by the user.
     */
    public String submitJob(MultipartFile file,
                            String tier, String clientIp,
                            String model, int numCtx,
                            boolean addSummaries, boolean addNodeText,
                            boolean addDocDescription, boolean addNodeId) throws Exception {

        String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        IndexJob job = new IndexJob(jobId, originalName);
        jobs.put(jobId, job);

        // Save upload to temp file
        String suffix = originalName.toLowerCase().endsWith(".pdf") ? ".pdf" : ".md";
        Path tmpFile = Files.createTempFile("pageindexj_", suffix);
        file.transferTo(tmpFile);

        boolean isFree = "free".equals(tier);

        executor.submit(() -> {
            ProgressHub.register(jobId);
            ProgressHub.setJobId(jobId);
            job.setStatus(IndexJob.Status.RUNNING);
            try {
                // Free-tier: enforce 50-page limit before doing any LLM work
                if (isFree && suffix.equals(".pdf")) {
                    int pageCount = PdfParser.getPageCount(tmpFile.toString());
                    if (pageCount > UsageLimiter.MAX_FREE_PAGES) {
                        String msg = "Free tier limit: document has " + pageCount
                                + " pages (max " + UsageLimiter.MAX_FREE_PAGES + ")";
                        System.out.println("ERROR: " + msg);
                        job.setError(msg);
                        job.setStatus(IndexJob.Status.ERROR);
                        return;
                    }
                }

                PageIndexConfig userOpt = new PageIndexConfig();
                OpenAIClient ai;

                if (isFree) {
                    // Pool mode: distribute calls across Groq/Cerebras/Mistral/Gemini
                    ai = OpenAIClient.freeCloudPool();
                    userOpt.model = "gpt-4o-mini"; // tokenisation reference only
                    userOpt.baseUrl = "";           // not used in pool mode
                } else {
                    // Paid / custom model
                    if (model != null && !model.isBlank()) userOpt.model = model;
                    userOpt.baseUrl = ModelRegistry.resolveBaseUrl(model);
                    ai = new OpenAIClient(userOpt.baseUrl, numCtx > 0 ? numCtx : 8192);
                }

                if (numCtx > 0) userOpt.numCtx = numCtx;
                userOpt.ifAddNodeSummary    = addSummaries     ? "yes" : "no";
                userOpt.ifAddNodeText       = addNodeText       ? "yes" : "no";
                userOpt.ifAddDocDescription = addDocDescription ? "yes" : "no";
                userOpt.ifAddNodeId         = addNodeId         ? "yes" : "no";

                PageIndexConfig opt = new ConfigLoader().load(userOpt);
                Map<String, Object> result;

                if (suffix.equals(".pdf")) {
                    savePageTokens(tmpFile.toString(), opt, originalName);
                    result = PageIndex.pageIndexMain(tmpFile.toString(), opt, ai);
                } else {
                    PageIndexMd mdIndexer = new PageIndexMd(ai);
                    result = mdIndexer.mdToTree(tmpFile.toString(), false, 5000,
                            opt.ifAddNodeSummary, 200, opt.model,
                            opt.ifAddDocDescription, opt.ifAddNodeText, opt.ifAddNodeId);
                }

                String docKey = originalName.replaceAll("\\.(pdf|md|markdown)$", "");
                String outPath = RESULTS_DIR + "/" + docKey + "_structure.json";
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(outPath), result);
                System.out.println("Parsing done, saving to file...");
                System.out.println("Tree structure saved to: " + outPath);

                job.setResult(result);
                job.setStatus(IndexJob.Status.DONE);

            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
                job.setError(e.getMessage());
                job.setStatus(IndexJob.Status.ERROR);
            } finally {
                ProgressHub.complete(jobId);
                ProgressHub.setJobId(null);
                ProgressHub.clearJobId();
                tmpFile.toFile().delete();
            }
        });

        return jobId;
    }

    private void savePageTokens(String pdfPath, PageIndexConfig opt, String originalName) {
        try {
            System.out.println("Loading page tokens for query support...");
            List<PdfParser.PageEntry> pages = PdfParser.getPageTokens(pdfPath, opt.model);
            List<Map<String, Object>> pageList = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                pageList.add(Map.of("index", i, "text", pages.get(i).text()));
            }
            String docKey = originalName.replaceAll("\\.pdf$", "");
            MAPPER.writerWithDefaultPrettyPrinter()
                  .writeValue(new File(RESULTS_DIR + "/" + docKey + "_pages.json"), pageList);
        } catch (Exception e) {
            System.out.println("Warning: could not save page tokens: " + e.getMessage());
        }
    }

    public IndexJob getJob(String jobId) { return jobs.get(jobId); }

    public List<Map<String, Object>> listIndexedDocs() {
        File dir = new File(RESULTS_DIR);
        List<Map<String, Object>> docs = new ArrayList<>();
        if (!dir.exists()) return docs;
        for (File f : Objects.requireNonNull(dir.listFiles())) {
            if (f.getName().endsWith("_structure.json")) {
                String docName = f.getName().replace("_structure.json", "");
                docs.add(Map.of("docName", docName, "file", f.getName()));
            }
        }
        return docs;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> loadStructure(String docName) throws Exception {
        File f = new File(RESULTS_DIR + "/" + docName + "_structure.json");
        if (!f.exists()) throw new IllegalArgumentException("Structure not found: " + docName);
        return MAPPER.readValue(f, Map.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> loadPages(String docName) {
        File f = new File(RESULTS_DIR + "/" + docName + "_pages.json");
        if (!f.exists()) return List.of();
        try { return MAPPER.readValue(f, List.class); } catch (Exception e) { return List.of(); }
    }
}
