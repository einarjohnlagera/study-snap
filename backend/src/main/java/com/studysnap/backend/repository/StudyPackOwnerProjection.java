package com.studysnap.backend.repository;

import java.util.UUID;

/**
 * The three Study Pack columns an ownership predicate needs, and nothing else — the sibling of
 * {@link NoteOwnerVisibilityProjection} on the pack side, so a catalog-wide scan stops loading
 * {@code summary}, {@code key_concepts} and the {@code quiz} JSONB it never reads.
 *
 * <p>⚠️ A RECORD, NOT AN INTERFACE, AND DELIBERATELY SO: {@code StudyPackRepository:151-158} records
 * a reproduced {@code ConverterNotFoundException} when a JPQL {@code @Query}'s declared return type
 * is an interface {@code StudyPackEntity} also implements. A constructor projection cannot hit it.
 */
public record StudyPackOwnerProjection(
        UUID id,
        UUID noteId,
        UUID ownerUserId
) {
}
