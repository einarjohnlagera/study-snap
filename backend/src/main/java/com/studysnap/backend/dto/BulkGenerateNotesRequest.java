package com.studysnap.backend.dto;

import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteTargetProfileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkGenerateNotesRequest(
        @NotBlank(message = "Subject is required.")
        @Size(max = 160, message = "Subject must be 160 characters or less.")
        String subject,
        @NotEmpty(message = "Add at least one title.")
        List<String> titles,
        boolean makePublic,
        @Size(max = 160, message = "Course/program must be 160 characters or less.")
        String courseProgram,
        NoteTargetProfileType targetProfileType,
        LearnerLevel learnerLevel
) {
}
