package com.studysnap.backend.service.model;

public record OcrResult(
		String extractedText,
		double confidence
) {
}
