package com.studysnap.backend.exception;

import org.springframework.http.HttpStatus;

public class DeletedUserSentinelNotFoundException extends AppException {
    public DeletedUserSentinelNotFoundException() {
        super(
                "DELETED_USER_SENTINEL_NOT_FOUND",
                "Deleted user sentinel is not configured.",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
