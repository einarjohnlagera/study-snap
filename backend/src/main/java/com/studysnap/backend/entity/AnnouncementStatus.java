package com.studysnap.backend.entity;

/**
 * ⚠️ CONTENT IS IMMUTABLE FROM {@code PUBLISHED} ONWARDS. To correct copy materially, end the
 * announcement and publish a replacement — an edit on a published or ended row is refused, never
 * silently ignored, because delivered notification rows carry a COPY of the text and would otherwise
 * disagree with their own source.
 */
public enum AnnouncementStatus {
    DRAFT,
    PUBLISHED,
    ENDED
}
