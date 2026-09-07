package com.studysnap.backend.repository;

import java.util.UUID;

/**
 * One {@code (userId, studyPackId)} pair that already has at least one Challenge bank row.
 *
 * <p>⚠️ IT IS THE BATCHED FORM OF {@code existsByUserIdAndStudyPackId}, and it is a record on
 * purpose: value equality makes the returned list usable directly as a lookup {@code Set}, so the
 * caller never has to invent a second key type that could drift from the pair it is checking.
 */
public record ChallengeQuizQuestionBankOwnerProjection(
        UUID userId,
        UUID studyPackId
) {
}
