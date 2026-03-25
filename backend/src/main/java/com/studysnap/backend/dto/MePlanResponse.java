package com.studysnap.backend.dto;

import com.studysnap.backend.entity.PlanType;

public record MePlanResponse(
        PlanType plan,
        Limits limits,
        Usage usage,
        Remaining remaining,
        Features features
) {
    public record Limits(
            int studyPacksPerMonth,
            int challengeQuizzesPerMonth,
            int adaptivePracticePerMonth,
            int ocrPerMonth
    ) {
    }

    public record Usage(
            int studyPacksUsed,
            int challengeQuizzesUsed,
            int adaptivePracticeUsed,
            int ocrUsed
    ) {
    }

    public record Remaining(
            int studyPacksRemaining,
            int challengeQuizzesRemaining,
            int adaptivePracticeRemaining,
            int ocrRemaining
    ) {
    }

    public record Features(
            boolean adaptivePracticeAvailable,
            boolean difficultySelectionAvailable,
            boolean fileUploadAvailable,
            boolean ocrAvailable
    ) {
    }
}
