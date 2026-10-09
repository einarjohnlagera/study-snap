package com.studysnap.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyPackUsageService {
    private final UserUsageService userUsageService;

    @Transactional(readOnly = true)
    public UsageSnapshot resolveUsage(UUID userId, OffsetDateTime referenceTime) {
        return resolveUsage(userId, userUsageService.getMonthlyUsage(userId, referenceTime));
    }

    /**
     * Counts generations only — never note copies or shared-pack remixes, both of which insert a
     * {@code study_packs} row with no LLM call. A prior version additionally floored this at the raw
     * {@code study_packs} row count for the period, which silently counted those non-generation rows
     * as if they were paid generations. No construction site for a real generation has ever skipped
     * {@link UserUsageService#incrementStudyPackGeneration}, so that floor never corrected a real
     * undercount — it only ever inflated usage for copies and remixes.
     */
    @Transactional(readOnly = true)
    public UsageSnapshot resolveUsage(UUID userId, UserUsageService.MonthlyUsage trackedUsage) {
        return new UsageSnapshot(trackedUsage.periodStart(), trackedUsage.periodEnd(), trackedUsage.studyPackGenerations());
    }

    public record UsageSnapshot(
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd,
            int usedCount
    ) {
    }
}
