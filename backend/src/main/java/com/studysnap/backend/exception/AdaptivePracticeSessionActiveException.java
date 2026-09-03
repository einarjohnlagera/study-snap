package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when Interview Practice cannot start because an Adaptive Practice session is already active
 * on the same note.
 *
 * <p>Both modes share the {@code ADAPTIVE} session discriminator and therefore {@code V41}'s
 * {@code (user_id, study_pack_id, session_mode)} unique index on active sessions, so only one of them
 * can be live per note. Interview Practice previously FORFEITED the other session to make room --
 * silently ending a session it does not own. Refusing is the correct behaviour: starting anyway
 * would be rejected by the unique index.
 */
public class AdaptivePracticeSessionActiveException extends AppException {
    public AdaptivePracticeSessionActiveException() {
        super(
            "ADAPTIVE_PRACTICE_SESSION_ACTIVE",
            "You have an Adaptive Practice session in progress on this note. Finish or end it before starting Interview Practice.",
            HttpStatus.CONFLICT
        );
    }
}
