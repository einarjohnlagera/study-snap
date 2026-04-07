package com.studysnap.backend.util;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Sanitization helpers for LLM-generated concept fields (both quiz {@code concept} and
 * study-pack {@code keyConcepts}).
 *
 * <p>Handles overlong concepts by:
 * <ol>
 *   <li>Stripping known filler prefixes that bloat the label without adding meaning.</li>
 *   <li>Hard-truncating to the word limit when filler stripping alone is insufficient.</li>
 * </ol>
 */
public final class KeyConceptSanitizer {

    /** Word limit enforced on quiz {@code concept} fields. */
    public static final int MAX_QUIZ_CONCEPT_WORDS = 4;

    /** Word limit enforced on individual {@code keyConcepts} list entries. */
    public static final int MAX_KEY_CONCEPT_WORDS = 4;

    /**
     * Filler phrase prefixes that signal an over-verbose LLM concept.
     * Stripping these recovers the meaningful core of the phrase.
     */
    static final List<String> FILLER_PREFIXES = List.of(
            "relationship between",
            "using the",
            "using a",
            "the role of",
            "definition of",
            "overview of",
            "the concept of",
            "how to"
    );

    private KeyConceptSanitizer() {}

    /**
     * Attempts to repair an overlong concept:
     * <ol>
     *   <li>Strip the leading filler prefix if present.</li>
     *   <li>Truncate to {@code maxWords}.</li>
     * </ol>
     *
     * <p>Returns {@code null} when the repaired result is empty, blank, or still invalid.
     */
    public static String tryRepair(String normalized, int maxWords) {
        if (normalized == null) {
            return null;
        }
        String candidate = normalized;
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String prefix : FILLER_PREFIXES) {
            if (lower.startsWith(prefix + " ")) {
                candidate = normalized.substring(prefix.length()).trim();
                break;
            }
        }
        if (StringNormalizationUtils.countWords(candidate) > maxWords) {
            String[] words = candidate.split("\\s+");
            candidate = String.join(" ", Arrays.copyOf(words, maxWords));
        }
        String rechecked = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(candidate);
        return (rechecked != null && StringNormalizationUtils.hasWordCountBetween(rechecked, 1, maxWords))
                ? rechecked : null;
    }
}
