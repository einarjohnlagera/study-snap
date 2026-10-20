package com.studysnap.backend.service.model;

import java.util.List;

public record CompanionGenerationContext(
        String title,
        String description,
        String courseProgram,
        List<CompanionContextItem> subjectPlans,
        List<CompanionContextItem> notes,
        LlmRouting routing
) {
    public CompanionGenerationContext(String title, String description, String courseProgram,
            List<CompanionContextItem> subjectPlans, List<CompanionContextItem> notes) {
        this(title, description, courseProgram, subjectPlans, notes, null);
    }

    public CompanionGenerationContext withRouting(LlmRouting value) {
        return new CompanionGenerationContext(title, description, courseProgram, subjectPlans, notes, value);
    }
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
