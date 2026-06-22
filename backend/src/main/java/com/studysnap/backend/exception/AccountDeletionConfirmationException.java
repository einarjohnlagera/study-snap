package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AccountDeletionConfirmationException extends AppException {
    public AccountDeletionConfirmationException() {
        super(
                "INVALID_ACCOUNT_DELETION_CONFIRMATION",
                "Type DELETE to confirm account deletion.",
                HttpStatus.BAD_REQUEST
        );
    }
}
