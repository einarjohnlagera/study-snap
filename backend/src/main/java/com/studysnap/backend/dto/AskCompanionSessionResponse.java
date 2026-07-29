package com.studysnap.backend.dto;

import com.studysnap.backend.entity.AskCompanionSessionStatus;
import com.studysnap.backend.entity.AskCompanionTurn;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AskCompanionSessionResponse(
        UUID sessionId,
        UUID collectionId,
        AskCompanionSessionStatus status,
        int turnCount,
        int turnLimit,
        int turnsRemaining,
        List<AskCompanionTurn> turns,
        int usedThisMonth,
        int monthlyLimit,
        OffsetDateTime usagePeriodEndsAt
) {
}
