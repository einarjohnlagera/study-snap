package com.studysnap.backend.dto;

public record SharedQuizResultItem(
        boolean correct,
        int correctIndex,
        String explanation
) {
}
