package com.studysnap.backend.security;

import com.studysnap.backend.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimitService {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final SecurityProperties securityProperties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AuthRateLimitService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public void assertAllowed(String action, String key) {
        String bucketKey = action + ":" + key;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, ignored -> new Bucket(OffsetDateTime.now(), 0));
        synchronized (bucket) {
            OffsetDateTime now = OffsetDateTime.now();
            if (bucket.windowStart.plus(WINDOW).isBefore(now)) {
                bucket.windowStart = now;
                bucket.count = 0;
            }
            if (bucket.count >= securityProperties.getAuth().getAuthRateLimitPerMinute()) {
                throw new AppException(
                        "TOO_MANY_REQUESTS",
                        "Too many requests. Please wait a minute and try again.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }
            bucket.count++;
        }
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
