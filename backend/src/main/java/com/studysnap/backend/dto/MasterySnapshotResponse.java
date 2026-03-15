package com.studysnap.backend.dto;

import java.math.BigDecimal;

public record MasterySnapshotResponse(
        BigDecimal averageRecentScore,
        BigDecimal bestRecentScore,
        Integer studyPacksReviewed
) {
}
