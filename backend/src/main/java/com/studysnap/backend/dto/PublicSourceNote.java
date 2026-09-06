package com.studysnap.backend.dto;

import java.util.UUID;

/**
 * A source the recipient of a shared quiz may legitimately continue learning from.
 *
 * <p>⚠️ Only ever built from a note whose {@code visibility == PUBLIC}. This travels on a
 * {@code permitAll} payload, so a private note's id or title appearing here would be a disclosure.
 * Private sources are omitted ENTIRELY -- not counted, not hinted, not placeheld.
 */
public record PublicSourceNote(
        UUID id,
        String title
) {
}
