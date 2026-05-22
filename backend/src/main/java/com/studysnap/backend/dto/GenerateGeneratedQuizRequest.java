package com.studysnap.backend.dto;

public record GenerateGeneratedQuizRequest(
        Integer questionCount,
        String targetLearnerLevel
) {
    public GenerateGeneratedQuizRequest(Integer questionCount) {
        this(questionCount, null);
    }
}
