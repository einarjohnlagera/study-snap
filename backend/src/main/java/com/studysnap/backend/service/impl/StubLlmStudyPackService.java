package com.studysnap.backend.service.impl;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.service.LlmStudyPackService;
import com.studysnap.backend.service.model.GeneratedStudyPackContent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

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
                                List.of("Topic A", "Topic B", "Topic C"),
                                "Topic A",
                                "The topic comes directly from the provided notes."
                        ),
                        new QuizItem(
                                "Which concept should be studyPacked first?",
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

