package com.studysnap.backend.dto;

public record BulkGenerationFailureReason(
        String topic,
        String code,
        String reason
) {
}
