package com.studysnap.backend.dto;

import java.time.LocalDate;

public record UpdateNoteCollectionRequest(
        String title,
        String description,
        String courseProgram,
        Integer estimatedStudyHours,
        LocalDate targetCompletionDate
) {
}
