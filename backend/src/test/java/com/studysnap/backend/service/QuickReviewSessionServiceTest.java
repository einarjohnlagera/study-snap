package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuickReviewSessionCompleteRequest;
import com.studysnap.backend.dto.QuickReviewSessionProgressRequest;
import com.studysnap.backend.dto.QuickReviewSessionResponse;
import com.studysnap.backend.dto.QuickReviewSessionStartResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.QuizSessionReviewResponse;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewConfidenceLevel;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.model.StudyPackProgressProjection;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackLatestCompletionProjection;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.testutil.builders.QuickReviewSessionEntityBuilder;
import com.studysnap.backend.testutil.builders.StudyPackEntityBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuickReviewSessionServiceTest {

    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private ActivityEventRepository activityEventRepository;
    @Mock
    private ActivityTrackingService activityTrackingService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private ConceptHealthService conceptHealthService;

    private QuickReviewSessionService quickReviewSessionService;

    @BeforeEach
    void setUp() {
        FeatureGateService featureGateService = new FeatureGateService(subscriptionService, new StudySnapProperties());
        quickReviewSessionService = new QuickReviewSessionService(
                quickReviewSessionRepository,
                studyPackRepository,
                activityEventRepository,
                activityTrackingService,
                analyticsService,
                subscriptionService,
                featureGateService,
                conceptHealthService
        );
        lenient().when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(any(UUID.class), any(UUID.class), any()))
                .thenAnswer(invocation -> quickReviewSessionRepository.findByIdAndUserId(
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
        lenient().when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        any(UUID.class),
                        any(UUID.class),
                        any(QuickReviewSessionMode.class),
                        any(QuickReviewSessionStatus.class)
                ))
                .thenAnswer(invocation -> quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndStatusOrderByCreatedAtDesc(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(3)
                ));
        lenient().when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        any(UUID.class),
                        any(UUID.class),
                        any(QuickReviewSessionMode.class),
                        any()
                ))
                .thenAnswer(invocation -> quickReviewSessionRepository.findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(3)
                ));
        lenient().when(quickReviewSessionRepository.countByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNull(
                        any(UUID.class),
                        any(UUID.class),
                        any(QuickReviewSessionMode.class)
                ))
                .thenAnswer(invocation -> quickReviewSessionRepository.countByUserIdAndStudyPackIdAndCompletedAtIsNotNull(
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
        lenient().when(quickReviewSessionRepository.findBestScorePercentageByUserIdAndStudyPackIdAndSessionMode(
                        any(UUID.class),
                        any(UUID.class),
                        any(QuickReviewSessionMode.class)
                ))
                .thenAnswer(invocation -> quickReviewSessionRepository.findBestScorePercentageByUserIdAndStudyPackId(
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
        lenient().when(subscriptionService.resolvePlan(any(UUID.class))).thenReturn(PlanType.PRO);
        lenient().when(quickReviewSessionRepository.existsByUserIdAndStatusAndCompletedAtIsNotNull(
                any(UUID.class),
                any(QuickReviewSessionStatus.class)
        )).thenReturn(false);
        lenient().when(activityEventRepository.existsByUserIdAndActivityTypeIn(any(UUID.class), any())).thenReturn(false);
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
    void completeSession_marksFirstCompletedQuizWhenNoPriorQuickReviewOrChallengeActivityExists() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(activityEventRepository.existsByUserIdAndActivityTypeIn(
                userId,
                Set.of(ActivityType.COMPLETED_QUICK_REVIEW, ActivityType.COMPLETED_CHALLENGE_QUIZ)
        )).thenReturn(false);

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(
                sessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(1, 1, 0, 60, null)
        );

        assertThat(response.isFirstCompletedQuiz()).isTrue();
    }

    @Test
    void completeSession_marksReturningLearnerWhenPriorChallengeQuizActivityExists() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(activityEventRepository.existsByUserIdAndActivityTypeIn(
                userId,
                Set.of(ActivityType.COMPLETED_QUICK_REVIEW, ActivityType.COMPLETED_CHALLENGE_QUIZ)
        )).thenReturn(true);

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(
                sessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(1, 1, 0, 60, null)
        );

        assertThat(response.isFirstCompletedQuiz()).isFalse();
    }

    @Test
    void completeSession_marksFirstCompletedSessionEverWhenNoPriorCompletedSessionExistsInAnyMode() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.existsByUserIdAndStatusAndCompletedAtIsNotNull(
                userId,
                QuickReviewSessionStatus.COMPLETED
        )).thenReturn(false);

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(
                sessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(1, 1, 0, 60, null)
        );

        assertThat(response.isFirstCompletedSessionEver()).isTrue();
    }

    @Test
    void completeSession_marksNotFirstCompletedSessionEverWhenAnyPriorCompletedSessionExistsInAnyMode() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.existsByUserIdAndStatusAndCompletedAtIsNotNull(
                userId,
                QuickReviewSessionStatus.COMPLETED
        )).thenReturn(true);

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(
                sessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(1, 1, 0, 60, null)
        );

        assertThat(response.isFirstCompletedSessionEver()).isFalse();
    }

    @Test
    void completeSession_recordsCorrectAndMissedConceptsFromPersistedQuickReviewSelections() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        session.setSessionState(Map.of("selectedChoices", Map.of("0", "A", "1", "C", "2", "D")));
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId, 0);
        studyPack.setQuiz(List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), "A", "Mastered", "Explanation"),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), "B", "Needs Practice", "Explanation"),
                new QuizItem("Q3", List.of("A", "B", "C", "D"), "D", "Mastered", "Explanation")
        ));

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(conceptHealthService.recordIncorrectAnswers(
                eq(userId),
                eq(studyPackId),
                eq(List.of("Needs Practice")),
                any(OffsetDateTime.class)
        )).thenReturn(List.of("Needs Practice"));

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(
                sessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(2, 3, 0, 120, null)
        );

        verify(conceptHealthService).recordCorrectAnswers(
                eq(userId), eq(studyPackId), eq(List.of("Mastered")), any(OffsetDateTime.class)
        );
        verify(conceptHealthService).recordIncorrectAnswers(
                eq(userId), eq(studyPackId), eq(List.of("Needs Practice")), any(OffsetDateTime.class)
        );
        assertThat(response.twiceMissedConcepts()).containsExactly("Needs Practice");
        assertThat(session.getVerifiedCorrectAnswers()).isEqualTo(2);
    }

    @Test
    void completeSession_persistsVerifiedPerfectScoreFromFirstPassSelections() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        session.setSessionState(Map.of("selectedChoices", Map.of("0", 0, "1", 0, "2", 0, "3", 0, "4", 0)));
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId, 0);
        studyPack.setQuiz(buildSingleChoiceQuiz(5));
        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        quickReviewSessionService.completeSession(
                sessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(5, 5, 0, 90, null)
        );

        assertThat(session.getVerifiedCorrectAnswers()).isEqualTo(5);
    }

    @Test
    void completeSession_retryCorrectionOverwritesTheMissAndPersistsVerifiedPerfectScore() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        session.setSessionState(Map.of("selectedChoices", Map.of("0", 0, "1", 0, "2", 0, "3", 0, "4", 0)));
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId, 0);
        studyPack.setQuiz(buildSingleChoiceQuiz(5));
        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        quickReviewSessionService.completeSession(
                sessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(5, 5, 1, 120, null)
        );

        assertThat(session.getRetryCount()).isEqualTo(1);
        assertThat(session.getVerifiedCorrectAnswers()).isEqualTo(5);
    }

    @Test
    void completeSession_doesNotTrustClientReportedPerfectScoreWhenStoredSelectionsAreImperfect() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        session.setSessionState(Map.of("selectedChoices", Map.of("0", 0, "1", 0, "2", 0, "3", 0, "4", 1)));
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId, 0);
        studyPack.setQuiz(buildSingleChoiceQuiz(5));
        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        quickReviewSessionService.completeSession(
                sessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(5, 5, 0, 90, null)
        );

        assertThat(session.getCorrectAnswers()).isEqualTo(5);
        assertThat(session.getVerifiedCorrectAnswers()).isEqualTo(4);
    }

    @Test
    void completeSession_leavesVerifiedScoreNullWhenBreakdownCannotBeDerived() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId))
                .thenThrow(new IllegalStateException("unparseable state"));

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(
                sessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(5, 5, 0, 90, null)
        );

        assertThat(response.id()).isEqualTo(sessionId.toString());
        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.COMPLETED);
        assertThat(session.getVerifiedCorrectAnswers()).isNull();
        verifyNoInteractions(conceptHealthService);
    }

    @Test
    void completeSession_skipsConceptHealthWritesWhenPersistedSelectionsAreUnavailable() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId, 0);
        studyPack.setQuiz(List.of(new QuizItem(
                "Q1", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"
        )));

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        quickReviewSessionService.completeSession(
                sessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(0, 1, 0, 60, null)
        );

        verifyNoInteractions(conceptHealthService);
    }

    @Test
    void completeSession_skipsConceptHealthWritesWhenTheStudyPackHasNoQuizConcepts() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        session.setSessionState(Map.of("selectedChoices", Map.of("0", "A")));
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId, 0);
        studyPack.setQuiz(List.of());

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));

        quickReviewSessionService.completeSession(
                sessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(0, 1, 0, 60, null)
        );

        verifyNoInteractions(conceptHealthService);
    }

    @Test
    void completeSession_rejectsAlreadyCompletedSessionsBeforeConceptHealthWrites() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        String sessionIdRaw = sessionId.toString();
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(1, 1, 0, 60, null);

        assertThatThrownBy(() -> quickReviewSessionService.completeSession(sessionIdRaw, userId, request))
                .isInstanceOf(AppException.class)
                .hasMessage("Quick Review session has already ended.");

        verifyNoInteractions(conceptHealthService);
    }

    @Test
    void startSession_tracksAnalyticsWhenCreatingNewQuickReviewSession() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setNoteId(noteId);
        studyPack.setOwnerUserId(userId);
        studyPack.setQuiz(List.of());
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionMode.QUICK_REVIEW,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty());

        QuickReviewSessionStartResponse response = quickReviewSessionService.startSession(studyPackId.toString(), userId);

        assertThat(response.sessionId()).isNotNull();
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.QUICK_REVIEW_STARTED), eq(studyPackId), any());
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

        assertThat(response.correctAnswers()).isZero();
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
    void completeSession_returnsWeakConceptsFromSessionMetadata() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(
                3,
                5,
                1,
                120,
                Map.of("weakConcepts", List.of("Light Reactions", "Calvin Cycle", "Light Reactions"))
        );

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(sessionId.toString(), userId, request);

        assertThat(response.weakConcepts()).containsExactly("Light Reactions", "Calvin Cycle", "Light Reactions");
        assertThat(session.getSessionMetadata())
                .containsEntry("weakConcepts", List.of("Light Reactions", "Calvin Cycle", "Light Reactions"));
    }

    @Test
    void completeSession_skipsConceptHealthWritesWithoutPersistedSelections() {
        UUID userId = UUID.randomUUID();
        UUID fullyCorrectSessionId = UUID.randomUUID();
        UUID partiallyCorrectSessionId = UUID.randomUUID();
        UUID allMissedSessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity fullyCorrectSession = buildInProgressSession(fullyCorrectSessionId, userId, studyPackId);
        QuickReviewSessionEntity partiallyCorrectSession = buildInProgressSession(partiallyCorrectSessionId, userId, studyPackId);
        QuickReviewSessionEntity allMissedSession = buildInProgressSession(allMissedSessionId, userId, studyPackId);

        when(quickReviewSessionRepository.findByIdAndUserId(fullyCorrectSessionId, userId))
                .thenReturn(Optional.of(fullyCorrectSession));
        when(quickReviewSessionRepository.findByIdAndUserId(partiallyCorrectSessionId, userId))
                .thenReturn(Optional.of(partiallyCorrectSession));
        when(quickReviewSessionRepository.findByIdAndUserId(allMissedSessionId, userId))
                .thenReturn(Optional.of(allMissedSession));

        quickReviewSessionService.completeSession(
                fullyCorrectSessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(5, 5, 0, 120, Map.of("weakConcepts", List.of()))
        );
        quickReviewSessionService.completeSession(
                partiallyCorrectSessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(2, 5, 0, 120, Map.of("weakConcepts", List.of("Cells")))
        );
        quickReviewSessionService.completeSession(
                allMissedSessionId.toString(),
                userId,
                new QuickReviewSessionCompleteRequest(0, 5, 0, 120, Map.of("weakConcepts", List.of("Cells", "Genetics")))
        );

        verifyNoInteractions(conceptHealthService);
        verify(studyPackRepository, times(3)).findByIdAndOwnerUserId(studyPackId, userId);
    }

    @Test
    void completeSession_keepsWeakConceptsVisibleForFreePlan() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(
                3,
                5,
                1,
                120,
                Map.of("weakConcepts", List.of("Light Reactions", "Calvin Cycle"))
        );

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuickReviewSessionResponse response = quickReviewSessionService.completeSession(sessionId.toString(), userId, request);

        assertThat(response.weakConcepts()).containsExactly("Light Reactions", "Calvin Cycle");
        assertThat(session.getSessionMetadata()).isNotNull();
    }

    @Test
    void getSessionReview_derivesConceptBreakdownAndWeakConceptsFromStoredSelections() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = StudyPackEntityBuilder.aStudyPack()
                .withId(studyPackId)
                .withOwnerUserId(userId)
                .withQuiz(List.of(
                        new QuizItem("Q1", List.of("A", "B", "C", "D"), "A", "Cells", "Explanation 1"),
                        new QuizItem("Q2", List.of("A", "B", "C", "D"), "B", "Cells", "Explanation 2"),
                        new QuizItem("Q3", List.of("A", "B", "C", "D"), "D", "Genetics", "Explanation 3")
                ))
                .build();
        QuickReviewSessionEntity session = QuickReviewSessionEntityBuilder.aCompletedSession()
                .withId(sessionId)
                .withUserId(userId)
                .withStudyPackId(studyPackId)
                .withSessionMode(QuickReviewSessionMode.QUICK_REVIEW)
                .withSessionMetadata(null)
                .withSessionState(Map.of(
                        "selectedChoices",
                        Map.of(
                                "0", 0,
                                "1", 0,
                                "2", 3
                        )
                ))
                .build();

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuizSessionReviewResponse response = quickReviewSessionService.getSessionReview(
                studyPackId.toString(),
                sessionId.toString(),
                userId
        );

        assertThat(response.selectedChoices()).containsEntry(0, 0).containsEntry(1, 0).containsEntry(2, 3);
        assertThat(response.quiz()).hasSize(3);
        assertThat(response.conceptBreakdown())
                .extracting(stat -> stat.concept() + ":" + stat.correctAnswers() + "/" + stat.totalQuestions())
                .containsExactly("Cells:1/2", "Genetics:1/1");
        assertThat(response.weakConcepts()).containsExactly("Cells");
    }

    @Test
    void getSessionReview_prefersPersistedWeakConceptsWhenAvailable() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId, 2);
        QuickReviewSessionEntity session = QuickReviewSessionEntityBuilder.aCompletedSession()
                .withId(sessionId)
                .withUserId(userId)
                .withStudyPackId(studyPackId)
                .withSessionMode(QuickReviewSessionMode.QUICK_REVIEW)
                .withSessionMetadata(Map.of("weakConcepts", List.of("Membranes")))
                .withSessionState(Map.of("selectedChoices", Map.of("0", 0)))
                .build();

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuizSessionReviewResponse response = quickReviewSessionService.getSessionReview(
                studyPackId.toString(),
                sessionId.toString(),
                userId
        );

        assertThat(response.weakConcepts()).containsExactly("Membranes");
    }

    @Test
    void completeSession_rejectsInvalidResultWhenCorrectAnswersExceedTotal() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        QuickReviewSessionCompleteRequest request = new QuickReviewSessionCompleteRequest(6, 5, 0, null, null);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        String id = sessionId.toString();
        assertThatThrownBy(() -> quickReviewSessionService.completeSession(id, userId, request))
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
        assertThat(saved.getCurrentQuestionIndex()).isZero();
        assertThat(saved.getCurrentRound()).isEqualTo(QuickReviewRound.INITIAL);
        assertThat(saved.getRetryCount()).isZero();
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
        assertThat(response.currentQuestionIndex()).isZero();
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

        String id = sessionId.toString();
        assertThatThrownBy(() -> quickReviewSessionService.updateSessionProgress(id, userId, request))
                .isInstanceOf(AppException.class)
                .hasMessage("Quick Review session is already completed.")
                .extracting(ex -> ((AppException) ex).getCode())
                .isEqualTo("SESSION_NOT_IN_PROGRESS");
    }

    @Test
    void forfeitSession_marksInProgressSessionAsForfeitedWithoutCompletingIt() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        var response = quickReviewSessionService.forfeitSession(sessionId.toString(), userId);

        assertThat(response.message()).isEqualTo("Quick Review session forfeited.");
        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FORFEITED);
        assertThat(session.getCompletedAt()).isNull();
        verify(activityTrackingService, never()).recordActivity(any(UUID.class), any(ActivityType.class), any(UUID.class));
    }

    @Test
    void saveConfidenceLevel_persistsOptionalConfidenceForCompletedSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        QuickReviewSessionResponse response = quickReviewSessionService.saveConfidenceLevel(
                sessionId.toString(),
                userId,
                QuickReviewConfidenceLevel.MEDIUM
        );

        assertThat(session.getConfidenceLevel()).isEqualTo(QuickReviewConfidenceLevel.MEDIUM);
        assertThat(response.confidenceLevel()).isEqualTo(QuickReviewConfidenceLevel.MEDIUM);
    }

    @Test
    void saveConfidenceLevel_rejectsInProgressSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressSession(sessionId, userId, studyPackId);

        when(quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        String id = sessionId.toString();
        assertThatThrownBy(() -> quickReviewSessionService.saveConfidenceLevel(
                id,
                userId,
                QuickReviewConfidenceLevel.HIGH
        ))
                .isInstanceOf(AppException.class)
                .hasMessage("Quick Review session must be completed before saving confidence feedback.")
                .extracting(ex -> ((AppException) ex).getCode())
                .isEqualTo("SESSION_NOT_COMPLETED");
    }

    @Test
    void listRecentSessions_clampsLimitBelowMinimumToOne() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId))
                .thenReturn(Optional.of(StudyPackEntityBuilder.aStudyPack().withId(studyPackId).withOwnerUserId(userId).build()));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.QUICK_REVIEW), pageableCaptor.capture()))
                .thenReturn(List.of());

        quickReviewSessionService.listRecentSessions(studyPackId.toString(), userId, 0);

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void listRecentSessions_clampsLimitAboveMaximumToTen() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId))
                .thenReturn(Optional.of(StudyPackEntityBuilder.aStudyPack().withId(studyPackId).withOwnerUserId(userId).build()));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.QUICK_REVIEW), pageableCaptor.capture()))
                .thenReturn(List.of());

        quickReviewSessionService.listRecentSessions(studyPackId.toString(), userId, 15);

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void listRecentSessions_passesValidLimitThrough() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId))
                .thenReturn(Optional.of(StudyPackEntityBuilder.aStudyPack().withId(studyPackId).withOwnerUserId(userId).build()));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.QUICK_REVIEW), pageableCaptor.capture()))
                .thenReturn(List.of());

        quickReviewSessionService.listRecentSessions(studyPackId.toString(), userId, 5);

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void getLastReviewedAtByNoteIdsReturnsOnlyOwnedQuickReviewCompletions() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID quickReviewNoteId = UUID.randomUUID();
        UUID challengeOnlyNoteId = UUID.randomUUID();
        UUID packlessNoteId = UUID.randomUUID();
        UUID otherUserNoteId = UUID.randomUUID();
        UUID quickReviewStudyPackId = UUID.randomUUID();
        UUID challengeOnlyStudyPackId = UUID.randomUUID();
        UUID otherUserStudyPackId = UUID.randomUUID();
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-07-18T09:30:00Z");
        StudyPackProgressProjection quickReviewStudyPack = studyPackProgressProjection(
                quickReviewStudyPackId, quickReviewNoteId, userId
        );
        StudyPackProgressProjection challengeOnlyStudyPack = studyPackProgressProjection(
                challengeOnlyStudyPackId, challengeOnlyNoteId, userId
        );
        StudyPackProgressProjection otherUserStudyPack = studyPackProgressProjection(
                otherUserStudyPackId, otherUserNoteId, otherUserId
        );
        List<UUID> requestedNoteIds = List.of(
                quickReviewNoteId, challengeOnlyNoteId, packlessNoteId, otherUserNoteId
        );
        when(studyPackRepository.findProgressViewsByNoteIdIn(requestedNoteIds))
                .thenReturn(List.of(quickReviewStudyPack, challengeOnlyStudyPack, otherUserStudyPack));
        when(quickReviewSessionRepository.findLatestCompletedAtByUserIdAndStudyPackIdInAndSessionMode(
                eq(userId),
                eq(QuickReviewSessionStatus.COMPLETED),
                eq(QuickReviewSessionMode.QUICK_REVIEW),
                argThat(studyPackIds -> studyPackIds.size() == 2
                        && studyPackIds.contains(quickReviewStudyPackId)
                        && studyPackIds.contains(challengeOnlyStudyPackId)
                        && !studyPackIds.contains(otherUserStudyPackId))
        )).thenReturn(List.of(new StudyPackLatestCompletionProjection(quickReviewStudyPackId, completedAt)));

        Map<UUID, OffsetDateTime> result = quickReviewSessionService
                .getLastReviewedAtByNoteIds(requestedNoteIds, userId);

        assertThat(result).containsExactly(Map.entry(quickReviewNoteId, completedAt));
    }

    @Test
    void getLastReviewedAtByNoteIdsSkipsCompletionQueryWhenNoOwnedStudyPacksExist() {
        UUID userId = UUID.randomUUID();
        List<UUID> requestedNoteIds = List.of(UUID.randomUUID());
        when(studyPackRepository.findProgressViewsByNoteIdIn(requestedNoteIds)).thenReturn(List.of());

        Map<UUID, OffsetDateTime> result = quickReviewSessionService
                .getLastReviewedAtByNoteIds(requestedNoteIds, userId);

        assertThat(result).isEmpty();
        verify(quickReviewSessionRepository, never())
                .findLatestCompletedAtByUserIdAndStudyPackIdInAndSessionMode(any(), any(), any(), any());
    }

    @Test
    void getLastReviewedAtByNoteIdsSkipsAllQueriesForEmptyInput() {
        Map<UUID, OffsetDateTime> result = quickReviewSessionService
                .getLastReviewedAtByNoteIds(List.of(), UUID.randomUUID());

        assertThat(result).isEmpty();
        verify(studyPackRepository, never()).findProgressViewsByNoteIdIn(any());
        verify(quickReviewSessionRepository, never())
                .findLatestCompletedAtByUserIdAndStudyPackIdInAndSessionMode(any(), any(), any(), any());
    }

    private StudyPackProgressProjection studyPackProgressProjection(
            UUID studyPackId,
            UUID noteId,
            UUID ownerUserId
    ) {
        StudyPackProgressProjection projection = mock(StudyPackProgressProjection.class);
        lenient().when(projection.getId()).thenReturn(studyPackId);
        lenient().when(projection.getNoteId()).thenReturn(noteId);
        lenient().when(projection.getOwnerUserId()).thenReturn(ownerUserId);
        return projection;
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

    private List<QuizItem> buildSingleChoiceQuiz(int questionCount) {
        List<QuizItem> quiz = new java.util.ArrayList<>();
        for (int index = 0; index < questionCount; index++) {
            quiz.add(new QuizItem(
                    "Question " + index,
                    List.of("Correct", "Incorrect"),
                    0,
                    "Concept " + index,
                    "Explanation"
            ));
        }
        return quiz;
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
