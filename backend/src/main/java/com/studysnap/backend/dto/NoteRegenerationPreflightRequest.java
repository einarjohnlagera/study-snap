package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

/** Body of {@code POST /notes/regenerate/preflight}. Companion to {@code GET /notes/library/ids}. */
public record NoteRegenerationPreflightRequest(List<UUID> noteIds, String scope) {
}
