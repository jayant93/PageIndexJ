package ai.pageindex.web;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks free-tier usage per client IP.
 * Limits: 2 indexing jobs and 50 pages per account (resets on server restart).
 */
@Component
public class UsageLimiter {

    public static final int MAX_FREE_USES  = 2;
    public static final int MAX_FREE_PAGES = 50;

    private final Map<String, AtomicInteger> usageMap = new ConcurrentHashMap<>();

    public int getUsageCount(String ip) {
        return usageMap.getOrDefault(ip, new AtomicInteger(0)).get();
    }

    public boolean canUse(String ip) {
        return getUsageCount(ip) < MAX_FREE_USES;
    }

    public int remaining(String ip) {
        return Math.max(0, MAX_FREE_USES - getUsageCount(ip));
    }

    public void recordUse(String ip) {
        usageMap.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
    }
}
