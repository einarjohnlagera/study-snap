package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record StudyPackResponse(
		String id,
		String inputType,
		String extractedText,
		String title,
		String summary,
		String sourceText,
		String subject,
		List<String> keyConcepts,
		List<String> tags,
		List<QuizItem> quiz,
		OffsetDateTime createdAt,
		StudyPackMeta meta
) {
}

