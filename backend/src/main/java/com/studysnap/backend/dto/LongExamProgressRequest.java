package com.studysnap.backend.dto;

import jakarta.validation.constraints.Min;

import java.util.List;

public record LongExamProgressRequest(
        @Min(0) int questionIndex,
        @Min(0) int selectedChoiceIndex,
        List<Integer> selectedMultiChoiceIndices,
        String selectedIdentificationAnswer
) {
    public LongExamProgressRequest(int questionIndex, int selectedChoiceIndex) {
        this(questionIndex, selectedChoiceIndex, null, null);
    }
}
