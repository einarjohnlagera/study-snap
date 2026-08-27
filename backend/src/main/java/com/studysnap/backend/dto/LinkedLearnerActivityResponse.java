package com.studysnap.backend.dto;

import com.studysnap.backend.entity.EngagementMode;

public record LinkedLearnerActivityResponse(
        String displayName,
        EngagementMode engagementMode,
        int currentStreak,
        int longestStreak,
        int studyDaysThisWeek
) {
}
