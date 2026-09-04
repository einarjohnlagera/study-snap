package com.studysnap.backend.dto;

import java.util.List;

/**
 * One graded question, returned only after the recipient submits.
 *
 * <p>{@code correctIndices} is populated for MULTI_SELECT questions and empty for every other format,
 * so the review screen has a single rule — prefer {@code correctIndices} when non-empty, else
 * {@code correctIndex} — and never has to branch on the question format itself.
 */
public record SharedQuizResultItem(
        boolean correct,
        int correctIndex,
        List<Integer> correctIndices,
        String explanation
) {
}
