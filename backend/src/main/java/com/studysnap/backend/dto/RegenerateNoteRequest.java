package com.studysnap.backend.dto;

/**
 * Body of {@code POST /notes/{id}/regenerate}.
 *
 * <p>{@code scope} is intentionally a raw String rather than the enum: Jackson would reject an unknown
 * enum value with a generic deserialization error, and the contract owes a named exception. Absent body
 * or absent scope means {@code STUDY_PACK}.
 */
public record RegenerateNoteRequest(String scope) {
}
