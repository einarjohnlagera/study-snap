package com.studysnap.backend.dto;

public record AdminAnalyticsSummaryResponse(
        long landingPageViews,
        long landingCtaClicks,
        long publicNoteViews,
        long publicNoteCopyClicks,
        long signupsStarted,
        long signupsCompleted,
        long emailVerificationsCompleted,
        long totalUsers,
        long totalNotes,
        long totalStudyPacksGenerated,
        long totalChallengeQuizzes,
        long totalAdaptiveQuizzes,
        long totalUpgrades
) {
}
