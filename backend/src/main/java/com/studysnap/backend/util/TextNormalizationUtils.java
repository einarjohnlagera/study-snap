package com.studysnap.backend.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TextNormalizationUtils {

    public boolean containsAlphaNumeric(String value) {
        return value != null && value.matches(".*[A-Za-z0-9].*");
    }

    public String normalizeForDuplicateCheck(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
    }
}
