package com.studysnap.backend.dto;

import java.util.List;

/** A stored snapshot section; {@code title} is copied from the source note, never resolved later. */
public record CombinedQuizSection(
        String title,
        List<QuizItem> questions
) {
}
