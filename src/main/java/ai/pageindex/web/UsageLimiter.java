package ai.pageindex.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks free-tier usage per client IP.
 * Uses MongoDB when available (persisted, TTL-based 24h reset).
 * Falls back to in-memory ConcurrentHashMap when MongoDB is not configured.
 */
@Component
public class UsageLimiter {

    public static final int MAX_FREE_USES  = 2;
    public static final int MAX_FREE_PAGES = 50;

    private final UsageRepository repo;
    private final Map<String, AtomicInteger> fallback = new ConcurrentHashMap<>();

    @Autowired
    public UsageLimiter(@Nullable UsageRepository repo) {
        this.repo = repo;
        if (repo == null) {
            System.out.println("Warning: MongoDB not available — using in-memory usage tracking (resets on restart)");
        }
    }

    public int getUsageCount(String ip) {
        if (repo == null) return fallback.getOrDefault(ip, new AtomicInteger(0)).get();
        return repo.findById(ip).map(UsageRecord::getCount).orElse(0);
    }

    public boolean canUse(String ip) {
        return getUsageCount(ip) < MAX_FREE_USES;
    }

    public int remaining(String ip) {
        return Math.max(0, MAX_FREE_USES - getUsageCount(ip));
    }

    public void recordUse(String ip) {
        if (repo == null) {
            fallback.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
            return;
        }
        UsageRecord rec = repo.findById(ip).orElse(null);
        if (rec == null) {
            rec = new UsageRecord(ip, 1, Instant.now().plus(1, ChronoUnit.DAYS));
        } else {
            rec.setCount(rec.getCount() + 1);
        }
        repo.save(rec);
    }
}
