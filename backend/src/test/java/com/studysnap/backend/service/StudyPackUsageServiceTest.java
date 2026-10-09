package com.studysnap.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StudyPackUsageServiceTest {

    @Mock
    private UserUsageService userUsageService;

    private StudyPackUsageService studyPackUsageService;

    @BeforeEach
    void setUp() {
        studyPackUsageService = new StudyPackUsageService(userUsageService);
    }

    @Test
    void usedCountEqualsTrackedGenerationsExactly() {
        UUID userId = UUID.randomUUID();
        UserUsageService.MonthlyUsage trackedUsage = new UserUsageService.MonthlyUsage(
                OffsetDateTime.parse("2026-03-10T00:00:00Z"),
                OffsetDateTime.parse("2026-04-10T00:00:00Z"),
                4,
                0,
                0,
                0,
                0,
                0
        );

        StudyPackUsageService.UsageSnapshot snapshot = studyPackUsageService.resolveUsage(userId, trackedUsage);

        assertThat(snapshot.periodStart()).isEqualTo(trackedUsage.periodStart());
        assertThat(snapshot.periodEnd()).isEqualTo(trackedUsage.periodEnd());
        assertThat(snapshot.usedCount()).isEqualTo(4);
    }

    /**
     * Regression guard for the quota-metering defect: a user who has copied or remixed many notes
     * inserts a {@code study_packs} row each time, with no LLM call and no increment to
     * {@code studyPackGenerations}. {@link StudyPackUsageService} must never consult the raw
     * {@code study_packs} row count — only the tracked generation counter — so heavy copy/remix
     * activity must never inflate a user's reported usage above their real generation count.
     */
    @Test
    void usedCountIsZeroWhenNoGenerationsTrackedRegardlessOfCopyOrRemixActivity() {
        UUID userId = UUID.randomUUID();
        UserUsageService.MonthlyUsage trackedUsage = new UserUsageService.MonthlyUsage(
                OffsetDateTime.parse("2026-03-10T00:00:00Z"),
                OffsetDateTime.parse("2026-04-10T00:00:00Z"),
                0,
                0,
                0,
                0,
                0,
                0
        );

        StudyPackUsageService.UsageSnapshot snapshot = studyPackUsageService.resolveUsage(userId, trackedUsage);

        assertThat(snapshot.usedCount()).isZero();
    }
}
