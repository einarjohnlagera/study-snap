package com.studysnap.backend.dto;

import java.util.List;
import java.util.UUID;

public record InterviewPracticeStartRequest(
        UUID noteId,
        Integer questionCount,
        List<UUID> additionalNoteIds
) {
}
