package com.studysnap.backend.entity;

import java.time.OffsetDateTime;

public record AskCompanionTurn(
        String question,
        String answer,
        OffsetDateTime createdAt
) {
}
