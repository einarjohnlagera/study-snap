package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleConnectRequest(
        @NotBlank(message = "Google credential is required.")
        String credential
) {
}
