package com.studysnap.backend.dto;

import java.util.List;

public record MultiNoteQuizDocxExportRequest(
        List<Section> sections,
        boolean includeAnswerKey,
        boolean includeExplanations
) {
    public record Section(
            String title,
            List<String> noteIds
    ) {
    }
}
