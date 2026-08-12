package com.studysnap.backend.service.model;

import java.time.OffsetDateTime;

public record StudyPackQuizMastery(
        boolean mastered,
        OffsetDateTime masteredAt
) {
    public static StudyPackQuizMastery notMastered() {
        return new StudyPackQuizMastery(false, null);
    }

    public static StudyPackQuizMastery masteredAt(OffsetDateTime masteredAt) {
        return new StudyPackQuizMastery(true, masteredAt);
    }
}
