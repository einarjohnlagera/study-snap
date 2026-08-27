package com.studysnap.backend.dto;

import java.util.List;

public record SharedStudyPackResponse(
        String id,
        String noteId,
        String title,
        String summary,
        String fullNotes,
        List<String> keyConcepts,
        List<QuizItem> quiz,
        String ownerDisplayName
) {
}
