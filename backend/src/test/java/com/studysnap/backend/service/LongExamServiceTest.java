package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
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
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.dto.LongExamSourceNoteRef;
import com.studysnap.backend.exception.InvalidLongExamSourceException;
import com.studysnap.backend.exception.LongExamInsufficientEligibleSourcesException;
import com.studysnap.backend.exception.LongExamNotAvailableException;
import com.studysnap.backend.exception.LongExamSessionNotInProgressException;
import com.studysnap.backend.exception.LongExamSessionNotPausableException;
import com.studysnap.backend.exception.MonthlyLongExamLimitReachedException;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizSessionStateUtils;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class LongExamServiceTest {

    private static final String DEFAULT_DIFFICULTY = "mixed";
    private static final String BIOLOGY_SUBJECT = "Biology";
    private static final String PRIMARY_BIOLOGY_TITLE = "Primary Biology";
    private static final String CELL_BIOLOGY_TITLE = "Cell Biology";

    @Mock
    private NoteRepository noteRepository;
    @Mock
    private PlanSourcedExamVerifier planSourcedExamVerifier;
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
    @Mock
    private UserUsageService userUsageService;
    @Mock
    private ExamQuestionPoolService examQuestionPoolService;
    @Mock
    private ConceptHealthService conceptHealthService;
    @Mock
    private GenerationRecoveryRowWriter generationRecoveryRowWriter;

    private LongExamService longExamService;
    private Runnable dispatchedTask;

    @BeforeEach
    void setUp() {
        dispatchedTask = null;
        lenient().doAnswer(invocation -> {
            dispatchedTask = invocation.getArgument(0);
            return null;
        }).when(studyPackGenerationTaskDispatcher).execute(any(Runnable.class));
        lenient().doAnswer(invocation -> {
            UUID sessionId = invocation.getArgument(0);
            quickReviewSessionRepository.findById(sessionId).ifPresent(session -> {
                session.setStatus(QuickReviewSessionStatus.FAILED);
                quickReviewSessionRepository.save(session);
            });
            return null;
        }).when(generationRecoveryRowWriter).failLongExamSession(any(UUID.class));
        // Default: the session is still GENERATING when generation finishes — the normal case. The race
        // test overrides findStatusById to FAILED, so the guard is exercised in BOTH directions and this
        // default cannot hide it.
        lenient().when(quickReviewSessionRepository.findByIdForUpdate(any(UUID.class)))
            .thenAnswer(invocation -> quickReviewSessionRepository.findById(invocation.getArgument(0)));
        lenient().when(quickReviewSessionRepository.findStatusById(any(UUID.class)))
            .thenReturn(Optional.of(QuickReviewSessionStatus.GENERATING));
        lenient().when(userUsageService.getMonthlyUsage(any(UUID.class), any(OffsetDateTime.class)))
            .thenReturn(UserUsageService.MonthlyUsage.zero());
        lenient().when(examQuestionPoolService.sampleQuestions(any(UUID.class), any(), anyInt(), any()))
            .thenReturn(Optional.empty());
        lenient().when(generationContextResolver.resolveForStudyPack(any(UUID.class), any(StudyPackEntity.class)))
            .thenReturn(buildGenerationContext(LearnerLevel.COLLEGE));
        TransactionOperations transactionOperations = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        longExamService = new LongExamService(
            noteRepository,
            planSourcedExamVerifier,
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
            userUsageService,
            studyPackGenerationTaskDispatcher,
            transactionOperations,
            new SimpleAsyncTaskExecutor(),
            new SimpleAsyncTaskExecutor(),
            examQuestionPoolService,
            conceptHealthService,
            new LongExamPlanSourceSampler(),
            generationRecoveryRowWriter
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
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(
            Optional.of(studyPack));
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
        assertThat(response.sourceNoteRefs()).hasSize(1);
        assertThat(response.sourceNoteRefs().getFirst().questionCount()).isEqualTo(25);
        assertThat(response.usedThisMonth()).isZero();
        assertThat(response.monthlyLimit()).isEqualTo(12);
        assertThat(dispatchedTask).isNotNull();
        QuickReviewSessionEntity generatingSession = savedSessions.getFirst();
        when(quickReviewSessionRepository.findById(response.sessionId())).thenReturn(Optional.of(generatingSession));

        dispatchedTask.run();

        assertThat(savedStatuses).containsExactly(
            QuickReviewSessionStatus.GENERATING,
            QuickReviewSessionStatus.IN_PROGRESS
        );
        verify(featureGateService).checkFeatureAccess(PlanType.PRO, Feature.LONG_EXAM_SESSION);
        verify(userUsageService).incrementLongExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.LONG_EXAM_STARTED), eq(studyPackId),
            any());
    }

    @Test
    void startSession_usesReadyPoolWithoutDispatchingAsyncGeneration() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        List<QuizItem> pooledQuiz = buildQuiz(25);
        StudyPackGenerationContext context = new StudyPackGenerationContext(
            LearnerLevel.GRADE_SCHOOL,
            "General Education",
            studyPack.getSubject(),
            List.of(),
            null,
            LearnerLevel.COLLEGE
        );

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(
            Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId),
            eq(studyPackId),
            eq(QuickReviewSessionMode.LONG_EXAM),
            any()
        )).thenReturn(Optional.empty());
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(context);
        when(examQuestionPoolService.sampleQuestions(
            studyPackId,
            ExamQuestionPoolService.MODE_LONG_EXAM,
            25,
            LearnerLevel.COLLEGE
        )).thenReturn(Optional.of(pooledQuiz));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LongExamStartResponse response = longExamService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.quiz()).hasSize(25);
        assertThat(response.canResume()).isTrue();
        verify(studyPackGenerationTaskDispatcher, never()).execute(any(Runnable.class));
        verify(quizGenerationService, never()).generateLongExamParallel(any(), any(), any(), any(), anyInt(), any(),
            any(), any());
        verify(userUsageService).incrementLongExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
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
        verify(quizGenerationService, never()).generateLongExamParallel(any(), any(), any(), any(), anyInt(), any(),
            any(), any());
    }

    @Test
    void startSession_existingInProgressSessionReturnsWithoutRegenerating() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        QuickReviewSessionEntity existing = buildSession(userId, studyPackId, QuickReviewSessionStatus.IN_PROGRESS,
            buildQuiz(20));

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(
            Optional.of(studyPack));
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
        verify(quizGenerationService, never()).generateLongExamParallel(any(), any(), any(), any(), anyInt(), any(),
            any(), any());
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(userUsageService, never()).incrementLongExamGenerationBy(any(UUID.class), anyInt(), any(OffsetDateTime.class));
    }

    @Test
    void startSession_afterRecoveredFailedSessionCreatesFreshSession() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        QuickReviewSessionEntity recoveredSession = buildSession(
                userId,
                studyPackId,
                QuickReviewSessionStatus.FAILED,
                List.of()
        );

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId))
                .thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.LONG_EXAM),
                any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LongExamStartResponse response = longExamService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.GENERATING.name());
        assertThat(response.sessionId()).isNotEqualTo(recoveredSession.getId());
        verify(quickReviewSessionRepository).save(any(QuickReviewSessionEntity.class));
    }

    @Test
    void startSession_monthlyLongExamLimitReachedThrowsBeforeGeneration() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
            .thenReturn(new UserUsageService.MonthlyUsage(
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(29),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                12,
                0
            ));

        String studyPackIdRaw = studyPackId.toString();
        assertThatThrownBy(() -> longExamService.startSession(studyPackIdRaw, userId, null))
            .isInstanceOf(MonthlyLongExamLimitReachedException.class);

        verify(studyPackRepository, never()).findByIdAndOwnerUserIdForUpdate(any(), any());
        verify(userUsageService, never()).incrementLongExamGenerationBy(any(UUID.class), anyInt(), any(OffsetDateTime.class));
    }

    @Test
    void startSession_llmFailureMarksSessionFailedAndDoesNotThrow() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        List<QuickReviewSessionStatus> savedStatuses = new ArrayList<>();
        List<QuickReviewSessionEntity> savedSessions = new ArrayList<>();

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(
            Optional.of(studyPack));
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
        when(quickReviewSessionRepository.findById(response.sessionId())).thenReturn(
            Optional.of(savedSessions.getFirst()));

        dispatchedTask.run();

        assertThat(savedStatuses).containsExactly(QuickReviewSessionStatus.GENERATING, QuickReviewSessionStatus.FAILED);
    }

    @Test
    void startSession_withOneAdditionalNoteStoresSourceNoteRefs() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE,
            BIOLOGY_SUBJECT);
        StudyPackEntity additionalStudyPack = buildStudyPack(additionalStudyPackId, userId, CELL_BIOLOGY_TITLE,
            BIOLOGY_SUBJECT);

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primaryStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId))
            .thenReturn(Optional.of(additionalStudyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId),
            eq(primaryStudyPackId),
            eq(QuickReviewSessionMode.LONG_EXAM),
            any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LongExamStartResponse response = longExamService.startSession(
            primaryStudyPackId.toString(),
            userId,
            new LongExamStartRequest(null, List.of(additionalStudyPackId.toString()))
        );

        assertThat(response.totalQuestions()).isEqualTo(25);
        assertThat(response.sourceNoteRefs())
            .extracting(source -> source.noteTitle() + ":" + source.questionCount())
            .containsExactly(PRIMARY_BIOLOGY_TITLE + ":13", CELL_BIOLOGY_TITLE + ":12");
        verify(examQuestionPoolService, never()).sampleQuestions(any(UUID.class), any(), anyInt(), any());
        verify(userUsageService).incrementLongExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
    }

    @Test
    void generateQuizForSources_stampsEachSourceBeforeMerging() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE,
                BIOLOGY_SUBJECT);
        StudyPackEntity additionalStudyPack = buildStudyPack(additionalStudyPackId, userId, CELL_BIOLOGY_TITLE,
                BIOLOGY_SUBJECT);
        UserEntity user = buildUser(userId, LearnerLevel.COLLEGE);
        List<LongExamSourceNoteRef> sources = List.of(
                new LongExamSourceNoteRef(primaryStudyPackId.toString(), primaryStudyPack.getNoteId().toString(),
                        primaryStudyPack.getTitle(), 2),
                new LongExamSourceNoteRef(additionalStudyPackId.toString(), additionalStudyPack.getNoteId().toString(),
                        additionalStudyPack.getTitle(), 2)
        );
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
                .thenReturn(Optional.of(primaryStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId))
                .thenReturn(Optional.of(additionalStudyPack));
        when(quizGenerationService.generateLongExamParallel(any(), any(), any(), any(), eq(2), any(), any(), any()))
                .thenReturn(
                        List.of(
                                new QuizItem("Primary one", List.of("A", "B"), 0, "Primary one", "Explanation"),
                                new QuizItem("Primary two", List.of("A", "B"), 1, "Primary two", "Explanation")
                        ),
                        List.of(
                                new QuizItem("Additional one", List.of("A", "B"), 0, "Additional one", "Explanation"),
                                new QuizItem("Additional two", List.of("A", "B"), 1, "Additional two", "Explanation")
                        )
                );

        @SuppressWarnings("unchecked")
        Object generated = ReflectionTestUtils.invokeMethod(
                longExamService,
                "generateQuizForSources",
                user,
                sources,
                DEFAULT_DIFFICULTY
        );
        List<QuizItem> quiz = (List<QuizItem>) ReflectionTestUtils.getField(generated, "quiz");

        assertThat(quiz).filteredOn(item -> primaryStudyPackId.toString().equals(item.sourceStudyPackId())).hasSize(2);
        assertThat(quiz).filteredOn(item -> additionalStudyPackId.toString().equals(item.sourceStudyPackId())).hasSize(2);
    }

    @Test
    void startSession_withTwoAdditionalNotesUsesOneLongExamUnitWhenOneSessionRemains() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID secondStudyPackId = UUID.randomUUID();
        UUID thirdStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE,
            BIOLOGY_SUBJECT);

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
            .thenReturn(new UserUsageService.MonthlyUsage(
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(29),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                11,
                0
            ));
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primaryStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(secondStudyPackId, userId))
            .thenReturn(Optional.of(buildStudyPack(secondStudyPackId, userId, "Second Biology", BIOLOGY_SUBJECT)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(thirdStudyPackId, userId))
            .thenReturn(Optional.of(buildStudyPack(thirdStudyPackId, userId, "Third Biology", BIOLOGY_SUBJECT)));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId),
            eq(primaryStudyPackId),
            eq(QuickReviewSessionMode.LONG_EXAM),
            any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        longExamService.startSession(
            primaryStudyPackId.toString(),
            userId,
            new LongExamStartRequest(null, List.of(secondStudyPackId.toString(), thirdStudyPackId.toString()))
        );

        verify(userUsageService).incrementLongExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
    }

    @Test
    void startSession_withThreeAdditionalNotesAssignsRemainderToPrimary() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        List<UUID> additionalStudyPackIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE,
            BIOLOGY_SUBJECT);

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primaryStudyPack));
        for (int index = 0; index < additionalStudyPackIds.size(); index++) {
            UUID additionalStudyPackId = additionalStudyPackIds.get(index);
            when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId))
                .thenReturn(Optional.of(buildStudyPack(
                    additionalStudyPackId,
                    userId,
                    "Additional Biology " + index,
                    BIOLOGY_SUBJECT
                )));
        }
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId),
            eq(primaryStudyPackId),
            eq(QuickReviewSessionMode.LONG_EXAM),
            any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LongExamStartResponse response = longExamService.startSession(
            primaryStudyPackId.toString(),
            userId,
            new LongExamStartRequest(
                null,
                additionalStudyPackIds.stream().map(UUID::toString).toList()
            )
        );

        assertThat(response.sourceNoteRefs())
            .extracting(source -> source.noteTitle() + ":" + source.questionCount())
            .containsExactly(
                PRIMARY_BIOLOGY_TITLE + ":7",
                "Additional Biology 0:6",
                "Additional Biology 1:6",
                "Additional Biology 2:6"
            );
        verify(userUsageService).incrementLongExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
    }

    @Test
    void startSession_withPlanSourcedMixedSubjectNotesSucceeds() {
        // ⚠️ THE DEFECT THIS RELEASE EXISTS TO FIX. The plan CTA pre-selects a plan's own notes and the
        // backend then rejected them for not sharing a subject — the product refusing a selection it made
        // itself. A verified plan makes membership the predicate, so mixed subjects are legitimate.
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE,
            BIOLOGY_SUBJECT);
        StudyPackEntity additionalStudyPack = buildStudyPack(additionalStudyPackId, userId,
            "Structural Analysis", "Engineering Mathematics");

        stubPlanSourcedStart(userId, collectionId, primaryStudyPack, List.of(additionalStudyPack));

        LongExamStartResponse response = longExamService.startSession(
            primaryStudyPackId.toString(),
            userId,
            new LongExamStartRequest(
                null,
                List.of(additionalStudyPackId.toString()),
                collectionId.toString()
            )
        );

        assertThat(response.sourceNoteRefs())
            .extracting(LongExamSourceNoteRef::noteTitle)
            .containsExactly(PRIMARY_BIOLOGY_TITLE, "Structural Analysis");
    }

    @Test
    void startSession_propagatesAVerifierRejectionRatherThanSwallowingIt() {
        // ⚠️ RENAMED AFTER A COLD AGENT PROVED THE OLD NAME WAS A LIE. It was
        // `startSession_withCollectionTheCallerDoesNotOwnThrows`, which claimed to cover ownership
        // enforcement — but the verifier is a MOCK here, so this stubs a throw and asserts the throw
        // propagates. The ownership check itself is covered by PlanSourcedExamVerifierTest, against the
        // real repositories; deleting that check passed all 1894 tests while this one was green.
        // What this DOES cover, and is worth keeping: startSession does not catch or swallow a source
        // rejection on its way out.
        UUID userId = UUID.randomUUID();
        UUID foreignCollectionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE,
            BIOLOGY_SUBJECT);

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primaryStudyPack));
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(foreignCollectionId.toString()), eq(userId), any()))
            .thenThrow(new InvalidLongExamSourceException());
        stubNoActiveLongExamSession(userId, primaryStudyPackId);

        LongExamStartRequest request = new LongExamStartRequest(
            null,
            List.of(additionalStudyPackId.toString()),
            foreignCollectionId.toString()
        );

        assertThatThrownBy(() -> longExamService.startSession(primaryStudyPackId.toString(), userId, request))
            .isInstanceOf(InvalidLongExamSourceException.class);
    }

    @Test
    void startSession_withPlanSourcedNoteThatIsNotAMemberStillEnforcesSubject() {
        // ⚠️ The gate is PER SOURCE, not per request. Naming a plan must not let one legitimate member
        // smuggle in arbitrary non-members: a source the plan does not contain still answers to the
        // same-subject rule.
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID outsiderStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE,
            BIOLOGY_SUBJECT);
        StudyPackEntity outsiderStudyPack = buildStudyPack(outsiderStudyPackId, userId, "Outsider",
            "Engineering Mathematics");

        // The plan contains ONLY the primary; the outsider is owned but not a member.
        stubPlanSourcedStart(userId, collectionId, primaryStudyPack, List.of());
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(outsiderStudyPackId, userId))
            .thenReturn(Optional.of(outsiderStudyPack));

        LongExamStartRequest request = new LongExamStartRequest(
            null,
            List.of(outsiderStudyPackId.toString()),
            collectionId.toString()
        );

        assertThatThrownBy(() -> longExamService.startSession(primaryStudyPackId.toString(), userId, request))
            .isInstanceOf(InvalidLongExamSourceException.class);
    }

    @Test
    void startSession_withPrimaryOutsideTheNamedPlanStillEnforcesSubject() {
        // ⚠️ The exam must be anchored IN the plan it claims. Otherwise a learner could name any plan
        // they own to relax the rule for an exam built somewhere else entirely.
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE,
            BIOLOGY_SUBJECT);
        StudyPackEntity additionalStudyPack = buildStudyPack(additionalStudyPackId, userId, "Structural Analysis",
            "Engineering Mathematics");

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primaryStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId))
            .thenReturn(Optional.of(additionalStudyPack));
        // The plan holds the ADDITIONAL note but not the primary.
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
            .thenReturn(Set.of(additionalStudyPack.getNoteId()));
        stubNoActiveLongExamSession(userId, primaryStudyPackId);

        LongExamStartRequest request = new LongExamStartRequest(
            null,
            List.of(additionalStudyPackId.toString()),
            collectionId.toString()
        );

        assertThatThrownBy(() -> longExamService.startSession(primaryStudyPackId.toString(), userId, request))
            .isInstanceOf(InvalidLongExamSourceException.class);
    }

    @Test
    void startSession_planSourcedCapComesFromLearnerLevelNotAConstant() {
        // ⚠️ A College learner gets 25 questions and MIN_QUESTIONS_PER_SOURCE is 3, so the ceiling is 8
        // sources — 7 additional. An 8th additional is one past it and must be refused, which is exactly
        // the boundary a flat "~10 notes" cap would have shipped over.
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        List<String> additionalIds = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            additionalIds.add(UUID.randomUUID().toString());
        }

        // Rejected before the primary pack is loaded, so nothing else needs stubbing — that ordering is
        // itself the point: request shape is validated before a locking read, not after one.
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));

        LongExamStartRequest request = new LongExamStartRequest(
            null,
            additionalIds,
            collectionId.toString()
        );

        assertThatThrownBy(() -> longExamService.startSession(primaryStudyPackId.toString(), userId, request))
            .isInstanceOf(InvalidLongExamSourceException.class);
    }

    @Test
    void startSession_planClaimThatFailsVerificationFallsBackToTheManualCap() {
        // ⚠️ The claimed scope sizes the EARLY check only. Four additional sources pass it (a College
        // learner may claim 7), so the request reaches the verified check — where the primary turns out
        // not to be a plan member, the scope collapses to manual, and the cap of 3 rejects it. Without
        // that fallback, naming any owned collection would permanently buy the larger cap.
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE,
            BIOLOGY_SUBJECT);
        // ⚠️ All four are OWNED and share the primary's subject, so ownership and the subject rule both
        // pass. The cap is then the ONLY thing that can reject this request — without that isolation the
        // test throws for an unrelated reason and proves nothing, which is how it first passed while the
        // cap fallback was mutated away.
        List<StudyPackEntity> additional = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            additional.add(buildStudyPack(UUID.randomUUID(), userId, "Same Subject " + index, BIOLOGY_SUBJECT));
        }
        List<String> additionalIds = additional.stream().map(pack -> pack.getId().toString()).toList();

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primaryStudyPack));
        for (StudyPackEntity additionalStudyPack : additional) {
            lenient().when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPack.getId(), userId))
                .thenReturn(Optional.of(additionalStudyPack));
        }
        // Owned, but it does not contain the primary — so this is not a plan-sourced exam.
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
            .thenReturn(Set.of(UUID.randomUUID()));
        stubNoActiveLongExamSession(userId, primaryStudyPackId);

        LongExamStartRequest request = new LongExamStartRequest(null, additionalIds, collectionId.toString());

        assertThatThrownBy(() -> longExamService.startSession(primaryStudyPackId.toString(), userId, request))
            .isInstanceOf(InvalidLongExamSourceException.class);
    }

    @Test
    void startSession_recordsTheVERIFIEDSourceScopeNotTheClaim() {
        // ⚠️ A caller who owns a collection that does not contain the primary is treated as a MANUAL exam
        // in every respect — strict cap, subject rule enforced. Reporting them as `plan` would let the
        // client set the single metric that separates the new path from the old one, and that metric is
        // what the release's checkpoint reads.
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE,
            BIOLOGY_SUBJECT);

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primaryStudyPack));
        // Owned, but the primary is not a member.
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
            .thenReturn(Set.of(UUID.randomUUID()));
        stubNoActiveLongExamSession(userId, primaryStudyPackId);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        // Take the POOLED branch, which fires LONG_EXAM_STARTED synchronously. The other site fires from
        // the dispatched async task, which this fixture captures without running.
        when(examQuestionPoolService.sampleQuestions(any(UUID.class), any(), anyInt(), any()))
            .thenReturn(Optional.of(List.of(new QuizItem(
                "Pooled question",
                List.of("A", "B", "C", "D"),
                0,
                "Cells",
                "Explanation"
            ))));

        longExamService.startSession(
            primaryStudyPackId.toString(),
            userId,
            new LongExamStartRequest(null, List.of(), collectionId.toString())
        );

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(analyticsService, atLeastOnce()).trackEvent(
            eq(userId),
            eq(AnalyticsEventType.LONG_EXAM_STARTED),
            any(),
            metadata.capture()
        );
        assertThat(metadata.getAllValues())
            .allSatisfy(recorded -> assertThat(recorded).containsEntry("sourceScope", "manual"));
    }

    @Test
    void resolveMaxSourceNotes_tracksTheLevelDerivedQuestionCount() {
        // The three tiers are 20 / 25 / 30, so the honest caps are 6 / 8 / 10. Pinned as arithmetic
        // because the UI states this number to the learner before they start.
        assertThat(longExamService.resolveMaxSourceNotes(20)).isEqualTo(6);
        assertThat(longExamService.resolveMaxSourceNotes(25)).isEqualTo(8);
        assertThat(longExamService.resolveMaxSourceNotes(30)).isEqualTo(10);
    }

    @Test
    void startSession_withDifferentSubjectAdditionalNoteThrows() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE,
            BIOLOGY_SUBJECT);
        StudyPackEntity additionalStudyPack = buildStudyPack(additionalStudyPackId, userId, "Organic Chemistry",
            "Chemistry");

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primaryStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId))
            .thenReturn(Optional.of(additionalStudyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId),
            eq(primaryStudyPackId),
            eq(QuickReviewSessionMode.LONG_EXAM),
            any()
        )).thenReturn(Optional.empty());
        LongExamStartRequest request = new LongExamStartRequest(null, List.of(additionalStudyPackId.toString()));

        String id = primaryStudyPackId.toString();
        assertThatThrownBy(() -> longExamService.startSession(id, userId, request))
            .isInstanceOf(InvalidLongExamSourceException.class);
    }

    @Test
    void startSession_withUnownedAdditionalNoteThrowsInvalidSource() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE,
            BIOLOGY_SUBJECT);

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primaryStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId))
            .thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId),
            eq(primaryStudyPackId),
            eq(QuickReviewSessionMode.LONG_EXAM),
            any()
        )).thenReturn(Optional.empty());
        LongExamStartRequest request = new LongExamStartRequest(null, List.of(additionalStudyPackId.toString()));

        String id = primaryStudyPackId.toString();
        assertThatThrownBy(() -> longExamService.startSession(id, userId, request))
            .isInstanceOf(InvalidLongExamSourceException.class);
    }


    @Test
    void startSession_withFourAdditionalNotesThrows() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        List<String> additionalStudyPackIds = List.of(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString()
        );

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        LongExamStartRequest request = new LongExamStartRequest(null, additionalStudyPackIds);

        String id = primaryStudyPackId.toString();
        assertThatThrownBy(() -> longExamService.startSession(id, userId, request))
            .isInstanceOf(InvalidLongExamSourceException.class);
    }

    @Test
    void pauseSession_inProgressSessionTransitionsToPaused() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, UUID.randomUUID(), QuickReviewSessionStatus.IN_PROGRESS,
            buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId,
            QuickReviewSessionMode.LONG_EXAM))
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
        QuickReviewSessionEntity session = buildSession(userId, UUID.randomUUID(), QuickReviewSessionStatus.PAUSED,
            buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId,
            QuickReviewSessionMode.LONG_EXAM))
            .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> longExamService.pauseSession(sessionId, userId))
            .isInstanceOf(LongExamSessionNotPausableException.class);
    }

    @Test
    void resumeSession_pausedSessionTransitionsToInProgress() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, UUID.randomUUID(), QuickReviewSessionStatus.PAUSED,
            buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId,
            QuickReviewSessionMode.LONG_EXAM))
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
        QuickReviewSessionEntity session = buildSession(userId, UUID.randomUUID(), QuickReviewSessionStatus.IN_PROGRESS,
            buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId,
            QuickReviewSessionMode.LONG_EXAM))
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
        QuickReviewSessionEntity session = buildSession(userId, UUID.randomUUID(), QuickReviewSessionStatus.PAUSED,
            buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId,
            QuickReviewSessionMode.LONG_EXAM))
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
        QuickReviewSessionEntity session = buildSession(userId, studyPackId, QuickReviewSessionStatus.IN_PROGRESS,
            List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), 0, "Cells", "Explanation"),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), 1, "Cells", "Explanation"),
                new QuizItem("Q3", List.of("A", "B", "C", "D"), 2, "Genetics", "Explanation")
            ));
        session.setId(sessionId);
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 0, 0));
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 1, 0));
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 2, 2));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId,
            QuickReviewSessionMode.LONG_EXAM))
            .thenReturn(Optional.of(session));
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
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
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.LONG_EXAM_COMPLETED), eq(studyPackId),
            any());
        verify(conceptHealthService).recordCorrectAnswersForKnownConcepts(
            eq(userId),
            eq(studyPackId),
            eq(List.of("Genetics")),
            eq(List.of("Cells", "Genetics")),
            any(OffsetDateTime.class)
        );
        verify(conceptHealthService).recordIncorrectAnswersForKnownConcepts(
            eq(userId),
            eq(studyPackId),
            eq(List.of("Cells")),
            eq(List.of("Cells", "Genetics")),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void completeSession_unstampedItemsFallBackToSourceStudyPackBroadcast() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, primaryStudyPackId, QuickReviewSessionStatus.IN_PROGRESS,
            List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), 0, "Database durability", "Explanation",
                    null, "MCQ", null, null, null, null, "Transactions", null, null),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), 1, "Thread safety", "Explanation",
                    null, "MCQ", null, null, null, null, "Concurrency", null, null),
                new QuizItem("Q3", List.of("A", "B", "C", "D"), 2, "Free-form", "Explanation")
            ));
        session.setId(sessionId);
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 0, 0));
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 1, 1));
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 2, 2));
        session.setSessionState(withLongExamSourceRefs(session.getSessionState(), additionalStudyPackId));
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId);
        primaryStudyPack.setKeyConcepts(List.of("Transactions"));
        StudyPackEntity additionalStudyPack = buildStudyPack(additionalStudyPackId, userId);
        additionalStudyPack.setKeyConcepts(List.of("Concurrency"));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId,
            QuickReviewSessionMode.LONG_EXAM))
            .thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primaryStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserId(additionalStudyPackId, userId))
            .thenReturn(Optional.of(additionalStudyPack));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        longExamService.completeSession(sessionId, userId, new LongExamCompleteRequest(900));

        verify(conceptHealthService).recordCorrectAnswersForKnownConcepts(
            eq(userId),
            eq(primaryStudyPackId),
            eq(List.of("Transactions", "Concurrency", "Free-form")),
            eq(List.of("Transactions")),
            any(OffsetDateTime.class)
        );
        verify(conceptHealthService).recordCorrectAnswersForKnownConcepts(
            eq(userId),
            eq(additionalStudyPackId),
            eq(List.of("Transactions", "Concurrency", "Free-form")),
            eq(List.of("Concurrency")),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void completeSession_attributesStampedSharedConceptsOnlyToTheirContributingSourcePacks() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID sourceAId = UUID.randomUUID();
        UUID sourceBId = UUID.randomUUID();
        UUID sourceCId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, sourceAId, QuickReviewSessionStatus.IN_PROGRESS,
                List.of(
                        new QuizItem("A Shear", List.of("A", "B"), 0, "Shear", "Explanation").withSourceStudyPackId(sourceAId.toString()),
                        new QuizItem("A Moment", List.of("A", "B"), 0, "Moment", "Explanation").withSourceStudyPackId(sourceAId.toString()),
                        new QuizItem("B Shear", List.of("A", "B"), 0, "Shear", "Explanation").withSourceStudyPackId(sourceBId.toString()),
                        new QuizItem("B Moment", List.of("A", "B"), 0, "Moment", "Explanation").withSourceStudyPackId(sourceBId.toString())
                ));
        session.setId(sessionId);
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 0, 0));
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 1, 1));
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 2, 1));
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 3, 0));
        Map<String, Object> state = new LinkedHashMap<>(session.getSessionState());
        state.put("sourceNoteRefs", List.of(
                Map.of("studyPackId", sourceBId.toString(), "noteId", UUID.randomUUID().toString(), "noteTitle", "B", "questionCount", 2),
                Map.of("studyPackId", sourceCId.toString(), "noteId", UUID.randomUUID().toString(), "noteTitle", "C", "questionCount", 0)
        ));
        session.setSessionState(state);
        StudyPackEntity sourceA = buildStudyPack(sourceAId, userId);
        sourceA.setKeyConcepts(List.of("Shear", "Moment"));
        StudyPackEntity sourceB = buildStudyPack(sourceBId, userId);
        sourceB.setKeyConcepts(List.of("Shear", "Moment"));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.LONG_EXAM))
                .thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(sourceAId, userId)).thenReturn(Optional.of(sourceA));
        when(studyPackRepository.findByIdAndOwnerUserId(sourceBId, userId)).thenReturn(Optional.of(sourceB));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        longExamService.completeSession(sessionId, userId, new LongExamCompleteRequest(900));

        verify(conceptHealthService).recordCorrectAnswersForKnownConcepts(
                eq(userId), eq(sourceAId), eq(List.of("Shear")), eq(List.of("Shear", "Moment")), any(OffsetDateTime.class));
        verify(conceptHealthService).recordIncorrectAnswersForKnownConcepts(
                eq(userId), eq(sourceAId), eq(List.of("Moment")), eq(List.of("Shear", "Moment")), any(OffsetDateTime.class));
        verify(conceptHealthService).recordCorrectAnswersForKnownConcepts(
                eq(userId), eq(sourceBId), eq(List.of("Moment")), eq(List.of("Shear", "Moment")), any(OffsetDateTime.class));
        verify(conceptHealthService).recordIncorrectAnswersForKnownConcepts(
                eq(userId), eq(sourceBId), eq(List.of("Shear")), eq(List.of("Shear", "Moment")), any(OffsetDateTime.class));
        verify(conceptHealthService, never()).recordCorrectAnswersForKnownConcepts(
                eq(userId), eq(sourceCId), any(), any(), any());
        verify(conceptHealthService, never()).recordIncorrectAnswersForKnownConcepts(
                eq(userId), eq(sourceCId), any(), any(), any());
    }

    @Test
    void completeSession_skipsMissingSourcePackAndStillReturnsReport() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID missingStudyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, primaryStudyPackId, QuickReviewSessionStatus.IN_PROGRESS,
            List.of(new QuizItem("Q1", List.of("A", "B", "C", "D"), 0, "Cells", "Explanation")));
        session.setId(sessionId);
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(session.getSessionState(), 0, 0));
        session.setSessionState(withLongExamSourceRefs(session.getSessionState(), missingStudyPackId));
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId);

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId,
            QuickReviewSessionMode.LONG_EXAM))
            .thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primaryStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserId(missingStudyPackId, userId))
            .thenReturn(Optional.empty());
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LongExamMasteryReportResponse response = longExamService.completeSession(
            sessionId,
            userId,
            new LongExamCompleteRequest(900)
        );

        assertThat(response.totalQuestions()).isEqualTo(1);
        verify(conceptHealthService).recordCorrectAnswersForKnownConcepts(
            eq(userId),
            eq(primaryStudyPackId),
            eq(List.of("Cells")),
            eq(List.of("Cells", "Genetics")),
            any(OffsetDateTime.class)
        );
        verify(conceptHealthService, never()).recordCorrectAnswersForKnownConcepts(
            eq(userId),
            eq(missingStudyPackId),
            any(),
            any(),
            any()
        );
        verify(conceptHealthService, never()).recordIncorrectAnswersForKnownConcepts(
            eq(userId),
            eq(missingStudyPackId),
            any(),
            any(),
            any()
        );
    }

    @Test
    void completeSession_forfeitedSessionThrows() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, UUID.randomUUID(), QuickReviewSessionStatus.FORFEITED,
            buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId,
            QuickReviewSessionMode.LONG_EXAM))
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
        QuickReviewSessionEntity session = buildSession(userId, studyPackId, QuickReviewSessionStatus.IN_PROGRESS,
            buildQuiz(20));
        session.setId(sessionId);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId,
            QuickReviewSessionMode.LONG_EXAM))
            .thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        longExamService.forfeitSession(sessionId, userId);

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FORFEITED);
        assertThat(session.getCompletedAt()).isNotNull();
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.LONG_EXAM_FORFEITED), eq(studyPackId),
            any());
        verify(conceptHealthService, never()).recordCorrectAnswersForKnownConcepts(any(), any(), any(), any(), any());
        verify(conceptHealthService, never()).recordIncorrectAnswersForKnownConcepts(any(), any(), any(), any(), any());
    }

    private NoteCollectionItemEntity buildCollectionItem(UUID collectionId, UUID noteId) {
        NoteCollectionItemEntity item = new NoteCollectionItemEntity();
        item.setId(UUID.randomUUID());
        item.setCollectionId(collectionId);
        item.setNoteId(noteId);
        item.setPosition(0);
        return item;
    }

    private void stubNoActiveLongExamSession(UUID userId, UUID primaryStudyPackId) {
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId),
            eq(primaryStudyPackId),
            eq(QuickReviewSessionMode.LONG_EXAM),
            any()
        )).thenReturn(Optional.empty());
    }

    /** A PRO learner starting from a plan that genuinely contains the primary and every listed source. */
    private void stubPlanSourcedStart(
        UUID userId,
        UUID collectionId,
        StudyPackEntity primaryStudyPack,
        List<StudyPackEntity> additionalStudyPacks
    ) {
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPack.getId(), userId))
            .thenReturn(Optional.of(primaryStudyPack));
        List<NoteCollectionItemEntity> items = new ArrayList<>();
        items.add(buildCollectionItem(collectionId, primaryStudyPack.getNoteId()));
        for (StudyPackEntity additionalStudyPack : additionalStudyPacks) {
            lenient().when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPack.getId(), userId))
                .thenReturn(Optional.of(additionalStudyPack));
            items.add(buildCollectionItem(collectionId, additionalStudyPack.getNoteId()));
        }
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
            .thenReturn(items.stream().map(NoteCollectionItemEntity::getNoteId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
        stubNoActiveLongExamSession(userId, primaryStudyPack.getId());
        lenient().when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }


    // ── v0.105.0 assembly-threshold, eligible-pool and Identification coverage ──────────────────────
    // Added inline after two Codex passes left these mutants alive: disabling the assembly threshold and
    // zeroing the minimum-contributing-sources check both left the whole suite green.

    @Test
    void asyncGeneration_belowMinimumAssembledQuestionsFailsTheSessionAndReversesQuota() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        StudyPackGenerationContext context = buildGenerationContext(LearnerLevel.COLLEGE);
        List<QuickReviewSessionStatus> savedStatuses = new ArrayList<>();
        List<QuickReviewSessionEntity> savedSessions = new ArrayList<>();

        // 5 assembled questions is below longExamMinimumAssembledQuestions (10).
        stubStartSession(userId, studyPackId, studyPack, context, buildQuiz(5), savedStatuses, savedSessions);

        LongExamStartResponse response = longExamService.startSession(
            studyPackId.toString(), userId, new LongExamStartRequest(null));
        when(quickReviewSessionRepository.findById(response.sessionId()))
            .thenReturn(Optional.of(savedSessions.getFirst()));

        dispatchedTask.run();

        assertThat(savedStatuses).endsWith(QuickReviewSessionStatus.FAILED);
        verify(generationRecoveryRowWriter).failLongExamSession(response.sessionId());
    }

    @Test
    void asyncGeneration_aboveThresholdButShortSucceedsAndKeepsQuota() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        StudyPackGenerationContext context = buildGenerationContext(LearnerLevel.COLLEGE);
        List<QuickReviewSessionStatus> savedStatuses = new ArrayList<>();
        List<QuickReviewSessionEntity> savedSessions = new ArrayList<>();

        // 20 of an expected 25 clears both floors, so this is a SHORT but valid exam.
        stubStartSession(userId, studyPackId, studyPack, context, buildQuiz(20), savedStatuses, savedSessions);

        LongExamStartResponse response = longExamService.startSession(
            studyPackId.toString(), userId, new LongExamStartRequest(null));
        when(quickReviewSessionRepository.findById(response.sessionId()))
            .thenReturn(Optional.of(savedSessions.getFirst()));

        dispatchedTask.run();

        assertThat(savedStatuses).containsExactly(
            QuickReviewSessionStatus.GENERATING, QuickReviewSessionStatus.IN_PROGRESS);
        // ⚠️ The learner keeps their charge for a short-but-valid exam; only a sub-threshold exam refunds.
        verify(generationRecoveryRowWriter, never()).failLongExamSession(any(UUID.class));
        QuickReviewSessionEntity ready = savedSessions.getLast();
        assertThat(ready.getSessionState().get("shortExam")).isEqualTo(true);
        assertThat(ready.getSessionState().get("expectedQuestionCount")).isEqualTo(25);
    }

    @Test
    void asyncGeneration_singleSourceExamIsNotFailedByTheContributingSourcesClamp() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        StudyPackGenerationContext context = buildGenerationContext(LearnerLevel.COLLEGE);
        List<QuickReviewSessionStatus> savedStatuses = new ArrayList<>();
        List<QuickReviewSessionEntity> savedSessions = new ArrayList<>();

        // One source can only ever contribute one source; Math.min(sourceCount, minimum) must not fail it.
        stubStartSession(userId, studyPackId, studyPack, context, buildQuiz(25), savedStatuses, savedSessions);

        LongExamStartResponse response = longExamService.startSession(
            studyPackId.toString(), userId, new LongExamStartRequest(null));
        when(quickReviewSessionRepository.findById(response.sessionId()))
            .thenReturn(Optional.of(savedSessions.getFirst()));

        dispatchedTask.run();

        assertThat(savedStatuses).containsExactly(
            QuickReviewSessionStatus.GENERATING, QuickReviewSessionStatus.IN_PROGRESS);
        verify(generationRecoveryRowWriter, never()).failLongExamSession(any(UUID.class));
    }


    @Test
    void asyncGeneration_doesNotResurrectASessionTheSweeperAlreadyFailedAndRefunded() {
        // ⚠️ THE TEST THE WHOLE RELEASE LACKED. Generation runs inside the transaction and can outlast the
        // stale-session sweeper's cutoff, so the sweeper can FAIL and REFUND a session while generation is
        // still running. Without the lock-then-scalar-read guard, the async completion resurrects that
        // refunded session as IN_PROGRESS with a full quiz — a free, usable exam — and rebuilding the
        // session state re-arms the reservation flag while erasing the reversal stamp.
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        StudyPackGenerationContext context = buildGenerationContext(LearnerLevel.COLLEGE);
        List<QuickReviewSessionStatus> savedStatuses = new ArrayList<>();
        List<QuickReviewSessionEntity> savedSessions = new ArrayList<>();

        stubStartSession(userId, studyPackId, studyPack, context, buildQuiz(25), savedStatuses, savedSessions);

        LongExamStartResponse response = longExamService.startSession(
            studyPackId.toString(), userId, new LongExamStartRequest(null));
        QuickReviewSessionEntity generating = savedSessions.getFirst();
        when(quickReviewSessionRepository.findById(response.sessionId())).thenReturn(Optional.of(generating));

        // The sweeper wins the race: the row is FAILED and refunded before generation finishes.
        when(quickReviewSessionRepository.findByIdForUpdate(response.sessionId()))
            .thenReturn(Optional.of(generating));
        when(quickReviewSessionRepository.findStatusById(response.sessionId()))
            .thenReturn(Optional.of(QuickReviewSessionStatus.FAILED));
        savedStatuses.clear();
        savedSessions.clear();

        dispatchedTask.run();

        // No resurrection: the quiz is never installed and the session is not written back to IN_PROGRESS.
        assertThat(savedStatuses).doesNotContain(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(savedSessions).isEmpty();
        // And the reversal stamp is never erased, so a second refund can never be armed.
        assertThat(generating.getSessionState()).doesNotContainKey("longExamQuotaReversed");
    }

    @Test
    void markSessionReady_carriesQuotaFlagsForwardInsteadOfReArmingARefund() {
        // ⚠️ Independent of the race: any path that marks a session ready must not regenerate the quota
        // flags. buildInitialSessionState writes longExamQuotaReserved=true and drops longExamQuotaReversed.
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(
            userId, studyPackId, QuickReviewSessionStatus.GENERATING, List.of());
        Map<String, Object> state = new LinkedHashMap<>(session.getSessionState());
        state.put("longExamQuotaReserved", true);
        state.put("longExamQuotaReversed", true);
        session.setSessionState(state);

        ReflectionTestUtils.invokeMethod(
            longExamService, "markSessionReady", session, buildQuiz(25), DEFAULT_DIFFICULTY);

        assertThat(session.getSessionState()).containsEntry("longExamQuotaReversed", true);
        assertThat(session.getSessionState()).containsEntry("longExamQuotaReserved", true);
    }


    @Test
    void startSession_persistsTheQuotaReservationFlagThatTheRefundPathReads() {
        // ⚠️ CONNECTIVE TEST. The refund is written by buildInitialSessionState here and read by
        // GenerationRecoveryRowWriter.markLongExamSessionFailed. Both sides were tested in isolation —
        // the writer's tests never asserted the flag, and the reader's tests hand-built the state map —
        // so deleting the write disabled every refund in the product and 1938 tests stayed green.
        // Assert the real persisted state carries the exact key the refund path keys on.
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        List<QuickReviewSessionStatus> savedStatuses = new ArrayList<>();
        List<QuickReviewSessionEntity> savedSessions = new ArrayList<>();

        stubStartSession(userId, studyPackId, studyPack, buildGenerationContext(LearnerLevel.COLLEGE),
            buildQuiz(25), savedStatuses, savedSessions);

        longExamService.startSession(studyPackId.toString(), userId, new LongExamStartRequest(null));

        assertThat(savedSessions.getFirst().getSessionState())
            .containsEntry(LongExamService.SESSION_STATE_LONG_EXAM_QUOTA_RESERVED, true);
        verify(userUsageService).incrementLongExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
    }


    @Test
    void startSession_planSourcedNeverServesOrWarmsTheSingleNotePool() {
        // ⚠️ additionalStudyPackIds.isEmpty() is no longer the single-note test: a plan launch sends only
        // sourceCollectionId, so that list is empty while the exam spans the plan. Without the planSourced
        // clause, the SECOND and every later plan launch was served a PRIMARY-ONLY pool while the session
        // still recorded the sampled multi-source refs and sourceScope=plan — a single-note exam reported
        // as a curriculum exam, corrupting the field a dated checkpoint reads.
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, userId);
        StudyPackEntity second = buildStudyPack(UUID.randomUUID(), userId, "Second", BIOLOGY_SUBJECT);
        StudyPackEntity third = buildStudyPack(UUID.randomUUID(), userId, "Third", BIOLOGY_SUBJECT);

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primary));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId), eq(primaryStudyPackId), eq(QuickReviewSessionMode.LONG_EXAM), any()
        )).thenReturn(Optional.empty());
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
            .thenReturn(Set.of(primary.getNoteId(), second.getNoteId(), third.getNoteId()));
        when(planSourcedExamVerifier.resolvePlanMembers(eq(collectionId.toString()), eq(userId), any()))
            .thenReturn(List.of(
                new PlanSourcedExamVerifier.PlanExamMember(primary.getNoteId(), "A", 0),
                new PlanSourcedExamVerifier.PlanExamMember(second.getNoteId(), "B", 1),
                new PlanSourcedExamVerifier.PlanExamMember(third.getNoteId(), "C", 2)
            ));
        when(studyPackRepository.findByOwnerUserIdAndNoteIdInAndStatus(eq(userId), any(), any()))
            .thenReturn(List.of(primary, second, third));
        // ⚠️ THIS STUB IS WHAT MAKES THE WARM ASSERTION REAL. The warm block calls findByIdAndOwnerUserId
        // (NOT ...ForUpdate) and guards on .ifPresent, so without this stub the block could never run and
        // `verify(never()).initiatePoolForUsage` passed vacuously — deleting the warm guard survived the
        // whole suite.
        // lenient: with the guard intact this stub is deliberately UNUSED — that is the proof the warm
        // block never runs. Strict mode would reject it, and removing it makes the assertion vacuous.
        lenient().when(studyPackRepository.findByIdAndOwnerUserId(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primary));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        longExamService.startSession(
            primaryStudyPackId.toString(), userId,
            new LongExamStartRequest(null, null, collectionId.toString()));

        verify(examQuestionPoolService, never()).sampleQuestions(any(UUID.class), any(), anyInt(), any());
        verify(examQuestionPoolService, never()).initiatePoolForUsage(any(), any(UUID.class), any());
    }

    @Test
    void startSession_eligiblePoolCountsOnlyReadyStudyPacks() {
        // ⚠️ The DONE literal IS the definition of "ready Study Pack" — the whole eligible-pool concept and
        // the InsufficientEligibleSources message rest on it, and nothing constrained it: the sole stub
        // passed any() for the status, so changing DONE to FAILED left the suite green.
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, userId);

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primary));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId), eq(primaryStudyPackId), eq(QuickReviewSessionMode.LONG_EXAM), any()
        )).thenReturn(Optional.empty());
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
            .thenReturn(Set.of(primary.getNoteId()));
        when(planSourcedExamVerifier.resolvePlanMembers(eq(collectionId.toString()), eq(userId), any()))
            .thenReturn(List.of(new PlanSourcedExamVerifier.PlanExamMember(primary.getNoteId(), "A", 0)));
        // The pool query is asserted to ask for DONE specifically, not "any status".
        when(studyPackRepository.findByOwnerUserIdAndNoteIdInAndStatus(
            eq(userId), any(), eq(com.studysnap.backend.entity.StudyPackStatus.DONE)))
            .thenReturn(List.of(primary));

        assertThatThrownBy(() -> longExamService.startSession(
            primaryStudyPackId.toString(), userId,
            new LongExamStartRequest(null, null, collectionId.toString())
        )).isInstanceOf(LongExamInsufficientEligibleSourcesException.class);

        verify(studyPackRepository).findByOwnerUserIdAndNoteIdInAndStatus(
            eq(userId), any(), eq(com.studysnap.backend.entity.StudyPackStatus.DONE));
    }


    @Test
    void asyncGeneration_failsWhenOnlyOneOfTwoSourcesActuallyContributes() {
        // ⚠️ contributingSourceCount must count sources that PRODUCED questions, not sources ATTEMPTED.
        // Moving the increment above the "did this source yield anything" guard left the suite green, and
        // that counter is what the release's own resilience decision rests on: with 2 sources the floor is
        // min(2, 2) = 2, so one silent source must fail the exam rather than pass it as complete.
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, userId, PRIMARY_BIOLOGY_TITLE, BIOLOGY_SUBJECT);
        StudyPackEntity additional = buildStudyPack(additionalStudyPackId, userId, CELL_BIOLOGY_TITLE, BIOLOGY_SUBJECT);
        List<QuickReviewSessionStatus> savedStatuses = new ArrayList<>();
        List<QuickReviewSessionEntity> savedSessions = new ArrayList<>();

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primary));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId))
            .thenReturn(Optional.of(additional));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId), eq(primaryStudyPackId), eq(QuickReviewSessionMode.LONG_EXAM), any()
        )).thenReturn(Optional.empty());
        when(generationContextResolver.resolveForStudyPack(eq(userId), any()))
            .thenReturn(buildGenerationContext(LearnerLevel.COLLEGE));
        // The primary yields plenty; the second source yields nothing at all.
        when(quizGenerationService.generateLongExamParallel(
            eq(PRIMARY_BIOLOGY_TITLE), any(), any(), any(), anyInt(), any(), any(), any()
        )).thenReturn(buildQuiz(13));
        when(quizGenerationService.generateLongExamParallel(
            eq(CELL_BIOLOGY_TITLE), any(), any(), any(), anyInt(), any(), any(), any()
        )).thenReturn(List.of());
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> {
                QuickReviewSessionEntity session = invocation.getArgument(0);
                savedStatuses.add(session.getStatus());
                savedSessions.add(session);
                return session;
            });

        LongExamStartResponse response = longExamService.startSession(
            primaryStudyPackId.toString(), userId,
            new LongExamStartRequest(null, List.of(additionalStudyPackId.toString())));
        when(quickReviewSessionRepository.findById(response.sessionId()))
            .thenReturn(Optional.of(savedSessions.getFirst()));

        dispatchedTask.run();

        assertThat(savedStatuses).endsWith(QuickReviewSessionStatus.FAILED);
        verify(generationRecoveryRowWriter).failLongExamSession(response.sessionId());
    }


    @Test
    void asyncGeneration_locksTheSessionRowBeforeReReadingItsStatus() {
        // ⚠️ THE ORDER IS THE GUARD, AND THE LOCK IS THE HALF NOTHING PINNED. The freshness of the scalar
        // read is already covered; this pins the serialisation that makes the read meaningful. Deleting the
        // lock, or reading before taking it, leaves plain TOCTOU: the entity has no @Version, so the sweeper
        // can commit FAILED + refund in the gap and the save then resurrects a refunded session.
        // Both mutations survived the whole suite before this test existed — the original defect minus one line.
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        List<QuickReviewSessionStatus> savedStatuses = new ArrayList<>();
        List<QuickReviewSessionEntity> savedSessions = new ArrayList<>();

        stubStartSession(userId, studyPackId, studyPack, buildGenerationContext(LearnerLevel.COLLEGE),
            buildQuiz(25), savedStatuses, savedSessions);

        LongExamStartResponse response = longExamService.startSession(
            studyPackId.toString(), userId, new LongExamStartRequest(null));
        when(quickReviewSessionRepository.findById(response.sessionId()))
            .thenReturn(Optional.of(savedSessions.getFirst()));

        dispatchedTask.run();

        InOrder ordered = inOrder(quickReviewSessionRepository);
        ordered.verify(quickReviewSessionRepository).findByIdForUpdate(response.sessionId());
        ordered.verify(quickReviewSessionRepository).findStatusById(response.sessionId());
    }


    @Test
    void startSession_planSourcedRejectsACallerSuppliedSourceListInsteadOfDiscardingIt() {
        // ⚠️ The rule CLAUDE.md singles out: "silently ignored" drifting into "silently accepted" is how a
        // cap gets bypassed. API-surface only — the client never sends both — so nothing else covers it.
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, userId);
        StudyPackEntity second = buildStudyPack(UUID.randomUUID(), userId, "Second", BIOLOGY_SUBJECT);

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primary));
        lenient().when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(second.getId(), userId))
            .thenReturn(Optional.of(second));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId), eq(primaryStudyPackId), eq(QuickReviewSessionMode.LONG_EXAM), any()
        )).thenReturn(Optional.empty());
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
            .thenReturn(Set.of(primary.getNoteId(), second.getNoteId()));
        when(planSourcedExamVerifier.resolvePlanMembers(eq(collectionId.toString()), eq(userId), any()))
            .thenReturn(List.of(
                new PlanSourcedExamVerifier.PlanExamMember(primary.getNoteId(), "A", 0),
                new PlanSourcedExamVerifier.PlanExamMember(second.getNoteId(), "B", 1)
            ));

        assertThatThrownBy(() -> longExamService.startSession(
            primaryStudyPackId.toString(), userId,
            new LongExamStartRequest(null, List.of(second.getId().toString()), collectionId.toString())
        )).isInstanceOf(InvalidLongExamSourceException.class);

        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
    }

    private void stubStartSession(
        UUID userId,
        UUID studyPackId,
        StudyPackEntity studyPack,
        StudyPackGenerationContext context,
        List<QuizItem> generatedQuiz,
        List<QuickReviewSessionStatus> savedStatuses,
        List<QuickReviewSessionEntity> savedSessions
    ) {
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId))
            .thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.LONG_EXAM), any()
        )).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(context);
        // lenient: callers that only exercise the START path never run the dispatched task.
        lenient().when(quizGenerationService.generateLongExamParallel(
            any(), any(), any(), any(), anyInt(), any(), any(), any()
        )).thenReturn(generatedQuiz);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> {
                QuickReviewSessionEntity session = invocation.getArgument(0);
                savedStatuses.add(session.getStatus());
                savedSessions.add(session);
                return session;
            });
    }

    @Test
    void completeSession_gradesIdentificationAnswersAndTreatsBlankAsIncorrect() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        // Three IDENTIFICATION items: correct, wrong, and blank. Blank must score incorrect, never throw.
        List<QuizItem> quiz = List.of(
            buildIdentificationItem("Name the powerhouse", "mitochondrion"),
            buildIdentificationItem("Name the process", "photosynthesis"),
            buildIdentificationItem("Name the unit", "newton")
        );
        QuickReviewSessionEntity session = buildSession(
            userId, studyPackId, QuickReviewSessionStatus.IN_PROGRESS, quiz);
        session.setSessionState(QuizSessionStateUtils.withSelectedIdentificationAnswer(
            session.getSessionState(), 0, "  Mitochondrion  "));
        session.setSessionState(QuizSessionStateUtils.withSelectedIdentificationAnswer(
            session.getSessionState(), 1, "respiration"));
        session.setSessionState(QuizSessionStateUtils.withSelectedIdentificationAnswer(
            session.getSessionState(), 2, "   "));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
            session.getId(), userId, QuickReviewSessionMode.LONG_EXAM)).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId))
            .thenReturn(Optional.of(buildStudyPack(studyPackId, userId)));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        LongExamMasteryReportResponse report = longExamService.completeSession(
            session.getId(), userId, new LongExamCompleteRequest(600));

        // Trim + lowercase makes item 0 correct; a wrong term and a blank are both incorrect.
        // A BLANK answer is excluded from the answered denominator, so 1 correct of 2 answered = 50%.
        assertThat(report.totalQuestions()).isEqualTo(3);
        assertThat(report.answeredQuestions()).isEqualTo(2);
        assertThat(report.scorePercentage()).isEqualTo(50);
    }

    @Test
    void updateProgress_roundTripsTheIdentificationAnswerIntoSessionState() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(
            userId,
            studyPackId,
            QuickReviewSessionStatus.IN_PROGRESS,
            List.of(buildIdentificationItem("Name the powerhouse", "mitochondrion"))
        );

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
            session.getId(), userId, QuickReviewSessionMode.LONG_EXAM)).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        longExamService.saveProgress(
            session.getId(), userId, new LongExamProgressRequest(0, -1, null, "mitochondrion"));

        assertThat(QuizSessionStateUtils.extractSelectedIdentificationAnswers(
            session.getSessionState(),
            QuizSessionStateUtils.extractQuiz(session.getSessionState())
        )).containsEntry(0, "mitochondrion");
    }

    private QuizItem buildIdentificationItem(String question, String answer) {
        return new QuizItem(
            question, List.of(), null, "Cells", "Explanation", null,
            "IDENTIFICATION", null, null, null, null, "Cells", List.of(answer), null);
    }


    @Test
    void startSession_planWithTooFewEligibleSourcesFailsWithoutCreatingASessionOrChargingQuota() {
        // ⚠️ Pool A is "plan member AND has a READY Study Pack" — two predicates. A plan whose members
        // mostly lack a ready pack must be refused before a session exists, not silently shrunk.
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(primaryStudyPackId, userId);
        UUID unreadyNoteId = UUID.randomUUID();

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
            .thenReturn(Optional.of(primaryStudyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId), eq(primaryStudyPackId), eq(QuickReviewSessionMode.LONG_EXAM), any()
        )).thenReturn(Optional.empty());
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
            .thenReturn(Set.of(primaryStudyPack.getNoteId(), unreadyNoteId));
        when(planSourcedExamVerifier.resolvePlanMembers(eq(collectionId.toString()), eq(userId), any()))
            .thenReturn(List.of(
                new PlanSourcedExamVerifier.PlanExamMember(primaryStudyPack.getNoteId(), "Algebra", 0),
                new PlanSourcedExamVerifier.PlanExamMember(unreadyNoteId, "Algebra", 1)
            ));
        // Only the primary has a READY pack, so pool A is 1 against a minimum of 2.
        when(studyPackRepository.findByOwnerUserIdAndNoteIdInAndStatus(eq(userId), any(), any()))
            .thenReturn(List.of(primaryStudyPack));

        assertThatThrownBy(() -> longExamService.startSession(
            primaryStudyPackId.toString(),
            userId,
            new LongExamStartRequest(null, null, collectionId.toString())
        )).isInstanceOf(LongExamInsufficientEligibleSourcesException.class);

        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(userUsageService, never()).incrementLongExamGenerationBy(any(UUID.class), anyInt(), any());
    }

    @Test
    void startSession_returnsTheExistingActiveSessionRatherThanCreatingASecondOne() {
        // ⚠️ REGRESSION GUARD for kickoff Amendment 1. The session stays keyed on the CALLER-SUPPLIED
        // primary; anchoring it on a sampled primary would make this lookup miss and spend a second
        // quota unit, or trip one of V41's two partial unique indexes.
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, userId);
        QuickReviewSessionEntity existing = buildSession(
            userId, studyPackId, QuickReviewSessionStatus.IN_PROGRESS, buildQuiz(25));

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, LearnerLevel.COLLEGE)));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId))
            .thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
            eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.LONG_EXAM), any()
        )).thenReturn(Optional.of(existing));

        LongExamStartResponse response = longExamService.startSession(
            studyPackId.toString(), userId, new LongExamStartRequest(null));

        assertThat(response.sessionId()).isEqualTo(existing.getId());
        verify(userUsageService, never()).incrementLongExamGenerationBy(any(UUID.class), anyInt(), any());
    }

    private StudyPackEntity buildStudyPack(UUID studyPackId, UUID userId) {
        return buildStudyPack(studyPackId, userId, "Long Exam Pack", BIOLOGY_SUBJECT);
    }

    private StudyPackEntity buildStudyPack(UUID studyPackId, UUID userId, String title, String subject) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        studyPack.setNoteId(UUID.randomUUID());
        studyPack.setTitle(title);
        studyPack.setSubject(subject);
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

    private Map<String, Object> withLongExamSourceRefs(Map<String, Object> sessionState, UUID studyPackId) {
        Map<String, Object> state = new LinkedHashMap<>(sessionState);
        state.put("sourceNoteRefs", List.of(Map.of(
            "studyPackId", studyPackId.toString(),
            "noteId", UUID.randomUUID().toString(),
            "noteTitle", "Additional source",
            "questionCount", 1
        )));
        return state;
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
