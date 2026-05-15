package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.LongExamCompleteRequest;
import com.studysnap.backend.dto.LongExamMasteryReportResponse;
import com.studysnap.backend.dto.LongExamProgressRequest;
import com.studysnap.backend.dto.LongExamSessionResponse;
import com.studysnap.backend.dto.LongExamStartRequest;
import com.studysnap.backend.dto.LongExamStartResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.LongExamNotAvailableException;
import com.studysnap.backend.exception.LongExamSessionNotInProgressException;
import com.studysnap.backend.exception.LongExamSessionNotPausableException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizSessionStateUtils;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class LongExamServiceTest {
    private static final String DEFAULT_DIFFICULTY = "mixed";

    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private FeatureGateService featureGateService;
    @Mock
    private AuthService authService;
    @Mock
    private QuizGenerationService quizGenerationService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private StudyPackGenerationContextResolver generationContextResolver;
    @Mock
    private StudyPackGenerationTaskDispatcher studyPackGenerationTaskDispatcher;

    private LongExamService longExamService;
    private Runnable dispatchedTask;

    @BeforeEach
    void setUp() {
        dispatchedTask = null;
        lenient().doAnswer(invocation -> {
            dispatchedTask = invocation.getArgument(0);
            return null;
        }).when(studyPackGenerationTaskDispatcher).execute(any(Runnable.class));
        TransactionOperations transactionOperations = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        longExamService = new LongExamService(
                studyPackRepository,
                quickReviewSessionRepository,
                userRepository,
                subscriptionService,
                featureGateService,
                authService,
                quizGenerationService,
                analyticsService,
                generationContextResolver,
                new StudySnapProperties(),
                studyPackGenerationTaskDispatcher,
                transactionOperations,
                new SimpleAsyncTaskExecutor()
        );
    }

    @Test
    void startSession_returnsGeneratingImmediatelyThenAsyncMarksInProgress() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        StudyPackGenerationContext context = buildGenerationContext(LearnerLevel.COLLEGE);
        List<QuizItem> generatedQuiz = buildQuiz(25);
        List<QuickReviewSessionStatus> savedStatuses = new ArrayList<>();
        List<QuickReviewSessionEntity> savedSessions = new ArrayList<>();

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.LONG_EXAM),
                any()
        )).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(context);
        when(quizGenerationService.generateLongExamParallel(
                eq(studyPack.getTitle()),
                eq(studyPack.getSummary()),
                eq(studyPack.getKeyConcepts()),
                eq(List.of("Existing question")),
                eq(25),
                eq(DEFAULT_DIFFICULTY),
                eq(context),
                any()
        )).thenReturn(generatedQuiz);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> {
                    QuickReviewSessionEntity session = invocation.getArgument(0);
                    savedStatuses.add(session.getStatus());
                    savedSessions.add(session);
                    return session;
                });

        LongExamStartResponse response = longExamService.startSession(
                studyPackId.toString(),
                userId,
                new LongExamStartRequest(null)
        );

        assertThat(savedStatuses).containsExactly(QuickReviewSessionStatus.GENERATING);
        assertThat(response.status()).isEqualTo("GENERATING");
        assertThat(response.quiz()).isEmpty();
        assertThat(response.totalQuestions()).isEqualTo(25);
        assertThat(response.difficulty()).isEqualTo(DEFAULT_DIFFICULTY);
        assertThat(response.canResume()).isFalse();
        assertThat(dispatchedTask).isNotNull();
        QuickReviewSessionEntity generatingSession = savedSessions.getFirst();
        when(quickReviewSessionRepository.findById(response.sessionId())).thenReturn(Optional.of(generatingSession));

        dispatchedTask.run();

        assertThat(savedStatuses).containsExactly(
                QuickReviewSessionStatus.GENERATING,
                QuickReviewSessionStatus.IN_PROGRESS
        );
        verify(featureGateService).checkFeatureAccess(PlanType.PRO, Feature.LONG_EXAM_SESSION);
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.LONG_EXAM_STARTED), eq(studyPackId), any());
    }

    @Test
    void startSession_nonProUserThrowsBeforeGeneration() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        doThrow(new LongExamNotAvailableException())
                .when(featureGateService)
                .checkFeatureAccess(PlanType.PLUS, Feature.LONG_EXAM_SESSION);

        String studyPackIdRaw = studyPackId.toString();
        assertThatThrownBy(() -> longExamService.startSession(studyPackIdRaw, userId, null))
                .isInstanceOf(LongExamNotAvailableException.class);

        verify(studyPackRepository, never()).findByIdAndOwnerUserIdForUpdate(any(), any());
        verify(quizGenerationService, never()).generateLongExam(any(), any(), any(), any(), anyInt(), any(), any());
        verify(quizGenerationService, never()).generateLongExamParallel(any(), any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void startSession_existingInProgressSessionReturnsWithoutRegenerating() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        QuickReviewSessionEntity existing = buildSession(userId, studyPackId, QuickReviewSessionStatus.IN_PROGRESS, buildQuiz(20));

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.LONG_EXAM),
                any()
        )).thenReturn(Optional.of(existing));

        LongExamStartResponse response = longExamService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.sessionId()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.quiz()).hasSize(20);
        verify(quizGenerationService, never()).generateLongExam(any(), any(), any(), any(), anyInt(), any(), any());
        verify(quizGenerationService, never()).generateLongExamParallel(any(), any(), any(), any(), anyInt(), any(), any(), any());
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
    }

    @Test
    void startSession_llmFailureMarksSessionFailedAndDoesNotThrow() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        List<QuickReviewSessionStatus> savedStatuses = new ArrayList<>();
        List<QuickReviewSessionEntity> savedSessions = new ArrayList<>();

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.LONG_EXAM),
                any()
        )).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(generationContextResolver.resolveForStudyPack(userId, studyPack))
            .thenReturn(buildGenerationContext(LearnerLevel.COLLEGE));
        when(quizGenerationService.generateLongExamParallel(any(), any(), any(), any(), anyInt(), any(), any(), any()))
                .thenThrow(new RuntimeException("LLM unavailable"));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> {
                    QuickReviewSessionEntity session = invocation.getArgument(0);
                    savedStatuses.add(session.getStatus());
                    savedSessions.add(session);
                    return session;
                });

        LongExamStartResponse response = longExamService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.status()).isEqualTo("GENERATING");
        assertThat(response.quiz()).isEmpty();
        when(quickReviewSessionRepository.findById(response.sessionId())).thenReturn(Optional.of(savedSessions.getFirst()));

        dispatchedTask.run();

        assertThat(savedStatuses).containsExactly(QuickReviewSessionStatus.GENERATING, QuickReviewSessionStatus.FAILED);
    }

    @Test
    void pauseSession_inProgressSessionTransitionsToPaused() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, UUID.randomUUID(), QuickReviewSessionStatus.IN_PROGRESS, buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.LONG_EXAM))
                .thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LongExamSessionResponse response = longExamService.pauseSession(sessionId, userId);

        assertThat(response.status()).isEqualTo("PAUSED");
        assertThat(response.paused()).isTrue();
    }

    @Test
    void pauseSession_nonInProgressSessionThrows() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, UUID.randomUUID(), QuickReviewSessionStatus.PAUSED, buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.LONG_EXAM))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> longExamService.pauseSession(sessionId, userId))
                .isInstanceOf(LongExamSessionNotPausableException.class);
    }

    @Test
    void resumeSession_pausedSessionTransitionsToInProgress() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, UUID.randomUUID(), QuickReviewSessionStatus.PAUSED, buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.LONG_EXAM))
                .thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LongExamSessionResponse response = longExamService.resumeSession(sessionId, userId);

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.paused()).isFalse();
    }

    @Test
    void saveProgress_savesSelectedChoiceIntoSessionState() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, UUID.randomUUID(), QuickReviewSessionStatus.IN_PROGRESS, buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.LONG_EXAM))
                .thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LongExamSessionResponse response = longExamService.saveProgress(
                sessionId,
                userId,
                new LongExamProgressRequest(2, 1)
        );

        assertThat(response.currentQuestionIndex()).isEqualTo(2);
        assertThat(response.selectedChoices()).containsEntry(2, 1);
    }

    @Test
    void saveProgress_pausedSessionThrows() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, UUID.randomUUID(), QuickReviewSessionStatus.PAUSED, buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.LONG_EXAM))
                .thenReturn(Optional.of(session));

        LongExamProgressRequest request = new LongExamProgressRequest(0, 0);
        assertThatThrownBy(() -> longExamService.saveProgress(sessionId, userId, request))
                .isInstanceOf(LongExamSessionNotInProgressException.class);
    }

    @Test
    void completeSession_computesMasteryReportAndPersistsMetadata() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, studyPackId, QuickReviewSessionStatus.IN_PROGRESS, List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), 0, "Cells", "Explanation"),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), 1, "Cells", "Explanation"),
                new QuizItem("Q3", List.of("A", "B", "C", "D"), 2, "Genetics", "Explanation")
        ));
        session.setId(sessionId);
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 0, 0));
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 1, 0));
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 2, 2));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.LONG_EXAM))
                .thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LongExamMasteryReportResponse response = longExamService.completeSession(
                sessionId,
                userId,
                new LongExamCompleteRequest(900)
        );

        assertThat(response.totalQuestions()).isEqualTo(3);
        assertThat(response.answeredQuestions()).isEqualTo(3);
        assertThat(response.scorePercentage()).isEqualTo(67);
        assertThat(response.performanceSummary()).isEqualTo("Fair");
        assertThat(response.suggestedNextStep()).isEqualTo("Review weak domains and retry");
        assertThat(response.weakDomains()).containsExactly("Cells");
        assertThat(response.domainBreakdown())
                .extracting(stat -> stat.domain() + ":" + stat.correctAnswers() + "/" + stat.totalQuestions())
                .containsExactly("Cells:1/2", "Genetics:1/1");
        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.COMPLETED);
        assertThat(session.getDurationSeconds()).isEqualTo(900);
        assertThat(session.getSessionMetadata()).containsKeys(
                "domainBreakdown",
                "weakDomains",
                "performanceSummary",
                "suggestedNextStep"
        );
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.LONG_EXAM_COMPLETED), eq(studyPackId), any());
    }

    @Test
    void completeSession_forfeitedSessionThrows() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, UUID.randomUUID(), QuickReviewSessionStatus.FORFEITED, buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.LONG_EXAM))
                .thenReturn(Optional.of(session));

        LongExamCompleteRequest request = new LongExamCompleteRequest(1);
        assertThatThrownBy(() -> longExamService.completeSession(sessionId, userId, request))
                .isInstanceOf(LongExamSessionNotInProgressException.class);
    }

    @Test
    void forfeitSession_inProgressSessionMarksForfeited() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, studyPackId, QuickReviewSessionStatus.IN_PROGRESS, buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.LONG_EXAM))
                .thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        longExamService.forfeitSession(sessionId, userId);

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FORFEITED);
        assertThat(session.getCompletedAt()).isNotNull();
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.LONG_EXAM_FORFEITED), eq(studyPackId), any());
    }

    private StudyPackEntity buildStudyPack(UUID studyPackId, UUID userId) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        studyPack.setNoteId(UUID.randomUUID());
        studyPack.setTitle("Long Exam Pack");
        studyPack.setSummary("Summary");
        studyPack.setKeyConcepts(List.of("Cells", "Genetics"));
        studyPack.setQuiz(List.of(new QuizItem(
                "Existing question",
                List.of("A", "B", "C", "D"),
                0,
                "Cells",
                "Explanation"
        )));
        return studyPack;
    }

    private UserEntity buildUser(UUID userId, LearnerLevel learnerLevel) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(learnerLevel);
        return user;
    }

    private StudyPackGenerationContext buildGenerationContext(LearnerLevel learnerLevel) {
        return new StudyPackGenerationContext(learnerLevel, "Biology", "Science", List.of());
    }

    private QuickReviewSessionEntity buildSession(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionStatus status,
            List<QuizItem> quiz
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(UUID.randomUUID());
        session.setSessionMode(QuickReviewSessionMode.LONG_EXAM);
        session.setStatus(status);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(quiz.size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO);
        session.setRetryCount(0);
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);
        session.setSessionState(QuizSessionStateUtils.withQuiz(quiz, Map.of("difficulty", DEFAULT_DIFFICULTY)));
        return session;
    }

    private List<QuizItem> buildQuiz(int count) {
        List<QuizItem> quiz = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            quiz.add(new QuizItem(
                    "Question " + index,
                    List.of("A", "B", "C", "D"),
                    index % 4,
                    index % 2 == 0 ? "Cells" : "Genetics",
                    "Explanation"
            ));
        }
        return quiz;
    }
}
