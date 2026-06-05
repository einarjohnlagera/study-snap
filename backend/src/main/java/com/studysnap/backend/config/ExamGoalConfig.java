package com.studysnap.backend.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExamGoalConfig {
    public static final String ALE = "ale";
    public static final String PNLE = "pnle";
    public static final String LET = "let";

    private static final String ARCHITECTURE = "Architecture";
    private static final String NURSING = "Nursing";
    private static final String MEDICAL_SURGICAL_NURSING = "Medical – Surgical Nursing";
    private static final String EDUCATION = "Education";

    private static final List<String> VALID_SLUGS = List.of(ALE, PNLE, LET);
    private static final Map<String, ExamGoalDefinition> EXAMS = Map.of(
            ALE,
            new ExamGoalDefinition(
                    "ALE",
                    "Architect Licensure Examination",
                    List.of(ARCHITECTURE)
            ),
            PNLE,
            new ExamGoalDefinition(
                    "PNLE",
                    "Philippine Nurse Licensure Examination",
                    List.of(NURSING, MEDICAL_SURGICAL_NURSING)
            ),
            LET,
            new ExamGoalDefinition(
                    "LET",
                    "Licensure Examination for Teachers",
                    List.of(EDUCATION)
            )
    );

    private ExamGoalConfig() {
    }

    /**
     * Keep this config in sync with frontend/lib/exam-hub-config.ts. CourseProgram values must match production DB
     * values exactly; "Medical – Surgical Nursing" uses U+2013 (en-dash), not a hyphen.
     */
    public static List<String> getCoursePrograms(String slug) {
        ExamGoalDefinition definition = EXAMS.get(normalizeSlug(slug));
        return definition == null ? List.of() : definition.coursePrograms();
    }

    public static boolean isValidSlug(String slug) {
        String normalizedSlug = normalizeSlug(slug);
        return normalizedSlug != null && EXAMS.containsKey(normalizedSlug);
    }

    public static String getShortName(String slug) {
        ExamGoalDefinition definition = EXAMS.get(normalizeSlug(slug));
        return definition == null ? null : definition.shortName();
    }

    public static String getFullName(String slug) {
        ExamGoalDefinition definition = EXAMS.get(normalizeSlug(slug));
        return definition == null ? null : definition.fullName();
    }

    public static List<String> getValidSlugs() {
        return VALID_SLUGS;
    }

    private static String normalizeSlug(String slug) {
        return slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
    }

    private record ExamGoalDefinition(
            String shortName,
            String fullName,
            List<String> coursePrograms
    ) {
    }
}
