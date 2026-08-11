package com.studysnap.backend.dto;

import java.util.List;

public record UpdateEmailPreferencesRequest(
        boolean inactivityRemindersEnabled,
        boolean weakConceptRemindersEnabled,
        boolean weeklySummaryRemindersEnabled,
        boolean dueConceptsDigestRemindersEnabled,
        boolean knowledgeImpactDigestRemindersEnabled,
        boolean marketingEmailsEnabled,
        List<String> reviewDays
) {
    public UpdateEmailPreferencesRequest(
            boolean inactivityRemindersEnabled,
            boolean weakConceptRemindersEnabled,
            boolean weeklySummaryRemindersEnabled,
            boolean dueConceptsDigestRemindersEnabled,
            boolean knowledgeImpactDigestRemindersEnabled,
            boolean marketingEmailsEnabled
    ) {
        this(
                inactivityRemindersEnabled,
                weakConceptRemindersEnabled,
                weeklySummaryRemindersEnabled,
                dueConceptsDigestRemindersEnabled,
                knowledgeImpactDigestRemindersEnabled,
                marketingEmailsEnabled,
                null
        );
    }
}
