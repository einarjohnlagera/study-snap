package com.studysnap.backend.dto;

import java.time.LocalDate;
import java.util.List;

public record UpdateReviewCommitmentRequest(
        LocalDate examDate,
        List<String> reviewDays
) {
}
