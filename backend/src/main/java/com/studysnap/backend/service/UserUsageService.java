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
                        usage.getStudyPackGenerations(),
                        usage.getChallengeQuizGenerations(),
                        usage.getAdaptiveQuizGenerations(),
                        usage.getOcrExtractions()
                ))
                .orElse(MonthlyUsage.zero());
    }

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
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    public void incrementStudyPackGeneration(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 1, 0, 0);
    }

    public void incrementChallengeQuizGeneration(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 0, 1, 0);
    }

    public void incrementAdaptiveQuizGeneration(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 0, 0, 1);
    }

    public void incrementOcrExtraction(UUID userId, OffsetDateTime occurredAt) {
        increment(userId, occurredAt, 0, 0, 0, 1);
    }

    private void increment(
            UUID userId,
            OffsetDateTime occurredAt,
            int studyPackDelta,
            int challengeDelta,
            int adaptiveDelta
    ) {
        increment(userId, occurredAt, studyPackDelta, challengeDelta, adaptiveDelta, 0);
    }

    private void increment(
            UUID userId,
            OffsetDateTime occurredAt,
            int studyPackDelta,
            int challengeDelta,
            int adaptiveDelta,
            int ocrDelta
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
                ocrDelta,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    public record MonthlyUsage(
            int studyPackGenerations,
            int challengeQuizGenerations,
            int adaptiveQuizGenerations,
            int ocrExtractions
    ) {
        public static MonthlyUsage zero() {
            return new MonthlyUsage(0, 0, 0, 0);
        }
    }
}
