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

    /**
     * Parses a slug-shaped value, tolerating the hyphen/space forms the rest of the Public Library's
     * query params already accept ({@code ?subject=}, {@code ?courseProgram=}). {@code senior-high}
     * and {@code "senior high"} both resolve to {@link #SENIOR_HIGH}.
     *
     * <p>Deliberately separate from {@link #fromString}: that method is also used by the authoring
     * metadata parser and by quiz level overrides, and this looser contract belongs to the URL
     * boundary rather than to those. Returns null on anything unrecognised, so an invalid
     * {@code ?level=} renders an unfiltered library instead of erroring.
     */
    public static LearnerLevel fromSlug(String value) {
        if (value == null) {
            return null;
        }
        return fromString(value.trim().replaceAll("[\\s-]+", "_"));
    }

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
