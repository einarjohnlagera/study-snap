package com.studysnap.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public record MultiNoteQuizDocxExportRequest(
        List<Section> sections,
        boolean includeAnswerKey,
        boolean includeExplanations,
        @Valid QuizDocxExportHeaderOverrideRequest headerOverride,
        @Min(1) @Max(3) Integer versionCount
) {
    public record Section(
            String title,
            List<QuestionRef> questionRefs
    ) {
    }

    public record QuestionRef(
            String noteId,
            Integer questionIndex
    ) {
    }
}
