package com.studysnap.backend.dto;

public record ReviewMeta(
		Double ocrConfidence,
		Long latencyMs
) {
}
