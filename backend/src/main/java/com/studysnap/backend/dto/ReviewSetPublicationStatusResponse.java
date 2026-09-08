package com.studysnap.backend.dto;

import java.time.Instant;
import java.util.UUID;

/** Curator-only publication state for one Official Review Set source root. */
public record ReviewSetPublicationStatusResponse(
        UUID collectionId,
        boolean unpublishedChanges,
        int topicsAdded,
        int subjectPlansAdded,
        Instant lastUpdatePublishedAt
) {
}
