package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuickReviewSessionCompleteRequest;
import com.studysnap.backend.dto.QuickReviewSessionProgressRequest;
import com.studysnap.backend.dto.QuickReviewSessionResponse;
import com.studysnap.backend.dto.QuickReviewSessionStartResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.testutil.builders.QuickReviewSessionEntityBuilder;
import com.studysnap.backend.testutil.builders.StudyPackEntityBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    void startSession_createsNewSessionWhenNoInProgressExists() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId, 5);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndStatusOrderByCreatedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());

        QuickReviewSessionStartResponse response = quickReviewSessionService.startSession(studyPackId.toString(), userId);

        ArgumentCaptor<QuickReviewSessionEntity> captor = ArgumentCaptor.forClass(QuickReviewSessionEntity.class);
        verify(quickReviewSessionRepository, times(1)).save(captor.capture());
        QuickReviewSessionEntity saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStudyPackId()).isEqualTo(studyPackId);
        assertThat(saved.getStatus()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(saved.getCurrentQuestionIndex()).isEqualTo(0);
        assertThat(saved.getCurrentRound()).isEqualTo(QuickReviewRound.INITIAL);
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getTotalQuestions()).isEqualTo(5);
        assertThat(response.sessionId()).isEqualTo(saved.getId().toString());
        verify(activityTrackingService, times(1)).recordActivity(userId, ActivityType.STARTED_QUICK_REVIEW, studyPackId);
    }

    @Test
    void startSession_reusesExistingInProgressSessionForSameStudyPack() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId, 5);
        QuickReviewSessionEntity existingSession = buildInProgressSession(sessionId, userId, studyPackId);
        existingSession.setCurrentRound(QuickReviewRound.RETRY);
        existingSession.setRetryCount(1);
        existingSession.setSessionState(Map.of("retryQuestionIndexes", List.of(1, 4)));

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndStatusOrderByCreatedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.of(existingSession));

        QuickReviewSessionStartResponse response = quickReviewSessionService.startSession(studyPackId.toString(), userId);

        assertThat(response.sessionId()).isEqualTo(sessionId.toString());
        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(response.currentRound()).isEqualTo(QuickReviewRound.RETRY);
        assertThat(response.retryCount()).isEqualTo(1);
        assertThat(response.sessionState()).containsKey("retryQuestionIndexes");
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(activityTrackingService, never()).recordActivity(any(UUID.class), any(ActivityType.class), any(UUID.class));
    }

    @Test
    void startSession_createsNewSessionWhenLatestKnownSessionWasCompleted() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId, 4);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndStatusOrderByCreatedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());

        QuickReviewSessionStartResponse response = quickReviewSessionService.startSession(studyPackId.toString(), userId);

        verify(quickReviewSessionRepository, times(1)).save(any(QuickReviewSessionEntity.class));
        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(response.currentQuestionIndex()).isEqualTo(0);
        assertThat(response.currentRound()).isEqualTo(QuickReviewRound.INITIAL);
    }

    @Test
    void updateSessionProgress_persistsRetryRoundStateAndRetryIndexes() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        QuickReviewSessionProgressRequest request = new QuickReviewSessionProgressRequest(
                0,
                QuickReviewRound.RETRY,
                1,
                Map.of("retryQuestionIndexes", List.of(1, 3))
        );
        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuickReviewSessionResponse response = quickReviewSessionService.updateSessionProgress(sessionId.toString(), userId, request);

        assertThat(response.currentRound()).isEqualTo(QuickReviewRound.RETRY);
        assertThat(response.retryCount()).isEqualTo(1);
        assertThat(response.sessionState()).containsEntry("retryQuestionIndexes", List.of(1, 3));
        assertThat(session.getCurrentRound()).isEqualTo(QuickReviewRound.RETRY);
        assertThat(session.getRetryCount()).isEqualTo(1);
        assertThat(session.getSessionState()).containsEntry("retryQuestionIndexes", List.of(1, 3));
    }

    @Test
    void getInProgressSession_preservesStoredProgressWhenResuming() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId, 5);
        QuickReviewSessionEntity inProgress = buildInProgressSession(sessionId, userId, studyPackId);
        inProgress.setCurrentQuestionIndex(3);
        inProgress.setCurrentRound(QuickReviewRound.RETRY);
        inProgress.setRetryCount(1);
        inProgress.setSessionState(Map.of("retryQuestionIndexes", List.of(1, 3)));

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndStatusOrderByCreatedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.of(inProgress));

        QuickReviewSessionStartResponse response = quickReviewSessionService.getInProgressSession(studyPackId.toString(), userId);

        assertThat(response.sessionId()).isEqualTo(sessionId.toString());
        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(response.currentQuestionIndex()).isEqualTo(3);
        assertThat(response.currentRound()).isEqualTo(QuickReviewRound.RETRY);
        assertThat(response.retryCount()).isEqualTo(1);
        assertThat(response.sessionState()).containsEntry("retryQuestionIndexes", List.of(1, 3));
    }

    @Test
    void completeSession_allowsScoreImprovementAfterRetryWithoutChangingOriginalTotal() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        session.setCorrectAnswers(3);
        session.setTotalQuestions(5);
        session.setScorePercentage(BigDecimal.valueOf(60).setScale(2, RoundingMode.HALF_UP));
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(5, 5, 1, 150, null);
        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(sessionId.toString(), userId, request);

        assertThat(response.id()).isEqualTo(sessionId.toString());
        assertThat(response.correctAnswers()).isEqualTo(5);
        assertThat(response.totalQuestions()).isEqualTo(5);
        assertThat(response.scorePercentage()).isEqualByComparingTo("100.00");
        assertThat(response.retryCount()).isEqualTo(1);
        assertThat(response.currentRound()).isEqualTo(QuickReviewRound.RETRY);
    }

    @Test
    void completeSession_updatesExistingSessionInsteadOfCreatingNewOne() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(4, 5, 1, 120, null);
        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(sessionId.toString(), userId, request);

        assertThat(response.id()).isEqualTo(sessionId.toString());
        assertThat(session.getId()).isEqualTo(sessionId);
        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.COMPLETED);
        verify(quickReviewSessionRepository, times(1)).save(session);
    }

    @Test
    void updateSessionProgress_rejectsFurtherRetryProgressAfterSessionCompleted() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        QuickReviewSessionProgressRequest request = new QuickReviewSessionProgressRequest(
                0,
                QuickReviewRound.RETRY,
                1,
                Map.of("retryQuestionIndexes", List.of(2, 4))
        );
        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> quickReviewSessionService.updateSessionProgress(sessionId.toString(), userId, request))
                .isInstanceOf(AppException.class)
                .hasMessage("Quick Review session is already completed.")
                .extracting(ex -> ((AppException) ex).getCode())
                .isEqualTo("SESSION_NOT_IN_PROGRESS");
    }

    private StudyPackEntity buildStudyPack(UUID studyPackId, UUID userId, int quizCount) {
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
        return StudyPackEntityBuilder.aStudyPack()
                .withId(studyPackId)
                .withOwnerUserId(userId)
                .withTitle("Quick Review Pack")
                .withSummary("Summary")
                .withQuizCount(quizCount)
                .withCreatedAt(createdAt)
                .withUpdatedAt(createdAt)
                .build();
    }

    private QuickReviewSessionEntity buildInProgressSession(UUID sessionId, UUID userId, UUID studyPackId) {
        return QuickReviewSessionEntityBuilder.anInProgressSession()
                .withId(sessionId)
                .withUserId(userId)
                .withStudyPackId(studyPackId)
                .withCurrentQuestionIndex(2)
                .withCurrentRound(QuickReviewRound.INITIAL)
                .withTotalQuestions(5)
                .withCorrectAnswers(2)
                .withScorePercentage(BigDecimal.valueOf(40).setScale(2, RoundingMode.HALF_EVEN))
                .withRetryCount(0)
                .withCreatedAt(OffsetDateTime.now().minusMinutes(15))
                .withCompletedAt(null)
                .build();
    }
}
