package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SharedQuizResultsRequest(
        @NotNull List<Integer> answers
) {
}
