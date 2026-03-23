package com.studysnap.backend.dto;

import java.util.List;

public record AdminDashboardRecentEventsResponse(
        List<AdminRecentUpgradeItemResponse> recentPremiumUpgrades,
        List<AdminRecentFailedPaymentItemResponse> recentFailedPayments
) {
}
