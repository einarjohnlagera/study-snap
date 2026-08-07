package com.studysnap.backend.dto;

import com.studysnap.backend.entity.LearnerLevel;

/**
 * Narrow, single-purpose payload for the two fields onboarding Step 2 collects.
 *
 * <p>Deliberately NOT the full {@code UpdateUserProfileRequest}: that path is a read-modify-write full
 * replace requiring firstName, email and username, so onboarding would have to round-trip and resend
 * identity fields it has no business touching — risking a lost update against a concurrent edit and
 * clobbering {@code pendingEmail}. It also cannot be called before those fields exist.
 *
 * <p>Follows the five existing single-purpose profile endpoints (exam-date, goal, focus-subjects,
 * public-visibility, study-days-per-week).
 */
public record UpdateLearningContextRequest(
        LearnerLevel learnerLevel,
        String courseProgram
) {
}
