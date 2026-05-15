package com.studysnap.backend.dto;

public record InterviewPracticeAnswerResponse(
        String verdict,
        String rationale,
        String followUp,
        QuizItem nextQuestion
) {
}
