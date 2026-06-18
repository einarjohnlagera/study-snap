package com.studysnap.backend.dto;

public record UpdateNoteCollectionRequest(
        String title,
        String description,
        String courseProgram
) {
}
