package com.studysnap.backend.service;

import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.repository.UserUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserUsageServiceTest {

    @Mock
    private UserUsageRepository userUsageRepository;
    @Mock
    private BillingUsagePeriodService billingUsagePeriodService;

    private UserUsageService userUsageService;

    @BeforeEach
    void setUp() {
        userUsageService = new UserUsageService(userUsageRepository, billingUsagePeriodService);
    }

    @Test
    void incrementBoardExamGenerationByAppliesExplicitDelta() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-03-10T12:00:00Z");
        BillingUsagePeriodService.UsagePeriod usagePeriod = usagePeriod();
        when(billingUsagePeriodService.resolveUsagePeriod(userId, occurredAt)).thenReturn(usagePeriod);

        userUsageService.incrementBoardExamGenerationBy(userId, 3, occurredAt);

        verify(userUsageRepository).incrementUsage(
                eq(userId),
                eq(usagePeriod.year()),
                eq(usagePeriod.month()),
                eq(usagePeriod.periodStart()),
                eq(usagePeriod.periodEnd()),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                eq(3),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void incrementLongExamGenerationByAppliesExplicitDelta() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-03-10T12:00:00Z");
        BillingUsagePeriodService.UsagePeriod usagePeriod = usagePeriod();
        when(billingUsagePeriodService.resolveUsagePeriod(userId, occurredAt)).thenReturn(usagePeriod);

        userUsageService.incrementLongExamGenerationBy(userId, 4, occurredAt);

        verify(userUsageRepository).incrementUsage(
                eq(userId),
                eq(usagePeriod.year()),
                eq(usagePeriod.month()),
                eq(usagePeriod.periodStart()),
                eq(usagePeriod.periodEnd()),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                eq(4),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                any(OffsetDateTime.class)
        );
    }

    private BillingUsagePeriodService.UsagePeriod usagePeriod() {
        return new BillingUsagePeriodService.UsagePeriod(
                PlanType.PRO,
                BillingCycle.MONTHLY,
                OffsetDateTime.parse("2026-03-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                2026,
                3
        );
    }
}
