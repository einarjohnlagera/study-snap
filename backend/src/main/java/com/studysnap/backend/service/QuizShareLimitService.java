package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.QuizShareLinkLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizShareLimitService {
    private final SubscriptionService subscriptionService;
    private final UserUsageService userUsageService;
    private final StudySnapProperties properties;

    public void assertShareLinkQuotaNotExceeded(UUID userId) {
        PlanType planType = subscriptionService.resolvePlan(userId);
        Integer limit = properties.getPricing().resolveMonthlyQuizShareLinkLimit(planType);
        if (limit == null) {
            return;
        }
        UserUsageService.MonthlyUsage usage = userUsageService.getMonthlyUsage(userId, OffsetDateTime.now(ZoneOffset.UTC));
        if (usage.quizShareLinksCreated() >= limit) {
            throw QuizShareLinkLimitExceededException.forPlan(planType);
        }
    }
}
