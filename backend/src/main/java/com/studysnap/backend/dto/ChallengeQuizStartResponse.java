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
        List<LongExamSourceNoteRef> sourceNoteRefs,
        int maxSourceNotes,
        boolean preparedNotStarted,
        ChallengeQuizCollision collision,
        int requestedQuestionCount,
        boolean redoMissed
) {
    public ChallengeQuizStartResponse(
            String sessionId, QuickReviewSessionStatus status, String studyPackId, String title,
            int totalQuestions, int timeLimitSeconds, int usedThisMonth, int monthlyLimit,
            int boardExamUsedThisMonth, int boardExamMonthlyLimit, String mode,
            String selectedDifficulty, List<QuizItem> quiz, int currentQuestionIndex,
            Map<String, Object> sessionState, List<LongExamSourceNoteRef> sourceNoteRefs,
            int maxSourceNotes, boolean preparedNotStarted, ChallengeQuizCollision collision
    ) {
        this(sessionId, status, studyPackId, title, totalQuestions, timeLimitSeconds, usedThisMonth,
                monthlyLimit, boardExamUsedThisMonth, boardExamMonthlyLimit, mode, selectedDifficulty,
                quiz, currentQuestionIndex, sessionState, sourceNoteRefs, maxSourceNotes,
                preparedNotStarted, collision, totalQuestions, false);
    }

    public ChallengeQuizStartResponse(
            String sessionId, QuickReviewSessionStatus status, String studyPackId, String title,
            int totalQuestions, int timeLimitSeconds, int usedThisMonth, int monthlyLimit,
            int boardExamUsedThisMonth, int boardExamMonthlyLimit, String mode,
            String selectedDifficulty, List<QuizItem> quiz, int currentQuestionIndex,
            Map<String, Object> sessionState, List<LongExamSourceNoteRef> sourceNoteRefs,
            int maxSourceNotes, boolean preparedNotStarted
    ) {
        this(sessionId, status, studyPackId, title, totalQuestions, timeLimitSeconds, usedThisMonth,
                monthlyLimit, boardExamUsedThisMonth, boardExamMonthlyLimit, mode, selectedDifficulty,
                quiz, currentQuestionIndex, sessionState, sourceNoteRefs, maxSourceNotes,
                preparedNotStarted, null, totalQuestions, false);
    }

    public ChallengeQuizStartResponse withCollision(ChallengeQuizCollision value) {
        return new ChallengeQuizStartResponse(sessionId, status, studyPackId, title, totalQuestions,
                timeLimitSeconds, usedThisMonth, monthlyLimit, boardExamUsedThisMonth,
                boardExamMonthlyLimit, mode, selectedDifficulty, quiz, currentQuestionIndex,
                sessionState, sourceNoteRefs, maxSourceNotes, preparedNotStarted, value,
                requestedQuestionCount, redoMissed);
    }
}
