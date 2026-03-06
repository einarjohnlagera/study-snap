package com.studysnap.backend.service.impl;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.service.LlmReviewService;
import com.studysnap.backend.service.model.GeneratedReviewContent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "studysnap.llm", name = "provider", havingValue = "stub")
public class StubLlmReviewService implements LlmReviewService {

    @Override
    public GeneratedReviewContent generateReview(String normalizedNotesText) {
        String preview = normalizedNotesText.length() > 80
                ? normalizedNotesText.substring(0, 80) + "..."
                : normalizedNotesText;

        return new GeneratedReviewContent(
                "Review: " + preview,
                "These notes have been organized into a concise study summary to support focused revision.",
                List.of(
                        "Main topic and scope",
                        "Core definitions and relationships",
                        "Important formulas or rules"
                ),
                List.of(
                        new QuizItem(
                                "What is the main topic of these notes?",
                                List.of("Topic A", "Topic B", "Topic C"),
                                "Topic A",
                                "The topic comes directly from the provided notes."
                        ),
                        new QuizItem(
                                "Which concept should be reviewed first?",
                                List.of("Background idea", "Core definition", "Edge case"),
                                "Core definition",
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
}
