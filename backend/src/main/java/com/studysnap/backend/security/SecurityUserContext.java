package com.studysnap.backend.security;

import com.studysnap.backend.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SecurityUserContext {
    public UUID requireUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AppException("AUTH_REQUIRED", "You need to log in to use this endpoint.", HttpStatus.UNAUTHORIZED);
        }
        return user.userId();
    }

    public AuthenticatedUser requireAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AppException("AUTH_REQUIRED", "You need to log in to use this endpoint.", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }
}
