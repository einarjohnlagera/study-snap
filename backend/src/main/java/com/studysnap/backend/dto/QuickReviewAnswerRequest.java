package com.studysnap.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QuickReviewAnswerRequest(
        @NotNull @Min(0) Integer questionIndex,
        @NotNull @Min(0) @Max(1) Integer retryCount,
        @Min(0) Integer selectedChoiceIndex,
        List<@Min(0) Integer> selectedMultiChoiceIndices
) {
}
