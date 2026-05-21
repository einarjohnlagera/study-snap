package com.studysnap.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpsertNoteRequest(
        String title,
        String subject,
        String courseProgram,
        List<String> tags,
        String targetProfileType,
        @JsonAlias("learnerLevel")
        String targetLearnerLevel,
        @NotBlank(message = "Please provide note content before saving.")
        String content
) {
    public UpsertNoteRequest(
            String title,
            String subject,
            String courseProgram,
            List<String> tags,
            String targetProfileType,
            String content
    ) {
        this(title, subject, courseProgram, tags, targetProfileType, null, content);
    }
}
