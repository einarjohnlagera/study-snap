package com.studysnap.backend.dto;

import jakarta.validation.constraints.Min;

public record LongExamCompleteRequest(
        @Min(0) int durationSeconds
) {
}
