package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record LongExamMasteryReportResponse(
        UUID sessionId,
        int totalQuestions,
        int answeredQuestions,
        int scorePercentage,
        List<LongExamDomainStat> domainBreakdown,
        List<String> weakDomains,
        String performanceSummary,
        String suggestedNextStep,
        List<LongExamSourceNote> sourceNotes,
        boolean shortExam,
        boolean isFirstCompletedSessionEver,
        boolean isSecondCompletedSessionEver
) {
    public record LongExamDomainStat(
            String domain,
            int totalQuestions,
            int correctAnswers,
            int accuracyPercentage
    ) {
    }
}
