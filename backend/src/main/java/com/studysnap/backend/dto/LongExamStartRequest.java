package com.studysnap.backend.dto;

import java.util.List;

public record LongExamStartRequest(
        String difficulty,
        List<String> additionalStudyPackIds
) {
    public LongExamStartRequest(String difficulty) {
        this(difficulty, null);
    }
}
