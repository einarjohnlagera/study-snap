package com.studysnap.backend.dto;

import java.util.List;

/**
 * @param sourceCollectionId optional Review Set / Study Plan the sources come from.
 *
 *     <p>⚠️ It is a CLAIM, never a permission. The server re-verifies that the caller owns the
 *     collection and that each source note is a live member of it; a collection id alone must never
 *     relax a validation rule, or the same-subject check becomes a client-supplied opt-out flag.
 */
public record LongExamStartRequest(
        String difficulty,
        List<String> additionalStudyPackIds,
        String sourceCollectionId
) {
    public LongExamStartRequest(String difficulty) {
        this(difficulty, null, null);
    }

    public LongExamStartRequest(String difficulty, List<String> additionalStudyPackIds) {
        this(difficulty, additionalStudyPackIds, null);
    }
}
