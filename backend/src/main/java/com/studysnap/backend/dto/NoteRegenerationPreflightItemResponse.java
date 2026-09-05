package com.studysnap.backend.dto;

import java.util.UUID;

/**
 * One Note's deterministic preflight verdict.
 *
 * <p>{@code readiness} is {@code READY}, {@code BLOCKED} or {@code NOT_ELIGIBLE}. There is deliberately
 * no "review recommended" state: it would require judging metadata quality, and a Note with a NULL
 * Domain Context and one joined program is fully generation-ready.
 */
public record NoteRegenerationPreflightItemResponse(
        UUID noteId,
        String title,
        String readiness,
        String reasonCode,
        String reason
) {
}
