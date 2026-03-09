package ai.pageindex.web;

import java.util.Map;

/**
 * Tracks the state of one document indexing job.
 */
public class IndexJob {

    public enum Status { QUEUED, RUNNING, DONE, ERROR }

    private final String jobId;
    private final String docName;
    private volatile Status status = Status.QUEUED;
    private volatile Map<String, Object> result;
    private volatile String error;

    public IndexJob(String jobId, String docName) {
        this.jobId = jobId;
        this.docName = docName;
    }

    public String getJobId()  { return jobId; }
    public String getDocName() { return docName; }
    public Status getStatus() { return status; }
    public Map<String, Object> getResult() { return result; }
    public String getError()  { return error; }

    public void setStatus(Status s)           { this.status = s; }
    public void setResult(Map<String, Object> r) { this.result = r; }
    public void setError(String e)            { this.error = e; }

    /** Summary map for API responses. */
    public Map<String, Object> toMap() {
        return Map.of(
                "jobId",   jobId,
                "docName", docName,
                "status",  status.name().toLowerCase()
        );
    }
}
