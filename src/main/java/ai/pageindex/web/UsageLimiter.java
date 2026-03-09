package ai.pageindex.web;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Tracks free-tier indexing usage per client IP via MongoDB.
 * Limit: 2 indexing jobs per IP, resets automatically after 24 hours (TTL index).
 */
@Component
public class UsageLimiter {

    public static final int MAX_FREE_USES  = 2;
    public static final int MAX_FREE_PAGES = 50;

    private final UsageRepository repo;

    public UsageLimiter(UsageRepository repo) {
        this.repo = repo;
    }

    public int getUsageCount(String ip) {
        return repo.findById(ip).map(UsageRecord::getCount).orElse(0);
    }

    public boolean canUse(String ip) {
        return getUsageCount(ip) < MAX_FREE_USES;
    }

    public int remaining(String ip) {
        return Math.max(0, MAX_FREE_USES - getUsageCount(ip));
    }

    public void recordUse(String ip) {
        UsageRecord rec = repo.findById(ip).orElse(null);
        if (rec == null) {
            // First use: create record with 24-hour TTL
            rec = new UsageRecord(ip, 1, Instant.now().plus(1, ChronoUnit.DAYS));
        } else {
            rec.setCount(rec.getCount() + 1);
            // expiresAt stays at original value — TTL counts from first use
        }
        repo.save(rec);
    }
}
