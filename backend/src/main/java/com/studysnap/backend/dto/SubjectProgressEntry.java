package com.studysnap.backend.dto;

public record SubjectProgressEntry(
    String subject,
    int totalConcepts,
    int masteredConcepts,
    int dueConcepts,
    int notPracticedConcepts,
    int masteryPercentage
) {
}
