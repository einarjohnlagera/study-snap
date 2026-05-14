package com.studysnap.backend.dto;

import java.time.LocalDate;

public record UpdateExamDateRequest(
        LocalDate examDate
) {
}
