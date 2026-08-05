package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record UpsertNoteRequest(
        String title,
        String subject,
        List<UUID> courseProgramIds,
        String courseProgramText,
        String domainContext,
        String learnerLevel,
        List<String> tags,
        String targetProfileType,
        @NotBlank(message = "Please provide note content before saving.")
        String content
) {
    /** Internal compatibility overload; the JSON contract uses courseProgramText. */
    public UpsertNoteRequest(
            String title,
            String subject,
            String courseProgramText,
            String domainContext,
            String learnerLevel,
            List<String> tags,
            String targetProfileType,
            String content
    ) {
        this(title, subject, List.of(), courseProgramText, domainContext, learnerLevel, tags, targetProfileType, content);
    }
}
