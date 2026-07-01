package com.studysnap.backend.dto;

public record NoteConceptCountsResponse(
        int totalConceptCount,
        int masteredConceptCount,
        int dueConceptCount,
        int notPracticedConceptCount
) {
}
