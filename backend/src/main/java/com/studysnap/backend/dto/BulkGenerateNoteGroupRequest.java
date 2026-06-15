package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkGenerateNoteGroupRequest(
        @NotBlank(message = "Subject is required.")
        @Size(max = 160, message = "Subject must be 160 characters or less.")
        String subject,
        @NotEmpty(message = "Each subject must include at least one title.")
        List<String> titles
) {
}
