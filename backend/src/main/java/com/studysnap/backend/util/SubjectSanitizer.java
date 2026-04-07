package com.studysnap.backend.util;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Sanitization helpers for LLM-generated subject fields.
 *
 * <p>Handles two distinct failure modes:
 * <ul>
 *   <li>Overlong subjects (exceed MAX_SUBJECT_WORDS): attempt structured trimming before rejecting.</li>
 *   <li>Overly broad labels (Medicine, Engineering…): reject so the LLM retries with a more specific one.</li>
 * </ul>
 */
public final class SubjectSanitizer {

    /** Max words for a subject including the "Primary – Subtopic" structure. */
    public static final int MAX_SUBJECT_WORDS = 6;

    /**
     * Single-word broad umbrella labels that should never appear as a final subject.
     * These are domains, not academic subjects — the LLM should refine them to something
     * specific when the note content supports it.
     */
    static final Set<String> OVERLY_BROAD_LABELS = Set.of(
            "business",
            "education",
            "engineering",
            "law",
            "medicine"
    );

    private SubjectSanitizer() {}

    /**
     * Attempts to repair an overlong subject by structured trimming:
     * <ol>
     *   <li>If the subject contains " – ", trim the subtopic portion so the whole label
     *       fits within {@code maxWords}.</li>
     *   <li>Fallback: truncate the raw string to {@code maxWords}.</li>
     * </ol>
     *
     * <p>Returns {@code null} if the repair result is not valid (empty, no alphanumeric content).
     */
    public static String tryRepair(String normalized, int maxWords) {
        if (normalized == null) {
            return null;
        }
        if (normalized.contains(" – ")) {
            int dashIdx = normalized.indexOf(" – ");
            String primary = normalized.substring(0, dashIdx).trim();
            String subtopic = normalized.substring(dashIdx + 3).trim();
            int primaryWordCount = StringNormalizationUtils.countWords(primary);
            int maxSubtopicWords = maxWords - primaryWordCount - 1; // -1 for the em-dash word slot
            if (maxSubtopicWords > 0) {
                String[] subtopicWords = subtopic.split("\\s+");
                if (subtopicWords.length > maxSubtopicWords) {
                    subtopic = String.join(" ", Arrays.copyOf(subtopicWords, maxSubtopicWords));
                    subtopic = subtopic.replaceAll("[,;]+$", "").trim();
                }
                String repaired = primary + " – " + subtopic;
                if (StringNormalizationUtils.hasWordCountBetween(repaired, 1, maxWords)
                        && StringNormalizationUtils.containsAlphaNumeric(repaired)) {
                    return repaired;
                }
            }
        }
        // Fallback: plain word truncation
        String[] words = normalized.split("\\s+");
        if (words.length > maxWords) {
            String truncated = String.join(" ", Arrays.copyOf(words, maxWords));
            truncated = truncated.replaceAll("[,;]+$", "").trim();
            if (StringNormalizationUtils.containsAlphaNumeric(truncated)) {
                return truncated;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} when the subject is a known overly-broad single-word umbrella label
     * that should be refined into a more specific academic subject.
     */
    public static boolean isOverlyBroad(String subject) {
        if (subject == null) {
            return false;
        }
        return OVERLY_BROAD_LABELS.contains(subject.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns {@code true} when the subject is just a flat echo of the course/program name
     * with no subtopic qualifier, which adds no discovery value beyond the course shelf.
     */
    public static boolean matchesBroadCourseProgram(String subject, String courseProgram) {
        if (subject == null || courseProgram == null) {
            return false;
        }
        String normalizedCourseProgram = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(courseProgram);
        if (normalizedCourseProgram == null) {
            return false;
        }
        if (!subject.equalsIgnoreCase(normalizedCourseProgram)) {
            return false;
        }
        // Allow if it already contains a subtopic (the LLM added context beyond the course name)
        return !subject.contains(" - ") && !subject.contains(" – ");
    }
}
