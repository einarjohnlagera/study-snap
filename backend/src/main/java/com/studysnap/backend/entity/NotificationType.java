package com.studysnap.backend.entity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum NotificationType {
    ANNOUNCEMENT(NotificationCategory.ANNOUNCEMENT),
    /**
     * ⚠️ TRANSITIONAL, for BACKWARD-COMPATIBLE TAXONOMY TRANSITION ONLY. Stage D replaces this with
     * the first type with a real producer and its learning-system category. Nothing produces
     * ACTION_REQUIRED today.
     *
     * <p>⚠️ Do NOT justify this value by the tests that exercise it. Tests exercise the production
     * model; they do not determine it. Do NOT build on this as a permanent value.
     */
    ACTION_REQUIRED(NotificationCategory.ACTION_REQUIRED);

    private final NotificationCategory category;

    NotificationType(NotificationCategory category) {
        this.category = category;
    }

    public NotificationCategory category() {
        return category;
    }

    public boolean isActionable() {
        return category.isBadgeEligible();
    }

    public static Set<NotificationType> actionableTypes() {
        return Arrays.stream(values())
                .filter(type -> NotificationCategory.badgeEligibleCategories().contains(type.category))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<NotificationType> retentionExpirableTypes() {
        return Arrays.stream(values())
                .filter(type -> NotificationCategory.retentionExpirableCategories().contains(type.category))
                .collect(Collectors.toUnmodifiableSet());
    }
}
