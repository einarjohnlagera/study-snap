package com.studysnap.backend.service.event;

import java.time.Instant;
import java.util.UUID;

/** Plain committed values safe to carry beyond the publishing transaction and persistence context. */
public record ReviewSetUpdatePublishedEvent(
        UUID sourceCollectionId,
        Instant publishedAt
) {
}
