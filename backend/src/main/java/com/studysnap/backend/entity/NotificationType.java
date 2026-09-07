package com.studysnap.backend.entity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum NotificationType {
    ANNOUNCEMENT(false),
    ACTION_REQUIRED(true);

    private final boolean actionable;

    NotificationType(boolean actionable) {
        this.actionable = actionable;
    }

    public boolean isActionable() {
        return actionable;
    }

    public static Set<NotificationType> actionableTypes() {
        return Arrays.stream(values())
                .filter(NotificationType::isActionable)
                .collect(Collectors.toUnmodifiableSet());
    }
}
