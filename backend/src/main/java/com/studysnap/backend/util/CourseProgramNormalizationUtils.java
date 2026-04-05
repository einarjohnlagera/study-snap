package com.studysnap.backend.util;

import lombok.experimental.UtilityClass;

import java.util.Locale;

@UtilityClass
public class CourseProgramNormalizationUtils {

    public String normalizeForStorage(String value) {
        return SubjectNormalizationUtils.normalizeForStorage(value);
    }

    public String normalizeForLookup(String value) {
        String normalized = normalizeForStorage(value);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
    }
}
