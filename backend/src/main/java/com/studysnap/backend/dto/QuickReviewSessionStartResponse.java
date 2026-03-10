package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionStatus;

import java.util.Map;

public record QuickReviewSessionStartResponse(
        String sessionId,
        QuickReviewSessionStatus status,
        int currentQuestionIndex,
        QuickReviewRound currentRound,
        int retryCount,
        Map<String, Object> sessionState
) {
}
