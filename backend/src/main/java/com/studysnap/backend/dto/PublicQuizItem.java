package com.studysnap.backend.dto;

import java.util.List;

/**
 * The pre-answer payload a shared-quiz recipient receives.
 *
 * <p>⚠️ It carries {@code questionFormat} so the recipient's UI can offer the right control — a
 * MULTI_SELECT question needs multiple selections — but it must NEVER carry {@code correctIndex},
 * {@code correctIndices} or {@code explanation}. {@code shareable-quiz-links.md} states that rule, and
 * this record is the only thing enforcing it: the answer key reaches the recipient exclusively through
 * {@link SharedQuizResultItem}, after they submit.
 */
public record PublicQuizItem(
        String question,
        List<String> choices,
        String concept,
        String questionFormat
) {
}
