package com.studysnap.backend.dto;

import java.util.List;

/**
 * Everything the curator needs before committing a bulk regeneration, all of it deterministic.
 *
 * <p>⚠️ {@code sharedQuizzesToDeactivate} is EXACT rather than an estimate:
 * {@code uq_generated_quizzes_note_id} gives at most one generated quiz per note, and the count is of
 * notes in the selection whose quiz still has a live share link. It is zero for
 * {@code STUDY_PACK} scope, matching the single-Note primitive — Study-Pack-only regeneration does not
 * replace the Note content the shared quiz was built from.
 *
 * <p>⚠️ {@code noteGenerationUnitsRequired} counts only items that would actually be DISPATCHED. A
 * blocked Note spends nothing, so counting the raw selection would over-reject a curator whose
 * selection is mostly blocked.
 *
 * <p>⚠️ The Study Pack numbers are DISCLOSURE ONLY. Note generation is the hard floor that produces a
 * 422 before dispatch; a Study Pack shortfall is surfaced so the curator can decide, and is enforced
 * per item by the existing monthly assertion.
 */
public record NoteRegenerationPreflightResponse(
        String scope,
        int requestedCount,
        int readyCount,
        int blockedCount,
        int notEligibleCount,
        int publicNotesAffected,
        int sharedQuizzesToDeactivate,
        int noteGenerationUnitsRequired,
        int noteGenerationUnitsRemaining,
        int studyPackUnitsRequired,
        int studyPackUnitsRemaining,
        boolean quotaExceeded,
        int itemsToRemove,
        int maxBatchSize,
        List<NoteRegenerationPreflightItemResponse> items
) {
}
