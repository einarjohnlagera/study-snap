package com.studysnap.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminFunnelMetricsResponse(
        Integer windowDays,
        OffsetDateTime windowStartedAt,
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
            double ratePercent,
            List<QuotaTypeHitMetrics> quotaTypes
    ) {
    }

    public record QuotaTypeHitMetrics(
            String quotaType,
            String label,
            int monthlyLimit,
            long usersHitQuota,
            long applicableFreeUsers,
            double ratePercent,
            boolean applicable
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
