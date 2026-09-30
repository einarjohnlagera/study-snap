package com.studysnap.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AdaptivePracticeAnswerRequest(
        @NotNull @Min(0) Integer questionIndex,
        @Min(0) Integer selectedChoiceIndex,
        List<@Min(0) Integer> selectedMultiChoiceIndices
) {
}
