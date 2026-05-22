package com.studysnap.backend.dto;

import jakarta.validation.Valid;

import java.util.List;

public record MultiNoteQuizDocxExportRequest(
        List<Section> sections,
        boolean includeAnswerKey,
        boolean includeExplanations,
        @Valid QuizDocxExportHeaderOverrideRequest headerOverride
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
