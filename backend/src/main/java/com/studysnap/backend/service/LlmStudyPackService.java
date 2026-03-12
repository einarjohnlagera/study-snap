package com.studysnap.backend.service;

import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.dto.QuizItem;

import java.util.List;

public interface LlmStudyPackService {
	GeneratedStudyPackContent generateStudyPack(String normalizedNotesText);

	String generateQuickReviewStudyTip(List<String> incorrectQuestionSummaries);

	List<QuizItem> generateAdaptivePracticeQuiz(
			String studyPackTitle,
			String studyPackSummary,
			List<String> keyConcepts,
			List<String> weakConcepts,
			int questionCount
	);
}

