package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record QuickReviewSessionStartResponse(
        String sessionId,
        QuickReviewSessionStatus status,
        int currentQuestionIndex,
        QuickReviewRound currentRound,
        int retryCount,
        Map<String, Object> sessionState,
        String noteId,
        List<QuizItem> quiz,
        String title,
        List<String> keyConcepts,
        boolean quizMastered,
        OffsetDateTime quizMasteredAt,
        int quizCount,
        boolean isOwner
) {
}
