package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller is not a curator, so bulk regeneration is not available to them.
 *
 * <p>⚠️ 403 rather than 404: the capability's existence is not a secret, and nothing about another
 * account is disclosed by saying the caller lacks it.
 */
public class BulkRegenerationNotPermittedException extends AppException {
    public BulkRegenerationNotPermittedException() {
        super(
                "BULK_REGENERATION_NOT_PERMITTED",
                "Bulk regeneration is available to curator accounts.",
                HttpStatus.FORBIDDEN
        );
    }
}
