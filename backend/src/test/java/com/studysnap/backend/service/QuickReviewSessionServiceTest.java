package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuickReviewSessionCompleteRequest;
import com.studysnap.backend.dto.QuickReviewSessionProgressRequest;
import com.studysnap.backend.dto.QuickReviewSessionResponse;
import com.studysnap.backend.dto.QuickReviewSessionStartResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
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
import java.util.ArrayList;
import java.util.List;
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

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
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
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        studyPack.setInputType(InputType.TEXT);
        studyPack.setTitle("Quick Review Pack");
        studyPack.setSummary("Summary");
        List<QuizItem> quizItems = new ArrayList<>();
        for (int index = 0; index < quizCount; index++) {
            quizItems.add(new QuizItem("Q" + (index + 1), List.of("A", "B"), "A", "E"));
        }
        studyPack.setQuiz(quizItems);
        studyPack.setModelTier(ModelTier.FREE);
        studyPack.setModelUsed("gpt-4.1-mini");
        studyPack.setStatus(StudyPackStatus.DONE);
        studyPack.setCreatedAt(OffsetDateTime.now().minusDays(1));
        studyPack.setUpdatedAt(OffsetDateTime.now().minusDays(1));
        studyPack.setTags(new String[]{"retry"});
        return studyPack;
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
