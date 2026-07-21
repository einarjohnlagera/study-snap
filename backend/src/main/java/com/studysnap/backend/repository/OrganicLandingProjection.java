package com.studysnap.backend.repository;

import java.time.LocalDate;

public interface OrganicLandingProjection {
    LocalDate getWeekStart();

    String getEventType();

    String getReferrerSource();

    long getTotalCount();
}
