package com.studysnap.backend.dto;

import com.studysnap.backend.entity.PlanType;

import java.time.OffsetDateTime;

public record MePlanResponse(
        PlanType plan,
        UsageCycle usageCycle,
        Limits limits,
        Usage usage,
        Remaining remaining,
        Features features
) {
    public record UsageCycle(
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {
    }

    public record Limits(
            int studyPacksPerMonth,
            int challengeQuizzesPerMonth,
            int adaptivePracticePerMonth,
            int ocrPerMonth,
            int noteGenerationsPerMonth
    ) {
    }

    public record Usage(
            int studyPacksUsed,
            int challengeQuizzesUsed,
            int adaptivePracticeUsed,
            int ocrUsed,
            int noteGenerationsUsed
    ) {
    }

    public record Remaining(
            int studyPacksRemaining,
            int challengeQuizzesRemaining,
            int adaptivePracticeRemaining,
            int ocrRemaining,
            int noteGenerationsRemaining
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
