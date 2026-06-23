package com.studysnap.backend.dto;

public record ReactivateAccountRequest(
        String email,
        String password,
        String googleCode,
        Boolean keepSignedIn
) {
}
