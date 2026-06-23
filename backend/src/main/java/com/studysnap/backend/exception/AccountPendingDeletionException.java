package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class AccountPendingDeletionException extends AppException {
    public AccountPendingDeletionException() {
        super(
                "ACCOUNT_PENDING_DELETION",
                "This account is scheduled for deletion. Reactivate to keep it.",
                HttpStatus.FORBIDDEN
        );
    }
}
