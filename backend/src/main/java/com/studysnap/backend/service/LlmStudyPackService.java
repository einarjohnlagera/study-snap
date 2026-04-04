package com.studysnap.backend.service;

import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.dto.QuizItem;

import java.util.List;

public interface LlmStudyPackService {
	GeneratedStudyPackContent generateStudyPack(String normalizedNotesText, StudyPackGenerationContext context);

	String generateQuickReviewStudyTip(List<String> incorrectQuestionSummaries);

	List<QuizItem> generateAdaptivePracticeQuiz(
			String studyPackTitle,
			String studyPackSummary,
			List<String> keyConcepts,
			List<String> weakConcepts,
			List<String> disallowedQuestions,
			int questionCount,
			StudyPackGenerationContext context
	);

	List<QuizItem> generateChallengeQuiz(
			String studyPackTitle,
			String studyPackSummary,
			List<String> keyConcepts,
			List<String> disallowedQuestions,
			int questionCount,
			String difficulty,
			StudyPackGenerationContext context
	);
}
