package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record StudyPackListItemResponse(
        String id,
        String title,
        String summaryPreview,
        int quizCount,
        List<String> tags,
        OffsetDateTime createdAt
) {
}
