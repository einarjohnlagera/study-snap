package com.studysnap.backend.dto;

import java.util.List;

public record SharedQuizResultsResponse(
        int score,
        int total,
        List<SharedQuizResultItem> items
) {
}
