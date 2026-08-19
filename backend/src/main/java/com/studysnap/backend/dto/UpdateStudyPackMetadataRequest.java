package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.studysnap.backend.util.NoteMetadataBounds;

public record UpdateStudyPackMetadataRequest(
        @NotBlank(message = "Title is required.")
        String title,
        @Size(max = NoteMetadataBounds.SUBJECT_MAX_LENGTH, message = NoteMetadataBounds.SUBJECT_TOO_LONG_MESSAGE)
        String subject
) {
}
