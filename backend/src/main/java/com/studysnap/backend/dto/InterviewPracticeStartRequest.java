package com.studysnap.backend.dto;

import java.util.UUID;

public record InterviewPracticeStartRequest(
        UUID noteId,
        Integer questionCount
) {
}
