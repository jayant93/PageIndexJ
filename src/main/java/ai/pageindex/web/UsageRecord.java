package ai.pageindex.web;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Tracks free-tier indexing usage per IP. TTL index auto-deletes after 1 day. */
@Document("usage")
public class UsageRecord {

    @Id
    private String ip;

    private int count;

    /** MongoDB TTL index: document is deleted when this timestamp is reached. */
    @Indexed(name = "usage_ttl", expireAfterSeconds = 0)
    private Instant expiresAt;

    public UsageRecord() {}

    public UsageRecord(String ip, int count, Instant expiresAt) {
        this.ip = ip;
        this.count = count;
        this.expiresAt = expiresAt;
    }

    public String getIp()          { return ip; }
    public int    getCount()       { return count; }
    public Instant getExpiresAt()  { return expiresAt; }

    public void setCount(int count)          { this.count = count; }
    public void setExpiresAt(Instant t)      { this.expiresAt = t; }
}
