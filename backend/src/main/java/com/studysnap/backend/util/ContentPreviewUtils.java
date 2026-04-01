package com.studysnap.backend.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ContentPreviewUtils {

    public String buildContentPreview(String content, int maxLength) {
        if (content == null || maxLength <= 0) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }
}
