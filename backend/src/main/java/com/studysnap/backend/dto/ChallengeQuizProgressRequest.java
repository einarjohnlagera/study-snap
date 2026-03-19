package com.studysnap.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record ChallengeQuizProgressRequest(
        @NotNull @Min(0) Integer currentQuestionIndex,
        Map<String, Object> sessionState
) {
}
