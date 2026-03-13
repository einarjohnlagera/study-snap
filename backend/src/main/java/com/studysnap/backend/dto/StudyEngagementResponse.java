package com.studysnap.backend.dto;

import com.studysnap.backend.entity.EngagementMode;

public record StudyEngagementResponse(
        EngagementMode engagementMode,
        int currentStreak,
        int longestStreak,
        int studyDaysThisWeek
) {
}
