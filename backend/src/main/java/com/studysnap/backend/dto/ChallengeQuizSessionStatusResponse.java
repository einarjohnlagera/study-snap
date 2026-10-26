package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewSessionStatus;

public record ChallengeQuizSessionStatusResponse(
        String sessionId, String studyPackId, QuickReviewSessionStatus status,
        boolean preparedNotStarted, String mode, boolean expired
) {}
