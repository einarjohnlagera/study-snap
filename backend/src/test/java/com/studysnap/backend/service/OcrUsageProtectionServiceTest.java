package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.StudyPackDraftRepository;
import com.studysnap.backend.repository.StudyPackRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrUsageProtectionServiceTest {

    @Mock
    private UserUsageService userUsageService;
    @Mock
    private BillingUsagePeriodService billingUsagePeriodService;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private StudyPackDraftRepository studyPackDraftRepository;

    private OcrUsageProtectionService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getPricing().setFreeMonthlyOcrLimit(2);
        properties.getPricing().setPremiumMonthlyOcrLimit(5);
        service = new OcrUsageProtectionService(
                properties,
                userUsageService,
                billingUsagePeriodService,
                studyPackRepository,
                studyPackDraftRepository
        );
        userId = UUID.randomUUID();
        lenient().when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE,
                        com.studysnap.backend.entity.BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        lenient().when(studyPackRepository.countByOwnerUserIdAndInputTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(com.studysnap.backend.entity.InputType.IMAGE),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
        lenient().when(studyPackDraftRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
    }

    @Test
    void rejectsWhenTrackedOcrUsageReachesFreeLimit() {
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(
                        OffsetDateTime.parse("2026-03-10T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-10T00:00:00Z"),
                        0,
                        0,
                        0,
                        2
                ));

        AppException error = assertThrows(AppException.class, () -> service.assertQuotaAvailable(userId, PlanType.FREE));

        assertEquals("OCR_LIMIT_REACHED", error.getCode());
        assertEquals("You have reached your OCR limit for now. Please try again later or upgrade to Premium.", error.getMessage());
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
                        3
                ));

        assertDoesNotThrow(() -> service.assertQuotaAvailable(userId, PlanType.PREMIUM));
    }

    @Test
    void preservesLegacyOcrUsageCountsFromImageStudyPacksAndDrafts() {
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(UserUsageService.MonthlyUsage.zero());
        when(studyPackRepository.countByOwnerUserIdAndInputTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(com.studysnap.backend.entity.InputType.IMAGE),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(1L);
        when(studyPackDraftRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(1L);

        AppException error = assertThrows(AppException.class, () -> service.assertQuotaAvailable(userId, PlanType.FREE));

        assertEquals("OCR_LIMIT_REACHED", error.getCode());
    }

    @Test
    void recordsUsageThroughSharedUserUsageService() {
        OffsetDateTime occurredAt = OffsetDateTime.now();

        service.recordUsage(userId, occurredAt);

        verify(userUsageService).incrementOcrExtraction(userId, occurredAt);
    }
}
