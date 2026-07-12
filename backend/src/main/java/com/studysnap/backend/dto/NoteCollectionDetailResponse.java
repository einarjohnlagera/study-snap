package com.studysnap.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record NoteCollectionDetailResponse(
        UUID id,
        String title,
        String description,
        String visibility,
        String courseProgram,
        Integer estimatedStudyHours,
        LocalDate targetCompletionDate,
        CompanionContent companion,
        UUID sourcePlanId,
        UUID parentCollectionId,
        int childCount,
        int readyCount,
        Instant createdAt,
        Instant updatedAt,
        NoteCollectionProgressResponse progress,
        List<NoteCollectionItemResponse> items
) {
}
