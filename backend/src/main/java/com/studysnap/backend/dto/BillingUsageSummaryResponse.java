package com.studysnap.backend.dto;

import com.studysnap.backend.entity.PlanType;

public record BillingUsageSummaryResponse(
        PlanType planType,
        int studyPacksUsed,
        int studyPacksLimit,
        int challengeQuizUsed,
        int challengeQuizLimit,
        int adaptivePracticeUsed,
        int adaptivePracticeLimit,
        int interviewPracticeUsed,
        int interviewPracticeLimit,
        int longExamUsed,
        int longExamLimit,
        int boardExamUsed,
        int boardExamLimit,
        boolean adaptivePracticeAvailable,
        boolean interviewPracticeAvailable,
        boolean difficultySelectionAvailable
) {
}
