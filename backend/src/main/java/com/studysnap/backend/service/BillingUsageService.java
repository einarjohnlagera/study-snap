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

    public BillingUsageSummaryResponse getMonthlyUsageSummary(UUID userId) {
        PlanType planType = subscriptionService.resolvePlan(userId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime nextMonthStart = monthStart.plusMonths(1);

        int studyPacksUsedFromStudyPacks = (int) studyPackRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                monthStart,
                nextMonthStart
        );
        int challengeQuizUsedFromSessions = (int) quickReviewSessionRepository.countByUserIdAndSessionModeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                QuickReviewSessionMode.CHALLENGE,
                monthStart,
                nextMonthStart
        );
        int adaptivePracticeUsedFromEvents = (int) activityEventRepository.countByUserIdAndActivityTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                ActivityType.STARTED_ADAPTIVE_PRACTICE,
                monthStart,
                nextMonthStart
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
