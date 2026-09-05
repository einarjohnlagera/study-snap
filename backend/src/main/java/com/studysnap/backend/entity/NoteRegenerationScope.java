package com.studysnap.backend.entity;

import com.studysnap.backend.exception.UnknownNoteRegenerationScopeException;

/**
 * What a {@code POST /notes/{id}/regenerate} call replaces.
 *
 * <p>{@link #STUDY_PACK} is today's behaviour and is the default: an absent or blank scope resolves to
 * it, so the endpoint is a strict superset of {@code POST /notes/{id}/generate}. An unrecognised
 * non-blank value is a client error rather than a silent downgrade.
 */
public enum NoteRegenerationScope {
    STUDY_PACK,
    NOTE_AND_STUDY_PACK;

    public static NoteRegenerationScope parseOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return STUDY_PACK;
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        for (NoteRegenerationScope scope : values()) {
            if (scope.name().equals(normalized)) {
                return scope;
            }
        }
        throw new UnknownNoteRegenerationScopeException();
    }
}
