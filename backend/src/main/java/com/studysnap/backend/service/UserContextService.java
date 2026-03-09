package com.studysnap.backend.service;

import com.studysnap.backend.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserContextService {
    public UUID requireUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new AppException(
                    "AUTH_REQUIRED",
                    "You need to log in to use this endpoint.",
                    HttpStatus.UNAUTHORIZED
            );
        }
        try {
            return UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException ex) {
            throw new AppException(
                    "INVALID_USER_CONTEXT",
                    "Invalid user context. Please log in again.",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }
}
