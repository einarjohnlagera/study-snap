package com.studysnap.backend.service.impl;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.service.LlmStudyPackService;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
@ConditionalOnProperty(prefix = "studysnap.llm.api", name = "provider", havingValue = "stub")
public class StubLlmStudyPackService implements LlmStudyPackService {

    @Override
    public GeneratedStudyPackContent generateStudyPack(String normalizedNotesText, StudyPackGenerationContext context) {
        String preview = normalizedNotesText.length() > 80
                ? normalizedNotesText.substring(0, 80) + "..."
                : normalizedNotesText;

        return new GeneratedStudyPackContent(
                "Study Pack: " + preview,
                "These notes have been organized into a concise study summary to support focused revision.",
                "General Studies",
                List.of("Core Concepts", "Study Skills", "Review Practice"),
                List.of(
                        "Main topic and scope",
                        "Core definitions and relationships",
                        "Important formulas or rules"
                ),
                List.of(
                        new QuizItem(
                                "What is the main topic of these notes?",
                                List.of("Topic A", "Topic B", "Topic C", "Topic D"),
                                "Topic A",
                                "Main Topic",
                                "The topic comes directly from the provided notes."
                        ),
                        new QuizItem(
                                "Which concept should be studyPacked first?",
                                List.of("Background idea", "Core definition", "Edge case", "Advanced exception"),
                                "Core definition",
                                "Core Definitions",
                                "Foundational definitions are best reviewed first."
                        )
                ),
                "stub-model",
                null,
                null,
                null,
                null
        );
    }

    @Override
    public String generateQuickReviewStudyTip(List<String> incorrectQuestionSummaries) {
        if (incorrectQuestionSummaries == null || incorrectQuestionSummaries.isEmpty()) {
            return null;
        }
        String first = incorrectQuestionSummaries.getFirst();
        return "Review this concept again: " + first;
    }

    @Override
    public List<QuizItem> generateAdaptivePracticeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> weakConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            StudyPackGenerationContext context
    ) {
        if (weakConcepts == null || weakConcepts.isEmpty()) {
            return List.of();
        }
        int normalizedCount = Math.max(5, Math.min(10, questionCount));
        return IntStream.range(0, normalizedCount)
                .mapToObj(index -> {
                    String concept = weakConcepts.get(index % weakConcepts.size());
                    String correctAnswer = concept + " core principle";
                    return new QuizItem(
                            "Which statement best explains " + concept + "?",
                            List.of(
                                    correctAnswer,
                                    concept + " unrelated detail",
                                    concept + " common misconception",
                                    concept + " less accurate interpretation"
                            ),
                            correctAnswer,
                            concept,
                            "Review the " + concept + " concept in your notes."
                    );
                })
                .toList();
    }

    @Override
    public List<QuizItem> generateChallengeQuiz(
            String studyPackTitle,
            String studyPackSummary,
            List<String> keyConcepts,
            List<String> disallowedQuestions,
            int questionCount,
            String difficulty,
            StudyPackGenerationContext context
    ) {
        List<String> concepts = keyConcepts == null || keyConcepts.isEmpty()
                ? List.of("Core Concept")
                : keyConcepts;
        int normalizedCount = Math.max(10, Math.min(15, questionCount));
        return IntStream.range(0, normalizedCount)
                .mapToObj(index -> {
                    String concept = concepts.get(index % concepts.size());
                    String correctAnswer = concept + " applied understanding";
                    return new QuizItem(
                            "In a " + difficulty + " scenario, what best represents " + concept + "?",
                            List.of(
                                    correctAnswer,
                                    concept + " superficial detail",
                                    concept + " unrelated association",
                                    concept + " incorrect assumption"
                            ),
                            correctAnswer,
                            concept,
                            "Review the " + concept + " concept in your notes."
                    );
                })
                .toList();
    }
}
