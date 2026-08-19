package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import com.studysnap.backend.util.NoteMetadataBounds;

import java.util.List;
import java.util.UUID;

public record BulkGenerateNotesRequest(
        @NotBlank(message = "Subject is required.")
        @Size(max = NoteMetadataBounds.SUBJECT_MAX_LENGTH, message = NoteMetadataBounds.SUBJECT_TOO_LONG_MESSAGE)
        String subject,
        @NotEmpty(message = "Add at least one topic.")
        List<String> topics,
        boolean makePublic,
        List<UUID> courseProgramIds,
        @Size(max = NoteMetadataBounds.COURSE_PROGRAM_MAX_LENGTH, message = NoteMetadataBounds.COURSE_PROGRAM_TOO_LONG_MESSAGE)
        String courseProgramText,
        String domainContext,
        String learnerLevel,
        UUID collectionId,
        @Size(max = 120, message = "Section must be 120 characters or less.")
        String sectionLabel
) {
    public BulkGenerateNotesRequest(
            String subject,
            List<String> topics,
            boolean makePublic,
            String courseProgramText,
            String domainContext,
            String learnerLevel
    ) {
        this(subject, topics, makePublic, List.of(), courseProgramText, domainContext, learnerLevel, null, null);
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
                learnerLevel, null, null);
    }
}
