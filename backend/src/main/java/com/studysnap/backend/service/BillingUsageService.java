package com.studysnap.backend.service;

import com.studysnap.backend.dto.BillingUsageSummaryResponse;
import com.studysnap.backend.dto.MePlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BillingUsageService {
    private final MePlanService mePlanService;

    public BillingUsageSummaryResponse getMonthlyUsageSummary(UUID userId) {
        MePlanResponse plan = mePlanService.getPlan(userId);
        return new BillingUsageSummaryResponse(
                plan.plan(),
                plan.usage().studyPacksUsed(),
                plan.limits().studyPacksPerMonth(),
                plan.usage().challengeQuizzesUsed(),
                plan.limits().challengeQuizzesPerMonth(),
                plan.usage().adaptivePracticeUsed(),
                plan.limits().adaptivePracticePerMonth(),
                plan.usage().interviewPracticeUsed(),
                plan.limits().interviewPracticePerMonth(),
                plan.features().adaptivePracticeAvailable(),
                plan.features().interviewPracticeAvailable(),
                plan.features().difficultySelectionAvailable()
        );
    }
}
