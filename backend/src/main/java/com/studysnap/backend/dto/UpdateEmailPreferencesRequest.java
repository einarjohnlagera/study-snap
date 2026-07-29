package com.studysnap.backend.dto;

public record UpdateEmailPreferencesRequest(
        boolean inactivityRemindersEnabled,
        boolean weakConceptRemindersEnabled,
        boolean weeklySummaryRemindersEnabled,
        boolean dueConceptsDigestRemindersEnabled,
        boolean knowledgeImpactDigestRemindersEnabled,
        boolean marketingEmailsEnabled
) {
}
