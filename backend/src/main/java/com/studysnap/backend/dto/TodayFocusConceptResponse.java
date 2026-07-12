package com.studysnap.backend.dto;

public record TodayFocusConceptResponse(
        String concept,
        String noteId,
        String noteTitle
) {
}
