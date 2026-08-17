package com.studysnap.backend.entity;

import lombok.Getter;

import java.util.Locale;

/**
 * Adding a Domain Context is an architectural decision governed by ADR-001, not routine authoring.
 */
@Getter
public enum DomainContext {
    ENGINEERING_MATHEMATICS("Engineering Mathematics", true),
    ENGINEERING_SCIENCES("Engineering Sciences", true),
    CIVIL_ENGINEERING("Civil Engineering", true),
    PROFESSIONAL_PRACTICE_AND_REGULATION("Professional Practice & Regulation", true),
    GENERAL_EDUCATION("General Education", false),
    PROFESSIONAL_EDUCATION("Professional Education", false),
    NURSING("Nursing", true),
    ACCOUNTANCY("Accountancy", true);

    private final String label;
    private final boolean quantitative;

    DomainContext(String label, boolean quantitative) {
        this.label = label;
        this.quantitative = quantitative;
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
