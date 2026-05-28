package com.studysnap.backend.dto;

public record InterviewSourceNoteRef(
        String studyPackId,
        String noteId,
        String noteTitle,
        int questionCount
) {
}
