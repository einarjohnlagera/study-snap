package com.studysnap.backend.repository;

import java.time.LocalDate;

public interface WeeklyRetentionCohortProjection {
    LocalDate getWeekStart();

    long getCohortSize();

    long getReturnedCount();

    long getReturnedAfterDay7Count();
}
