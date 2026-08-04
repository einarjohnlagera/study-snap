package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpsertNoteRequest(
        String title,
        String subject,
        String courseProgram,
        String domainContext,
        String learnerLevel,
        List<String> tags,
        String targetProfileType,
        @NotBlank(message = "Please provide note content before saving.")
        String content
) {
}
