package com.studysnap.backend.service.model;

import java.util.List;

public record CompanionGenerationContext(
        String title,
        String description,
        String courseProgram,
        List<CompanionContextItem> subjectPlans,
        List<CompanionContextItem> notes
) {
    public CompanionGenerationContext {
        subjectPlans = subjectPlans == null ? List.of() : List.copyOf(subjectPlans);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public record CompanionContextItem(
            String title,
            String description
    ) {
    }
}
