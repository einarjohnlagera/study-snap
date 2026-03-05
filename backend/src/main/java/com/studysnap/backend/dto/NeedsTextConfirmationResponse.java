package com.studysnap.backend.dto;

public record NeedsTextConfirmationResponse(
		String status,
		String id,
		String extractedText,
		ReviewMeta meta
) {
}
