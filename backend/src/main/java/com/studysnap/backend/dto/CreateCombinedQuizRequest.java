package com.studysnap.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateCombinedQuizRequest(
        // ⚠️ The Size bound must not exceed combined_quizzes.title's VARCHAR(512). Without it an
        // over-long title reaches the column, raises DataIntegrityViolationException, and surfaces as a
        // 500 instead of a 400 -- the endpoint is directly callable, so the frontend's maxLength is not
        // a bound.
        @NotBlank @Size(max = 512) String title,
        List<@Valid Section> sections
) {
    public record Section(
            UUID noteId,
            List<Integer> questionIndexes
    ) {
    }
}
