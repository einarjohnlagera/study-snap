package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteVisibility;

import java.util.UUID;

/**
 * The three note columns an ownership/visibility predicate needs, and nothing else.
 *
 * <p>⚠️ IT EXISTS SO A CATALOG-WIDE SCAN STOPS LOADING {@code content}. The Official Challenge
 * template backfill used to materialize every public {@link com.studysnap.backend.entity.NoteEntity}
 * — ~1,442 rows, each carrying the full note body — to answer a question about three columns.
 */
public record NoteOwnerVisibilityProjection(
        UUID id,
        UUID ownerUserId,
        NoteVisibility visibility
) {
}
