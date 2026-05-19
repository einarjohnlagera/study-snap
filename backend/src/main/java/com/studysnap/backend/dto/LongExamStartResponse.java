package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record LongExamStartResponse(
        UUID sessionId,
        String status,
        List<QuizItem> quiz,
        int totalQuestions,
        String difficulty,
        boolean canResume,
        int timeLimitSeconds,
        long timerStartedAtEpochSeconds,
        List<LongExamSourceNoteRef> sourceNoteRefs,
        int usedThisMonth,
        int monthlyLimit
) {
}
