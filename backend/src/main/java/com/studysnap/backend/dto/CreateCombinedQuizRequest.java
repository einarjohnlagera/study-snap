package com.studysnap.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record CreateCombinedQuizRequest(
        @NotBlank String title,
        List<@Valid Section> sections
) {
    public record Section(
            UUID noteId,
            List<Integer> questionIndexes
    ) {
    }
}
