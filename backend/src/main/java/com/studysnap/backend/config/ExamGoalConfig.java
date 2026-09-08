package com.studysnap.backend.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExamGoalConfig {
    public static final String ALE = "ale";
    public static final String PNLE = "pnle";
    public static final String LET = "let";
    public static final String CPALE = "cpale";

    private static final String ARCHITECTURE = "Architecture";
    private static final String NURSING = "Nursing";
    private static final String EDUCATION = "Education";
    private static final String ACCOUNTANCY = "Accountancy";

    private static final List<String> VALID_SLUGS = List.of(ALE, PNLE, LET, CPALE);
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
                    List.of(NURSING)
            ),
            LET,
            new ExamGoalDefinition(
                    "LET",
                    "Licensure Examination for Teachers",
                    // ⚠️ FALLBACK ONLY -- used when the live catalog read fails or returns empty.
                    // It must mirror every course_program carrying exam_goal_slug = 'let'. V142
                    // (v0.133.0) took that from ONE row to EIGHT, and this list was left behind:
                    // found at the v0.133.0 signoff by sweeping the surface rather than the diff.
                    // ⚠️ A stale fallback fails SILENTLY -- it under-represents the exam goal instead
                    // of erroring -- so if a future migration adds a 'let' program, add it here too.
                    List.of(
                            EDUCATION,
                            "Special Needs Education",
                            "Elementary Education",
                            "Secondary Education",
                            "Early Childhood Education",
                            "Technical-Vocational Teacher Education",
                            "Physical Education",
                            "Teacher Certification"
                    )
            ),
            CPALE,
            new ExamGoalDefinition(
                    "CPALE",
                    "Certified Public Accountant Licensure Examination",
                    List.of(ACCOUNTANCY)
            )
    );

    private ExamGoalConfig() {
    }

    public static List<String> getFallbackCoursePrograms(String slug) {
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
