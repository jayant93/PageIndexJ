package ai.pageindex.web;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Routes System.out messages from pipeline threads to per-job SSE queues.
 */
public class ProgressHub {

    private static final String DONE_SENTINEL = "__DONE__";

    private static final Map<String, BlockingQueue<String>> queues = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> currentJob = new ThreadLocal<>();

    public static void register(String jobId) {
        queues.put(jobId, new LinkedBlockingQueue<>());
    }

    public static void setJobId(String jobId) {
        currentJob.set(jobId);
    }

    public static void clearJobId() {
        currentJob.remove();
    }

    public static void emit(String message) {
        String jobId = currentJob.get();
        if (jobId == null) return;
        BlockingQueue<String> q = queues.get(jobId);
        if (q != null) q.offer(message);
    }

    public static void complete(String jobId) {
        BlockingQueue<String> q = queues.get(jobId);
        if (q != null) q.offer(DONE_SENTINEL);
    }

    public static boolean isDone(String message) {
        return DONE_SENTINEL.equals(message);
    }

    public static BlockingQueue<String> getQueue(String jobId) {
        return queues.get(jobId);
    }

    public static void cleanup(String jobId) {
        queues.remove(jobId);
    }
}
