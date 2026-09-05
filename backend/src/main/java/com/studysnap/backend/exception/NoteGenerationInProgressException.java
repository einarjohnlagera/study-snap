package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * A Study Pack generation is already running for this note.
 *
 * <p>The code and message are the ones {@code StudyPackService} has thrown from its generation-start
 * guard since that guard existed; this class exists so {@code NoteService.update} can reject with the
 * SAME contract rather than a second, drifting copy of the literals.
 */
public class NoteGenerationInProgressException extends AppException {
    public NoteGenerationInProgressException() {
        super(
                "NOTE_GENERATION_IN_PROGRESS",
                "A Study Pack is already being generated for this note.",
                HttpStatus.CONFLICT
        );
    }
}
