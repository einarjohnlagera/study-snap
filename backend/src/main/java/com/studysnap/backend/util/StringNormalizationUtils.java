package com.studysnap.backend.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StringNormalizationUtils {

    public boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public boolean containsAlphaNumeric(String value) {
        return value != null && value.matches(".*[A-Za-z0-9].*");
    }

    public String normalizeForDuplicateCheck(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
    }

    public String normalizeWhitespaceToSingleSpaceOrNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    public int countWords(String value) {
        if (isBlank(value)) {
            return 0;
        }
        return value.trim().split("\\s+").length;
    }

    public boolean hasWordCountBetween(String value, int minWords, int maxWords) {
        int wordCount = countWords(value);
        return wordCount >= minWords && wordCount <= maxWords;
    }
}
