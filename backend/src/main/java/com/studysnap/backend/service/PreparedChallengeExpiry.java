package com.studysnap.backend.service;

import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.util.QuizSessionStateUtils;

import java.time.OffsetDateTime;

/** The preparation clock is stamped at readiness; older rows fall back to creation. */
final class PreparedChallengeExpiry {
    private PreparedChallengeExpiry() {
    }

    static OffsetDateTime preparedAt(QuickReviewSessionEntity session) {
        long epoch = QuizSessionStateUtils.extractPreparedAtEpochSeconds(session.getSessionState());
        return epoch > 0 ? OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(epoch),
                java.time.ZoneOffset.UTC) : session.getCreatedAt();
    }

    static boolean expired(QuickReviewSessionEntity session, OffsetDateTime cutoff) {
        OffsetDateTime preparedAt = preparedAt(session);
        return QuizSessionStateUtils.extractPreparedNotStarted(session.getSessionState())
                && preparedAt != null && !preparedAt.isAfter(cutoff);
    }
}
