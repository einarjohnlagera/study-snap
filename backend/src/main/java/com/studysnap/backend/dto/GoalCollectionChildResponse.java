package com.studysnap.backend.dto;

import java.util.UUID;

public record GoalCollectionChildResponse(
        UUID collectionId,
        String title,
        String description,
        int itemCount,
        int overallReadinessPercentage,
        int masteredConcepts,
        int dueConcepts,
        int notPracticedConcepts,
        int totalConcepts
) {
}
