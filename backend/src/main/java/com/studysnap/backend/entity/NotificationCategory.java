package com.studysnap.backend.entity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Badge and retention policy shared by notification producer types.
 *
 * <p>Future category slots are {@code LEARNING_SYSTEM}, {@code SHARED_WITH_YOU}, {@code IMPACT}, and
 * {@code DISCOVERY}. Add one only when a notification type with a real producer maps to it.
 */
public enum NotificationCategory {
    ANNOUNCEMENT(false),
    ACTION_REQUIRED(true);

    private final boolean badgeEligible;
    // true: counts toward the numeric badge and is retained while unread.
    // false: never badges and remains retention-expirable while unread.

    NotificationCategory(boolean badgeEligible) {
        this.badgeEligible = badgeEligible;
    }

    public boolean isBadgeEligible() {
        return badgeEligible;
    }

    public static Set<NotificationCategory> badgeEligibleCategories() {
        return Arrays.stream(values())
                .filter(NotificationCategory::isBadgeEligible)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<NotificationCategory> retentionExpirableCategories() {
        return Arrays.stream(values())
                .filter(category -> !category.isBadgeEligible())
                .collect(Collectors.toUnmodifiableSet());
    }
}
