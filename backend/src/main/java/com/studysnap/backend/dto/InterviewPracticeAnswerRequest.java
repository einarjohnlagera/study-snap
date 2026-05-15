package com.studysnap.backend.dto;

public record InterviewPracticeAnswerRequest(
        int questionIndex,
        String selectedChoice,
        int timeSpentSeconds
) {
}
