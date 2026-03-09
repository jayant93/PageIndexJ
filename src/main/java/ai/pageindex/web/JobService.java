package ai.pageindex.web;

import ai.pageindex.PageIndex;
import ai.pageindex.config.ConfigLoader;
import ai.pageindex.config.PageIndexConfig;
import ai.pageindex.core.PageIndexMd;
import ai.pageindex.util.OpenAIClient;
import ai.pageindex.util.PdfParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class JobService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, IndexJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** Null when MongoDB is not configured — falls back to re-indexing every time. */
    private final IndexedDocRepository docRepo;

    @Autowired
    public JobService(@Nullable IndexedDocRepository docRepo) {
        this.docRepo = docRepo;
        if (docRepo == null) {
            System.out.println("Warning: MongoDB not available — indexed documents will not be cached");
        }
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

        String suffix = originalName.toLowerCase().endsWith(".pdf") ? ".pdf" : ".md";
        Path tmpFile = Files.createTempFile("pageindexj_", suffix);
        file.transferTo(tmpFile);

        String docKey = originalName.replaceAll("\\.(pdf|md|markdown)$", "");
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

                // Check MongoDB cache — skip LLM work if already indexed
                if (docRepo != null && docRepo.existsByDocKey(docKey)) {
                    System.out.println("Document already indexed, loading from database: " + docKey);
                    IndexedDoc existing = docRepo.findByDocKey(docKey).get();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cached = MAPPER.readValue(existing.getStructureJson(), Map.class);
                    job.setResult(cached);
                    job.setStatus(IndexJob.Status.DONE);
                    return;
                }

                PageIndexConfig userOpt = new PageIndexConfig();
                OpenAIClient ai;

                if (isFree) {
                    ai = OpenAIClient.freeCloudPool();
                    userOpt.model = "gpt-4o-mini";
                    userOpt.baseUrl = "";
                } else {
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
                String pagesJson = "[]";

                if (suffix.equals(".pdf")) {
                    pagesJson = buildPagesJson(tmpFile.toString(), opt);
                    result = PageIndex.pageIndexMain(tmpFile.toString(), opt, ai);
                } else {
                    PageIndexMd mdIndexer = new PageIndexMd(ai);
                    result = mdIndexer.mdToTree(tmpFile.toString(), false, 5000,
                            opt.ifAddNodeSummary, 200, opt.model,
                            opt.ifAddDocDescription, opt.ifAddNodeText, opt.ifAddNodeId);
                }

                // Persist to MongoDB if available
                if (docRepo != null) {
                    String structureJson = MAPPER.writeValueAsString(result);
                    IndexedDoc doc = new IndexedDoc();
                    doc.setDocKey(docKey);
                    doc.setFilename(originalName);
                    doc.setStructureJson(structureJson);
                    doc.setPagesJson(pagesJson);
                    doc.setPageCount(suffix.equals(".pdf") ? PdfParser.getPageCount(tmpFile.toString()) : 0);
                    doc.setIndexedAt(Instant.now());
                    docRepo.save(doc);
                    System.out.println("Index saved to database: " + docKey);
                }

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

    private String buildPagesJson(String pdfPath, PageIndexConfig opt) {
        try {
            System.out.println("Loading page tokens for query support...");
            List<PdfParser.PageEntry> pages = PdfParser.getPageTokens(pdfPath, opt.model);
            List<Map<String, Object>> pageList = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                pageList.add(Map.of("index", i, "text", pages.get(i).text()));
            }
            return MAPPER.writeValueAsString(pageList);
        } catch (Exception e) {
            System.out.println("Warning: could not build page tokens: " + e.getMessage());
            return "[]";
        }
    }

    public IndexJob getJob(String jobId) { return jobs.get(jobId); }

    public List<Map<String, Object>> listIndexedDocs() {
        if (docRepo == null) return List.of();
        List<Map<String, Object>> docs = new ArrayList<>();
        for (IndexedDoc d : docRepo.findAllByOrderByIndexedAtDesc()) {
            docs.add(Map.of(
                "docName",   d.getDocKey(),
                "filename",  d.getFilename(),
                "pageCount", d.getPageCount(),
                "indexedAt", d.getIndexedAt() != null ? d.getIndexedAt().toString() : ""
            ));
        }
        return docs;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> loadStructure(String docName) throws Exception {
        if (docRepo == null) throw new IllegalStateException("MongoDB not configured");
        IndexedDoc doc = docRepo.findByDocKey(docName)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + docName));
        return MAPPER.readValue(doc.getStructureJson(), Map.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> loadPages(String docName) {
        if (docRepo == null) return List.of();
        return docRepo.findByDocKey(docName)
                .map(d -> {
                    try { return (List<Map<String, Object>>) MAPPER.readValue(d.getPagesJson(), List.class); }
                    catch (Exception e) { return List.<Map<String, Object>>of(); }
                })
                .orElse(List.of());
    }
}
