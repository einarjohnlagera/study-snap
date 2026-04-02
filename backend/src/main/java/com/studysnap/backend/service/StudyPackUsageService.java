package com.studysnap.backend.service;

import com.studysnap.backend.repository.StudyPackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyPackUsageService {
    private final UserUsageService userUsageService;
    private final StudyPackRepository studyPackRepository;

    @Transactional(readOnly = true)
    public UsageSnapshot resolveUsage(UUID userId, OffsetDateTime referenceTime) {
        return resolveUsage(userId, userUsageService.getMonthlyUsage(userId, referenceTime));
    }

    @Transactional(readOnly = true)
    public UsageSnapshot resolveUsage(UUID userId, UserUsageService.MonthlyUsage trackedUsage) {
        long persistedStudyPackCount = studyPackRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                trackedUsage.periodStart(),
                trackedUsage.periodEnd()
        );
        int usedCount = Math.toIntExact(Math.max(trackedUsage.studyPackGenerations(), persistedStudyPackCount));
        return new UsageSnapshot(trackedUsage.periodStart(), trackedUsage.periodEnd(), usedCount);
    }

    public record UsageSnapshot(
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd,
            int usedCount
    ) {
    }
}
