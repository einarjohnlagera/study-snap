package com.studysnap.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GoalCollectionDetailResponse(
        UUID collectionId,
        String title,
        String description,
        String visibility,
        String courseProgram,
        LocalDate targetCompletionDate,
        UUID sourcePlanId,
        UUID parentCollectionId,
        int itemCount,
        int childCount,
        int overallReadinessPercentage,
        int masteredConcepts,
        int dueConcepts,
        int notPracticedConcepts,
        int totalConcepts,
        Integer weeksRemaining,
        Integer conceptsRemaining,
        Integer todaysConceptBudget,
        List<WeeklyFocusDayEntry> weeklyFocusByDay,
        Instant createdAt,
        Instant updatedAt,
        List<GoalCollectionChildResponse> children
) {
}
