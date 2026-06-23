package com.studysnap.backend.dto;

public record UpdateEmailPreferencesRequest(
        boolean inactivityRemindersEnabled,
        boolean weakConceptRemindersEnabled,
        boolean weeklySummaryRemindersEnabled,
        boolean marketingEmailsEnabled
) {
}
