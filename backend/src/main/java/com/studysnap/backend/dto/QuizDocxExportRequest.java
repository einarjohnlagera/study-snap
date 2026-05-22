package com.studysnap.backend.dto;

import jakarta.validation.Valid;

public record QuizDocxExportRequest(
        @Valid QuizDocxExportHeaderOverrideRequest headerOverride
) {
}
