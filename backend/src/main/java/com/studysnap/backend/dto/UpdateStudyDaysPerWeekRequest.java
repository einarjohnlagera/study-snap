package com.studysnap.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateStudyDaysPerWeekRequest(
        @Min(value = 1, message = "Study days per week must be between 1 and 7.")
        @Max(value = 7, message = "Study days per week must be between 1 and 7.")
        Integer studyDaysPerWeek
) {
}
