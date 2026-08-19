package com.studysnap.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.studysnap.backend.util.NoteMetadataBounds;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpsertNoteRequest(
        String title,
        @Size(max = NoteMetadataBounds.SUBJECT_MAX_LENGTH, message = NoteMetadataBounds.SUBJECT_TOO_LONG_MESSAGE)
        String subject,
        List<UUID> courseProgramIds,
        // v0.71.0 renamed this field on the wire from `courseProgram`. Without the alias a LEARNER on a
        // stale bundle sends the old name, courseProgramText reads as null, and
        // NoteService.resolveRequestedCourseProgram falls back to the PROFILE program -- never the note's
        // existing value -- silently reassigning the note during the deploy window. The Java overload
        // below is a compile-time convenience and does nothing for JSON.
        @JsonAlias("courseProgram")
        @Size(max = NoteMetadataBounds.COURSE_PROGRAM_MAX_LENGTH, message = NoteMetadataBounds.COURSE_PROGRAM_TOO_LONG_MESSAGE)
        String courseProgramText,
        String domainContext,
        String learnerLevel,
        List<String> tags,
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
            String content
    ) {
        this(title, subject, List.of(), courseProgramText, domainContext, learnerLevel, tags, content);
    }
}
