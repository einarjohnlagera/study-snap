package com.studysnap.backend.dto;

import java.util.List;

public record ReviewResponse(
		String id,
		String inputType,
		String extractedText,
		String title,
		String summary,
		List<String> keyConcepts,
		List<QuizItem> quiz,
		ReviewMeta meta
) {
}
