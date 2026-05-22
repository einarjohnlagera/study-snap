package com.studysnap.backend.entity;

import java.util.Locale;

public enum LearnerLevel {
    GRADE_SCHOOL,
    JUNIOR_HIGH,
    SENIOR_HIGH,
    COLLEGE,
    BOARD_EXAM_REVIEW,
    PROFESSIONAL,
    PERSONAL_LEARNING;

    public static LearnerLevel fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LearnerLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
