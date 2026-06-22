package com.studysnap.backend.dto;

public record UpdateStudyRemindersRequest(
        boolean inactivityRemindersEnabled,
        boolean weakConceptRemindersEnabled,
        boolean weeklySummaryRemindersEnabled
) {
}
