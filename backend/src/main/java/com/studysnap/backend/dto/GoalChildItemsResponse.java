package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * The item detail of ONE child Subject Plan of a Goal, carried by the batch read.
 *
 * <p>⚠️ IT IS DELIBERATELY MINIMAL AND MUST STAY THAT WAY. The Goal builder consumes only
 * {@code (collectionId, items)} — {@code buildSubjects} reads nothing else off a child detail — so
 * returning N full {@code NoteCollectionDetailResponse} payloads would defeat much of the point of
 * replacing the per-child fan-out.
 *
 * <p>⚠️ {@code items} is the SAME {@code NoteCollectionItemResponse} shape {@code getCollection}
 * already returns, because a divergent shape would turn a request-count fix into a rendering rewrite.
 */
public record GoalChildItemsResponse(
        UUID collectionId,
        List<NoteCollectionItemResponse> items
) {
}
