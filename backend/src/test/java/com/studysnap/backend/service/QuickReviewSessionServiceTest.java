package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuickReviewSessionCompleteRequest;
import com.studysnap.backend.dto.QuickReviewSessionResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuickReviewSessionServiceTest {

    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private ActivityTrackingService activityTrackingService;

    private QuickReviewSessionService quickReviewSessionService;

    @BeforeEach
    void setUp() {
        quickReviewSessionService = new QuickReviewSessionService(
                quickReviewSessionRepository,
                studyPackRepository,
                activityTrackingService
        );
        lenient().when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void completeSession_calculatesMixedScoreCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(
                3,
                5,
                1,
                120,
                Map.of("source", "unit-test")
        );

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(sessionId.toString(), userId, request);

        assertThat(response.correctAnswers()).isEqualTo(3);
        assertThat(response.totalQuestions()).isEqualTo(5);
        assertThat(response.scorePercentage()).isEqualByComparingTo("60.00");
        assertThat(response.retryCount()).isEqualTo(1);
        assertThat(response.currentRound()).isEqualTo(QuickReviewRound.RETRY);
    }

    @Test
    void completeSession_returnsHundredPercentForAllCorrect() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(5, 5, 0, null, null);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(sessionId.toString(), userId, request);

        assertThat(response.correctAnswers()).isEqualTo(5);
        assertThat(response.totalQuestions()).isEqualTo(5);
        assertThat(response.scorePercentage()).isEqualByComparingTo("100.00");
        assertThat(response.currentRound()).isEqualTo(QuickReviewRound.INITIAL);
    }

    @Test
    void completeSession_returnsZeroPercentForAllIncorrect() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(0, 5, 0, 45, null);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(sessionId.toString(), userId, request);

        assertThat(response.correctAnswers()).isEqualTo(0);
        assertThat(response.totalQuestions()).isEqualTo(5);
        assertThat(response.scorePercentage()).isEqualByComparingTo("0.00");
    }

    @Test
    void completeSession_keepsOriginalTotalQuestionCountWhenRetryHappened() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(4, 5, 1, 88, null);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(sessionId.toString(), userId, request);

        assertThat(response.totalQuestions()).isEqualTo(5);
        assertThat(response.currentQuestionIndex()).isEqualTo(5);
        assertThat(response.retryCount()).isEqualTo(1);
    }

    @Test
    void completeSession_appliesProductionRoundingConsistency() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(2, 3, 0, null, null);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(sessionId.toString(), userId, request);

        assertThat(response.scorePercentage()).isEqualByComparingTo("66.67");
    }

    @Test
    void completeSession_persistsScoreFieldsConsistently() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(7, 8, 0, 200, null);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(sessionId.toString(), userId, request);

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.COMPLETED);
        assertThat(session.getCorrectAnswers()).isEqualTo(7);
        assertThat(session.getTotalQuestions()).isEqualTo(8);
        assertThat(session.getScorePercentage()).isEqualByComparingTo("87.50");
        assertThat(response.correctAnswers()).isEqualTo(session.getCorrectAnswers());
        assertThat(response.totalQuestions()).isEqualTo(session.getTotalQuestions());
        assertThat(response.scorePercentage()).isEqualByComparingTo(session.getScorePercentage());
    }

    @Test
    void completeSession_rejectsInvalidResultWhenCorrectAnswersExceedTotal() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(6, 5, 0, null, null);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> quickReviewSessionService.completeSession(sessionId.toString(), userId, request))
                .isInstanceOf(AppException.class)
                .hasMessage("Correct answers cannot exceed total questions.")
                .extracting(ex -> ((AppException) ex).getCode())
                .isEqualTo("INVALID_SESSION_RESULT");
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(activityTrackingService, never()).recordActivity(any(UUID.class), any(ActivityType.class), any(UUID.class));
    }

    private QuickReviewSessionEntity buildInProgressSession(UUID sessionId, UUID userId, UUID studyPackId) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(2);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(5);
        session.setCorrectAnswers(2);
        session.setScorePercentage(BigDecimal.valueOf(40).setScale(2, RoundingMode.HALF_EVEN));
        session.setRetryCount(0);
        session.setDurationSeconds(null);
        session.setSessionMetadata(null);
        session.setSessionState(null);
        session.setCreatedAt(OffsetDateTime.now().minusMinutes(15));
        session.setCompletedAt(null);
        return session;
    }
}
