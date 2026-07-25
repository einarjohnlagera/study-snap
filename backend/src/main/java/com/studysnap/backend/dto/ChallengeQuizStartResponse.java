package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewSessionStatus;

import java.util.List;
import java.util.Map;

public record ChallengeQuizStartResponse(
        String sessionId,
        QuickReviewSessionStatus status,
        String studyPackId,
        String title,
        int totalQuestions,
        int timeLimitSeconds,
        int usedThisMonth,
        int monthlyLimit,
        int boardExamUsedThisMonth,
        int boardExamMonthlyLimit,
        String mode,
        String selectedDifficulty,
        List<QuizItem> quiz,
        int currentQuestionIndex,
        Map<String, Object> sessionState,
        List<LongExamSourceNoteRef> sourceNoteRefs
) {
}
