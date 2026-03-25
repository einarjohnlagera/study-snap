package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.MePlanResponse;
import com.studysnap.backend.entity.PlanType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MePlanService {
    private final SubscriptionService subscriptionService;
    private final UserUsageService userUsageService;
    private final StudySnapProperties properties;

    public MePlanResponse getPlan(UUID userId) {
        PlanType planType = subscriptionService.resolvePlan(userId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserUsageService.MonthlyUsage usage = userUsageService.getMonthlyUsage(userId, now);

        int studyPackLimit = properties.getPricing().resolveMonthlyStudyPackLimit(planType);
        int challengeQuizLimit = properties.getPricing().resolveMonthlyChallengeQuizLimit(planType);
        int adaptivePracticeLimit = properties.getPricing().resolveMonthlyAdaptivePracticeLimit(planType);
        int ocrLimit = properties.getPricing().resolveMonthlyOcrLimit(planType);

        int studyPackUsed = usage.studyPackGenerations();
        int challengeQuizUsed = usage.challengeQuizGenerations();
        int adaptivePracticeUsed = usage.adaptiveQuizGenerations();
        int ocrUsed = usage.ocrExtractions();

        return new MePlanResponse(
                planType,
                new MePlanResponse.Limits(
                        studyPackLimit,
                        challengeQuizLimit,
                        adaptivePracticeLimit,
                        ocrLimit
                ),
                new MePlanResponse.Usage(
                        studyPackUsed,
                        challengeQuizUsed,
                        adaptivePracticeUsed,
                        ocrUsed
                ),
                new MePlanResponse.Remaining(
                        remaining(studyPackLimit, studyPackUsed),
                        remaining(challengeQuizLimit, challengeQuizUsed),
                        remaining(adaptivePracticeLimit, adaptivePracticeUsed),
                        remaining(ocrLimit, ocrUsed)
                ),
                new MePlanResponse.Features(
                        properties.getPricing().isAdaptivePracticeAvailable(planType),
                        properties.getPricing().isDifficultySelectionAvailable(planType),
                        true,
                        ocrLimit > 0
                )
        );
    }

    private int remaining(int limit, int used) {
        return Math.max(0, limit - used);
    }
}
