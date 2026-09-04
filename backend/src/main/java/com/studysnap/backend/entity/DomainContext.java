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
    PROFESSIONAL_PRACTICE_AND_REGULATION("Professional Practice & Regulation", false),
    GENERAL_EDUCATION("General Education", false),
    PROFESSIONAL_EDUCATION("Professional Education", false),
    NURSING("Nursing", true),
    ACCOUNTANCY("Accountancy", true),
    // Added in v0.111.0. Appended rather than inserted: DomainContextTest asserts labels with
    // containsExactly, and @Enumerated(EnumType.STRING) persists the NAME, so ordinal position
    // carries no data -- but keeping declaration order stable keeps the assertion readable.
    // ⚠️ All three ship quantitative = false, and that is a decision rather than a default. The
    // computational architecture material -- bioclimatic design, passive cooling, drainage,
    // building services -- already routes to ENGINEERING_SCIENCES, which is true. Per v0.85.0,
    // false is a no-op that falls through to the untouched QUANTITATIVE_KEYWORDS scan, while true
    // is a new signal that is PERMANENT PER NOTE because Study Packs never auto-regenerate.
    // PROFESSIONAL_EDUCATION and PROFESSIONAL_PRACTICE_AND_REGULATION were both delivered true and
    // had to be corrected. Do not flip one of these to true without an owner decision.
    ARCHITECTURAL_DESIGN("Architectural Design", false),
    ARCHITECTURAL_HISTORY_AND_THEORY("History and Theory of Architecture", false),
    PLANNING_AND_SITE_DEVELOPMENT("Planning and Site Development", false);

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
