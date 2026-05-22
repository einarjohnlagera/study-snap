package com.studysnap.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record QuizDocxExportRequest(
        @Valid QuizDocxExportHeaderOverrideRequest headerOverride,
        @Min(1) @Max(3) Integer versionCount
) {
}
