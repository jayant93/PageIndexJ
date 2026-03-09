package ai.pageindex.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class IndexController {

    private final JobService jobService;
    private final UsageLimiter usageLimiter;

    public IndexController(JobService jobService, UsageLimiter usageLimiter) {
        this.jobService = jobService;
        this.usageLimiter = usageLimiter;
    }

    /** Upload a document and start indexing. Returns jobId. */
    @PostMapping("/index")
    public Map<String, Object> startIndex(
            @RequestParam("file")                                              MultipartFile file,
            @RequestParam(value = "tier",              defaultValue = "free")  String tier,
            @RequestParam(value = "model",             defaultValue = "")      String model,
            @RequestParam(value = "numCtx",            defaultValue = "8192")  int numCtx,
            @RequestParam(value = "addSummaries",      defaultValue = "false") boolean addSummaries,
            @RequestParam(value = "addNodeText",       defaultValue = "false") boolean addNodeText,
            @RequestParam(value = "addDocDescription", defaultValue = "false") boolean addDocDescription,
            @RequestParam(value = "addNodeId",         defaultValue = "true")  boolean addNodeId,
            HttpServletRequest request
    ) throws Exception {
        String clientIp = getClientIp(request);

        if ("free".equals(tier)) {
            if (!usageLimiter.canUse(clientIp)) {
                return Map.of(
                    "error", "Free tier limit reached — you have used all " + UsageLimiter.MAX_FREE_USES + " free indexing jobs.",
                    "remaining", 0
                );
            }
            // Record usage before job starts (prevents double-submit race)
            usageLimiter.recordUse(clientIp);
        }

        String jobId = jobService.submitJob(file, tier, clientIp, model, numCtx,
                addSummaries, addNodeText, addDocDescription, addNodeId);

        return Map.of(
            "jobId", jobId,
            "remaining", usageLimiter.remaining(clientIp),
            "tier", tier
        );
    }

    /** Return usage info for the current client IP. */
    @GetMapping("/usage")
    public Map<String, Object> usage(HttpServletRequest request) {
        String ip = getClientIp(request);
        return Map.of(
            "used",      usageLimiter.getUsageCount(ip),
            "remaining", usageLimiter.remaining(ip),
            "maxUses",   UsageLimiter.MAX_FREE_USES,
            "maxPages",  UsageLimiter.MAX_FREE_PAGES
        );
    }

    /** Return the model catalog (tiers, names, descriptions) for the UI. */
    @GetMapping("/models")
    public List<Map<String, Object>> getModels() {
        return ModelRegistry.catalog();
    }

    /** SSE stream of progress messages for a job. */
    @GetMapping(value = "/events/{jobId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable String jobId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout

        Thread.ofVirtual().start(() -> {
            try {
                BlockingQueue<String> queue = null;
                for (int i = 0; i < 50; i++) {
                    queue = ProgressHub.getQueue(jobId);
                    if (queue != null) break;
                    Thread.sleep(100);
                }
                if (queue == null) { emitter.complete(); return; }

                while (true) {
                    String msg = queue.poll(60, TimeUnit.SECONDS);
                    if (msg == null) {
                        emitter.send(SseEmitter.event().comment("keepalive"));
                        continue;
                    }
                    if (ProgressHub.isDone(msg)) {
                        IndexJob job = jobService.getJob(jobId);
                        String status = job != null ? job.getStatus().name().toLowerCase() : "done";
                        emitter.send(SseEmitter.event().name("done").data(status));
                        emitter.complete();
                        ProgressHub.cleanup(jobId);
                        break;
                    }
                    if (!msg.isBlank()) {
                        emitter.send(SseEmitter.event().name("log").data(msg));
                    }
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /** Status of a specific job. */
    @GetMapping("/jobs/{jobId}")
    public Map<String, Object> jobStatus(@PathVariable String jobId) {
        IndexJob job = jobService.getJob(jobId);
        if (job == null) return Map.of("error", "Job not found");
        return job.toMap();
    }

    /** List all indexed documents available for querying. */
    @GetMapping("/docs")
    public List<Map<String, Object>> listDocs() {
        return jobService.listIndexedDocs();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
