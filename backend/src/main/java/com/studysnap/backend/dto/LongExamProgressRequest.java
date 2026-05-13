package com.studysnap.backend.dto;

import jakarta.validation.constraints.Min;

public record LongExamProgressRequest(
        @Min(0) int questionIndex,
        @Min(0) int selectedChoiceIndex
) {
}
