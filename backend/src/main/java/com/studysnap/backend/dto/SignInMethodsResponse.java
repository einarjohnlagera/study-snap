package com.studysnap.backend.dto;

public record SignInMethodsResponse(
        String email,
        boolean passwordEnabled,
        boolean googleConnected,
        String googleEmail
) {
}
