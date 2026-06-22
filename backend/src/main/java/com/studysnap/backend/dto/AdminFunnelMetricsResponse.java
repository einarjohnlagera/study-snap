package com.studysnap.backend.dto;

import java.util.List;

public record AdminFunnelMetricsResponse(
        ActivationMetrics activation,
        StuckUsersMetrics stuckUsers,
        QuotaHitMetrics quotaHit,
        PaywallConversionMetrics paywallConversion,
        ValueLoopMetrics valueLoop,
        RetentionCohortMetrics retentionCohort,
        CheckoutConversionMetrics checkoutConversion
) {
    public record ActivationMetrics(
            long totalVerifiedUsers,
            long activatedUsers,
            double activationRatePercent,
            Double medianDaysToFirstPack
    ) {
    }

    public record StuckUsersMetrics(
            long stuckUsersCount
    ) {
    }

    public record QuotaHitMetrics(
            long freeUsersHitQuota,
            long totalFreeUsers,
            double ratePercent
    ) {
    }

    public record PaywallConversionMetrics(
            long usersSeenPaywall,
            long usersUpgradedAfterPaywall,
            double ratePercent
    ) {
    }

    public record ValueLoopMetrics(
            long usersGeneratedPack,
            long usersStartedQuizWithin7Days,
            double ratePercent
    ) {
    }

    public record RetentionCohortMetrics(
            long eligibleActivatedUsers,
            long returnedWeek2Users,
            double ratePercent,
            List<WeeklyRetentionCohortMetrics> weeklyCohorts
    ) {
    }

    public record WeeklyRetentionCohortMetrics(
            String weekStart,
            long cohortSize,
            long returnedCount,
            double ratePercent
    ) {
    }

    public record CheckoutConversionMetrics(
            long usersClickedUpgrade,
            long usersInitiatedCheckout,
            long usersSubscribed,
            double clickToCheckoutRatePercent,
            double checkoutToPaidRatePercent,
            double clickToPaidRatePercent
    ) {
    }
}
