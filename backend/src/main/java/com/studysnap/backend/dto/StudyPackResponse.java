package com.studysnap.backend.dto;

import java.util.List;

public record StudyPackResponse(
		String id,
		String inputType,
		String extractedText,
		String title,
		String summary,
		List<String> keyConcepts,
		List<String> tags,
		List<QuizItem> quiz,
		StudyPackMeta meta
) {
}

