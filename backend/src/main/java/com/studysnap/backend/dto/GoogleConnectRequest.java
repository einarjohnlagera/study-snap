package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleConnectRequest(
        @NotBlank(message = "Google authorization code is required.")
        String code
) {
}
