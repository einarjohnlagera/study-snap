package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.MockQuizGenerationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizGenerationService {
    private static final String QUIZ_GENERATION_MODE_MOCK = "mock";
    private static final String QUIZ_TYPE_CHALLENGE = "challenge quiz";
    private static final String QUIZ_TYPE_ADAPTIVE = "adaptive practice";
    private static final String QUIZ_TYPE_TEACHER = "teacher quiz";
    private static final String MOCK_GENERATION_LOG_MESSAGE = "Quiz generation mock mode enabled for {}; real LLM not called.";
    private static final int MAX_MOCK_DELAY_MS = 5_000;

    private final LlmStudyPackService llmStudyPackService;
    private final StudySnapProperties properties;

    public List<QuizItem> generateChallengeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context
    ) {
        if (!isMockModeEnabled()) {
            return llmStudyPackService.generateChallengeQuiz(
                    studyPackTitle,
                    studyPackSummary,
                    keyConcepts,
                    disallowedQuestions,
                    questionCount,
                    difficulty,
                    context
            );
        }

        log.info(MOCK_GENERATION_LOG_MESSAGE, QUIZ_TYPE_CHALLENGE);
        maybeApplyMockDelay();
        return MockQuizGenerationUtils.generateChallengeQuiz(
                studyPackTitle,
                keyConcepts,
                disallowedQuestions,
                questionCount,
                difficulty,
                context
        );
    }

    public List<QuizItem> generateMoreChallengeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            List<String> existingConcepts,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context
    ) {
        if (!isMockModeEnabled()) {
            return llmStudyPackService.generateMoreChallengeQuiz(
                    studyPackTitle,
                    studyPackSummary,
                    keyConcepts,
                    disallowedQuestions,
                    existingConcepts,
                    questionCount,
                    difficulty,
                    context
            );
        }

        log.info(MOCK_GENERATION_LOG_MESSAGE, QUIZ_TYPE_CHALLENGE);
        maybeApplyMockDelay();
        return MockQuizGenerationUtils.generateChallengeQuiz(
                studyPackTitle,
                keyConcepts,
                disallowedQuestions,
                questionCount,
                difficulty,
                context
        );
    }

    public List<QuizItem> generateAdaptivePracticeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> weakConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            StudyPackGenerationContext context
    ) {
        if (!isMockModeEnabled()) {
            return llmStudyPackService.generateAdaptivePracticeQuiz(
                    studyPackTitle,
                    studyPackSummary,
                    keyConcepts,
                    weakConcepts,
                    disallowedQuestions,
                    questionCount,
                    context
            );
        }

        log.info(MOCK_GENERATION_LOG_MESSAGE, QUIZ_TYPE_ADAPTIVE);
        maybeApplyMockDelay();
        return MockQuizGenerationUtils.generateAdaptivePracticeQuiz(
                studyPackTitle,
                weakConcepts,
                disallowedQuestions,
                questionCount,
                context
        );
    }

    public List<QuizItem> generateTeacherQuiz(
            String noteTitle,
            String noteContent,
            List<String> disallowedQuestions,
            int questionCount,
            StudyPackGenerationContext context
    ) {
        if (!isMockModeEnabled()) {
            return llmStudyPackService.generateTeacherQuiz(
                    noteTitle,
                    noteContent,
                    disallowedQuestions,
                    questionCount,
                    context
            );
        }

        log.info(MOCK_GENERATION_LOG_MESSAGE, QUIZ_TYPE_TEACHER);
        maybeApplyMockDelay();
        return MockQuizGenerationUtils.generateTeacherQuiz(
                noteTitle,
                disallowedQuestions,
                questionCount,
                context
        );
    }

    private boolean isMockModeEnabled() {
        String configuredMode = properties.getQuizGeneration().getMode();
        return configuredMode != null && QUIZ_GENERATION_MODE_MOCK.equalsIgnoreCase(configuredMode.trim());
    }

    private void maybeApplyMockDelay() {
        int configuredDelayMs = properties.getQuizGeneration().getMockDelayMs();
        int normalizedDelayMs = Math.max(0, Math.min(configuredDelayMs, MAX_MOCK_DELAY_MS));
        if (normalizedDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(normalizedDelayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.warn("Quiz generation mock delay was interrupted.");
        }
    }
}
