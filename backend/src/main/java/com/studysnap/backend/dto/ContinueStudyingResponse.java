package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewRound;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ContinueStudyingResponse(
        String studyPackId,
        String noteId,
        String noteTitle,
        String subject,
        String courseProgram,
        String domainContext,
        String learnerLevel,
        String summaryPreview,
        ContinueStudyingResumeType resumeType,
        ContinueStudyingReason reason,
        BigDecimal lastScorePercentage,
        OffsetDateTime lastReviewedAt,
        OffsetDateTime lastOpenedAt,
        OffsetDateTime createdAt,
        Integer currentQuestionIndex,
        Integer totalQuestions,
        QuickReviewRound currentRound,
        Integer remainingQuestions,
        ContinueStudyingResumeState resumeState,
        String sessionId,
        boolean preparedNotStarted
) {
    public ContinueStudyingResponse(
            String studyPackId, String noteId, String noteTitle, String subject, String courseProgram,
            String domainContext, String learnerLevel, String summaryPreview,
            ContinueStudyingResumeType resumeType, ContinueStudyingReason reason,
            BigDecimal lastScorePercentage, OffsetDateTime lastReviewedAt, OffsetDateTime lastOpenedAt,
            OffsetDateTime createdAt, Integer currentQuestionIndex, Integer totalQuestions,
            QuickReviewRound currentRound, Integer remainingQuestions,
            ContinueStudyingResumeState resumeState, String sessionId
    ) {
        this(studyPackId, noteId, noteTitle, subject, courseProgram, domainContext, learnerLevel,
                summaryPreview, resumeType, reason, lastScorePercentage, lastReviewedAt, lastOpenedAt,
                createdAt, currentQuestionIndex, totalQuestions, currentRound, remainingQuestions,
                resumeState, sessionId, false);
    }

    public ContinueStudyingResponse withPreparedNotStarted(boolean prepared) {
        return new ContinueStudyingResponse(studyPackId, noteId, noteTitle, subject, courseProgram,
                domainContext, learnerLevel, summaryPreview, resumeType, reason, lastScorePercentage,
                lastReviewedAt, lastOpenedAt, createdAt, currentQuestionIndex, totalQuestions,
                currentRound, remainingQuestions, resumeState, sessionId, prepared);
    }
}
