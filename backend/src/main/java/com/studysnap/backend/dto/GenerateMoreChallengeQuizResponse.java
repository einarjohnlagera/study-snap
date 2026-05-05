package com.studysnap.backend.dto;

import java.util.List;

public record GenerateMoreChallengeQuizResponse(
        List<QuizItem> newQuestions,
        int totalQuestions
) {
}
