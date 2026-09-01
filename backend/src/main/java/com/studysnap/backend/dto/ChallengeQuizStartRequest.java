package com.studysnap.backend.dto;

import java.util.List;

/**
 * @param sourceCollectionId optional Review Set / Study Plan the Board Exam sources come from.
 *
 *     <p>⚠️ A CLAIM, never a permission — the server re-verifies ownership and per-source membership.
 *     See {@link com.studysnap.backend.service.PlanSourcedExamVerifier}.
 */
public record ChallengeQuizStartRequest(
        String mode,
        List<String> additionalStudyPackIds,
        String sourceCollectionId
) {
    public ChallengeQuizStartRequest(String mode, List<String> additionalStudyPackIds) {
        this(mode, additionalStudyPackIds, null);
    }
}
