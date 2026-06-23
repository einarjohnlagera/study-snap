package com.studysnap.backend.security;

import com.studysnap.backend.exception.AppException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRateLimitServiceTest {

    private static AuthRateLimitService service(int perMinute) {
        SecurityProperties properties = new SecurityProperties();
        properties.getAuth().setAuthRateLimitPerMinute(perMinute);
        return new AuthRateLimitService(properties);
    }

    @Test
    void enforcesPerMinuteLimitPerKey() {
        AuthRateLimitService service = service(1);

        assertDoesNotThrow(() -> service.assertAllowed("login", "1.1.1.1:a@example.com"));
        AppException error = assertThrows(
                AppException.class,
                () -> service.assertAllowed("login", "1.1.1.1:a@example.com")
        );
        assertEquals("TOO_MANY_REQUESTS", error.getCode());
    }

    @Test
    void purgeExpiredBuckets_evictsStaleKeysButKeepsActive() {
        AuthRateLimitService service = service(100);

        // Distinct IP/email keys would otherwise accumulate forever (the leak that OOM'd prod).
        service.assertAllowed("login", "1.1.1.1:a@example.com");
        service.assertAllowed("login", "2.2.2.2:b@example.com");
        assertEquals(2, service.trackedBucketCount());

        // Nothing expired at the current time — active buckets are retained.
        service.purgeExpiredBuckets(OffsetDateTime.now());
        assertEquals(2, service.trackedBucketCount());

        // Once the window has fully elapsed, stale buckets are evicted.
        service.purgeExpiredBuckets(OffsetDateTime.now().plusMinutes(5));
        assertEquals(0, service.trackedBucketCount());
    }
}
