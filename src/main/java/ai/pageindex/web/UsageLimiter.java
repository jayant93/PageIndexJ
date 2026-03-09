package ai.pageindex.web;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks free-tier usage per client IP.
 * Uses Firestore when available (persisted, 24h TTL via expiresAt field).
 * Falls back to in-memory ConcurrentHashMap when Firestore is not configured.
 */
@Component
public class UsageLimiter {

    public static final int MAX_FREE_USES  = 2;
    public static final int MAX_FREE_PAGES = 50;

    private static final String COLLECTION = "usage";

    private final Firestore db;
    private final Map<String, AtomicInteger> fallback = new ConcurrentHashMap<>();

    @Autowired
    public UsageLimiter(@Nullable Firestore db) {
        this.db = db;
    }

    public int getUsageCount(String ip) {
        if (db == null) return fallback.getOrDefault(ip, new AtomicInteger(0)).get();
        try {
            DocumentSnapshot snap = db.collection(COLLECTION).document(sanitize(ip)).get().get();
            if (!snap.exists()) return 0;
            Timestamp expiresAt = snap.getTimestamp("expiresAt");
            if (expiresAt != null && expiresAt.toDate().before(new Date())) return 0; // expired
            Long count = snap.getLong("count");
            return count == null ? 0 : count.intValue();
        } catch (Exception e) {
            return fallback.getOrDefault(ip, new AtomicInteger(0)).get();
        }
    }

    public boolean canUse(String ip) {
        return getUsageCount(ip) < MAX_FREE_USES;
    }

    public int remaining(String ip) {
        return Math.max(0, MAX_FREE_USES - getUsageCount(ip));
    }

    public void recordUse(String ip) {
        if (db == null) {
            fallback.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
            return;
        }
        try {
            String docId = sanitize(ip);
            int current = getUsageCount(ip);
            if (current == 0) {
                // First use (or expired): create fresh record with 24h TTL
                Date expiresAt = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));
                db.collection(COLLECTION).document(docId).set(Map.of(
                        "count", 1L,
                        "expiresAt", Timestamp.of(expiresAt),
                        "ip", ip
                ));
            } else {
                db.collection(COLLECTION).document(docId).update("count", FieldValue.increment(1));
            }
        } catch (Exception e) {
            fallback.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }

    /** Firestore document IDs cannot contain slashes — replace with underscores. */
    private String sanitize(String ip) {
        return ip.replace(".", "_").replace(":", "_");
    }
}
