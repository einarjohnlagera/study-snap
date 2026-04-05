package com.studysnap.backend.util;

import lombok.experimental.UtilityClass;

import java.util.Locale;

@UtilityClass
public class SubjectNormalizationUtils {

    public String normalizeForStorage(String value) {
        String normalized = StringNormalizationUtils.normalizeWhitespaceToSingleSpaceOrNull(value);
        if (normalized == null) {
            return null;
        }

        normalized = normalized.replaceAll("\\s*[-–—]\\s*", " – ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized.isBlank() ? null : normalized;
    }

    public String normalizeForLookup(String value) {
        String normalized = normalizeForStorage(value);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
    }
}
