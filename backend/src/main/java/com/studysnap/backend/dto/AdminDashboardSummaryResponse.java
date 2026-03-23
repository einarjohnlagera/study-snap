package com.studysnap.backend.dto;

import java.math.BigDecimal;

public record AdminDashboardSummaryResponse(
        Overview overview,
        Billing billing,
        Engagement engagement
) {
    public record Overview(
            long totalUsers,
            long verifiedUsers,
            long premiumUsers,
            long totalNotes,
            long totalStudyPacksGenerated,
            long totalPublicNotes,
            long totalPublicNoteViews,
            long totalPublicNoteCopies,
            long totalUpgrades
    ) {
    }

    public record Billing(
            long activePremiumSubscriptions,
            long monthlySubscriptions,
            long yearlySubscriptions,
            long cancelAtPeriodEndSubscriptions,
            long failedPayments,
            BigDecimal estimatedMrr,
            BigDecimal estimatedArr
    ) {
    }

    public record Engagement(
            long studyPacksGeneratedThisWeek,
            long quickReviewsStarted,
            long challengeQuizzesStarted,
            long adaptivePracticeStarted,
            long paywallViews,
            long upgradeClicks,
            long signups,
            long verifiedAccounts
    ) {
    }
}
