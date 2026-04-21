package com.studysnap.backend.dto;

import java.util.List;

public record MultiNoteQuizDocxExportRequest(
        List<String> noteIds,
        boolean includeAnswerKey,
        boolean includeExplanations
) {
}
