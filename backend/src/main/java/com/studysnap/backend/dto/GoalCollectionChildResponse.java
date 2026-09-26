package com.studysnap.backend.dto;

import java.util.UUID;

public record GoalCollectionChildResponse(
        UUID collectionId,
        String title,
        String description,
        String termLabel,
        Integer termOrder,
        /** True once the Subject Plan is published: its term can no longer change (see NoteCollectionService.assertTermChangeAllowed). */
        boolean termLocked,
        int itemCount,
        int overallReadinessPercentage,
        int masteredConcepts,
        int dueConcepts,
        int notPracticedConcepts,
        int totalConcepts,
        Integer todaysConceptBudget
) {
}
