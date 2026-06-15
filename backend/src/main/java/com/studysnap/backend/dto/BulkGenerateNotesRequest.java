package com.studysnap.backend.dto;

import com.studysnap.backend.entity.LearnerLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkGenerateNotesRequest(
        @NotBlank(message = "Course/program is required.")
        @Size(max = 160, message = "Course/program must be 160 characters or less.")
        String courseProgram,
        @NotNull(message = "Target audience is required.")
        LearnerLevel targetAudience,
        boolean makePublic,
        @NotEmpty(message = "Add at least one subject group.")
        List<@Valid BulkGenerateNoteGroupRequest> groups
) {
}
