package com.studysnap.backend.dto;

import jakarta.validation.constraints.Size;

public record QuizDocxExportHeaderOverrideRequest(
        @Size(max = 120, message = "Class or section must be 120 characters or less.")
        String className,
        Boolean includeDate
) {
}
