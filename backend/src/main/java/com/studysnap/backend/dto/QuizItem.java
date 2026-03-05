package com.studysnap.backend.dto;

import java.util.List;

public record QuizItem(
		String question,
		List<String> choices,
		String answer,
		String explanation
) {
}
