package com.studysnap.backend.security;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OcrRateLimitService {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final long BUCKET_PURGE_INTERVAL_MS = 60_000L;

    private final StudySnapProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public OcrRateLimitService(StudySnapProperties properties) {
        this.properties = properties;
    }

    public void assertAllowed(UUID userId, PlanType planType) {
        String bucketKey = userId + ":" + planType.name();
        Bucket bucket = buckets.computeIfAbsent(bucketKey, ignored -> new Bucket(OffsetDateTime.now(), 0));
        int rateLimit = resolveRateLimitPerMinute(planType);
        synchronized (bucket) {
            OffsetDateTime now = OffsetDateTime.now();
            if (bucket.windowStart.plus(WINDOW).isBefore(now)) {
                bucket.windowStart = now;
                bucket.count = 0;
            }
            if (bucket.count >= rateLimit) {
                throw new AppException(
                        "OCR_RATE_LIMIT_EXCEEDED",
                        "Too many requests. Please wait a moment and try again.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }
            bucket.count++;
        }
    }

    @Scheduled(fixedDelay = BUCKET_PURGE_INTERVAL_MS)
    void purgeExpiredBuckets() {
        purgeExpiredBuckets(OffsetDateTime.now());
    }

    void purgeExpiredBuckets(OffsetDateTime now) {
        buckets.values().removeIf(bucket -> {
            synchronized (bucket) {
                return bucket.windowStart.plus(WINDOW).isBefore(now);
            }
        });
    }

    int trackedBucketCount() {
        return buckets.size();
    }

    private int resolveRateLimitPerMinute(PlanType planType) {
        int configured = planType != null && planType.isPaid()
                ? properties.getLimits().getPremiumOcrRateLimitPerMinute()
                : properties.getLimits().getFreeOcrRateLimitPerMinute();
        return Math.max(1, configured);
    }

    private static final class Bucket {
        private OffsetDateTime windowStart;
        private int count;

        private Bucket(OffsetDateTime windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
