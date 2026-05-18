package com.studysnap.backend.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LongExamSessionResponse(
        UUID sessionId,
        String status,
        List<QuizItem> quiz,
        Map<Integer, Integer> selectedChoices,
        int currentQuestionIndex,
        int totalQuestions,
        String difficulty,
        boolean paused,
        int timeLimitSeconds,
        long timerStartedAtEpochSeconds,
        List<LongExamSourceNoteRef> sourceNoteRefs
) {
}
