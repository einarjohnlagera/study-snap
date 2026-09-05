package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /notes/bulk-regenerate}.
 *
 * <p>{@code scope} is a raw String for the same reason {@link RegenerateNoteRequest}'s is: Jackson
 * would reject an unknown enum value with a generic deserialization error where the contract owes a
 * named exception. Absent scope means {@code STUDY_PACK}.
 *
 * <p>⚠️ Selection is explicitly human. There is no filter, no "everything in this Review Set" and no
 * staleness detection here — the caller sends the exact note ids it means.
 */
public record BulkRegenerateNotesRequest(List<UUID> noteIds, String scope) {
}
