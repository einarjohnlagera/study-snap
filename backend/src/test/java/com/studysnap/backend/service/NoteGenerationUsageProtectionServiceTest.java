package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteGenerationUsageProtectionServiceTest {

    @Mock
    private UserUsageService userUsageService;

    private NoteGenerationUsageProtectionService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getPricing().setFreeMonthlyNoteGenerationLimit(2);
        properties.getPricing().setProMonthlyNoteGenerationLimit(5);
        service = new NoteGenerationUsageProtectionService(properties, userUsageService);
        userId = UUID.randomUUID();
    }

    @Test
    void rejectsWhenTrackedUsageReachesFreeLimit() {
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(
                        OffsetDateTime.parse("2026-03-10T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-10T00:00:00Z"),
                        0,
                        0,
                        0,
                        0,
                        2,
                        0
                ));

        AppException error = assertThrows(AppException.class, () -> service.assertQuotaAvailable(userId, PlanType.FREE));

        assertEquals("NOTE_GENERATION_LIMIT_REACHED", error.getCode());
        assertEquals("You have reached your note generation limit for this billing cycle.", error.getMessage());
    }

    @Test
    void allowsPremiumUsersWithHigherConfiguredQuota() {
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(
                        OffsetDateTime.parse("2026-03-10T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-10T00:00:00Z"),
                        0,
                        0,
                        0,
                        0,
                        3,
                        0
                ));

        assertDoesNotThrow(() -> service.assertQuotaAvailable(userId, PlanType.PRO));
    }

    @Test
    void recordsUsageThroughSharedUserUsageService() {
        OffsetDateTime occurredAt = OffsetDateTime.now();

        service.recordUsage(userId, occurredAt);

        verify(userUsageService).incrementNoteGeneration(userId, occurredAt);
    }
}
