package com.studysnap.backend.service;

import com.studysnap.backend.repository.StudyPackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyPackUsageServiceTest {

    @Mock
    private UserUsageService userUsageService;
    @Mock
    private StudyPackRepository studyPackRepository;

    private StudyPackUsageService studyPackUsageService;

    @BeforeEach
    void setUp() {
        studyPackUsageService = new StudyPackUsageService(userUsageService, studyPackRepository);
    }

    @Test
    void usesTrackedUsageWhenItIsHigherThanPersistedStudyPackCount() {
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
        when(studyPackRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                trackedUsage.periodStart(),
                trackedUsage.periodEnd()
        )).thenReturn(3L);

        StudyPackUsageService.UsageSnapshot snapshot = studyPackUsageService.resolveUsage(userId, trackedUsage);

        assertThat(snapshot.usedCount()).isEqualTo(4);
    }

    @Test
    void reconcilesUpToPersistedStudyPackCountWhenTrackedUsageLags() {
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
        when(studyPackRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                trackedUsage.periodStart(),
                trackedUsage.periodEnd()
        )).thenReturn(5L);

        StudyPackUsageService.UsageSnapshot snapshot = studyPackUsageService.resolveUsage(userId, trackedUsage);

        assertThat(snapshot.periodStart()).isEqualTo(trackedUsage.periodStart());
        assertThat(snapshot.periodEnd()).isEqualTo(trackedUsage.periodEnd());
        assertThat(snapshot.usedCount()).isEqualTo(5);
    }
}
