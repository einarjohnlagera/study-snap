package com.studysnap.backend.service.model;

import com.studysnap.backend.dto.QuizItem;

import java.util.List;

public record GeneratedReviewContent(
		String title,
		String summary,
		List<String> keyConcepts,
		List<QuizItem> quiz
) {
}
