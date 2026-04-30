package com.studysnap.backend.entity;

public enum PlanType {
    FREE,
    PLUS,
    PRO;

    public boolean isPaid() {
        return this == PLUS || this == PRO;
    }

    public String getDisplayName() {
        return switch (this) {
            case FREE -> "Free";
            case PLUS -> "Plus";
            case PRO -> "Pro";
        };
    }

}
