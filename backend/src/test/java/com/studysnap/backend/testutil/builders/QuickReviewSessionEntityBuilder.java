package com.studysnap.backend.testutil.builders;

import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewConfidenceLevel;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unused")
@With
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class QuickReviewSessionEntityBuilder {
    private final UUID id;
    private final UUID userId;
    private final UUID studyPackId;
    private final UUID noteId;
    private final QuickReviewSessionMode sessionMode;
    private final QuickReviewSessionStatus status;
    private final Integer currentQuestionIndex;
    private final QuickReviewRound currentRound;
    private final Integer totalQuestions;
    private final Integer correctAnswers;
    private final BigDecimal scorePercentage;
    private final Integer retryCount;
    private final Integer durationSeconds;
    private final QuickReviewConfidenceLevel confidenceLevel;
    private final Map<String, Object> sessionMetadata;
    private final Map<String, Object> sessionState;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime completedAt;

    public static QuickReviewSessionEntityBuilder anInProgressSession() {
        return new QuickReviewSessionEntityBuilder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                QuickReviewSessionMode.QUICK_REVIEW,
                QuickReviewSessionStatus.IN_PROGRESS,
                0,
                QuickReviewRound.INITIAL,
                5,
                0,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                0,
                null,
                null,
                null,
                null,
                OffsetDateTime.now().minusMinutes(30),
                null
        );
    }

    public static QuickReviewSessionEntityBuilder aCompletedSession() {
        return anInProgressSession()
                .withStatus(QuickReviewSessionStatus.COMPLETED)
                .withCurrentQuestionIndex(5)
                .withCurrentRound(QuickReviewRound.RETRY)
                .withRetryCount(1)
                .withScorePercentage(BigDecimal.valueOf(60).setScale(2, RoundingMode.HALF_UP))
                .withCorrectAnswers(3)
                .withCompletedAt(OffsetDateTime.now().minusMinutes(1));
    }

    public static QuickReviewSessionEntityBuilder aPerfectScoreSession() {
        return aCompletedSession()
                .withCorrectAnswers(5)
                .withScorePercentage(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP))
                .withRetryCount(0)
                .withCurrentRound(QuickReviewRound.INITIAL);
    }

    public static QuickReviewSessionEntityBuilder aWeakScoreSession() {
        return aCompletedSession()
                .withCorrectAnswers(2)
                .withScorePercentage(BigDecimal.valueOf(40).setScale(2, RoundingMode.HALF_UP));
    }

    public QuickReviewSessionEntity build() {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(id);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(sessionMode);
        session.setStatus(status);
        session.setCurrentQuestionIndex(currentQuestionIndex);
        session.setCurrentRound(currentRound);
        session.setTotalQuestions(totalQuestions);
        session.setCorrectAnswers(correctAnswers);
        session.setScorePercentage(scorePercentage);
        session.setRetryCount(retryCount);
        session.setDurationSeconds(durationSeconds);
        session.setConfidenceLevel(confidenceLevel);
        session.setSessionMetadata(sessionMetadata);
        session.setSessionState(sessionState);
        session.setCreatedAt(createdAt);
        session.setCompletedAt(completedAt);
        return session;
    }
}
