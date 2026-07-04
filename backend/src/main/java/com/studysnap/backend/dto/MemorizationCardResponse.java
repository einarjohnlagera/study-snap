package com.studysnap.backend.dto;

import com.studysnap.backend.entity.MemorizationGrade;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MemorizationCardResponse(
    String concept,
    int intervalDays,
    BigDecimal easeFactor,
    int repetitions,
    OffsetDateTime dueAt,
    OffsetDateTime lastReviewedAt,
    MemorizationGrade lastGrade
) {
}
