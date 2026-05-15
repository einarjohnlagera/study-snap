package com.studysnap.backend.dto;

import java.util.UUID;

public record InterviewPracticeStartResponse(
        UUID sessionId,
        String status,
        UUID noteId,
        UUID studyPackId,
        int questionCount,
        int currentQuestionIndex,
        int softTimerSeconds,
        QuizItem question
) {
}
