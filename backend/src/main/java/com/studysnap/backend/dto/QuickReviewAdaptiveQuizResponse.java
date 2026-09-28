package com.studysnap.backend.dto;

import com.studysnap.backend.entity.QuickReviewSessionStatus;

import java.util.List;
import java.util.Map;

public record QuickReviewAdaptiveQuizResponse(
        String sessionId,
        QuickReviewSessionStatus status,
        String studyPackId,
        String noteId,
        String title,
        List<AdaptivePracticeFocusConceptResponse> focusConcepts,
        List<QuizItem> quiz,
        Map<Integer, Integer> selectedChoices,
        Map<Integer, List<Integer>> selectedMultiChoices,
        String message
) {
    public QuickReviewAdaptiveQuizResponse(
            String sessionId,
            QuickReviewSessionStatus status,
            String studyPackId,
            String noteId,
            String title,
            List<AdaptivePracticeFocusConceptResponse> focusConcepts,
            List<QuizItem> quiz,
            String message
    ) {
        this(sessionId, status, studyPackId, noteId, title, focusConcepts, quiz, Map.of(), Map.of(), message);
    }
}
