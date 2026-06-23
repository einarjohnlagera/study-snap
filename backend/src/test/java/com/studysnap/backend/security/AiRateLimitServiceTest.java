package com.studysnap.backend.security;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiRateLimitServiceTest {

    @Test
    void appliesDifferentPerMinuteLimitsForFreeAndPremiumUsers() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getLimits().setFreeAiRateLimitPerMinute(1);
        properties.getLimits().setPremiumAiRateLimitPerMinute(2);
        AiRateLimitService service = new AiRateLimitService(properties);
        UUID userId = UUID.randomUUID();

        assertDoesNotThrow(() -> service.assertAllowed(userId, PlanType.FREE, "study-pack"));
        AppException freeError = assertThrows(
                AppException.class,
                () -> service.assertAllowed(userId, PlanType.FREE, "study-pack")
        );
        assertEquals("TOO_MANY_REQUESTS", freeError.getCode());
        assertEquals("Too many requests. Please wait a moment and try again.", freeError.getMessage());

        assertDoesNotThrow(() -> service.assertAllowed(userId, PlanType.PRO, "study-pack"));
        assertDoesNotThrow(() -> service.assertAllowed(userId, PlanType.PRO, "study-pack"));
        AppException premiumError = assertThrows(
                AppException.class,
                () -> service.assertAllowed(userId, PlanType.PRO, "study-pack")
        );
        assertEquals("TOO_MANY_REQUESTS", premiumError.getCode());
    }

    @Test
    void purgeExpiredBuckets_evictsStaleBucketsButKeepsActive() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getLimits().setFreeAiRateLimitPerMinute(100);
        AiRateLimitService service = new AiRateLimitService(properties);

        service.assertAllowed(UUID.randomUUID(), PlanType.FREE, "study-pack");
        service.assertAllowed(UUID.randomUUID(), PlanType.FREE, "study-pack");
        assertEquals(2, service.trackedBucketCount());

        service.purgeExpiredBuckets(OffsetDateTime.now());
        assertEquals(2, service.trackedBucketCount());

        service.purgeExpiredBuckets(OffsetDateTime.now().plusMinutes(5));
        assertEquals(0, service.trackedBucketCount());
    }
}
