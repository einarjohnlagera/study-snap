package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record GeneratedQuizResponse(
        String noteId,
        List<QuizItem> questions,
        OffsetDateTime generatedAt
) {
}
