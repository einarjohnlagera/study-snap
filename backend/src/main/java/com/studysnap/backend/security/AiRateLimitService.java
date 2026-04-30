package com.studysnap.backend.security;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiRateLimitService {
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StudySnapProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AiRateLimitService(StudySnapProperties properties) {
        this.properties = properties;
    }

    public void assertAllowed(UUID userId, PlanType planType, String operation) {
        String bucketKey = operation + ":" + userId + ":" + planType.name();
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
                        "TOO_MANY_REQUESTS",
                        "Too many requests. Please wait a moment and try again.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }
            bucket.count++;
        }
    }

    private int resolveRateLimitPerMinute(PlanType planType) {
        int configured = planType != null && planType.isPaid()
                ? properties.getLimits().getPremiumAiRateLimitPerMinute()
                : properties.getLimits().getFreeAiRateLimitPerMinute();
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
