package com.studysnap.backend.entity;

import lombok.Getter;

import java.util.Locale;

/**
 * Adding a Domain Context is an architectural decision governed by ADR-001, not routine authoring.
 */
@Getter
public enum DomainContext {
    ENGINEERING_MATHEMATICS("Engineering Mathematics"),
    ENGINEERING_SCIENCES("Engineering Sciences"),
    CIVIL_ENGINEERING("Civil Engineering"),
    PROFESSIONAL_PRACTICE_AND_REGULATION("Professional Practice & Regulation"),
    GENERAL_EDUCATION("General Education"),
    PROFESSIONAL_EDUCATION("Professional Education"),
    NURSING("Nursing"),
    ACCOUNTANCY("Accountancy");

    private final String label;

    DomainContext(String label) {
        this.label = label;
    }

    public static DomainContext fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DomainContext.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
