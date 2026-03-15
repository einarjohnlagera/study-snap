package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateStudyPackTagsRequest(
        @NotNull(message = "Tags are required.")
        List<String> tags
) {
}
