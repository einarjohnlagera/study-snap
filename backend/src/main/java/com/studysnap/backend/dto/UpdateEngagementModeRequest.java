package com.studysnap.backend.dto;

import com.studysnap.backend.entity.EngagementMode;
import jakarta.validation.constraints.NotNull;

public record UpdateEngagementModeRequest(
        @NotNull EngagementMode engagementMode
) {
}
