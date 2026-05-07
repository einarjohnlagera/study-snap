package com.studysnap.backend.entity;

import java.util.EnumSet;
import java.util.Set;

public enum ActivityType {
    CREATED_STUDY_PACK,
    OPENED_STUDY_PACK,
    STARTED_QUICK_REVIEW,
    STARTED_ADAPTIVE_PRACTICE,
    COMPLETED_QUICK_REVIEW,
    COMPLETED_CHALLENGE_QUIZ,
    COMPLETED_ADAPTIVE_QUIZ;

    public static final Set<ActivityType> MEANINGFUL_STUDY_ACTIVITIES = EnumSet.of(
            CREATED_STUDY_PACK,
            STARTED_QUICK_REVIEW,
            STARTED_ADAPTIVE_PRACTICE,
            COMPLETED_QUICK_REVIEW,
            COMPLETED_CHALLENGE_QUIZ,
            COMPLETED_ADAPTIVE_QUIZ
    );
}
