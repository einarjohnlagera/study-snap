package com.studysnap.backend.service;

import com.studysnap.backend.repository.UserUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserUsageService {
    private final UserUsageRepository userUsageRepository;
    private final BillingUsagePeriodService billingUsagePeriodService;

    @Transactional(readOnly = true)
    public MonthlyUsage getMonthlyUsage(UUID userId, OffsetDateTime referenceTime) {
        BillingUsagePeriodService.UsagePeriod usagePeriod = billingUsagePeriodService.resolveUsagePeriod(userId, referenceTime);
        return userUsageRepository.findByUserIdAndPeriodStart(userId, usagePeriod.periodStart())
                .map(usage -> new MonthlyUsage(
                        usage.getPeriodStart(),
                        usage.getPeriodEnd(),
                        usage.getStudyPackGenerations(),
                        usage.getChallengeQuizGenerations(),
                        usage.getAdaptiveQuizGenerations(),
                        usage.getInterviewPracticeUsedThisMonth(),
                        usage.getOcrExtractions(),
                        usage.getNoteGenerations(),
                        usage.getExportsCount()
                ))
                .orElse(MonthlyUsage.zero(usagePeriod.periodStart(), usagePeriod.periodEnd()));
    }

    @Transactional
    public void ensureUsagePeriod(UUID userId, OffsetDateTime referenceTime) {
        BillingUsagePeriodService.UsagePeriod usagePeriod = billingUsagePeriodService.resolveUsagePeriod(userId, referenceTime);
        userUsageRepository.incrementUsage(
                userId,
                usagePeriod.year(),
                usagePeriod.month(),
                usagePeriod.periodStart(),
                usagePeriod.periodEnd(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    @Transactional
    public void incrementStudyPackGeneration(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 1, 0, 0, 0);
    }

    @Transactional
    public void incrementChallengeQuizGeneration(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 0, 1, 0, 0);
    }

    @Transactional
    public void incrementAdaptiveQuizGeneration(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 0, 0, 1, 0);
    }

    @Transactional
    public void incrementInterviewPracticeGeneration(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 0, 0, 0, 1);
    }

    @Transactional
    public void incrementOcrExtraction(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 0, 0, 0, 0, 1, 0);
    }

    @Transactional
    public void incrementNoteGeneration(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 0, 0, 0, 0, 0, 1);
    }

    @Transactional
    public void incrementExport(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 0, 0, 0, 0, 0, 0, 1);
    }

    private void increment(
            UUID userId,
            OffsetDateTime occurredAt,
            int studyPackDelta,
            int challengeDelta,
            int adaptiveDelta,
            int interviewPracticeDelta
    ) {
        increment(userId, occurredAt, studyPackDelta, challengeDelta, adaptiveDelta, interviewPracticeDelta, 0, 0);
    }

    private void increment(
            UUID userId,
            OffsetDateTime occurredAt,
            int studyPackDelta,
            int challengeDelta,
            int adaptiveDelta,
            int interviewPracticeDelta,
            int ocrDelta,
            int noteGenerationDelta
    ) {
        increment(userId, occurredAt, studyPackDelta, challengeDelta, adaptiveDelta, interviewPracticeDelta, ocrDelta, noteGenerationDelta, 0);
    }

    private void increment(
            UUID userId,
            OffsetDateTime occurredAt,
            int studyPackDelta,
            int challengeDelta,
            int adaptiveDelta,
            int interviewPracticeDelta,
            int ocrDelta,
            int noteGenerationDelta,
            int exportDelta
    ) {
        BillingUsagePeriodService.UsagePeriod usagePeriod = billingUsagePeriodService.resolveUsagePeriod(userId, occurredAt);
        userUsageRepository.incrementUsage(
                userId,
                usagePeriod.year(),
                usagePeriod.month(),
                usagePeriod.periodStart(),
                usagePeriod.periodEnd(),
                studyPackDelta,
                challengeDelta,
                adaptiveDelta,
                interviewPracticeDelta,
                ocrDelta,
                noteGenerationDelta,
                exportDelta,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    public record MonthlyUsage(
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd,
            int studyPackGenerations,
            int challengeQuizGenerations,
            int adaptiveQuizGenerations,
            int interviewPracticeUsedThisMonth,
            int ocrExtractions,
            int noteGenerations,
            int exportsCount
    ) {
        public MonthlyUsage(
                OffsetDateTime periodStart,
                OffsetDateTime periodEnd,
                int studyPackGenerations,
                int challengeQuizGenerations,
                int adaptiveQuizGenerations,
                int ocrExtractions,
                int noteGenerations,
                int exportsCount
        ) {
            this(
                    periodStart,
                    periodEnd,
                    studyPackGenerations,
                    challengeQuizGenerations,
                    adaptiveQuizGenerations,
                    0,
                    ocrExtractions,
                    noteGenerations,
                    exportsCount
            );
        }

        public static MonthlyUsage zero() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return new MonthlyUsage(now, now, 0, 0, 0, 0, 0, 0, 0);
        }

        public static MonthlyUsage zero(OffsetDateTime periodStart, OffsetDateTime periodEnd) {
            return new MonthlyUsage(periodStart, periodEnd, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
