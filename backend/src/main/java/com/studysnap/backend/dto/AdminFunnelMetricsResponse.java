package com.studysnap.backend.dto;

public record AdminFunnelMetricsResponse(
        ActivationMetrics activation,
        StuckUsersMetrics stuckUsers,
        QuotaHitMetrics quotaHit,
        PaywallConversionMetrics paywallConversion,
        ValueLoopMetrics valueLoop
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
}
