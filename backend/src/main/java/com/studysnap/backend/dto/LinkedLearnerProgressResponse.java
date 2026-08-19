package com.studysnap.backend.dto;

import java.util.UUID;

public record LinkedLearnerProgressResponse(
        UUID relationshipId,
        String learnerDisplayName,
        MasterySnapshotResponse quizPerformance,
        StudyEngagementResponse engagement,
        ReadinessCounts readiness,
        CollectionProgressCounts collectionProgress,
        boolean hasActivity
) {
    public record ReadinessCounts(
            int totalConcepts,
            int masteredConcepts,
            int dueConcepts,
            int notStartedConcepts,
            int readinessPercentage
    ) {
    }

    public record CollectionProgressCounts(
            int collectionCount,
            int totalItems,
            int readyItems,
            int practicedItems
    ) {
    }
}
