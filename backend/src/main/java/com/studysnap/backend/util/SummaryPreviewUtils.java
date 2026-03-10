package com.studysnap.backend.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SummaryPreviewUtils {

    public String buildSummaryPreview(String summary, int maxLength) {
        if (summary == null || summary.isBlank()) {
            return "";
        }
        if (maxLength <= 0) {
            return "";
        }
        if (summary.length() <= maxLength) {
            return summary;
        }
        return summary.substring(0, maxLength).trim() + "...";
    }
}
