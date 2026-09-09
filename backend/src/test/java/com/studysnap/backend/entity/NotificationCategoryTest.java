package com.studysnap.backend.entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationCategoryTest {
    @Test
    void badgeEligibleAndRetentionExpirableCategoriesPartitionEveryCategory() {
        var badgeEligible = NotificationCategory.badgeEligibleCategories();
        var retentionExpirable = NotificationCategory.retentionExpirableCategories();

        for (NotificationCategory category : NotificationCategory.values()) {
            assertThat(badgeEligible.contains(category) ^ retentionExpirable.contains(category))
                    .as("category %s belongs to exactly one policy set", category)
                    .isTrue();
        }
    }

    /**
     * ⚠️ THIS CANNOT DISCRIMINATE TODAY AND IS STILL WORTH HAVING — say so rather than let a later
     * reader assume it proves more than it does. With two types mapped one-to-one onto two
     * like-named categories, a hardcoded {@code this == REVIEW_SET_UPDATE} inside
     * {@link NotificationType#isActionable()} is observationally identical to the category
     * delegation, and a mutation test confirmed that mutant survives the whole suite.
     *
     * <p>It is kept because it becomes a real guard the moment a THIRD type exists — which is
     * exactly when the delegation starts to matter and when silent drift would otherwise ship. Same
     * argument as the partition test above: it enumerates {@code values()}, so it fails on the
     * addition rather than on the behaviour that addition breaks.
     */
    @Test
    void everyTypeDerivesItsActionabilityFromItsCategory() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(type.isActionable())
                    .as("type %s must derive actionability from category %s", type, type.category())
                    .isEqualTo(type.category().isBadgeEligible());
        }
    }

    @Test
    void actionableTypesMatchTheTypesWhoseCategoryIsBadgeEligible() {
        var expected = Arrays.stream(NotificationType.values())
                .filter(type -> type.category().isBadgeEligible())
                .collect(Collectors.toUnmodifiableSet());

        assertThat(NotificationType.actionableTypes()).isEqualTo(expected);
    }

    @Test
    void retentionExpirableTypesAreTheComplementOfActionableTypes() {
        var actionable = NotificationType.actionableTypes();
        var expirable = NotificationType.retentionExpirableTypes();

        for (NotificationType type : NotificationType.values()) {
            assertThat(actionable.contains(type) ^ expirable.contains(type))
                    .as("type %s belongs to exactly one retention policy set", type)
                    .isTrue();
        }
    }

    @Test
    void reviewSetUpdatesAreActionableAndAnnouncementsRemainNonActionable() {
        assertThat(NotificationType.actionableTypes()).containsExactly(NotificationType.REVIEW_SET_UPDATE);
        assertThat(NotificationType.ANNOUNCEMENT.isActionable()).isFalse();
    }
}
