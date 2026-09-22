package com.studysnap.backend.dto;

import com.studysnap.backend.entity.PlanIssue;
import com.studysnap.backend.entity.PrimaryBlocker;
import com.studysnap.backend.entity.QuizIssue;

import java.util.List;

public record SubmitCampaignFeedbackRequest(
        List<PrimaryBlocker> primaryBlockers,
        List<QuizIssue> quizIssues,
        PlanIssue planIssue,
        String missingFeatureText,
        String contentSubjectText,
        String freeText
) {
}
