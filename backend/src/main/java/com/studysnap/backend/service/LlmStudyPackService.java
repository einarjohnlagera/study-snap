package com.studysnap.backend.service;

import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.InterviewPracticeCritique;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.dto.QuizItem;
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.List;

public interface LlmStudyPackService {
	GeneratedStudyPackContent generateStudyPack(String normalizedNotesText, StudyPackGenerationContext context);

	String generateNoteFromTopic(String topic, StudyPackGenerationContext context);

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

	List<QuizItem> generateInterviewPracticeQuiz(
			String studyPackTitle,
			String studyPackSummary,
			List<String> keyConcepts,
			List<String> disallowedQuestions,
			int questionCount,
			StudyPackGenerationContext context
	);

	InterviewPracticeCritique generateInterviewCritique(
			QuizItem question,
			int selectedChoiceIndex
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

	List<QuizItem> generateBoardExamQuiz(
			String studyPackTitle,
			String studyPackSummary,
			List<String> keyConcepts,
			List<String> disallowedQuestions,
			int questionCount,
			String difficulty,
			StudyPackGenerationContext context
	);

	List<QuizItem> generateLongExam(
			String studyPackTitle,
			String studyPackSummary,
			List<String> keyConcepts,
			List<String> disallowedQuestions,
			int questionCount,
			String difficulty,
			StudyPackGenerationContext context
	);

	List<QuizItem> generateLongExamParallel(
			String studyPackTitle,
			String studyPackSummary,
			List<String> keyConcepts,
			List<String> disallowedQuestions,
			int totalQuestions,
			String difficulty,
			StudyPackGenerationContext context,
			AsyncTaskExecutor taskExecutor
	);

	default List<QuizItem> generateMoreChallengeQuiz(
			String studyPackTitle,
			String studyPackSummary,
			List<String> keyConcepts,
			List<String> disallowedQuestions,
			List<String> existingConcepts,
			int questionCount,
			String difficulty,
			StudyPackGenerationContext context
	) {
		return generateChallengeQuiz(studyPackTitle, studyPackSummary, keyConcepts,
				disallowedQuestions, questionCount, difficulty, context);
	}

	List<QuizItem> generateTeacherQuiz(
			String noteTitle,
			String noteContent,
			List<String> disallowedQuestions,
			int questionCount,
			StudyPackGenerationContext context
	);
}
