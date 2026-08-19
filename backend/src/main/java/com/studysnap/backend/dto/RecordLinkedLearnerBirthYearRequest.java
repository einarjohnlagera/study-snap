package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotNull;

public record RecordLinkedLearnerBirthYearRequest(
        @NotNull(message = "Birth year is required.")
        Integer birthYear
) {
}
