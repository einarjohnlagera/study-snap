package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record StudyPackResponse(
		String id,
		String noteId,
		String inputType,
		String extractedText,
		String title,
		String summary,
		String sourceText,
		String subject,
		List<String> keyConcepts,
		List<String> tags,
		List<QuizItem> quiz,
		boolean quizMastered,
		OffsetDateTime quizMasteredAt,
		OffsetDateTime createdAt,
		StudyPackMeta meta
) {
	public StudyPackResponse(
			String id,
			String noteId,
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
		this(
				id,
				noteId,
				inputType,
				extractedText,
				title,
				summary,
				sourceText,
				subject,
				keyConcepts,
				tags,
				quiz,
				false,
				null,
				createdAt,
				meta
		);
	}
}
