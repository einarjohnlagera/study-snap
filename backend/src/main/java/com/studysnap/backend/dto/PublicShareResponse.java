package com.studysnap.backend.dto;

import java.util.List;

public record PublicShareResponse(
		String id,
		String title,
		String summary,
		List<String> keyConcepts,
		List<QuizItem> quiz
) {
}
