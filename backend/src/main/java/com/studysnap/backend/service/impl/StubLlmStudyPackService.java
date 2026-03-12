package com.studysnap.backend.service.impl;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.service.LlmStudyPackService;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
@ConditionalOnProperty(prefix = "studysnap.llm.api", name = "provider", havingValue = "stub")
public class StubLlmStudyPackService implements LlmStudyPackService {

    @Override
    public GeneratedStudyPackContent generateStudyPack(String normalizedNotesText) {
        String preview = normalizedNotesText.length() > 80
                ? normalizedNotesText.substring(0, 80) + "..."
                : normalizedNotesText;

        return new GeneratedStudyPackContent(
                "Study Pack: " + preview,
                "These notes have been organized into a concise study summary to support focused revision.",
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
            int questionCount
    ) {
        if (weakConcepts == null || weakConcepts.isEmpty()) {
            return List.of();
        }
        int normalizedCount = Math.max(3, Math.min(5, questionCount));
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
                            "The correct option matches the central idea of " + concept + "."
                    );
                })
                .toList();
    }
}

