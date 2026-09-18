package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.studysnap.backend.util.NoteMetadataBounds;

import java.util.List;
import java.util.UUID;

public record GenerateNoteFromTopicRequest(
        @NotBlank(message = "Topic is required.")
        @Size(max = 160, message = "Topic must be 160 characters or less.")
        String topic,
        @Size(max = 160, message = "Course/program must be 160 characters or less.")
        List<UUID> courseProgramIds,
        @Size(max = 160, message = "Course/program must be 160 characters or less.")
        String courseProgramText,
        String domainContext,
        @Size(max = NoteMetadataBounds.SUBJECT_MAX_LENGTH, message = NoteMetadataBounds.SUBJECT_TOO_LONG_MESSAGE)
        String subject
) {
    public GenerateNoteFromTopicRequest(String topic, String courseProgramText, String domainContext) {
        this(topic, List.of(), courseProgramText, domainContext, null);
    }

    public GenerateNoteFromTopicRequest(
            String topic,
            List<UUID> courseProgramIds,
            String courseProgramText,
            String domainContext
    ) {
        this(topic, courseProgramIds, courseProgramText, domainContext, null);
    }
}
