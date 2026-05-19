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
    private final StudyPackUsageService studyPackUsageService;
    private final StudySnapProperties properties;

    public MePlanResponse getPlan(UUID userId) {
        PlanType planType = subscriptionService.resolvePlan(userId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserUsageService.MonthlyUsage usage = userUsageService.getMonthlyUsage(userId, now);
        StudyPackUsageService.UsageSnapshot studyPackUsage = studyPackUsageService.resolveUsage(userId, usage);

        int studyPackLimit = properties.getPricing().resolveMonthlyStudyPackLimit(planType);
        int challengeQuizLimit = properties.getPricing().resolveMonthlyChallengeQuizLimit(planType);
        int adaptivePracticeLimit = properties.getPricing().resolveMonthlyAdaptivePracticeLimit(planType);
        int interviewPracticeLimit = properties.getPricing().resolveMonthlyInterviewPracticeLimit(planType);
        int longExamLimit = properties.getPricing().resolveMonthlyLongExamLimit(planType);
        int boardExamLimit = properties.getPricing().resolveMonthlyBoardExamLimit(planType);
        int ocrLimit = properties.getPricing().resolveMonthlyOcrLimit(planType);
        int noteGenerationLimit = properties.getPricing().resolveMonthlyNoteGenerationLimit(planType);
        Integer exportLimit = properties.getPricing().resolveMonthlyExportLimit(planType);

        int studyPackUsed = studyPackUsage.usedCount();
        int challengeQuizUsed = usage.challengeQuizGenerations();
        int adaptivePracticeUsed = usage.adaptiveQuizGenerations();
        int interviewPracticeUsed = usage.interviewPracticeUsedThisMonth();
        int longExamUsed = usage.longExamUsedThisMonth();
        int boardExamUsed = usage.boardExamUsedThisMonth();
        int ocrUsed = usage.ocrExtractions();
        int noteGenerationUsed = usage.noteGenerations();
        int exportUsed = usage.exportsCount();

        return new MePlanResponse(
                planType,
                new MePlanResponse.UsageCycle(
                        studyPackUsage.periodStart(),
                        studyPackUsage.periodEnd()
                ),
                new MePlanResponse.Limits(
                        studyPackLimit,
                        challengeQuizLimit,
                        adaptivePracticeLimit,
                        interviewPracticeLimit,
                        ocrLimit,
                        noteGenerationLimit,
                        exportLimit,
                        longExamLimit,
                        boardExamLimit
                ),
                new MePlanResponse.Usage(
                        studyPackUsed,
                        challengeQuizUsed,
                        adaptivePracticeUsed,
                        interviewPracticeUsed,
                        ocrUsed,
                        noteGenerationUsed,
                        exportUsed,
                        longExamUsed,
                        boardExamUsed
                ),
                new MePlanResponse.Remaining(
                        remaining(studyPackLimit, studyPackUsed),
                        remaining(challengeQuizLimit, challengeQuizUsed),
                        remaining(adaptivePracticeLimit, adaptivePracticeUsed),
                        remaining(interviewPracticeLimit, interviewPracticeUsed),
                        remaining(ocrLimit, ocrUsed),
                        remaining(noteGenerationLimit, noteGenerationUsed),
                        remainingNullable(exportLimit, exportUsed),
                        remaining(longExamLimit, longExamUsed),
                        remaining(boardExamLimit, boardExamUsed)
                ),
                new MePlanResponse.Features(
                        properties.getPricing().isAdaptivePracticeAvailable(planType),
                        properties.getPricing().isInterviewPracticeAvailable(planType),
                        properties.getPricing().isDifficultySelectionAvailable(planType),
                        true,
                        ocrLimit > 0,
                        exportLimit == null || exportLimit > 0
                )
        );
    }

    private int remaining(int limit, int used) {
        return Math.max(0, limit - used);
    }

    private Integer remainingNullable(Integer limit, int used) {
        if (limit == null) {
            return null;
        }
        return remaining(limit, used);
    }
}
