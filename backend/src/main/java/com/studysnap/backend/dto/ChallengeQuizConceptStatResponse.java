package com.studysnap.backend.dto;

public record ChallengeQuizConceptStatResponse(
        String concept,
        int correctAnswers,
        int totalQuestions,
        int accuracyPercentage
) {
}
