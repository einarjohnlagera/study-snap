package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record InterviewReadinessReportResponse(
        UUID sessionId,
        int totalQuestions,
        int correctAnswers,
        int scorePercentage,
        String band,
        List<String> strengths,
        List<InterviewGap> gaps,
        List<String> talkingPoints,
        List<Integer> pacingNotes
) {
    public record InterviewGap(
            String concept,
            UUID noteId
    ) {
    }
}
