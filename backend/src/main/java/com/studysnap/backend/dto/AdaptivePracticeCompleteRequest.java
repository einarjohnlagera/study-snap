package com.studysnap.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record AdaptivePracticeCompleteRequest(
        @NotNull @Min(0) Integer correctAnswers,
        @NotNull @Min(1) Integer totalQuestions,
        @Min(0) Integer durationSeconds,
        List<String> correctConceptNames,
        Map<Integer, Integer> selectedChoices,
        Map<Integer, List<Integer>> selectedMultiChoices
) {
}
