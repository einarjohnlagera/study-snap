package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkGenerateNotesRequest(
        @NotBlank(message = "Subject is required.")
        @Size(max = 160, message = "Subject must be 160 characters or less.")
        String subject,
        @NotEmpty(message = "Add at least one topic.")
        List<String> topics,
        boolean makePublic,
        List<UUID> courseProgramIds,
        @Size(max = 160, message = "Course/program must be 160 characters or less.")
        String courseProgramText,
        String domainContext,
        String learnerLevel,
        UUID collectionId
) {
    public BulkGenerateNotesRequest(
            String subject,
            List<String> topics,
            boolean makePublic,
            String courseProgramText,
            String domainContext,
            String learnerLevel
    ) {
        this(subject, topics, makePublic, List.of(), courseProgramText, domainContext, learnerLevel, null);
    }

    public BulkGenerateNotesRequest(
            String subject,
            List<String> topics,
            boolean makePublic,
            List<UUID> courseProgramIds,
            String courseProgramText,
            String domainContext,
            String learnerLevel
    ) {
        this(subject, topics, makePublic, courseProgramIds, courseProgramText, domainContext,
                learnerLevel, null);
    }
}
