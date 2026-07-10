package com.studysnap.backend.dto;

import java.util.UUID;

public record CompanionMentorTip(
        UUID id,
        String title,
        String body,
        CompanionMentorTipAction linkedAction,
        CompanionMentorTipSurfacingCondition surfacingCondition
) {
}
