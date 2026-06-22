package com.studysnap.backend.service;

public enum UnsubscribeCategory {
    MARKETING("Product news & tips"),
    WEEKLY_SUMMARY("Weekly summary"),
    STUDY_REMINDERS("Study reminders"),
    WEAK_CONCEPT("Weak-concept nudges");

    private final String displayName;

    UnsubscribeCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
