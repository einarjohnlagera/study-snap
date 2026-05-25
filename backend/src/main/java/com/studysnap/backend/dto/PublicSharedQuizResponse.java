package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record PublicSharedQuizResponse(
        UUID quizId,
        String noteTitle,
        List<PublicQuizItem> questions
) {
}
