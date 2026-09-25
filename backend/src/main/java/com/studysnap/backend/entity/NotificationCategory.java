package com.studysnap.backend.entity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Badge and retention policy shared by notification producer types.
 *
 * <p>Future category slots are {@code SHARED_WITH_YOU}, {@code IMPACT}, and {@code DISCOVERY}. Add one
 * only when a notification type with a real producer maps to it.
 */
public enum NotificationCategory {
    ANNOUNCEMENT(false, true),
    LEARNING_SYSTEM(true, false),
    ASYNC_RESULT(true, true);

    private final boolean badgeEligible;
    private final boolean retentionExpirable;
    // Badge eligibility and unread-retention expiry are independent policies. An async result needs
    // the learner's attention now without occupying the inbox forever when it is never opened.

    NotificationCategory(boolean badgeEligible, boolean retentionExpirable) {
        this.badgeEligible = badgeEligible;
        this.retentionExpirable = retentionExpirable;
    }

    public boolean isBadgeEligible() {
        return badgeEligible;
    }

    public boolean isRetentionExpirable() {
        return retentionExpirable;
    }

    public static Set<NotificationCategory> badgeEligibleCategories() {
        return Arrays.stream(values())
                .filter(NotificationCategory::isBadgeEligible)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<NotificationCategory> retentionExpirableCategories() {
        return Arrays.stream(values())
                .filter(NotificationCategory::isRetentionExpirable)
                .collect(Collectors.toUnmodifiableSet());
    }
}
