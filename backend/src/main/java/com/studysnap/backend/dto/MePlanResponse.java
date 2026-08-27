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
            Integer quizShareLinksPerMonth,
            int adaptivePracticePerMonth,
            int interviewPracticePerMonth,
            int ocrPerMonth,
            int noteGenerationsPerMonth,
            Integer docxExportsPerMonth,
            Integer pdfExportsPerMonth,
            int longExamPerMonth,
            int boardExamPerMonth
    ) {
        public Limits(
                int studyPacksPerMonth,
                int challengeQuizzesPerMonth,
                int adaptivePracticePerMonth,
                int ocrPerMonth,
                int noteGenerationsPerMonth,
                Integer exportsPerMonth
        ) {
            this(
                    studyPacksPerMonth,
                    challengeQuizzesPerMonth,
                    0,
                    adaptivePracticePerMonth,
                    0,
                    ocrPerMonth,
                    noteGenerationsPerMonth,
                    exportsPerMonth,
                    exportsPerMonth,
                    0,
                    0
            );
        }

    }

    public record Usage(
            int studyPacksUsed,
            int challengeQuizzesUsed,
            int quizShareLinksUsed,
            int adaptivePracticeUsed,
            int interviewPracticeUsed,
            int ocrUsed,
            int noteGenerationsUsed,
            int docxExportsUsed,
            int pdfExportsUsed,
            int longExamUsed,
            int boardExamUsed
    ) {
        public Usage(
                int studyPacksUsed,
                int challengeQuizzesUsed,
                int adaptivePracticeUsed,
                int ocrUsed,
                int noteGenerationsUsed,
                int exportsUsed
        ) {
            this(
                    studyPacksUsed,
                    challengeQuizzesUsed,
                    0,
                    adaptivePracticeUsed,
                    0,
                    ocrUsed,
                    noteGenerationsUsed,
                    exportsUsed,
                    0,
                    0,
                    0
            );
        }

    }

    public record Remaining(
            int studyPacksRemaining,
            int challengeQuizzesRemaining,
            Integer quizShareLinksRemaining,
            int adaptivePracticeRemaining,
            int interviewPracticeRemaining,
            int ocrRemaining,
            int noteGenerationsRemaining,
            Integer docxExportsRemaining,
            Integer pdfExportsRemaining,
            int longExamRemaining,
            int boardExamRemaining
    ) {
        public Remaining(
                int studyPacksRemaining,
                int challengeQuizzesRemaining,
                int adaptivePracticeRemaining,
                int ocrRemaining,
                int noteGenerationsRemaining,
                Integer exportsRemaining
        ) {
            this(
                    studyPacksRemaining,
                    challengeQuizzesRemaining,
                    0,
                    adaptivePracticeRemaining,
                    0,
                    ocrRemaining,
                    noteGenerationsRemaining,
                    exportsRemaining,
                    exportsRemaining,
                    0,
                    0
            );
        }

    }

    public record Features(
            boolean adaptivePracticeAvailable,
            boolean interviewPracticeAvailable,
            boolean fileUploadAvailable,
            boolean ocrAvailable,
            boolean exportAvailable
    ) {
        public Features(
                boolean adaptivePracticeAvailable,
                boolean fileUploadAvailable,
                boolean ocrAvailable,
                boolean exportAvailable
        ) {
            this(
                    adaptivePracticeAvailable,
                    false,
                    fileUploadAvailable,
                    ocrAvailable,
                    exportAvailable
            );
        }
    }
}
