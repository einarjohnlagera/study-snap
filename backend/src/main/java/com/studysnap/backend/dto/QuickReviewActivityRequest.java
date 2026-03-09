package com.studysnap.backend.dto;

import com.studysnap.backend.entity.ActivityType;
import jakarta.validation.constraints.NotNull;

public record QuickReviewActivityRequest(
        @NotNull ActivityType activityType
) {
}
