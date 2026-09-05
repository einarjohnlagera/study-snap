package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * A combined Note + Study Pack regeneration was requested for a note that has no Study Pack yet.
 *
 * <p>This is regeneration, not first generation. Without a prior pack the operation would silently
 * become "first generation that overwrites the learner's typed content" — a different operation with
 * different disclosure obligations — and the identity guarantee would have nothing to preserve.
 */
public class NoteRegenerationStudyPackRequiredException extends AppException {
    public NoteRegenerationStudyPackRequiredException() {
        super(
                "NOTE_REGENERATION_STUDY_PACK_REQUIRED",
                "This note has no Study Pack yet. Generate one first, then you can regenerate both together.",
                HttpStatus.CONFLICT
        );
    }
}
