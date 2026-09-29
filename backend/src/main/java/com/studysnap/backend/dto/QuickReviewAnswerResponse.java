package com.studysnap.backend.dto;

public record QuickReviewAnswerResponse(
        int questionIndex,
        QuizItem question
) {
}
