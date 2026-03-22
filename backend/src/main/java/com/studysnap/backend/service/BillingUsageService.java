package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingUsageSummaryResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BillingUsageService {
    private final SubscriptionService subscriptionService;
    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final ActivityEventRepository activityEventRepository;
    private final StudySnapProperties properties;
    private final UserUsageService userUsageService;
    private final BillingUsagePeriodService billingUsagePeriodService;

    public BillingUsageSummaryResponse getMonthlyUsageSummary(UUID userId) {
        PlanType planType = subscriptionService.resolvePlan(userId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BillingUsagePeriodService.UsagePeriod usagePeriod = billingUsagePeriodService.resolveUsagePeriod(userId, now);

        int studyPacksUsedFromStudyPacks = (int) studyPackRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                usagePeriod.periodStart(),
                usagePeriod.periodEnd()
        );
        int challengeQuizUsedFromSessions = (int) quickReviewSessionRepository.countByUserIdAndSessionModeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                QuickReviewSessionMode.CHALLENGE,
                usagePeriod.periodStart(),
                usagePeriod.periodEnd()
        );
        int adaptivePracticeUsedFromEvents = (int) activityEventRepository.countByUserIdAndActivityTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                ActivityType.STARTED_ADAPTIVE_PRACTICE,
                usagePeriod.periodStart(),
                usagePeriod.periodEnd()
        );
        UserUsageService.MonthlyUsage monthlyUsage = userUsageService.getMonthlyUsage(userId, now);
        int studyPacksUsed = Math.max(studyPacksUsedFromStudyPacks, monthlyUsage.studyPackGenerations());
        int challengeQuizUsed = Math.max(challengeQuizUsedFromSessions, monthlyUsage.challengeQuizGenerations());
        int adaptivePracticeUsed = Math.max(adaptivePracticeUsedFromEvents, monthlyUsage.adaptiveQuizGenerations());

        int studyPacksLimit = planType == PlanType.PREMIUM
                ? properties.getPricing().getPremiumMonthlyStudyPackLimit()
                : properties.getPricing().getFreeMonthlyStudyPackLimit();

        return new BillingUsageSummaryResponse(
                planType,
                studyPacksUsed,
                studyPacksLimit,
                challengeQuizUsed,
                properties.getPricing().getPremiumMonthlyChallengeQuizLimit(),
                adaptivePracticeUsed,
                properties.getPricing().getPremiumMonthlyAdaptivePracticeLimit()
        );
    }
}
