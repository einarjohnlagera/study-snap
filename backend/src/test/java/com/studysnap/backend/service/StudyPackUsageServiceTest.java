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
     * This test exercises the pass-through logic only — it does not simulate a copy or remix, since
     * {@link StudyPackUsageService} has nothing to simulate against: it takes no
     * {@code StudyPackRepository} and consults no {@code study_packs} row count at all, so no copy or
     * remix activity could reach this class even if the test constructed one. The actual regression
     * guard against the quota-metering defect is structural, not a runtime check: {@code NoteService}
     * (owns {@code copySourceStudyPack}) and {@code ShareService} (owns
     * {@code remixSharedStudyPack}) have no {@code UserUsageService} dependency anywhere in their
     * constructors, and neither does anything either one depends on — confirmed by reading both
     * classes' full field lists. Copying or remixing a note is therefore architecturally incapable of
     * calling {@code incrementStudyPackGeneration}, not merely observed not to in this test.
     */
    @Test
    void usedCountIsZeroWhenNoGenerationsAreTracked() {
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
