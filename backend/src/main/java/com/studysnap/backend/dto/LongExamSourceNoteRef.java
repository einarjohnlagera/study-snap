package com.studysnap.backend.dto;

public record LongExamSourceNoteRef(
        String studyPackId,
        String noteId,
        String noteTitle,
        int questionCount
) {
}
