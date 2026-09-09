package com.studysnap.backend.entity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum NotificationType {
    ANNOUNCEMENT(NotificationCategory.ANNOUNCEMENT),
    REVIEW_SET_UPDATE(NotificationCategory.LEARNING_SYSTEM);

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
