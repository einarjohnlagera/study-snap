package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ChallengeQuizCompleteRequest;
import com.studysnap.backend.dto.ChallengeQuizSessionResponse;
import com.studysnap.backend.dto.GenerateMoreChallengeQuizResponse;
import com.studysnap.backend.dto.QuizSessionReviewResponse;
import com.studysnap.backend.dto.ChallengeQuizStartRequest;
import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.dto.LongExamSourceNoteRef;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.ChallengeQuizSessionNotInProgressException;
import com.studysnap.backend.exception.InvalidBoardExamSourceException;
import com.studysnap.backend.exception.NotEnoughNewQuestionsException;
import com.studysnap.backend.exception.BoardExamInsufficientEligibleSourcesException;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.InvalidChallengeQuizModeException;
import com.studysnap.backend.exception.MonthlyBoardExamLimitReachedException;
import com.studysnap.backend.exception.MonthlyChallengeQuizLimitReachedException;
import com.studysnap.backend.exception.MonthlyMultiNoteLimitReachedException;
import com.studysnap.backend.exception.MatchingQuestionGroupSourceMismatchException;
import com.studysnap.backend.exception.MultiNoteChallengeQuizSourceNotAllowedException;
import com.studysnap.backend.service.model.GeneratedChallengeQuizContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.service.model.StudyPackQuizMastery;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.ExamQuestionPoolRepository;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.util.QuizSessionStateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeQuizServiceTest {
    private static final int DEFAULT_ADAPTIVE_QUESTION_COUNT = 12;
    private static final int REDO_MISSED_QUESTION_COUNT = 5;
    private static final int CHALLENGE_SECONDS_PER_QUESTION = 90;

    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private PlanSourcedExamVerifier planSourcedExamVerifier;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private QuizGenerationService quizGenerationService;
    @Mock
    private LlmStudyPackService llmStudyPackService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserUsageService userUsageService;
    @Mock
    private BillingUsagePeriodService billingUsagePeriodService;
    @Mock
    private AuthService authService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private AiRateLimitService aiRateLimitService;
    @Mock
    private ActivityTrackingService activityTrackingService;
    @Mock
    private StudyPackGenerationContextResolver generationContextResolver;
    @Mock
    private ExamQuestionPoolService examQuestionPoolService;
    @Mock
    private ChallengeQuizQuestionBankService challengeQuizQuestionBankService;
    @Mock
    private OfficialChallengeQuizTemplateService officialChallengeQuizTemplateService;
    @Mock
    private ConceptHealthService conceptHealthService;
    @Mock
    private StudyPackQuizMasteryService studyPackQuizMasteryService;
    // ⚠️ NOT a @Mock. A mocked TransactionOperations returns null and never invokes the callback, so the
    // async Board Exam generation body would never run and every test would observe GENERATING forever.
    // Mirrors LongExamServiceTest, which executes the callback directly.
    private final TransactionOperations studyPackGenerationTransactionOperations = new TransactionOperations() {
        @Override
        public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    };
    /**
     * ⚠️ NOT a {@code @Mock}. The two-meter Board Exam reversal lives INSIDE
     * {@link GenerationRecoveryRowWriter#failBoardExamSession(UUID)}, so a mocked row writer reduces every
     * refund assertion to {@code verify(rowWriter).failBoardExamSession(id)} — which keeps passing after the
     * reversal is deleted outright. A spy runs the real body and still records the call.
     */
    private GenerationRecoveryRowWriter generationRecoveryRowWriter;
    @Mock
    private NoteCollectionRepository noteCollectionRepository;
    @Mock
    private NoteCollectionItemRepository noteCollectionItemRepository;

    /**
     * ⚠️ MOCKED, and the capture is the point. With a {@code Runnable::run} dispatcher the generation body
     * executes BEFORE {@code startSession} builds its response, so the start response would show
     * IN_PROGRESS with a full quiz and no test could ever observe the GENERATING hand-off this release
     * introduced. Capturing the task and running it explicitly is the LongExamServiceTest model.
     */
    @Mock
    private StudyPackGenerationTaskDispatcher studyPackGenerationTaskDispatcher;
    private Runnable dispatchedTask;
    /**
     * A SPY, not a mock: the real stratified draw must run, while the arguments stay observable so a test
     * can prove the sample is keyed on the session id the row is persisted under.
     */
    private LongExamPlanSourceSampler longExamPlanSourceSampler;

    private final Map<UUID, QuickReviewSessionEntity> savedSessionsById = new LinkedHashMap<>();

    /**
     * Records every persisted session so the ASYNCHRONOUS Board Exam generation body can re-fetch it by id.
     * Board Exam generation moved off the request transaction in v0.106.0: startSession returns GENERATING
     * and the dispatched task loads the row again. Without this the task throws SessionNotFound, the catch
     * swallows it, and every Board Exam test observes a session stuck at GENERATING.
     */
    private QuickReviewSessionEntity recordSession(QuickReviewSessionEntity session) {
        if (session != null && session.getId() != null) {
            savedSessionsById.put(session.getId(), session);
        }
        return session;
    }


    /**
     * Asserts the Board Exam start handed back a GENERATING session with no quiz yet, then runs the task
     * the request dispatched and returns the session AS PERSISTED.
     *
     * <p>⚠️ As of v0.106.0 Board Exam generation is off the request transaction, so the start response
     * deliberately carries no questions. Every assertion about quiz content, per-source stamping and the
     * IN_PROGRESS transition belongs on the persisted session, not on the start response.
     */
    private QuickReviewSessionEntity completeDispatchedBoardExam(ChallengeQuizStartResponse startResponse) {
        assertThat(startResponse.status()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        assertThat(startResponse.quiz()).isEmpty();
        assertThat(dispatchedTask)
                .as("a Board Exam start must dispatch an asynchronous generation task")
                .isNotNull();
        dispatchedTask.run();
        QuickReviewSessionEntity persisted = savedSessionsById.get(UUID.fromString(startResponse.sessionId()));
        assertThat(persisted).as("the dispatched task must persist the session it generated").isNotNull();
        return persisted;
    }

    private List<QuizItem> persistedQuiz(QuickReviewSessionEntity session) {
        return QuizSessionStateUtils.extractQuiz(session.getSessionState());
    }

    private ChallengeQuizService challengeQuizService;
    /**
     * Held as a field rather than constructed inline so a test can set a NON-DEFAULT Board Exam target,
     * which is the only way an assertion can tell a configurable value from a hardcoded literal that
     * happens to equal today's default.
     */
    private StudySnapProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StudySnapProperties();
        dispatchedTask = null;
        longExamPlanSourceSampler = spy(new LongExamPlanSourceSampler());
        lenient().doAnswer(invocation -> {
            dispatchedTask = invocation.getArgument(0);
            return null;
        }).when(studyPackGenerationTaskDispatcher).execute(any(Runnable.class));
        generationRecoveryRowWriter = spy(new GenerationRecoveryRowWriter(
                mock(ExamQuestionPoolRepository.class),
                quickReviewSessionRepository,
                noteRepository,
                mock(StudyPackService.class),
                userUsageService
        ));
        lenient().when(examQuestionPoolService.sampleQuestions(any(UUID.class), any(), anyInt(), any()))
                .thenReturn(Optional.empty());
        lenient().when(challengeQuizQuestionBankService.claimEligibleQuestions(
                any(UUID.class), any(UUID.class), any(), any(UUID.class), any(), anyInt()
        )).thenReturn(List.of());
        lenient().when(officialChallengeQuizTemplateService.copyTemplateQuestions(
                any(UUID.class), any(UUID.class), any(), any(UUID.class), any(), anyInt()
        )).thenReturn(List.of());
        lenient().when(noteRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        // ⚠️ Board Exam generation is ASYNCHRONOUS as of v0.106.0: startSession returns a GENERATING
        // session and the dispatched task re-fetches it by id. Without this, generateBoardExamAsync throws
        // SessionNotFound, is caught, and every Board Exam test observes a session stuck at GENERATING —
        // which reads as "the feature is broken" rather than "the harness never let it finish".
        lenient().when(quickReviewSessionRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return savedSessionsById.containsKey(id) ? Optional.of(savedSessionsById.get(id)) : Optional.empty();
        });
        // The async body takes the row lock and THEN re-reads the scalar status. Both must resolve to the
        // persisted session or the body returns early (or throws) and the session stays GENERATING forever —
        // a harness gap that is indistinguishable from a broken feature.
        lenient().when(quickReviewSessionRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(savedSessionsById.get(id));
        });
        lenient().when(quickReviewSessionRepository.findStatusById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            QuickReviewSessionEntity session = savedSessionsById.get(id);
            return session == null ? Optional.empty() : Optional.of(session.getStatus());
        });
        lenient().when(studyPackRepository.findByIdAndOwnerUserId(any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> {
                    StudyPackEntity studyPack = new StudyPackEntity();
                    studyPack.setId(invocation.getArgument(0));
                    return Optional.of(studyPack);
                });
        challengeQuizService = new ChallengeQuizService(
                studyPackRepository,
                userRepository,
                noteRepository,
                planSourcedExamVerifier,
                quickReviewSessionRepository,
                quizGenerationService,
                subscriptionService,
                properties,
                userUsageService,
                billingUsagePeriodService,
                authService,
                analyticsService,
                aiRateLimitService,
                activityTrackingService,
                generationContextResolver,
                examQuestionPoolService,
                challengeQuizQuestionBankService,
                officialChallengeQuizTemplateService,
                conceptHealthService,
                studyPackQuizMasteryService,
                studyPackGenerationTaskDispatcher,
                studyPackGenerationTransactionOperations,
                generationRecoveryRowWriter,
                noteCollectionRepository,
                noteCollectionItemRepository,
                longExamPlanSourceSampler
        );
    }

    @Test
    void startSession_resumesNonExpiredInProgressSession_withoutCreatingDuplicate() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity inProgress = new QuickReviewSessionEntity();
        inProgress.setId(sessionId);
        inProgress.setUserId(userId);
        inProgress.setStudyPackId(studyPackId);
        inProgress.setNoteId(noteId);
        inProgress.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        inProgress.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        inProgress.setCurrentRound(QuickReviewRound.INITIAL);
        inProgress.setCurrentQuestionIndex(2);
        inProgress.setTotalQuestions(2);
        inProgress.setCorrectAnswers(0);
        inProgress.setScorePercentage(BigDecimal.ZERO);
        inProgress.setRetryCount(0);
        inProgress.setCreatedAt(OffsetDateTime.now().minusMinutes(4));
        inProgress.setSessionState(QuizSessionStateUtils.withQuiz(
                List.of(new QuizItem(
                        "Question?",
                        List.of("A", "B", "C", "D"),
                        "A",
                        "Concept",
                        "Explanation"
                )),
                Map.of(
                        "timeLimitSeconds", 600,
                        "timerStartedAtEpochSeconds", OffsetDateTime.now().minusSeconds(60).toEpochSecond(),
                        "mode", "challenge",
                        "selectedChoices", Map.of("0", "A"),
                        "completed", false
                )
        ));

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(Optional.of(inProgress));
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(inProgress));
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

        verify(authService).requireEmailVerified(userId);
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(quizGenerationService, never()).generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        verify(quizGenerationService, never()).generateBoardExamQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        verify(aiRateLimitService, never()).assertAllowed(any(), any(), any());
        verify(userUsageService, never()).incrementChallengeQuizGeneration(any(UUID.class), any(OffsetDateTime.class));
        assertThat(response.sessionId()).isEqualTo(sessionId.toString());
        assertThat(response.quiz()).hasSize(1);
    }

    @Test
    void startSession_forfeitsExpiredInProgressSessionReleasesClaimsAndStartsFresh() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID expiredSessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity expired = new QuickReviewSessionEntity();
        expired.setId(expiredSessionId);
        expired.setUserId(userId);
        expired.setStudyPackId(studyPackId);
        expired.setNoteId(noteId);
        expired.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        expired.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        expired.setCurrentRound(QuickReviewRound.INITIAL);
        expired.setCurrentQuestionIndex(0);
        expired.setTotalQuestions(1);
        expired.setCreatedAt(OffsetDateTime.now().minusMinutes(15));
        expired.setSessionState(QuizSessionStateUtils.withQuiz(
                buildQuiz(1),
                Map.of(
                        "mode", "challenge",
                        "timeLimitSeconds", 60,
                        "timerStartedAtEpochSeconds", OffsetDateTime.now().minusMinutes(10).toEpochSecond(),
                        "completed", false
                )
        ));
        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, "Engineering", studyPack.getSubject(), List.of()
        );
        List<QuizItem> freshQuiz = buildQuizWithPrefix("Fresh", DEFAULT_ADAPTIVE_QUESTION_COUNT);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.of(expired));
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                expiredSessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(expired));
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId), eq(QuickReviewSessionMode.CHALLENGE), any(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE, BillingCycle.MONTHLY, OffsetDateTime.now().minusDays(5), OffsetDateTime.now().plusDays(25), 2026, 3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.QUICK_REVIEW), any()
        )).thenReturn(List.of());
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);
        when(challengeQuizQuestionBankService.claimEligibleQuestions(
                eq(userId),
                eq(studyPackId),
                eq(LearnerLevel.COLLEGE),
                any(UUID.class),
                any(),
                eq(DEFAULT_ADAPTIVE_QUESTION_COUNT)
        )).thenReturn(freshQuiz);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class))).thenAnswer(invocation -> {
            QuickReviewSessionEntity saved = recordSession(invocation.getArgument(0));
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

        assertThat(expired.getStatus()).isEqualTo(QuickReviewSessionStatus.FORFEITED);
        assertThat(response.sessionId()).isNotEqualTo(expiredSessionId.toString());
        assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(withSourceStudyPackId(freshQuiz, studyPackId));
        verify(challengeQuizQuestionBankService).releaseClaims(userId, studyPackId, expiredSessionId);
    }

    @ParameterizedTest
    @CsvSource({"challenge", "board_exam"})
    void resolveExistingChallengeSession_doesNotForfeitACompletedSessionObservedBeforeTheLock(String mode) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity unlocked = activeChallengeSession(
                sessionId, userId, studyPackId, noteId, mode, QuickReviewSessionStatus.IN_PROGRESS
        );
        QuickReviewSessionEntity completed = activeChallengeSession(
                sessionId, userId, studyPackId, noteId, mode, QuickReviewSessionStatus.COMPLETED
        );

        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.of(unlocked));
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                eq(sessionId), eq(userId), eq(QuickReviewSessionMode.CHALLENGE)
        )).thenReturn(Optional.of(completed));

        Optional<ChallengeQuizStartResponse> resolved = resolveExistingChallengeSession(
                userId, studyPackId, studyPack, PlanType.FREE
        );

        assertThat(resolved).isEmpty();
        assertThat(completed.getStatus()).isEqualTo(QuickReviewSessionStatus.COMPLETED);
        verify(quickReviewSessionRepository, never()).save(completed);
        verify(challengeQuizQuestionBankService, never()).releaseClaims(userId, studyPackId, sessionId);
    }

    @Test
    void startSession_leavesGeneratingSessionUntouched_withoutCallingLlmAgain() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity generating = new QuickReviewSessionEntity();
        generating.setId(sessionId);
        generating.setUserId(userId);
        generating.setStudyPackId(studyPackId);
        generating.setNoteId(noteId);
        generating.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        generating.setStatus(QuickReviewSessionStatus.GENERATING);
        generating.setCurrentRound(QuickReviewRound.INITIAL);
        generating.setCurrentQuestionIndex(0);
        generating.setTotalQuestions(0);
        generating.setCorrectAnswers(0);
        generating.setScorePercentage(BigDecimal.ZERO);
        generating.setRetryCount(0);
        generating.setCreatedAt(OffsetDateTime.now().minusSeconds(30));
        generating.setSessionState(Map.of(
                "timeLimitSeconds", 600,
                "timerStartedAtEpochSeconds", 0L,
                "selectedChoices", Map.of(),
                "completed", false
        ));

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(Optional.of(generating));
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(generating));
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(10),
                        OffsetDateTime.now().plusDays(20),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.sessionId()).isEqualTo(sessionId.toString());
        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        assertThat(response.quiz()).isEmpty();
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(aiRateLimitService, never()).assertAllowed(any(), any(), any());
        verify(quizGenerationService, never()).generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        verify(userUsageService, never()).incrementChallengeQuizGeneration(any(UUID.class), any(OffsetDateTime.class));
    }

    @Test
    void startSession_reusesEligibleBankedQuestionsBeforeCallingLlm() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        List<QuizItem> bankedQuiz = buildQuizWithPrefix("Banked", DEFAULT_ADAPTIVE_QUESTION_COUNT);
        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, "Engineering", studyPack.getSubject(), List.of()
        );
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId), eq(QuickReviewSessionMode.CHALLENGE), any(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE, BillingCycle.MONTHLY, OffsetDateTime.now().minusDays(5), OffsetDateTime.now().plusDays(25), 2026, 3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);
        when(challengeQuizQuestionBankService.claimEligibleQuestions(
                eq(userId),
                eq(studyPackId),
                eq(LearnerLevel.COLLEGE),
                any(UUID.class),
                any(),
                eq(DEFAULT_ADAPTIVE_QUESTION_COUNT)
        )).thenReturn(bankedQuiz);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(withSourceStudyPackId(bankedQuiz, studyPackId));
        verify(quizGenerationService, never()).generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        verify(aiRateLimitService, never()).assertAllowed(any(), any(), any());
        verify(challengeQuizQuestionBankService, never()).persistGeneratedQuestions(any(), any(), any(), any(), any());
    }

    @Test
    void startSession_usesOfficialTemplateQuestionsBeforeCallingLlmAndKeepsLlmUsageNull() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        List<QuizItem> templateQuiz = buildQuizWithPrefix("Official", DEFAULT_ADAPTIVE_QUESTION_COUNT);
        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, "Engineering", studyPack.getSubject(), List.of()
        );
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId), eq(QuickReviewSessionMode.CHALLENGE), any(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE, BillingCycle.MONTHLY, OffsetDateTime.now().minusDays(5), OffsetDateTime.now().plusDays(25), 2026, 3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);
        when(officialChallengeQuizTemplateService.copyTemplateQuestions(
                eq(userId),
                eq(studyPackId),
                eq(LearnerLevel.COLLEGE),
                any(UUID.class),
                any(),
                eq(DEFAULT_ADAPTIVE_QUESTION_COUNT)
        )).thenReturn(templateQuiz);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(withSourceStudyPackId(templateQuiz, studyPackId));
        ArgumentCaptor<QuickReviewSessionEntity> sessionCaptor = ArgumentCaptor.forClass(QuickReviewSessionEntity.class);
        verify(quickReviewSessionRepository, atLeastOnce()).save(sessionCaptor.capture());
        QuickReviewSessionEntity savedSession = sessionCaptor.getAllValues().getLast();
        assertThat(savedSession.getModelUsed()).isNull();
        assertThat(savedSession.getInputTokens()).isNull();
        assertThat(savedSession.getOutputTokens()).isNull();
        assertThat(savedSession.getCachedInputTokens()).isNull();
        verify(quizGenerationService, never()).generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        verify(aiRateLimitService, never()).assertAllowed(any(), any(), any());
        verify(userUsageService).incrementChallengeQuizGeneration(eq(userId), any(OffsetDateTime.class));
        verify(challengeQuizQuestionBankService, never()).persistGeneratedQuestions(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "FREE, 20",
            "PLUS, 100",
            "PRO, 200"
    })
    void startSession_returnsRaisedMonthlyChallengeQuizLimitForEachPlan(
            PlanType planType,
            int expectedMonthlyLimit
    ) {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Engineering",
                studyPack.getSubject(),
                List.of(),
                null,
                LearnerLevel.SENIOR_HIGH
        );

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId))
                .thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId), eq(QuickReviewSessionMode.CHALLENGE), any(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(subscriptionService.resolvePlan(userId)).thenReturn(planType);
        stubChallengeUsagePeriod(userId, planType);
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);
        when(challengeQuizQuestionBankService.claimEligibleQuestions(
                eq(userId),
                eq(studyPackId),
                eq(LearnerLevel.SENIOR_HIGH),
                any(UUID.class),
                any(),
                eq(DEFAULT_ADAPTIVE_QUESTION_COUNT)
        )).thenReturn(buildQuizWithPrefix("Banked", DEFAULT_ADAPTIVE_QUESTION_COUNT));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                studyPackId.toString(),
                userId,
                null
        );

        assertThat(response.monthlyLimit()).isEqualTo(expectedMonthlyLimit);
    }

    @Test
    void startRedoMissedSession_usesIncorrectBankedQuestionsWithoutLlmOrQuotaIncrement() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Engineering",
                studyPack.getSubject(),
                List.of(),
                null,
                LearnerLevel.SENIOR_HIGH
        );
        List<QuizItem> missedQuestions = buildQuizWithPrefix("Missed", 3);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);
        when(challengeQuizQuestionBankService.claimIncorrectQuestions(
                eq(userId),
                eq(studyPackId),
                eq(LearnerLevel.SENIOR_HIGH),
                any(UUID.class),
                eq(REDO_MISSED_QUESTION_COUNT),
                eq(3)
        )).thenReturn(missedQuestions);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE, BillingCycle.MONTHLY, OffsetDateTime.now().minusDays(5), OffsetDateTime.now().plusDays(25), 2026, 3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId), eq(QuickReviewSessionMode.CHALLENGE), any(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(1L);
        when(quickReviewSessionRepository
                .countByUserIdAndSessionModeAndStatusInAndQuotaExemptTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        eq(userId),
                        eq(QuickReviewSessionMode.CHALLENGE),
                        any(),
                        any(OffsetDateTime.class),
                        any(OffsetDateTime.class)
                )).thenReturn(1L);

        ChallengeQuizStartResponse response = challengeQuizService.startRedoMissedSession(studyPackId.toString(), userId);

        assertThat(response.mode()).isEqualTo("challenge");
        assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(withSourceStudyPackId(missedQuestions, studyPackId));
        assertThat(response.usedThisMonth()).isZero();
        verify(quizGenerationService, never()).generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        verify(userUsageService, never()).incrementChallengeQuizGeneration(any(UUID.class), any(OffsetDateTime.class));
        org.mockito.ArgumentCaptor<QuickReviewSessionEntity> sessionCaptor = org.mockito.ArgumentCaptor.forClass(QuickReviewSessionEntity.class);
        verify(quickReviewSessionRepository, org.mockito.Mockito.times(2)).save(sessionCaptor.capture());
        QuickReviewSessionEntity savedRedoSession = sessionCaptor.getAllValues().getLast();
        assertThat(savedRedoSession.isQuotaExempt()).isTrue();
        assertThat(QuizSessionStateUtils.extractRedoMissedSource(savedRedoSession.getSessionState())).isTrue();
        verify(challengeQuizQuestionBankService).claimIncorrectQuestions(
                eq(userId),
                eq(studyPackId),
                eq(LearnerLevel.SENIOR_HIGH),
                any(UUID.class),
                eq(REDO_MISSED_QUESTION_COUNT),
                eq(ChallengeQuizQuestionBankService.MINIMUM_REDO_MISSED_QUESTIONS)
        );
    }

    @Test
    void startRedoMissedSession_reusesAnExistingRedoMissedSession() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity existing = new QuickReviewSessionEntity();
        existing.setId(sessionId);
        existing.setUserId(userId);
        existing.setStudyPackId(studyPackId);
        existing.setNoteId(noteId);
        existing.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        existing.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        existing.setCurrentRound(QuickReviewRound.INITIAL);
        existing.setCurrentQuestionIndex(0);
        existing.setTotalQuestions(1);
        existing.setCreatedAt(OffsetDateTime.now());
        existing.setSessionState(QuizSessionStateUtils.withRedoMissedSource(
                QuizSessionStateUtils.withQuiz(buildQuiz(1), Map.of("mode", "challenge")),
                true
        ));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.of(existing));
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(existing));
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE, BillingCycle.MONTHLY, OffsetDateTime.now().minusDays(5), OffsetDateTime.now().plusDays(25), 2026, 3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());

        ChallengeQuizStartResponse response = challengeQuizService.startRedoMissedSession(studyPackId.toString(), userId);

        assertThat(response.sessionId()).isEqualTo(sessionId.toString());
        verify(challengeQuizQuestionBankService, never()).claimIncorrectQuestions(any(), any(), any(), any(), anyInt(), anyInt());
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
    }

    @Test
    void startRedoMissedSession_resumesAGeneratingRedoMissedSessionInsteadOfForfeitingIt() {
        // Guards against a race where a second startRedoMissedSession call arrives while the first
        // call's session is still GENERATING: the marker must already be set on the very first save
        // (before claimIncorrectQuestions runs), or the second call would wrongly treat the first
        // call's own in-flight redo session as a stale ordinary session and forfeit it out from under it.
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity generating = new QuickReviewSessionEntity();
        generating.setId(sessionId);
        generating.setUserId(userId);
        generating.setStudyPackId(studyPackId);
        generating.setNoteId(noteId);
        generating.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        generating.setStatus(QuickReviewSessionStatus.GENERATING);
        generating.setCurrentRound(QuickReviewRound.INITIAL);
        generating.setCurrentQuestionIndex(0);
        generating.setTotalQuestions(0);
        generating.setCorrectAnswers(0);
        generating.setScorePercentage(BigDecimal.ZERO);
        generating.setRetryCount(0);
        generating.setCreatedAt(OffsetDateTime.now());
        generating.setSessionState(QuizSessionStateUtils.withRedoMissedSource(
                Map.of(
                        "timeLimitSeconds", 600,
                        "timerStartedAtEpochSeconds", 0L,
                        "selectedChoices", Map.of(),
                        "completed", false
                ),
                true
        ));

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.of(generating));
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(generating));
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE, BillingCycle.MONTHLY, OffsetDateTime.now().minusDays(5), OffsetDateTime.now().plusDays(25), 2026, 3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());

        ChallengeQuizStartResponse response = challengeQuizService.startRedoMissedSession(studyPackId.toString(), userId);

        assertThat(response.sessionId()).isEqualTo(sessionId.toString());
        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        assertThat(generating.getStatus()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        verify(challengeQuizQuestionBankService, never()).claimIncorrectQuestions(any(), any(), any(), any(), anyInt(), anyInt());
        verify(challengeQuizQuestionBankService, never()).releaseClaims(any(), any(), any());
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
    }

    @Test
    void startRedoMissedSession_forfeitsStaleOrdinarySessionAndStartsFreshWithMissedQuestions() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID staleSessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity staleOrdinarySession = activeChallengeSession(
                staleSessionId,
                userId,
                studyPackId,
                noteId,
                "challenge",
                QuickReviewSessionStatus.IN_PROGRESS
        );
        List<QuizItem> missedQuestions = buildQuizWithPrefix("Missed", 3);
        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Engineering",
                studyPack.getSubject(),
                List.of()
        );

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.of(staleOrdinarySession));
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                staleSessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(staleOrdinarySession));
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);
        when(challengeQuizQuestionBankService.claimIncorrectQuestions(
                eq(userId),
                eq(studyPackId),
                eq(LearnerLevel.COLLEGE),
                any(UUID.class),
                eq(REDO_MISSED_QUESTION_COUNT),
                eq(ChallengeQuizQuestionBankService.MINIMUM_REDO_MISSED_QUESTIONS)
        )).thenReturn(missedQuestions);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));
        stubChallengeUsagePeriod(userId, PlanType.FREE);

        ChallengeQuizStartResponse response = challengeQuizService.startRedoMissedSession(studyPackId.toString(), userId);

        assertThat(staleOrdinarySession.getStatus()).isEqualTo(QuickReviewSessionStatus.FORFEITED);
        assertThat(response.sessionId()).isNotEqualTo(staleSessionId.toString());
        assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(withSourceStudyPackId(missedQuestions, studyPackId));
        verify(quickReviewSessionRepository).save(staleOrdinarySession);
        verify(challengeQuizQuestionBankService).releaseClaims(userId, studyPackId, staleSessionId);
    }

    @Test
    void startRedoMissedSession_startsFreshWhenStaleOrdinaryForfeitSaveFails() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID staleSessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity staleOrdinarySession = activeChallengeSession(
                staleSessionId,
                userId,
                studyPackId,
                noteId,
                "challenge",
                QuickReviewSessionStatus.IN_PROGRESS
        );
        List<QuizItem> missedQuestions = buildQuizWithPrefix("Missed", 3);
        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Engineering",
                studyPack.getSubject(),
                List.of()
        );

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.of(staleOrdinarySession));
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                staleSessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(staleOrdinarySession));
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);
        when(challengeQuizQuestionBankService.claimIncorrectQuestions(
                eq(userId),
                eq(studyPackId),
                eq(LearnerLevel.COLLEGE),
                any(UUID.class),
                eq(REDO_MISSED_QUESTION_COUNT),
                eq(ChallengeQuizQuestionBankService.MINIMUM_REDO_MISSED_QUESTIONS)
        )).thenReturn(missedQuestions);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class))).thenAnswer(invocation -> {
            QuickReviewSessionEntity session = recordSession(invocation.getArgument(0));
            if (session == staleOrdinarySession) {
                throw new IllegalStateException("save failed");
            }
            return session;
        });
        stubChallengeUsagePeriod(userId, PlanType.FREE);

        ChallengeQuizStartResponse response = challengeQuizService.startRedoMissedSession(studyPackId.toString(), userId);

        assertThat(response.sessionId()).isNotEqualTo(staleSessionId.toString());
        assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(withSourceStudyPackId(missedQuestions, studyPackId));
        verify(challengeQuizQuestionBankService).releaseClaims(userId, studyPackId, staleSessionId);
    }

    @Test
    void startSession_combinesBankedQuestionsWithGeneratedShortfall() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        List<QuizItem> bankedQuiz = buildQuizWithPrefix("Banked", 3);
        List<QuizItem> generatedQuiz = buildQuizWithPrefix("Generated", DEFAULT_ADAPTIVE_QUESTION_COUNT - 3);
        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, "Engineering", studyPack.getSubject(), List.of()
        );
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId), eq(QuickReviewSessionMode.CHALLENGE), any(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE, BillingCycle.MONTHLY, OffsetDateTime.now().minusDays(5), OffsetDateTime.now().plusDays(25), 2026, 3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);
        when(challengeQuizQuestionBankService.claimEligibleQuestions(
                eq(userId),
                eq(studyPackId),
                eq(LearnerLevel.COLLEGE),
                any(UUID.class),
                any(),
                eq(DEFAULT_ADAPTIVE_QUESTION_COUNT)
        )).thenReturn(bankedQuiz);
        when(quizGenerationService.generateChallengeQuiz(
                eq(studyPack.getTitle()),
                eq(studyPack.getSummary()),
                eq(studyPack.getKeyConcepts()),
                any(),
                eq(DEFAULT_ADAPTIVE_QUESTION_COUNT - 3),
                any(),
                eq(generationContext)
        )).thenReturn(GeneratedChallengeQuizContent.withoutUsage(generatedQuiz));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.quiz()).hasSize(DEFAULT_ADAPTIVE_QUESTION_COUNT);
        assertThat(response.quiz()).containsAll(withSourceStudyPackId(bankedQuiz, studyPackId));
        assertThat(response.quiz()).containsAll(withSourceStudyPackId(generatedQuiz, studyPackId));
        verify(aiRateLimitService).assertAllowed(userId, PlanType.FREE, "challenge-quiz");
        verify(challengeQuizQuestionBankService).persistGeneratedQuestions(
                eq(userId), eq(studyPackId), any(UUID.class), eq(LearnerLevel.COLLEGE), eq(generatedQuiz)
        );
    }

    private StudyPackEntity buildStudyPack(UUID studyPackId, UUID noteId, UUID userId) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setNoteId(noteId);
        studyPack.setOwnerUserId(userId);
        studyPack.setTitle("Pack title");
        studyPack.setSummary("Summary");
        studyPack.setSubject("Nursing");
        studyPack.setKeyConcepts(List.of("Concept"));
        studyPack.setQuiz(List.of(
                new QuizItem("Practice?", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation")
        ));
        return studyPack;
    }

    private static List<QuizItem> withSourceStudyPackId(List<QuizItem> quiz, UUID studyPackId) {
        return quiz.stream()
                .map(item -> item.withSourceStudyPackId(studyPackId.toString()))
                .toList();
    }

    @ParameterizedTest
    @CsvSource({
            "40, easy, 10, false, true",
            "65, medium, 12, false, false",
            "85, hard, 15, true, false"
    })
    void startSession_usesScoreBasedAutomaticDifficultyAndQuestionCountForChallengeQuiz(
            int previousScorePercentage,
            String expectedDifficulty,
            int expectedQuestionCount,
            boolean mastered,
            boolean masteryLookupFails
    ) {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity previousQuickReview = new QuickReviewSessionEntity();
        previousQuickReview.setScorePercentage(BigDecimal.valueOf(previousScorePercentage));

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        if (masteryLookupFails) {
            when(studyPackQuizMasteryService.tryResolve(userId, studyPack))
                    .thenThrow(new IllegalStateException("mastery unavailable"));
        } else {
            when(studyPackQuizMasteryService.tryResolve(userId, studyPack)).thenReturn(Optional.of(
                    mastered ? StudyPackQuizMastery.masteredAt(OffsetDateTime.now()) : StudyPackQuizMastery.notMastered()
            ));
        }
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.QUICK_REVIEW),
                any()
        )).thenReturn(List.of(previousQuickReview));
        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Nursing",
                null,
                List.of()
        );
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);
        when(quizGenerationService.generateChallengeQuiz(
                "Pack title",
                "Summary",
                List.of("Concept"),
                List.of("Practice?"),
                expectedQuestionCount,
                expectedDifficulty,
                generationContext
        )).thenReturn(new GeneratedChallengeQuizContent(
                buildQuizWithPrefix("Adaptive", expectedQuestionCount),
                "gpt-4.1-mini",
                120,
                60,
                30
        ));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.sessionId()).isNotNull();
        assertThat(response.mode()).isEqualTo("challenge");
        assertThat(response.selectedDifficulty()).isEqualTo(expectedDifficulty);
        assertThat(response.quiz()).hasSize(expectedQuestionCount);
        assertThat(response.timeLimitSeconds()).isEqualTo(expectedQuestionCount * CHALLENGE_SECONDS_PER_QUESTION);
        ArgumentCaptor<QuickReviewSessionEntity> sessionCaptor = ArgumentCaptor.forClass(QuickReviewSessionEntity.class);
        verify(quickReviewSessionRepository, atLeastOnce()).save(sessionCaptor.capture());
        QuickReviewSessionEntity savedSession = sessionCaptor.getAllValues().getLast();
        assertThat(savedSession.getModelUsed()).isEqualTo("gpt-4.1-mini");
        assertThat(savedSession.getInputTokens()).isEqualTo(120);
        assertThat(savedSession.getOutputTokens()).isEqualTo(60);
        assertThat(savedSession.getCachedInputTokens()).isEqualTo(30);
        verify(aiRateLimitService).assertAllowed(userId, PlanType.FREE, "challenge-quiz");
        verify(generationContextResolver).resolveForStudyPack(userId, studyPack);
        verify(quizGenerationService, never()).generateBoardExamQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        verify(challengeQuizQuestionBankService).persistGeneratedQuestions(
                eq(userId), eq(studyPackId), any(UUID.class), eq(LearnerLevel.BOARD_EXAM_REVIEW), any()
        );
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.CHALLENGE_QUIZ_STARTED), eq(studyPackId), any());
        if (masteryLookupFails) {
            verify(analyticsService, never()).trackEvent(
                    eq(userId), eq(AnalyticsEventType.CHALLENGE_QUIZ_LAUNCHED_BEFORE_MASTERY), eq(studyPackId), any()
            );
            verify(analyticsService, never()).trackEvent(
                    eq(userId), eq(AnalyticsEventType.CHALLENGE_QUIZ_LAUNCHED_AFTER_MASTERY), eq(studyPackId), any()
            );
        } else {
            AnalyticsEventType expectedMasteryEvent = mastered
                    ? AnalyticsEventType.CHALLENGE_QUIZ_LAUNCHED_AFTER_MASTERY
                    : AnalyticsEventType.CHALLENGE_QUIZ_LAUNCHED_BEFORE_MASTERY;
            AnalyticsEventType excludedMasteryEvent = mastered
                    ? AnalyticsEventType.CHALLENGE_QUIZ_LAUNCHED_BEFORE_MASTERY
                    : AnalyticsEventType.CHALLENGE_QUIZ_LAUNCHED_AFTER_MASTERY;
            verify(analyticsService).trackEvent(eq(userId), eq(expectedMasteryEvent), eq(studyPackId), any());
            verify(analyticsService, never()).trackEvent(eq(userId), eq(excludedMasteryEvent), eq(studyPackId), any());
        }
    }

    @Test
    void startSession_shufflesQuestionOrderButKeepsMatchingBlockContiguous() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity previousQuickReview = new QuickReviewSessionEntity();
        previousQuickReview.setScorePercentage(BigDecimal.valueOf(65));

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId), eq(QuickReviewSessionMode.CHALLENGE), any(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.FREE, BillingCycle.MONTHLY, OffsetDateTime.now().minusDays(5), OffsetDateTime.now().plusDays(25), 2026, 3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.QUICK_REVIEW), any()
        )).thenReturn(List.of(previousQuickReview));
        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW, "Nursing", null, List.of()
        );
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);

        List<QuizItem> generatedQuiz = new java.util.ArrayList<>(List.of(
                new QuizItem("Match 1", List.of("A", "B", "C", "D"), 0, "Concept", "Explanation", null, "MATCHING", null, null, null, "group-1"),
                new QuizItem("Match 2", List.of("A", "B", "C", "D"), 1, "Concept", "Explanation", null, "MATCHING", null, null, null, "group-1"),
                new QuizItem("Match 3", List.of("A", "B", "C", "D"), 2, "Concept", "Explanation", null, "MATCHING", null, null, null, "group-1")
        ));
        generatedQuiz.addAll(buildQuizWithPrefix("Standalone", DEFAULT_ADAPTIVE_QUESTION_COUNT - 3));
        when(quizGenerationService.generateChallengeQuiz(
                "Pack title",
                "Summary",
                List.of("Concept"),
                List.of("Practice?"),
                DEFAULT_ADAPTIVE_QUESTION_COUNT,
                "medium",
                generationContext
        )).thenReturn(GeneratedChallengeQuizContent.withoutUsage(generatedQuiz));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        // shuffleQuestionOrderPreservingMatchingGroups groups the MATCHING block into a single unit before
        // shuffling, so a correct implementation always keeps it contiguous and in order on every draw — this
        // loop doesn't compute an escape-rate reduction against the current code. It exists to catch a future
        // regression to a flat/naive shuffle, where each of the 30 independently-randomized draws is another
        // chance to expose scattering (a single draw alone
        // would have a real chance of accidentally landing contiguous — the flat-shuffle regression it guards
        // is caught only across many draws, not the first one).
        for (int attempt = 0; attempt < 30; attempt++) {
            ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

            assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(withSourceStudyPackId(generatedQuiz, studyPackId));
            List<Integer> groupIndices = new java.util.ArrayList<>();
            for (int index = 0; index < response.quiz().size(); index++) {
                if ("group-1".equals(response.quiz().get(index).questionGroup())) {
                    groupIndices.add(index);
                }
            }
            assertThat(groupIndices).hasSize(3);
            assertThat(java.util.Collections.max(groupIndices) - java.util.Collections.min(groupIndices))
                    .as("MATCHING block must stay contiguous after shuffling")
                    .isEqualTo(2);
            List<String> orderedGroupQuestions = groupIndices.stream()
                    .sorted()
                    .map(index -> response.quiz().get(index).question())
                    .toList();
            assertThat(orderedGroupQuestions)
                    .as("MATCHING block must preserve its original internal order after shuffling")
                    .containsExactly("Match 1", "Match 2", "Match 3");
        }
    }

    @Test
    void shuffleQuestionOrderPreservingMatchingGroups_splitsSameLabelBlocksFromDifferentSourcesInsteadOfFailing() {
        // ⚠️ REGRESSION GUARD FOR A LIVE DEFECT THIS RELEASE INTRODUCED AND THEN FIXED.
        // challenge-quiz-developer.txt tells EVERY generation to label its matching block "group-1", and
        // generateChallengeQuizForSources appends sources back to back — so A's trailing block and B's
        // leading block share a label and sit adjacent. Detecting that and throwing turned a working
        // multi-note session into a hard failure AFTER both LLM calls were paid for. The block scan now
        // breaks on the source stamp, so the two blocks stay separate and the session survives.
        String sourceA = UUID.randomUUID().toString();
        String sourceB = UUID.randomUUID().toString();
        List<QuizItem> adjacentSameLabelDifferentSources = List.of(
                new QuizItem("A1", List.of("A", "B"), 0, "Concept", "Explanation", null,
                        "MATCHING", null, null, null, "group-1").withSourceStudyPackId(sourceA),
                new QuizItem("A2", List.of("A", "B"), 1, "Concept", "Explanation", null,
                        "MATCHING", null, null, null, "group-1").withSourceStudyPackId(sourceA),
                new QuizItem("B1", List.of("A", "B"), 0, "Concept", "Explanation", null,
                        "MATCHING", null, null, null, "group-1").withSourceStudyPackId(sourceB),
                new QuizItem("B2", List.of("A", "B"), 1, "Concept", "Explanation", null,
                        "MATCHING", null, null, null, "group-1").withSourceStudyPackId(sourceB)
        );

        List<QuizItem> shuffled = ReflectionTestUtils.invokeMethod(
                challengeQuizService,
                "shuffleQuestionOrderPreservingMatchingGroups",
                adjacentSameLabelDifferentSources
        );

        assertThat(shuffled).hasSize(4);
        // Each source's pair stays contiguous and never interleaves with the other source's pair.
        List<String> sources = shuffled.stream().map(QuizItem::sourceStudyPackId).toList();
        assertThat(sources.get(0)).isEqualTo(sources.get(1));
        assertThat(sources.get(2)).isEqualTo(sources.get(3));
        assertThat(sources.get(0)).isNotEqualTo(sources.get(2));
    }

    @Test
    void shuffleQuestionOrderPreservingMatchingGroups_stillRejectsAGenuinelyMixedBlock() {
        // The guard remains as defence in depth: the block scan can no longer BUILD a mixed block, but if
        // a future change reintroduces one, provenance must fail loudly rather than be picked arbitrarily.
        List<QuizItem> mixedBlock = List.of(
                new QuizItem("A", List.of("A", "B"), 0, "Concept", "Explanation", null,
                        "MATCHING", null, null, null, "group-1").withSourceStudyPackId(UUID.randomUUID().toString()),
                new QuizItem("B", List.of("A", "B"), 1, "Concept", "Explanation", null,
                        "MATCHING", null, null, null, "group-1").withSourceStudyPackId(UUID.randomUUID().toString())
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                challengeQuizService,
                "assertMatchingGroupHasOneSourceStudyPack",
                mixedBlock
        )).isInstanceOf(MatchingQuestionGroupSourceMismatchException.class);
    }

    @Test
    void startSession_keepsBoardExamDifficultyFixedAtMixedWhileUsingStandardChallengeQuotaAndBoardCap() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.PRO,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Engineering",
                studyPack.getSubject(),
                studyPack.getTags() == null ? List.of() : List.of(studyPack.getTags())
        ));
        when(quizGenerationService.generateBoardExamQuiz(
                eq("Pack title"),
                eq("Summary"),
                eq(List.of("Concept")),
                eq(List.of("Practice?")),
                eq(12),
                eq("mixed"),
                any(StudyPackGenerationContext.class)
        )).thenReturn(List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q3", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q4", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q5", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q6", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q7", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q8", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q9", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q10", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q11", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q12", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation")
        ));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                studyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null)
        );

        // Quota is charged at START, before a single question exists — the meters below are asserted on the
        // start response on purpose.
        assertThat(response.mode()).isEqualTo("board_exam");
        assertThat(response.selectedDifficulty()).isEqualTo("mixed");
        assertThat(response.monthlyLimit()).isEqualTo(10);
        assertThat(response.usedThisMonth()).isEqualTo(1);
        assertThat(response.boardExamMonthlyLimit()).isEqualTo(10);
        assertThat(response.boardExamUsedThisMonth()).isEqualTo(1);

        QuickReviewSessionEntity persisted = completeDispatchedBoardExam(response);

        assertThat(persisted.getStatus()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(persistedQuiz(persisted)).hasSize(12);
        assertThat(persisted.getTotalQuestions()).isEqualTo(12);
        assertThat(persisted.getSessionState().get("timeLimitSeconds")).isEqualTo(12 * 60);
        // atLeastOnce: the asynchronous body resolves the context once per source in addition to the
        // start-time resolution that gates the ready question pool.
        verify(generationContextResolver, atLeastOnce()).resolveForStudyPack(userId, studyPack);
        verify(quizGenerationService, never()).generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        verify(userUsageService).incrementChallengeQuizGeneration(eq(userId), any(OffsetDateTime.class));
        verify(userUsageService).incrementBoardExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
    }

    @Test
    void startSession_usesReadyPoolForBoardExamWithoutLiveGeneration() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Engineering",
                studyPack.getSubject(),
                List.of(),
                null,
                LearnerLevel.BOARD_EXAM_REVIEW
        );

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.PRO,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);
        when(examQuestionPoolService.sampleQuestions(
                studyPackId,
                ExamQuestionPoolService.MODE_BOARD_EXAM,
                12,
                LearnerLevel.BOARD_EXAM_REVIEW
        )).thenReturn(Optional.of(buildQuiz(12)));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                studyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null)
        );

        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(response.mode()).isEqualTo("board_exam");
        assertThat(response.quiz()).hasSize(12);
        verify(aiRateLimitService, never()).assertAllowed(any(), any(), any());
        verify(quizGenerationService, never()).generateBoardExamQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        verify(userUsageService).incrementBoardExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
    }

    @Test
    void startSession_createsMultiNoteBoardExamWithSourceRefs() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID secondStudyPackId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        UUID thirdStudyPackId = UUID.randomUUID();
        UUID thirdNoteId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, primaryNoteId, userId);
        StudyPackEntity second = buildStudyPack(secondStudyPackId, secondNoteId, userId);
        second.setTitle("Second source");
        StudyPackEntity third = buildStudyPack(thirdStudyPackId, thirdNoteId, userId);
        third.setTitle("Third source");

        stubBoardExamStartDependencies(userId, primaryStudyPackId, primary);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(secondStudyPackId, userId)).thenReturn(Optional.of(second));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(thirdStudyPackId, userId)).thenReturn(Optional.of(third));
        when(generationContextResolver.resolveForStudyPack(eq(userId), any(StudyPackEntity.class))).thenReturn(new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Nursing",
                "Nursing",
                List.of()
        ));
        when(quizGenerationService.generateBoardExamQuiz(
                any(),
                any(),
                any(),
                any(),
                eq(10),
                eq("mixed"),
                any(StudyPackGenerationContext.class)
        )).thenReturn(
                buildQuizWithPrefix("Primary", 10),
                buildQuizWithPrefix("Second", 10),
                buildQuizWithPrefix("Third", 10)
        );
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primaryStudyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", List.of(
                        secondStudyPackId.toString(),
                        thirdStudyPackId.toString()
                ))
        );

        // The source plan is decided synchronously and is already on the GENERATING start response.
        assertThat(response.sourceNoteRefs())
                .extracting(LongExamSourceNoteRef::studyPackId)
                .containsExactly(primaryStudyPackId.toString(), secondStudyPackId.toString(), thirdStudyPackId.toString());
        assertThat(response.sourceNoteRefs())
                .extracting(LongExamSourceNoteRef::questionCount)
                .containsExactly(10, 10, 10);

        QuickReviewSessionEntity persisted = completeDispatchedBoardExam(response);
        List<QuizItem> quiz = persistedQuiz(persisted);

        assertThat(persisted.getStatus()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(quiz).hasSize(30);
        assertThat(quiz).filteredOn(item -> primaryStudyPackId.toString().equals(item.sourceStudyPackId())).hasSize(10);
        assertThat(quiz).filteredOn(item -> secondStudyPackId.toString().equals(item.sourceStudyPackId())).hasSize(10);
        assertThat(quiz).filteredOn(item -> thirdStudyPackId.toString().equals(item.sourceStudyPackId())).hasSize(10);
        verify(userUsageService).incrementBoardExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
        verify(examQuestionPoolService, never()).sampleQuestions(
                eq(primaryStudyPackId),
                eq(ExamQuestionPoolService.MODE_BOARD_EXAM),
                anyInt(),
                any()
        );
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.BOARD_EXAM_STARTED), eq(primaryStudyPackId), any());
    }

    @Test
    void startSession_deduplicatesDuplicateBoardExamSourceIds() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, primaryNoteId, userId);
        StudyPackEntity additional = buildStudyPack(additionalStudyPackId, additionalNoteId, userId);
        additional.setTitle("Additional source");

        stubBoardExamStartDependencies(userId, primaryStudyPackId, primary);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId)).thenReturn(Optional.of(additional));
        when(generationContextResolver.resolveForStudyPack(eq(userId), any(StudyPackEntity.class))).thenReturn(new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Nursing",
                "Nursing",
                List.of()
        ));
        when(quizGenerationService.generateBoardExamQuiz(
                any(),
                any(),
                any(),
                any(),
                eq(12),
                eq("mixed"),
                any(StudyPackGenerationContext.class)
        )).thenReturn(
                buildQuizWithPrefix("Primary", 12),
                buildQuizWithPrefix("Additional", 12)
        );
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primaryStudyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", List.of(
                        additionalStudyPackId.toString(),
                        additionalStudyPackId.toString()
                ))
        );

        assertThat(response.sourceNoteRefs()).hasSize(2);
        assertThat(response.sourceNoteRefs())
                .extracting(LongExamSourceNoteRef::questionCount)
                .containsExactly(12, 12);

        QuickReviewSessionEntity persisted = completeDispatchedBoardExam(response);

        assertThat(persistedQuiz(persisted)).hasSize(24);
        verify(userUsageService).incrementBoardExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
    }

    @Test
    void startSession_rejectsMultiNoteBoardExamWhenSubjectMismatches() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, primaryNoteId, userId);
        StudyPackEntity additional = buildStudyPack(additionalStudyPackId, additionalNoteId, userId);
        additional.setSubject("Engineering");

        stubBoardExamStartDependencies(userId, primaryStudyPackId, primary);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId)).thenReturn(Optional.of(additional));

        String id = primaryStudyPackId.toString();
        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest(
                "board_exam",
                List.of(additionalStudyPackId.toString())
        );

        assertThatThrownBy(() -> challengeQuizService.startSession(id, userId, request))
                .isInstanceOf(InvalidBoardExamSourceException.class)
                .hasMessage("All notes must share the same subject");
    }

    @Test
    void startSession_rejectsMultiNoteBoardExamWhenPrimarySubjectIsBlank() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, primaryNoteId, userId);
        primary.setSubject(" ");

        stubBoardExamStartDependencies(userId, primaryStudyPackId, primary);

        String id = primaryStudyPackId.toString();
        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest(
                "board_exam",
                List.of(additionalStudyPackId.toString())
        );

        assertThatThrownBy(() -> challengeQuizService.startSession(id, userId, request))
                .isInstanceOf(InvalidBoardExamSourceException.class)
                .hasMessage("Add a subject to this note before adding more sources");
    }

    @Test
    void startSession_boardExamFromAReviewSetAcceptsMixedSubjectMembers() {
        // ⚠️ SUPERSEDES startSession_boardExamFromAPlanAcceptsMixedSubjectMembers. Its property — a plan's
        // own notes are never refused for their subject — survives v0.106.0 and is now structural: the
        // Review Set pool is built from collection MEMBERSHIP and reads no subject at all. This test fails
        // the moment anyone reintroduces a subject predicate on the Review Set path.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 3, 3);
        packsByPlan.get(0).forEach(pack -> pack.setSubject("Biology"));
        packsByPlan.get(1).forEach(pack -> pack.setSubject("Engineering Mathematics"));
        StudyPackEntity primary = packsByPlan.get(0).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primary.getId().toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString())
        );

        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        assertThat(response.sourceNoteRefs()).hasSize(6);
        assertThat(planIndexesOf(response.sourceNoteRefs(), packsByPlan)).contains(0, 1);
    }

    @Test
    void startSession_boardExamFromAReviewSetRejectsACallerSuppliedNoteList() {
        // ⚠️ SUPERSEDES startSession_boardExamFromAPlanStillRejectsANonMemberOfADifferentSubject. The old
        // guard rejected a smuggled outsider per source; v0.106.0 makes smuggling impossible by
        // construction, because the pool is derived from the Review Set and never from the request. This
        // asserts the stronger property directly, so re-wiring request-supplied ids into the Review Set
        // path fails here.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 3, 3);
        StudyPackEntity primary = packsByPlan.get(0).get(0);
        UUID outsiderStudyPackId = UUID.randomUUID();
        StudyPackEntity outsider = buildStudyPack(outsiderStudyPackId, UUID.randomUUID(), userId);
        outsider.setSubject("Engineering Mathematics");

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        lenient().when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(outsiderStudyPackId, userId))
                .thenReturn(Optional.of(outsider));
        stubReviewSetBoardExamGeneration(userId);

        // ⚠️ STRENGTHENED: the request is now REJECTED, not silently narrowed. A Review Set exam is sampled
        // by the server, so a supplied note list has no meaning — and silently discarding it was worse than
        // cosmetic: sending that list was the ONLY way the client ever set sourceCollectionId, so the single
        // route into this capability was the one that threw the learner's selection away.
        assertThatThrownBy(() -> challengeQuizService.startSession(
                primary.getId().toString(),
                userId,
                new ChallengeQuizStartRequest(
                        "board_exam",
                        List.of(outsiderStudyPackId.toString()),
                        reviewSetId.toString()
                )
        )).isInstanceOf(InvalidBoardExamSourceException.class);

        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
    }

    @Test
    void startSession_boardExamSpreadsAcrossSubjectPlansInsteadOfExhaustingTheFirst() {
        // ⚠️ THE POOL MUST BE OVERSUBSCRIBED OR THIS TEST PROVES NOTHING. With 22 eligible notes and a
        // sample limit of 10 (30 questions / 3 per source), a flat draw would routinely miss the two small
        // Subject Plans entirely; a stratified draw exhausts them BOTH on every single draw, because
        // round-robin visits every bucket before taking a second source from any of them.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 18, 2, 2);
        StudyPackEntity primary = packsByPlan.get(0).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);

        // Every start draws a fresh random session id, so this repeats: stratification is a property of
        // every draw, not of a lucky one.
        for (int attempt = 0; attempt < 5; attempt++) {
            ChallengeQuizStartResponse response = challengeQuizService.startSession(
                    primary.getId().toString(),
                    userId,
                    new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString())
            );

            List<Integer> planIndexes = planIndexesOf(response.sourceNoteRefs(), packsByPlan);
            assertThat(response.sourceNoteRefs()).hasSize(10);
            assertThat(planIndexes).as("attempt %s must reach every Subject Plan", attempt).contains(0, 1, 2);
            assertThat(planIndexes.stream().filter(index -> index == 1).count())
                    .as("attempt %s must exhaust the small plan rather than the large one", attempt)
                    .isEqualTo(2);
            assertThat(planIndexes.stream().filter(index -> index == 2).count())
                    .as("attempt %s must exhaust the second small plan too", attempt)
                    .isEqualTo(2);
        }
    }

    @Test
    void startSession_boardExamSamplesDeterministicallyFromTheSessionIdItPersists() {
        // Determinism is only useful if the seed is the id the session is actually STORED under — a sample
        // seeded from a throwaway UUID is equally "deterministic" and completely unreproducible.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 18, 2, 2);
        StudyPackEntity primary = packsByPlan.get(0).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primary.getId().toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString())
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LongExamPlanSourceSampler.EligiblePlanSource>> poolCaptor =
                ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<UUID> seedCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(longExamPlanSourceSampler).sample(poolCaptor.capture(), eq(primary.getId()), eq(10), seedCaptor.capture());

        assertThat(seedCaptor.getValue())
                .as("the sample must be seeded from the session id the row is persisted under")
                .isEqualTo(UUID.fromString(response.sessionId()));

        List<String> first = new LongExamPlanSourceSampler()
                .sample(poolCaptor.getValue(), primary.getId(), 10, seedCaptor.getValue()).stream()
                .map(source -> source.studyPack().getId().toString())
                .toList();
        List<String> second = new LongExamPlanSourceSampler()
                .sample(poolCaptor.getValue(), primary.getId(), 10, seedCaptor.getValue()).stream()
                .map(source -> source.studyPack().getId().toString())
                .toList();
        assertThat(second).isEqualTo(first);
        assertThat(response.sourceNoteRefs()).extracting(LongExamSourceNoteRef::studyPackId).isEqualTo(first);
    }

    @Test
    void startSession_boardExamFromAChildlessReviewSetUsesItsOwnItemsAsOneStratum() {
        // Mirrors the Goal endpoint rule: child plans win, and a Review Set with no children is itself the
        // single stratum. Drop that fallback and the pool is empty, so this start throws instead.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, true, 6);
        StudyPackEntity primary = packsByPlan.get(0).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primary.getId().toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString())
        );

        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        assertThat(response.sourceNoteRefs()).hasSize(6);
        assertThat(response.sourceNoteRefs())
                .extracting(LongExamSourceNoteRef::studyPackId)
                .containsExactlyInAnyOrderElementsOf(
                        packsByPlan.get(0).stream().map(pack -> pack.getId().toString()).toList()
                );
    }

    @Test
    void startSession_boardExamPoolExcludesNotesWhoseStudyPackIsNotReady() {
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 4, 4);
        StudyPackEntity primary = packsByPlan.get(0).get(0);
        // Three notes have no READY Study Pack; they are Review Set members but not eligible material.
        StudyPackEntity needsConfirmation = packsByPlan.get(0).get(3);
        needsConfirmation.setStatus(StudyPackStatus.NEEDS_CONFIRMATION);
        StudyPackEntity failed = packsByPlan.get(1).get(2);
        failed.setStatus(StudyPackStatus.FAILED);
        StudyPackEntity alsoUnready = packsByPlan.get(1).get(3);
        alsoUnready.setStatus(StudyPackStatus.NEEDS_CONFIRMATION);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primary.getId().toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString())
        );

        assertThat(response.sourceNoteRefs()).hasSize(5);
        assertThat(response.sourceNoteRefs())
                .extracting(LongExamSourceNoteRef::studyPackId)
                .doesNotContain(
                        needsConfirmation.getId().toString(),
                        failed.getId().toString(),
                        alsoUnready.getId().toString()
                );
    }

    @Test
    void startSession_boardExamRejectsAReviewSetWithTooFewReadyNotesWithoutChargingOrCreatingASession() {
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 3);
        StudyPackEntity primary = packsByPlan.get(0).get(0);
        // Only the primary is ready — below the configured floor of two contributing sources.
        packsByPlan.get(0).get(1).setStatus(StudyPackStatus.NEEDS_CONFIRMATION);
        packsByPlan.get(0).get(2).setStatus(StudyPackStatus.FAILED);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);

        String id = primary.getId().toString();
        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString());

        assertThatThrownBy(() -> challengeQuizService.startSession(id, userId, request))
                .isInstanceOf(BoardExamInsufficientEligibleSourcesException.class);

        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(userUsageService, never()).incrementChallengeQuizGeneration(any(UUID.class), any(OffsetDateTime.class));
        verify(userUsageService, never()).incrementBoardExamGenerationBy(any(UUID.class), anyInt(), any(OffsetDateTime.class));
    }

    @Test
    void startSession_boardExamSizesItselfFromTheConfiguredTargetQuestionCountNotALiteral() {
        // ⚠️ A NON-DEFAULT TARGET IS THE WHOLE POINT. The shipped default is 30, so any assertion taken on
        // defaults passes just as well against a hardcoded 30 and proves nothing about configurability.
        properties.getPricing().setBoardExamTargetQuestionCount(18);
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 10, 10);
        StudyPackEntity primary = packsByPlan.get(0).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primary.getId().toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString())
        );

        // 18 questions / 3 minimum per source caps the sample at 6, and the 18 questions are spread over it.
        assertThat(response.sourceNoteRefs()).hasSize(6);
        assertThat(response.sourceNoteRefs()).extracting(LongExamSourceNoteRef::questionCount)
                .containsExactly(3, 3, 3, 3, 3, 3);
        verify(longExamPlanSourceSampler).sample(anyList(), eq(primary.getId()), eq(6), any(UUID.class));
    }

    @Test
    void startSession_boardExamAboveTheAssemblyFloorsShipsShortAndSaysSo() {
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 3, 3);
        StudyPackEntity primary = packsByPlan.get(0).get(0);
        StudyPackEntity survivor = packsByPlan.get(1).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);
        // Four of six sources fail their fan-out: 2 contributing sources and 10 questions survive, exactly
        // the configured floors, so the exam still ships — marked short.
        stubBoardExamSourceOutcomes(Set.of(primary.getTitle(), survivor.getTitle()));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primary.getId().toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString())
        );
        QuickReviewSessionEntity persisted = completeDispatchedBoardExam(response);

        assertThat(persisted.getStatus()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(persistedQuiz(persisted)).hasSize(10);
        assertThat(persisted.getSessionState()).containsEntry("shortExam", true);
        assertThat(persisted.getSessionState()).containsEntry("expectedQuestionCount", 30);
        verify(userUsageService, never()).reverseBoardExamGenerationBy(any(UUID.class), anyInt(), any(OffsetDateTime.class));
    }

    @Test
    void startSession_boardExamBelowTheAssemblyFloorsFailsAndReversesBothMeters() {
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 3, 3);
        StudyPackEntity primary = packsByPlan.get(0).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);
        // Only the primary contributes: 5 questions and 1 source, under both floors.
        stubBoardExamSourceOutcomes(Set.of(primary.getTitle()));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primary.getId().toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString())
        );
        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        dispatchedTask.run();

        QuickReviewSessionEntity persisted = savedSessionsById.get(UUID.fromString(response.sessionId()));
        assertThat(persisted.getStatus()).isEqualTo(QuickReviewSessionStatus.FAILED);
        verify(userUsageService).reverseBoardExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
    }

    @Test
    void startSession_boardExamGenerationFailureReversesBothMetersEndToEnd() {
        // ⚠️ DRIVEN THROUGH A REAL START, never by calling the reversal helper. The refund lives inside
        // GenerationRecoveryRowWriter, so a mocked row writer would leave only
        // verify(rowWriter).failBoardExamSession(id) — an assertion that keeps passing after the two-meter
        // reversal is deleted outright.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 3, 3);
        StudyPackEntity primary = packsByPlan.get(0).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);
        lenient().when(quizGenerationService.generateBoardExamQuiz(
                any(), any(), any(), any(), anyInt(), any(), any(StudyPackGenerationContext.class)
        )).thenThrow(new IllegalStateException("llm unavailable"));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primary.getId().toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString())
        );

        // Both meters were charged at start...
        verify(userUsageService).incrementChallengeQuizGeneration(eq(userId), any(OffsetDateTime.class));
        verify(userUsageService).incrementBoardExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));

        dispatchedTask.run();

        QuickReviewSessionEntity persisted = savedSessionsById.get(UUID.fromString(response.sessionId()));
        assertThat(persisted.getStatus()).isEqualTo(QuickReviewSessionStatus.FAILED);
        // ...and one call reverses BOTH of them, under a single stamp.
        verify(userUsageService).reverseBoardExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
        assertThat(persisted.getSessionState())
                .containsEntry(ChallengeQuizService.SESSION_STATE_BOARD_EXAM_QUOTA_REVERSED, true);
    }

    @Test
    void startSession_boardExamTakesTheRowLockBeforeReReadingTheStatusItDecidesOn() {
        // ⚠️ InOrder, NOT two plain verifies. Asynchronous generation may finish long after a sweeper or a
        // second request touched the row, so the GENERATING re-read is only meaningful while the row is
        // held. Reading the status first and locking afterwards leaves exactly the window this ordering
        // closes — and two independent verify() calls pass happily in either order.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 3, 3);
        StudyPackEntity primary = packsByPlan.get(0).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primary.getId().toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString())
        );
        UUID sessionId = UUID.fromString(response.sessionId());
        dispatchedTask.run();

        InOrder order = inOrder(quickReviewSessionRepository);
        order.verify(quickReviewSessionRepository).findByIdForUpdate(sessionId);
        order.verify(quickReviewSessionRepository).findStatusById(sessionId);
    }

    @Test
    void startSession_boardExamAsksOnlyForMultipleChoiceQuestions() {
        // Board Exam stays MCQ-only: it may reach the Board Exam generator and nothing else. The schema half
        // of this guard lives in OpenAiLlmStudyPackServiceTest, which asserts the request that generator
        // sends enables no alternative question format.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 3, 3);
        StudyPackEntity primary = packsByPlan.get(0).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primary.getId().toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString())
        );
        QuickReviewSessionEntity persisted = completeDispatchedBoardExam(response);

        assertThat(persistedQuiz(persisted)).isNotEmpty();
        assertThat(persistedQuiz(persisted)).allSatisfy(item -> {
            assertThat(item.getChoices()).hasSize(4);
            assertThat(item.getQuestionFormat()).isNull();
            assertThat(item.getCorrectIndices()).isNullOrEmpty();
            assertThat(item.getAcceptableAnswers()).isNullOrEmpty();
            assertThat(item.getAcceptableAnswerGroups()).isNullOrEmpty();
        });
        verify(quizGenerationService, never()).generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        verify(quizGenerationService, never()).generateMoreChallengeQuiz(any(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void startSession_multiNoteChallengeStaysSynchronousAndUnaffectedByTheBoardExamAsyncPath() {
        // ⚠️ REGRESSION GUARD. Board Exam and multi-note Challenge share resolveBoardExamSourceNoteRefs, and
        // Board Exam moved off the request transaction in v0.106.0. A Challenge Quiz must NOT follow it: it
        // still returns a ready session and dispatches no generation task.
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, primaryNoteId, userId);
        StudyPackEntity additional = buildStudyPack(additionalStudyPackId, additionalNoteId, userId);

        stubMultiNoteChallengeStart(userId, primaryStudyPackId, primary, additionalStudyPackId, additional, PlanType.FREE);
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(primaryNoteId, additionalNoteId)));
        when(generationContextResolver.resolveForStudyPack(eq(userId), any(StudyPackEntity.class)))
                .thenReturn(new StudyPackGenerationContext(
                        LearnerLevel.COLLEGE, "Engineering", primary.getSubject(), List.of()
                ));
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(), eq(9), any(), any()))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Primary", 9)))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Additional", 9)));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primaryStudyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("challenge", List.of(additionalStudyPackId.toString()), collectionId.toString())
        );

        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(response.quiz()).hasSize(18);
        assertThat(response.maxSourceNotes()).isEqualTo(3);
        assertThat(response.mode()).isEqualTo("challenge");
        assertThat(dispatchedTask).as("a Challenge Quiz must never dispatch asynchronous generation").isNull();
        verify(studyPackGenerationTaskDispatcher, never()).execute(any(Runnable.class));
        // The Review Set walk is Board Exam's alone; Challenge keeps the plan-membership verifier.
        verify(noteCollectionRepository, never()).findByIdAndOwnerUserId(any(UUID.class), any(UUID.class));
        verify(longExamPlanSourceSampler, never()).sample(anyList(), any(UUID.class), anyInt(), any(UUID.class));
        verify(userUsageService, never()).incrementBoardExamGenerationBy(any(UUID.class), anyInt(), any(OffsetDateTime.class));
    }

    @Test
    void startSession_rejectsBoardExamWithMoreThanTwoAdditionalSources() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, primaryNoteId, userId);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId)).thenReturn(Optional.of(primary));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(primaryStudyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(Optional.empty());

        String id = primaryStudyPackId.toString();
        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest(
                "board_exam",
                List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString())
        );

        assertThatThrownBy(() -> challengeQuizService.startSession(id, userId, request))
                .isInstanceOf(InvalidBoardExamSourceException.class)
                .hasMessage("Too many notes selected for the available question count — remove one");
    }

    @Test
    void getInProgressSession_returnsBoardExamSourceRefsFromSessionState() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        session.setCurrentQuestionIndex(0);
        session.setSessionState(Map.of(
                "mode", "board_exam",
                "difficulty", "mixed",
                "completed", false,
                "sourceNoteRefs", List.of(
                        Map.of("studyPackId", studyPackId.toString(), "noteId", noteId.toString(), "noteTitle", "Primary", "questionCount", 6),
                        Map.of("studyPackId", UUID.randomUUID().toString(), "noteId", UUID.randomUUID().toString(), "noteTitle", "Second", "questionCount", 6)
                )
        ));

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(Optional.of(session));

        ChallengeQuizStartResponse response = challengeQuizService.getInProgressSession(studyPackId.toString(), userId);

        assertThat(response.sourceNoteRefs()).hasSize(2);
        assertThat(response.sourceNoteRefs())
                .extracting(LongExamSourceNoteRef::questionCount)
                .containsExactly(6, 6);
    }

    @Test
    void startSession_blocksBoardExamWhenBoardExamHardCapReached() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.PRO,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        10
                ));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);

        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest("board_exam", null);
        String studyPackIdRaw = studyPackId.toString();
        assertThatThrownBy(() -> challengeQuizService.startSession(studyPackIdRaw, userId, request))
                .isInstanceOf(MonthlyBoardExamLimitReachedException.class);

        verify(aiRateLimitService, never()).assertAllowed(any(), any(), any());
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(userUsageService, never()).incrementBoardExamGenerationBy(any(UUID.class), anyInt(), any(OffsetDateTime.class));
    }

    @Test
    void startSession_allowsMultiNoteBoardExamWhenOneQuotaUnitRemains() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        StudyPackEntity additionalStudyPack = buildStudyPack(additionalStudyPackId, additionalNoteId, userId);
        additionalStudyPack.setTitle("Additional source");

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId))
                .thenReturn(Optional.of(additionalStudyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.PRO,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(new UserUsageService.MonthlyUsage(
                OffsetDateTime.now().minusDays(5),
                OffsetDateTime.now().plusDays(25),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                9
        ));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(generationContextResolver.resolveForStudyPack(eq(userId), any(StudyPackEntity.class))).thenReturn(new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Nursing",
                "Nursing",
                List.of()
        ));
        when(quizGenerationService.generateBoardExamQuiz(
                any(),
                any(),
                any(),
                any(),
                eq(12),
                eq("mixed"),
                any(StudyPackGenerationContext.class)
        )).thenReturn(
                buildQuizWithPrefix("Primary", 12),
                buildQuizWithPrefix("Additional", 12)
        );
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                studyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", List.of(additionalStudyPackId.toString()))
        );

        assertThat(response.boardExamUsedThisMonth()).isEqualTo(10);
        assertThat(response.sourceNoteRefs())
                .extracting(LongExamSourceNoteRef::questionCount)
                .containsExactly(12, 12);

        QuickReviewSessionEntity persisted = completeDispatchedBoardExam(response);

        assertThat(persisted.getStatus()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(persistedQuiz(persisted)).hasSize(24);
        verify(userUsageService).incrementBoardExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
    }

    @Test
    void startSession_blocksBoardExamWhenSharedChallengeQuizBudgetReached() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(200L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.PRO,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);

        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest("board_exam", null);
        String studyPackIdRaw = studyPackId.toString();
        assertThatThrownBy(() -> challengeQuizService.startSession(studyPackIdRaw, userId, request))
                .isInstanceOf(MonthlyChallengeQuizLimitReachedException.class);

        verify(aiRateLimitService, never()).assertAllowed(any(), any(), any());
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(userUsageService, never()).incrementBoardExamGenerationBy(any(UUID.class), anyInt(), any(OffsetDateTime.class));
    }

    @Test
    void startSession_mockModeCompletesBoardExamGenerationWithoutCallingRealLlm() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        StudySnapProperties properties = new StudySnapProperties();
        properties.getQuizGeneration().setMode("mock");
        ChallengeQuizService mockModeChallengeQuizService = new ChallengeQuizService(
                studyPackRepository,
                userRepository,
                noteRepository,
                planSourcedExamVerifier,
                quickReviewSessionRepository,
                new QuizGenerationService(llmStudyPackService, properties),
                subscriptionService,
                properties,
                userUsageService,
                billingUsagePeriodService,
                authService,
                analyticsService,
                aiRateLimitService,
                activityTrackingService,
                generationContextResolver,
                examQuestionPoolService,
                challengeQuizQuestionBankService,
                officialChallengeQuizTemplateService,
                conceptHealthService,
                studyPackQuizMasteryService,
                studyPackGenerationTaskDispatcher,
                studyPackGenerationTransactionOperations,
                generationRecoveryRowWriter,
                noteCollectionRepository,
                noteCollectionItemRepository,
                longExamPlanSourceSampler
        );

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.PRO,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Nursing",
                studyPack.getSubject(),
                studyPack.getTags() == null ? List.of() : List.of(studyPack.getTags())
        ));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = mockModeChallengeQuizService.startSession(
                studyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null)
        );

        assertThat(response.mode()).isEqualTo("board_exam");
        assertThat(response.selectedDifficulty()).isEqualTo("mixed");

        QuickReviewSessionEntity persisted = completeDispatchedBoardExam(response);

        assertThat(persisted.getStatus()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(persistedQuiz(persisted)).hasSize(12);
        verify(llmStudyPackService, never()).generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void getSessionReview_returnsStoredQuizAndDerivesConceptBreakdownWhenMetadataMissing() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setTotalQuestions(3);
        session.setCorrectAnswers(2);
        session.setScorePercentage(BigDecimal.valueOf(66.67));
        session.setRetryCount(0);
        session.setCreatedAt(OffsetDateTime.now().minusMinutes(12));
        session.setCompletedAt(OffsetDateTime.now().minusMinutes(2));
        session.setSessionMetadata(null);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                List.of(
                        new QuizItem("Question 1", List.of("A", "B", "C", "D"), "A", "Cells", "Explanation 1"),
                        new QuizItem("Question 2", List.of("A", "B", "C", "D"), "B", "Cells", "Explanation 2"),
                        new QuizItem("Question 3", List.of("A", "B", "C", "D"), "D", "Genetics", "Explanation 3")
                ),
                Map.of(
                        "selectedChoices",
                        Map.of(
                                "0", "A",
                                "1", "A",
                                "2", "D"
                        )
                )
        ));

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId,
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));

        QuizSessionReviewResponse response = challengeQuizService.getSessionReview(
                studyPackId.toString(),
                sessionId.toString(),
                userId
        );

        assertThat(response.quiz()).hasSize(3);
        assertThat(response.selectedChoices()).containsEntry(0, 0).containsEntry(1, 0).containsEntry(2, 3);
        assertThat(response.conceptBreakdown())
                .extracting(stat -> stat.concept() + ":" + stat.correctAnswers() + "/" + stat.totalQuestions())
                .containsExactly("Cells:1/2", "Genetics:1/1");
        assertThat(response.weakConcepts()).containsExactly("Cells");
    }

    @Test
    void getSessionReview_prefersPersistedConceptBreakdownAndWeakConceptsWhenAvailable() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setTotalQuestions(1);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO);
        session.setRetryCount(0);
        session.setCreatedAt(OffsetDateTime.now().minusMinutes(8));
        session.setCompletedAt(OffsetDateTime.now().minusMinutes(1));
        session.setSessionMetadata(Map.of(
                "conceptBreakdown",
                List.of(Map.of(
                        "concept", "Respiration",
                        "correctAnswers", 0,
                        "totalQuestions", 1,
                        "accuracyPercentage", 0
                )),
                "weakConcepts",
                List.of("Respiration")
        ));
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                List.of(new QuizItem("Question 1", List.of("A", "B", "C", "D"), "A", "Respiration", "Explanation")),
                Map.of("selectedChoices", Map.of("0", "B"))
        ));

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId,
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));

        QuizSessionReviewResponse response = challengeQuizService.getSessionReview(
                studyPackId.toString(),
                sessionId.toString(),
                userId
        );

        assertThat(response.conceptBreakdown())
                .extracting(stat -> stat.concept() + ":" + stat.correctAnswers() + "/" + stat.totalQuestions())
                .containsExactly("Respiration:0/1");
        assertThat(response.weakConcepts()).containsExactly("Respiration");
    }

    private static final String MODE_CHALLENGE_VALUE = "challenge";
    private static final String MODE_BOARD_EXAM_VALUE = "board_exam";

    private void stubCompletableSession(UUID userId, UUID studyPackId, UUID sessionId, String mode) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(UUID.randomUUID());
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(2);
        session.setCurrentQuestionIndex(1);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        Map<String, Object> state = new java.util.HashMap<>(QuizSessionStateUtils.withQuiz(
                List.of(
                        new QuizItem("Question 1", List.of("A", "B", "C", "D"), "A", "Concept 1", "Explanation"),
                        new QuizItem("Question 2", List.of("A", "B", "C", "D"), "B", "Concept 2", "Explanation")
                ),
                Map.of("selectedChoices", Map.of("0", "A", "1", "C"), "completed", false)
        ));
        state.put("mode", mode);
        session.setSessionState(state);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId,
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));
    }

    @Test
    void completeSession_recordsChallengeQuizActivity() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(2);
        session.setCurrentQuestionIndex(1);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                List.of(
                        new QuizItem("Question 1", List.of("A", "B", "C", "D"), "A", "Concept 1", "Explanation"),
                        new QuizItem("Question 2", List.of("A", "B", "C", "D"), "B", "Concept 2", "Explanation")
                ),
                Map.of(
                        "selectedChoices", Map.of("0", "A", "1", "C"),
                        "completed", false
                )
        ));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId,
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        challengeQuizService.completeSession(
                sessionId.toString(),
                userId,
                new ChallengeQuizCompleteRequest(1, 2, 120)
        );

        verify(activityTrackingService).recordActivity(userId, ActivityType.COMPLETED_CHALLENGE_QUIZ, studyPackId);
        verify(challengeQuizQuestionBankService).updateOutcomesAndReleaseClaims(
                eq(userId), eq(studyPackId), eq(sessionId), any(), any(), any(), any(), any()
        );
    }

    @Test
    void completeSession_emitsChallengeQuizCompletedWithItsFullMetadataMap() {
        // ⚠️ CHALLENGE_QUIZ_COMPLETED existed in the enum and was fired from NOWHERE before v0.102.0 —
        // enum membership is not instrumentation. The metadata MAP is asserted rather than "trackEvent was
        // called", because a payload nothing reads is the same dead end one level down.
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        stubCompletableSession(userId, studyPackId, sessionId, MODE_CHALLENGE_VALUE);

        challengeQuizService.completeSession(
                sessionId.toString(),
                userId,
                new ChallengeQuizCompleteRequest(1, 2, 120)
        );

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(analyticsService).trackEvent(
                eq(userId),
                eq(AnalyticsEventType.CHALLENGE_QUIZ_COMPLETED),
                eq(studyPackId),
                metadata.capture()
        );
        assertThat(metadata.getValue())
                .containsEntry("sessionId", sessionId.toString())
                .containsEntry("questionCount", 2)
                .containsKey("scorePercentage")
                // ⚠️ ONE, not zero. buildInitialSessionState only persists sourceNoteRefs above one
                // source, so the naive read reported 0 for every single-source session — and the first
                // version of this test PINNED that 0, which would have made the fix look like a
                // regression. A single-source session drew from exactly one note.
                .containsEntry("sourceCount", 1);
        verify(analyticsService, never())
                .trackEvent(any(), eq(AnalyticsEventType.BOARD_EXAM_COMPLETED), any(), any());
    }

    @Test
    void completeSession_emitsBoardExamCompletedWhenTheSessionIsABoardExam() {
        // ⚠️ The event is chosen by the completed session's MODE. One method completes both, so a
        // one-character change to that ternary would silently attribute every Board Exam to the Challenge
        // funnel — which is why both directions are pinned rather than just the new value existing.
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        stubCompletableSession(userId, studyPackId, sessionId, MODE_BOARD_EXAM_VALUE);

        challengeQuizService.completeSession(
                sessionId.toString(),
                userId,
                new ChallengeQuizCompleteRequest(1, 2, 120)
        );

        verify(analyticsService).trackEvent(
                eq(userId),
                eq(AnalyticsEventType.BOARD_EXAM_COMPLETED),
                eq(studyPackId),
                any()
        );
        verify(analyticsService, never())
                .trackEvent(any(), eq(AnalyticsEventType.CHALLENGE_QUIZ_COMPLETED), any(), any());
    }

    @Test
    void completeSession_unstampedItemsFallBackToThePrimaryStudyPack() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        QuickReviewSessionEntity session = buildInProgressChallengeSession(
                sessionId,
                userId,
                studyPackId,
                noteId,
                "challenge",
                List.of(
                        new QuizItem("Question 1", List.of("A", "B", "C", "D"), "A", "Mastered", "Explanation"),
                        new QuizItem("Question 2", List.of("A", "B", "C", "D"), "B", "Weak", "Explanation"),
                        new QuizItem("Question 3", List.of("A", "B", "C", "D"), "D", "Mastered", "Explanation")
                ),
                Map.of("0", "A", "1", "C", "2", "D")
        );

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId,
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));
        when(conceptHealthService.recordIncorrectAnswers(
                any(UUID.class),
                any(UUID.class),
                anyList(),
                any(OffsetDateTime.class)
        )).thenReturn(List.of("Weak"));

        ChallengeQuizSessionResponse response = challengeQuizService.completeSession(
                sessionId.toString(),
                userId,
                new ChallengeQuizCompleteRequest(2, 3, 120)
        );

        verify(conceptHealthService).recordCorrectAnswers(
                userId,
                studyPackId,
                List.of("Mastered"),
                session.getCompletedAt()
        );
        verify(conceptHealthService).recordIncorrectAnswers(
                userId,
                studyPackId,
                List.of("Weak"),
                session.getCompletedAt()
        );
        assertThat(response.twiceMissedConcepts()).containsExactly("Weak");
    }

    @Test
    void completeSession_attributesMixedChallengeConceptsByStampedSourceAndUnionsTwiceMissedConcepts() {
        UUID userId = UUID.randomUUID();
        UUID sourceAId = UUID.randomUUID();
        UUID sourceBId = UUID.randomUUID();
        UUID sourceCId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressChallengeSession(
                sessionId,
                userId,
                sourceAId,
                UUID.randomUUID(),
                "challenge",
                List.of(
                        new QuizItem("A Shear", List.of("A", "B"), 0, "Shear", "Explanation").withSourceStudyPackId(sourceAId.toString()),
                        new QuizItem("A Moment", List.of("A", "B"), 0, "Moment", "Explanation").withSourceStudyPackId(sourceAId.toString()),
                        new QuizItem("B Shear", List.of("A", "B"), 0, "Shear", "Explanation").withSourceStudyPackId(sourceBId.toString()),
                        new QuizItem("B Moment", List.of("A", "B"), 0, "Moment", "Explanation").withSourceStudyPackId(sourceBId.toString())
                ),
                Map.of("0", "A", "1", "B", "2", "B", "3", "A")
        );
        Map<String, Object> stateWithSources = new LinkedHashMap<>(session.getSessionState());
        stateWithSources.put("sourceNoteRefs", List.of(
                Map.of("studyPackId", sourceBId.toString(), "noteId", UUID.randomUUID().toString(),
                        "noteTitle", "B", "questionCount", 2),
                Map.of("studyPackId", sourceCId.toString(), "noteId", UUID.randomUUID().toString(),
                        "noteTitle", "C", "questionCount", 0)
        ));
        session.setSessionState(stateWithSources);

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));
        when(conceptHealthService.recordIncorrectAnswers(
                eq(userId), eq(sourceAId), eq(List.of("Moment")), any(OffsetDateTime.class)
        )).thenReturn(List.of("Moment"));
        when(conceptHealthService.recordIncorrectAnswers(
                eq(userId), eq(sourceBId), eq(List.of("Shear")), any(OffsetDateTime.class)
        )).thenReturn(List.of("Shear"));

        ChallengeQuizSessionResponse response = challengeQuizService.completeSession(
                sessionId.toString(), userId, new ChallengeQuizCompleteRequest(2, 4, 120)
        );

        verify(conceptHealthService).recordCorrectAnswers(
                userId, sourceAId, List.of("Shear"), session.getCompletedAt());
        verify(conceptHealthService).recordIncorrectAnswers(
                userId, sourceAId, List.of("Moment"), session.getCompletedAt());
        verify(conceptHealthService).recordCorrectAnswers(
                userId, sourceBId, List.of("Moment"), session.getCompletedAt());
        verify(conceptHealthService).recordIncorrectAnswers(
                userId, sourceBId, List.of("Shear"), session.getCompletedAt());
        // ⚠️ sourceC is a REAL session source that contributed no item — it is listed in sourceNoteRefs
        // below, so a broadcast-shaped regression could reach it. A bare fresh UUID here would make these
        // assertions vacuous: nothing could ever write to an id the session has never heard of.
        verify(conceptHealthService, never()).recordCorrectAnswers(
                eq(userId), eq(sourceCId), any(), any());
        verify(conceptHealthService, never()).recordIncorrectAnswers(
                eq(userId), eq(sourceCId), any(), any());
        // And the primary must not additionally absorb the other source's concepts.
        verify(conceptHealthService, never()).recordCorrectAnswers(
                eq(userId), eq(sourceAId), eq(List.of("Moment")), any());
        assertThat(response.twiceMissedConcepts()).containsExactly("Moment", "Shear");
    }

    @Test
    void completeSession_boardExamModeNeverSurfacesTwiceMissedConcepts() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        QuickReviewSessionEntity session = buildInProgressChallengeSession(
                sessionId,
                userId,
                studyPackId,
                noteId,
                "board_exam",
                List.of(
                        new QuizItem("Question 1", List.of("A", "B", "C", "D"), "A", "Mastered", "Explanation"),
                        new QuizItem("Question 2", List.of("A", "B", "C", "D"), "B", "Weak", "Explanation"),
                        new QuizItem("Question 3", List.of("A", "B", "C", "D"), "D", "Mastered", "Explanation")
                ),
                Map.of("0", "A", "1", "C", "2", "D")
        );

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId,
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));
        when(conceptHealthService.recordIncorrectAnswers(
                any(UUID.class),
                any(UUID.class),
                anyList(),
                any(OffsetDateTime.class)
        )).thenReturn(List.of("Weak"));

        ChallengeQuizSessionResponse response = challengeQuizService.completeSession(
                sessionId.toString(),
                userId,
                new ChallengeQuizCompleteRequest(2, 3, 120)
        );

        assertThat(response.twiceMissedConcepts()).isEmpty();
        verify(challengeQuizQuestionBankService, never()).updateOutcomesAndReleaseClaims(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void completeSession_scoresMixedMcqAndIdentificationAnswersWithAnsweredDenominator() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        List<QuizItem> quiz = List.of(
                new QuizItem("MCQ 1", List.of("A", "B", "C", "D"), "A", "MCQ Correct", "Explanation"),
                new QuizItem("MCQ 2", List.of("A", "B", "C", "D"), "B", "MCQ Wrong", "Explanation"),
                new QuizItem("MCQ 3", List.of("A", "B", "C", "D"), "C", "MCQ Correct", "Explanation"),
                new QuizItem("MCQ 4", List.of("A", "B", "C", "D"), "D", "MCQ Wrong", "Explanation"),
                new QuizItem("MCQ 5", List.of("A", "B", "C", "D"), "A", "MCQ Correct", "Explanation"),
                identificationQuizItem("Identification 1", "Ohm's Law", "ID Correct"),
                identificationQuizItem("Identification 2", "Kirchhoff's Law", "ID Wrong"),
                identificationQuizItem("Identification 3", "Faraday's Law", "ID Correct"),
                identificationQuizItem("Identification 4", "Lenz's Law", "ID Wrong"),
                identificationQuizItem("Identification 5", "Coulomb's Law", "ID Correct")
        );
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(quiz.size());
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                quiz,
                Map.of(
                        "mode", "challenge",
                        "difficulty", "medium",
                        "selectedChoices", Map.of("0", "A", "1", "C", "2", "C", "3", "B", "4", "A"),
                        "selectedIdentificationAnswers", Map.of(
                                "5", " ohm's   law ",
                                "6", "Ampere's Law",
                                "7", "FARADAY'S LAW",
                                "8", "Hooke's Law",
                                "9", "Coulomb's Law"
                        ),
                        "completed", false
                )
        ));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId,
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        var response = challengeQuizService.completeSession(
                sessionId.toString(),
                userId,
                new ChallengeQuizCompleteRequest(0, 10, 180)
        );

        assertThat(response.correctAnswers()).isEqualTo(6);
        assertThat(response.totalQuestions()).isEqualTo(10);
        assertThat(response.scorePercentage()).isEqualByComparingTo("60.00");
        assertThat(response.conceptBreakdown())
                .extracting(stat -> stat.concept() + ":" + stat.correctAnswers() + "/" + stat.totalQuestions())
                .contains(
                        "ID Correct:3/3",
                        "ID Wrong:0/2",
                        "MCQ Correct:3/3",
                        "MCQ Wrong:0/2"
                );
        assertThat(response.weakConcepts()).contains("ID Wrong", "MCQ Wrong");
        verify(conceptHealthService).recordCorrectAnswers(
                userId,
                studyPackId,
                List.of("MCQ Correct", "ID Correct"),
                session.getCompletedAt()
        );
        verify(conceptHealthService).recordIncorrectAnswers(
                userId,
                studyPackId,
                List.of("MCQ Wrong", "ID Wrong"),
                session.getCompletedAt()
        );
    }

    @Test
    void completeSession_scoresMixedMcqIdentificationAndEnumerationAnswersWithAnsweredDenominator() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        List<QuizItem> quiz = List.of(
                new QuizItem("MCQ 1", List.of("A", "B", "C", "D"), "A", "MCQ Correct", "Explanation"),
                new QuizItem("MCQ 2", List.of("A", "B", "C", "D"), "A", "MCQ Correct", "Explanation"),
                new QuizItem("MCQ 3", List.of("A", "B", "C", "D"), "B", "MCQ Wrong", "Explanation"),
                new QuizItem("MCQ 4", List.of("A", "B", "C", "D"), "B", "MCQ Wrong", "Explanation"),
                identificationQuizItem("Identification 1", "Ohm's Law", "ID Correct"),
                identificationQuizItem("Identification 2", "Faraday's Law", "ID Correct"),
                identificationQuizItem("Identification 3", "Kirchhoff's Law", "ID Wrong"),
                enumerationQuizItem("Enumeration 1", List.of(List.of("A"), List.of("B")), "Enum Correct"),
                enumerationQuizItem("Enumeration 2", List.of(List.of("X"), List.of("Y")), "Enum Correct"),
                enumerationQuizItem("Enumeration 3", List.of(List.of("P"), List.of("Q")), "Enum Wrong")
        );
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(quiz.size());
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                quiz,
                Map.of(
                        "mode", "challenge",
                        "difficulty", "medium",
                        "selectedChoices", Map.of("0", "A", "1", "A", "2", "D", "3", "D"),
                        "selectedIdentificationAnswers", Map.of(
                                "4", "Ohm's Law",
                                "5", "Faraday's Law",
                                "6", "Ampere's Law"
                        ),
                        "selectedEnumerationAnswers", Map.of(
                                "7", List.of("A", "B"),
                                "8", List.of("Y", "X"),
                                "9", List.of("P", "Wrong")
                        ),
                        "completed", false
                )
        ));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId,
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        var response = challengeQuizService.completeSession(
                sessionId.toString(),
                userId,
                new ChallengeQuizCompleteRequest(0, 10, 180)
        );

        assertThat(response.correctAnswers()).isEqualTo(6);
        assertThat(response.totalQuestions()).isEqualTo(10);
        assertThat(response.scorePercentage()).isEqualByComparingTo("60.00");
        assertThat(response.conceptBreakdown())
                .extracting(stat -> stat.concept() + ":" + stat.correctAnswers() + "/" + stat.totalQuestions())
                .contains(
                        "MCQ Correct:2/2",
                        "MCQ Wrong:0/2",
                        "ID Correct:2/2",
                        "ID Wrong:0/1",
                        "Enum Correct:2/2",
                        "Enum Wrong:0/1"
                );
        assertThat(response.weakConcepts()).contains("MCQ Wrong", "ID Wrong", "Enum Wrong");
        verify(conceptHealthService).recordCorrectAnswers(
                userId,
                studyPackId,
                List.of("MCQ Correct", "ID Correct", "Enum Correct"),
                session.getCompletedAt()
        );
        verify(conceptHealthService).recordIncorrectAnswers(
                userId,
                studyPackId,
                List.of("MCQ Wrong", "ID Wrong", "Enum Wrong"),
                session.getCompletedAt()
        );
    }

    @Test
    void completeSession_recordsFullyCorrectBoardExamConceptsToConceptHealth() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        QuickReviewSessionEntity session = buildInProgressChallengeSession(
                sessionId,
                userId,
                studyPackId,
                noteId,
                "board_exam",
                List.of(
                        new QuizItem("Question 1", List.of("A", "B", "C", "D"), "A", "Board Mastery", "Explanation"),
                        new QuizItem("Question 2", List.of("A", "B", "C", "D"), "C", "Board Weakness", "Explanation")
                ),
                Map.of("0", "A", "1", "B")
        );

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId,
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        challengeQuizService.completeSession(
                sessionId.toString(),
                userId,
                new ChallengeQuizCompleteRequest(1, 2, 120)
        );

        verify(conceptHealthService).recordCorrectAnswers(
                userId,
                studyPackId,
                List.of("Board Mastery"),
                session.getCompletedAt()
        );
        verify(conceptHealthService).recordIncorrectAnswers(
                userId,
                studyPackId,
                List.of("Board Weakness"),
                session.getCompletedAt()
        );
    }

    @Test
    void completeSession_skipsConceptHealthForASourcePackTheCallerDoesNotOwn() {
        // ⚠️ setUp stubs findByIdAndOwnerUserId to return a pack for ANY id, which makes the ownership
        // guard's empty branch unreachable — a cold agent deleted that guard from BOTH record methods and
        // all 1920 tests still passed. This overrides the blanket stub for one id so the guard executes.
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID unownedStudyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressChallengeSession(
                sessionId,
                userId,
                primaryStudyPackId,
                UUID.randomUUID(),
                "challenge",
                List.of(
                        new QuizItem("Owned", List.of("A", "B"), 0, "Owned Concept", "Explanation")
                                .withSourceStudyPackId(primaryStudyPackId.toString()),
                        new QuizItem("Unowned", List.of("A", "B"), 0, "Unowned Concept", "Explanation")
                                .withSourceStudyPackId(unownedStudyPackId.toString())
                ),
                Map.of("0", "A", "1", "A")
        );

        when(studyPackRepository.findByIdAndOwnerUserId(unownedStudyPackId, userId)).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        challengeQuizService.completeSession(
                sessionId.toString(), userId, new ChallengeQuizCompleteRequest(2, 2, 120)
        );

        verify(conceptHealthService).recordCorrectAnswers(
                userId, primaryStudyPackId, List.of("Owned Concept"), session.getCompletedAt());
        verify(conceptHealthService, never()).recordCorrectAnswers(
                eq(userId), eq(unownedStudyPackId), any(), any());
        verify(conceptHealthService, never()).recordIncorrectAnswers(
                eq(userId), eq(unownedStudyPackId), any(), any());
    }

    @Test
    void completeSession_labelsAnAbsentConceptUncategorizedNotUnknown() {
        // ⚠️ REGRESSION GUARD. ConceptHealthEntity is keyed (user_id, study_pack_id, concept), so the
        // label used for a null/blank concept IS part of the row identity. This service labels it
        // "Uncategorized"; QuizSessionReviewUtils labels it "Unknown". When the per-source aggregation
        // moved to that util, Challenge and Board Exam silently began writing "Unknown" — forking the row,
        // orphaning the accumulated incorrect_streak on "Uncategorized", and starting a parallel row at
        // zero, while the result screen kept displaying "Uncategorized".
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressChallengeSession(
                sessionId,
                userId,
                studyPackId,
                UUID.randomUUID(),
                "challenge",
                List.of(new QuizItem("No concept", List.of("A", "B"), 0, null, "Explanation")
                        .withSourceStudyPackId(studyPackId.toString())),
                Map.of("0", "A")
        );

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        challengeQuizService.completeSession(
                sessionId.toString(), userId, new ChallengeQuizCompleteRequest(1, 1, 60)
        );

        verify(conceptHealthService).recordCorrectAnswers(
                userId, studyPackId, List.of("Uncategorized"), session.getCompletedAt());
        verify(conceptHealthService, never()).recordCorrectAnswers(
                eq(userId), eq(studyPackId), eq(List.of("Unknown")), any());
    }

    @Test
    void completeSession_attributesMixedBoardExamConceptsByStampedSource() {
        UUID userId = UUID.randomUUID();
        UUID sourceAId = UUID.randomUUID();
        UUID sourceBId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressChallengeSession(
                sessionId,
                userId,
                sourceAId,
                UUID.randomUUID(),
                "board_exam",
                List.of(
                        new QuizItem("A", List.of("A", "B"), 0, "A Concept", "Explanation").withSourceStudyPackId(sourceAId.toString()),
                        new QuizItem("B", List.of("A", "B"), 0, "B Concept", "Explanation").withSourceStudyPackId(sourceBId.toString())
                ),
                Map.of("0", "A", "1", "B")
        );
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizSessionResponse response = challengeQuizService.completeSession(
                sessionId.toString(), userId, new ChallengeQuizCompleteRequest(1, 2, 120)
        );

        verify(conceptHealthService).recordCorrectAnswers(
                userId, sourceAId, List.of("A Concept"), session.getCompletedAt());
        verify(conceptHealthService).recordIncorrectAnswers(
                userId, sourceBId, List.of("B Concept"), session.getCompletedAt());
        // ⚠️ These negatives are what make this test discriminate. Without them, reintroducing Board
        // Exam's under-attribution — writing every bucket to the PRIMARY as well — left every positive
        // assertion above satisfied and the mutant survived. sourceA IS the primary here.
        verify(conceptHealthService, never()).recordIncorrectAnswers(
                eq(userId), eq(sourceAId), any(), any());
        verify(conceptHealthService, never()).recordCorrectAnswers(
                eq(userId), eq(sourceBId), any(), any());
        assertThat(response.twiceMissedConcepts()).isEmpty();
    }

    @Test
    void completeSession_acceptsCanonicalSelectedChoiceIndexes() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(1);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                List.of(
                        new QuizItem("Question 1", List.of("cos(x)", "-cos(x)", "-sin(x)", "tan(x)"), 0, "Concept 1", "Explanation")
                ),
                Map.of(
                        "selectedChoices", Map.of("0", 0),
                        "completed", false
                )
        ));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId,
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        var response = challengeQuizService.completeSession(
                sessionId.toString(),
                userId,
                new ChallengeQuizCompleteRequest(1, 1, 60)
        );

        assertThat(response.correctAnswers()).isEqualTo(1);
        assertThat(response.scorePercentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void forfeitSession_marksChallengeSessionForfeitedWithoutRefundingCreditOrCompletingIt() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(1);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId,
                userId,
                QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        var response = challengeQuizService.forfeitSession(sessionId.toString(), userId);

        assertThat(response.message()).isEqualTo("Challenge Quiz session forfeited.");
        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FORFEITED);
        assertThat(session.getCompletedAt()).isNull();
        verify(userUsageService, never()).incrementChallengeQuizGeneration(any(UUID.class), any(OffsetDateTime.class));
        verify(activityTrackingService, never()).recordActivity(userId, ActivityType.COMPLETED_CHALLENGE_QUIZ, studyPackId);
    }

    @Test
    void generateMoreQuestions_appendsNewQuestionsToSession() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);

        List<QuizItem> existingQuiz = List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q3", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q4", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q5", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation")
        );
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(5);
        session.setModelUsed("gpt-4.1-mini");
        session.setInputTokens(100);
        session.setOutputTokens(50);
        session.setCachedInputTokens(20);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                existingQuiz,
                Map.of("mode", "challenge", "difficulty", "medium", "completed", false)
        ));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Engineering",
                studyPack.getSubject(),
                studyPack.getTags() == null ? List.of() : List.of(studyPack.getTags())
        ));
        when(quizGenerationService.generateMoreChallengeQuiz(
                any(), any(), any(), any(), any(), eq(5), eq("medium"), any()
        )).thenReturn(new GeneratedChallengeQuizContent(
                List.of(
                        new QuizItem("Q6 new", List.of("A", "B", "C", "D"), "A", "Concept2", "Explanation"),
                        new QuizItem("Q7 new", List.of("A", "B", "C", "D"), "A", "Concept2", "Explanation"),
                        new QuizItem("Q8 new", List.of("A", "B", "C", "D"), "A", "Concept2", "Explanation"),
                        new QuizItem("Q9 new", List.of("A", "B", "C", "D"), "A", "Concept2", "Explanation"),
                        new QuizItem("Q10 new", List.of("A", "B", "C", "D"), "A", "Concept2", "Explanation")
                ),
                "gpt-4.1-mini",
                40,
                20,
                5
        ));
        when(quickReviewSessionRepository.save(any())).thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        GenerateMoreChallengeQuizResponse response = challengeQuizService.generateMoreQuestions(sessionId.toString(), userId);

        assertThat(response.newQuestions()).hasSize(5);
        verify(generationContextResolver).resolveForStudyPack(userId, studyPack);
        assertThat(response.totalQuestions()).isEqualTo(10);
        assertThat(session.getTotalQuestions()).isEqualTo(10);
        assertThat(session.getModelUsed()).isEqualTo("gpt-4.1-mini");
        assertThat(session.getInputTokens()).isEqualTo(140);
        assertThat(session.getOutputTokens()).isEqualTo(70);
        assertThat(session.getCachedInputTokens()).isEqualTo(25);
        verify(challengeQuizQuestionBankService).persistGeneratedQuestions(
                eq(userId), eq(studyPackId), eq(sessionId), eq(LearnerLevel.COLLEGE), any()
        );
    }

    @Test
    void generateMoreQuestions_shufflesOnlyTheNewBatchAndPreservesExistingAnsweredIndices() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);

        List<QuizItem> existingQuiz = buildQuizWithPrefix("Existing", 5);
        Map<String, Object> baseState = QuizSessionStateUtils.withQuiz(
                existingQuiz,
                Map.of("mode", "challenge", "difficulty", "medium", "completed", false)
        );
        Map<String, Object> stateWithAnswers = QuizSessionStateUtils.withSelectedChoice(
                QuizSessionStateUtils.withSelectedChoice(baseState, 0, 1),
                2, 3
        );

        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(5);
        session.setSessionState(stateWithAnswers);

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, "Engineering", studyPack.getSubject(), List.of()
        ));
        List<QuizItem> generatedBatch = buildQuizWithPrefix("New", 5);
        when(quizGenerationService.generateMoreChallengeQuiz(
                any(), any(), any(), any(), any(), eq(5), eq("medium"), any()
        )).thenReturn(GeneratedChallengeQuizContent.withoutUsage(generatedBatch));
        when(quickReviewSessionRepository.save(any())).thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        challengeQuizService.generateMoreQuestions(sessionId.toString(), userId);

        List<QuizItem> finalQuiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        assertThat(finalQuiz).hasSize(10);
        assertThat(finalQuiz.subList(0, 5))
                .as("existing questions must keep their original indices so recorded answers stay attached to the right question")
                .containsExactlyElementsOf(existingQuiz);
        assertThat(finalQuiz.subList(5, 10)).containsExactlyInAnyOrderElementsOf(withSourceStudyPackId(generatedBatch, studyPackId));

        @SuppressWarnings("unchecked")
        Map<String, Object> selectedChoices = (Map<String, Object>) session.getSessionState().get("selectedChoices");
        assertThat(selectedChoices).containsEntry("0", 1);
        assertThat(selectedChoices).containsEntry("2", 3);
    }

    @Test
    void generateMoreQuestions_reusesEligibleBankedQuestionsBeforeCallingLlm() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        List<QuizItem> existingQuiz = buildQuizWithPrefix("Existing", 5);
        List<QuizItem> bankedQuiz = buildQuizWithPrefix("Banked", 5);
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(5);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                existingQuiz,
                Map.of("mode", "challenge", "difficulty", "medium", "completed", false)
        ));

        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, "Engineering", studyPack.getSubject(), List.of()
        );
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);
        when(challengeQuizQuestionBankService.claimEligibleQuestions(
                eq(userId), eq(studyPackId), eq(LearnerLevel.COLLEGE), eq(sessionId), any(), eq(5)
        )).thenReturn(bankedQuiz);
        when(quickReviewSessionRepository.save(any())).thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        GenerateMoreChallengeQuizResponse response = challengeQuizService.generateMoreQuestions(sessionId.toString(), userId);

        assertThat(response.newQuestions()).containsExactlyInAnyOrderElementsOf(withSourceStudyPackId(bankedQuiz, studyPackId));
        assertThat(response.totalQuestions()).isEqualTo(10);
        verify(quizGenerationService, never()).generateMoreChallengeQuiz(any(), any(), any(), any(), any(), anyInt(), any(), any());
        verify(challengeQuizQuestionBankService, never()).persistGeneratedQuestions(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void generateMoreQuestions_usesOfficialTemplateBeforeCallingLlm() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        List<QuizItem> existingQuiz = buildQuizWithPrefix("Existing", 5);
        List<QuizItem> templateQuiz = buildQuizWithPrefix("Official", 5);
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(5);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                existingQuiz,
                Map.of("mode", "challenge", "difficulty", "medium", "completed", false)
        ));
        StudyPackGenerationContext generationContext = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, "Engineering", studyPack.getSubject(), List.of()
        );
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(generationContext);
        when(officialChallengeQuizTemplateService.copyTemplateQuestions(
                eq(userId), eq(studyPackId), eq(LearnerLevel.COLLEGE), eq(sessionId), any(), eq(5)
        )).thenReturn(templateQuiz);
        when(quickReviewSessionRepository.save(any())).thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        GenerateMoreChallengeQuizResponse response = challengeQuizService.generateMoreQuestions(sessionId.toString(), userId);

        assertThat(response.newQuestions()).containsExactlyInAnyOrderElementsOf(withSourceStudyPackId(templateQuiz, studyPackId));
        assertThat(response.totalQuestions()).isEqualTo(10);
        assertThat(session.getModelUsed()).isNull();
        assertThat(session.getInputTokens()).isNull();
        assertThat(session.getOutputTokens()).isNull();
        assertThat(session.getCachedInputTokens()).isNull();
        verify(quizGenerationService, never()).generateMoreChallengeQuiz(any(), any(), any(), any(), any(), anyInt(), any(), any());
        verify(challengeQuizQuestionBankService, never()).persistGeneratedQuestions(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void generateMoreQuestions_throwsWhenMaxQuestionsReached() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        List<QuizItem> fullQuiz = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            fullQuiz.add(new QuizItem("Q" + i, List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"));
        }

        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(20);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                fullQuiz,
                Map.of("mode", "challenge", "difficulty", "medium", "completed", false)
        ));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));

        String id = sessionId.toString();
        assertThatThrownBy(() -> challengeQuizService.generateMoreQuestions(id, userId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void generateMoreQuestions_rejectsAndForfeitsAlreadyExpiredSessionInsteadOfExtendingIt() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(5);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                buildQuiz(5),
                Map.of(
                        "mode", "challenge",
                        "difficulty", "medium",
                        "timeLimitSeconds", 60,
                        "timerStartedAtEpochSeconds", OffsetDateTime.now().minusMinutes(10).toEpochSecond(),
                        "completed", false
                )
        ));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class))).thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        String id = sessionId.toString();
        assertThatThrownBy(() -> challengeQuizService.generateMoreQuestions(id, userId))
                .isInstanceOf(ChallengeQuizSessionNotInProgressException.class);

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FORFEITED);
        verify(challengeQuizQuestionBankService).releaseClaims(userId, studyPackId, sessionId);
        verify(quizGenerationService, never()).generateMoreChallengeQuiz(any(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void generateMoreQuestions_throwsNotEnoughNewQuestionsWhenDeduplicationRemovesTooMany() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);

        List<QuizItem> existingQuiz = List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q3", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q4", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q5", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation")
        );
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(5);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                existingQuiz,
                Map.of("mode", "challenge", "difficulty", "medium", "completed", false)
        ));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                "Engineering",
                studyPack.getSubject(),
                studyPack.getTags() == null ? List.of() : List.of(studyPack.getTags())
        ));
        when(quizGenerationService.generateMoreChallengeQuiz(
                any(), any(), any(), any(), any(), anyInt(), any(), any()
        )).thenReturn(GeneratedChallengeQuizContent.withoutUsage(List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation")
        )));

        String id = sessionId.toString();
        assertThatThrownBy(() -> challengeQuizService.generateMoreQuestions(id, userId))
                .isInstanceOf(NotEnoughNewQuestionsException.class);
    }

    @Test
    void startSession_throwsTypedExceptionForInvalidMode() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        String id = studyPackId.toString();
        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest("oral_exam", null);
        assertThatThrownBy(() -> challengeQuizService.startSession(
                id,
                userId,
                request
        ))
                .isInstanceOf(InvalidChallengeQuizModeException.class)
                .hasMessage("Challenge Quiz mode must be challenge or board_exam.");
    }

    private List<QuizItem> buildQuiz(int count) {
        java.util.ArrayList<QuizItem> quiz = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            quiz.add(new QuizItem(
                    "Question " + index,
                    List.of("A", "B", "C", "D"),
                    index % 4,
                    "Concept",
                    "Explanation"
            ));
        }
        return quiz;
    }

    private List<QuizItem> buildQuizWithPrefix(String prefix, int count) {
        java.util.ArrayList<QuizItem> quiz = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            quiz.add(new QuizItem(
                    prefix + " question " + index,
                    List.of("A", "B", "C", "D"),
                    index % 4,
                    "Concept",
                    "Explanation"
            ));
        }
        return quiz;
    }

    private QuizItem identificationQuizItem(String question, String answer, String concept) {
        return new QuizItem(
                question,
                List.of(),
                null,
                concept,
                "Explanation",
                null,
                "IDENTIFICATION",
                null,
                null,
                null,
                null,
                concept,
                List.of(answer, answer.replace("'", "")),
                null
        );
    }

    private QuizItem enumerationQuizItem(String question, List<List<String>> acceptableAnswerGroups, String concept) {
        return new QuizItem(
                question,
                List.of(),
                null,
                concept,
                "Explanation",
                null,
                "ENUMERATION",
                null,
                null,
                null,
                null,
                concept,
                null,
                acceptableAnswerGroups
        );
    }

    private QuickReviewSessionEntity buildInProgressChallengeSession(
            UUID sessionId,
            UUID userId,
            UUID studyPackId,
            UUID noteId,
            String mode,
            List<QuizItem> quiz,
            Map<String, ?> selectedChoices
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(quiz.size());
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                quiz,
                Map.of(
                        "mode", mode,
                        "difficulty", "medium",
                        "selectedChoices", selectedChoices,
                        "completed", false
                )
        ));
        return session;
    }

    private QuickReviewSessionEntity activeChallengeSession(
            UUID sessionId,
            UUID userId,
            UUID studyPackId,
            UUID noteId,
            String mode,
            QuickReviewSessionStatus status
    ) {
        QuickReviewSessionEntity session = buildInProgressChallengeSession(
                sessionId, userId, studyPackId, noteId, mode, buildQuiz(1), Map.of()
        );
        session.setStatus(status);
        return session;
    }

    @SuppressWarnings("unchecked")
    private Optional<ChallengeQuizStartResponse> resolveExistingChallengeSession(
            UUID userId,
            UUID studyPackId,
            StudyPackEntity studyPack,
            PlanType planType
    ) throws Exception {
        java.lang.reflect.Method resolver = ChallengeQuizService.class.getDeclaredMethod(
                "resolveExistingChallengeSession",
                UUID.class,
                UUID.class,
                StudyPackEntity.class,
                PlanType.class
        );
        resolver.setAccessible(true);
        return (Optional<ChallengeQuizStartResponse>) resolver.invoke(
                challengeQuizService,
                userId,
                studyPackId,
                studyPack,
                planType
        );
    }

    @Test
    void startSession_freeMultiNoteChallengeUsesVerifiedPlanSourcesIncrementsCounterAndPersistsAnalyticsMetadata() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, primaryNoteId, userId);
        StudyPackEntity additional = buildStudyPack(additionalStudyPackId, additionalNoteId, userId);
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, "Engineering", primary.getSubject(), List.of()
        );

        stubMultiNoteChallengeStart(userId, primaryStudyPackId, primary, additionalStudyPackId, additional, PlanType.FREE);
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(primaryNoteId, additionalNoteId)));
        when(generationContextResolver.resolveForStudyPack(eq(userId), any(StudyPackEntity.class))).thenReturn(context);
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(), eq(9), any(), any()))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Primary", 9)))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Additional", 9)));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primaryStudyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("challenge", List.of(additionalStudyPackId.toString()), collectionId.toString())
        );

        assertThat(response.sourceNoteRefs()).hasSize(2);
        assertThat(response.maxSourceNotes()).isEqualTo(3);
        assertThat(response.quiz()).filteredOn(item -> primaryStudyPackId.toString().equals(item.sourceStudyPackId())).hasSize(9);
        assertThat(response.quiz()).filteredOn(item -> additionalStudyPackId.toString().equals(item.sourceStudyPackId())).hasSize(9);
        verify(userUsageService).incrementMultiNoteGeneration(eq(userId), any(OffsetDateTime.class));
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.CHALLENGE_QUIZ_STARTED),
                eq(primaryStudyPackId), metadata.capture());
        assertThat(metadata.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "sessionId", response.sessionId(),
                "questionCount", 18,
                "difficulty", "medium",
                "mode", "challenge",
                "sourceCount", 2,
                "sourceScope", "plan"
        ));
    }

    @Test
    void startSession_rejectsOverFreeMultiNoteSourceCapInsteadOfSilentlyStartingSingleNote() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, UUID.randomUUID(), userId);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId)).thenReturn(Optional.of(primary));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(primaryStudyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> challengeQuizService.startSession(primaryStudyPackId.toString(), userId,
                new ChallengeQuizStartRequest("challenge", List.of(
                        UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString()
                ))))
                .isInstanceOf(MultiNoteChallengeQuizSourceNotAllowedException.class);

        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(quizGenerationService, never()).generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void startSession_plusCapComesFromTHISQuizsQuestionCountAndEverySourceClearsTheMinimum() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, UUID.randomUUID(), userId);
        List<StudyPackEntity> additionalSources = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> buildStudyPack(UUID.randomUUID(), UUID.randomUUID(), userId))
                .toList();
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId)).thenReturn(Optional.of(primary));
        for (StudyPackEntity source : additionalSources) {
            when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(source.getId(), userId)).thenReturn(Optional.of(source));
        }
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(primaryStudyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        stubChallengeUsagePeriod(userId, PlanType.PLUS);
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(isNull(), eq(userId), any())).thenReturn(Set.of());
        when(generationContextResolver.resolveForStudyPack(eq(userId), any(StudyPackEntity.class))).thenReturn(
                new StudyPackGenerationContext(LearnerLevel.GRADE_SCHOOL, "Engineering", primary.getSubject(), List.of())
        );
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(), eq(3), any(), any()))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Mixed", 3)));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primaryStudyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("challenge", additionalSources.stream().map(source -> source.getId().toString()).toList())
        );

        // ⚠️ SIX. Two corrections got here. The cap was first derived from the LONG EXAM question count
        // (20/25/30) while a Challenge Quiz is far shorter, so at 6 sources a 12-question quiz gave 2 per
        // source — below the floor — and the delivered stub encoded exactly that with eq(2). Deriving it
        // from this quiz's own count fixed that but made the cap score-adaptive; a multi-note session now
        // uses a FIXED 18 questions, so the cap is a stable 18/3 = 6 whatever the learner last scored.
        assertThat(response.maxSourceNotes()).isEqualTo(6);
        assertThat(response.sourceNoteRefs()).hasSize(6);
        assertThat(response.sourceNoteRefs())
                .allSatisfy(source -> assertThat(source.questionCount()).isGreaterThanOrEqualTo(3));
    }

    @Test
    void startSession_multiNoteCapDoesNotMoveWithTheLearnersLastQuickReviewScore() {
        // ⚠️ THE PROPERTY THE OWNER ASKED FOR, 2026-09-02. A single-note Challenge Quiz sizes itself
        // from the last Quick Review score (10 / 12 / 15), which would make the source cap 3, 4 or 5
        // and MOVE between sessions on the same plan — while the prestart renders it as a stable
        // promise. A multi-note session uses a fixed 12, so the cap is always 4.
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, UUID.randomUUID(), userId);
        when(studyPackRepository.findByIdAndOwnerUserId(primaryStudyPackId, userId))
                .thenReturn(Optional.of(primary));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        stubChallengeUsagePeriod(userId, PlanType.PLUS);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(primaryStudyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());

        ChallengeQuizStartResponse response = challengeQuizService.getInProgressSession(
                primaryStudyPackId.toString(),
                userId
        );

        assertThat(response.maxSourceNotes()).isEqualTo(6);
        // ⚠️ Structural, not incidental: the score lookup that sizes a single-note quiz is never
        // consulted for the cap at all. Asserting the number alone would still pass if the cap went
        // back to being score-derived and this fixture merely happened to land on 12 questions.
        verify(quickReviewSessionRepository, never())
                .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        any(), any(), any(), any());
    }

    @Test
    void startSession_locksTheUserRowOnlyWhenTheMultiNoteCounterIsAtStake() {
        // ⚠️ TWO PROPERTIES, AND BOTH SHIPPED UNPINNED. A cold agent deleted the lock entirely and all
        // 1910 backend tests passed, so the concurrency guarantee rested on nothing. And the lock was
        // taken UNCONDITIONALLY — a PESSIMISTIC_WRITE on the user row for every Challenge and Board Exam
        // start, held across LLM generation by @Transactional, serializing that account's other starts
        // and blocking anything else writing the same row.
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, UUID.randomUUID(), userId);
        stubChallengeStartDependencies(userId, primaryStudyPackId, primary, PlanType.PLUS);
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Single", 12)));

        challengeQuizService.startSession(primaryStudyPackId.toString(), userId,
                new ChallengeQuizStartRequest("challenge", List.of()));

        // Single-note: no counter, therefore no lock.
        verify(userRepository, never()).findByIdForUpdate(userId);
    }

    @Test
    void startSession_takesTheUserLockBeforeAssertingTheMultiNoteCeiling() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, primaryNoteId, userId);
        StudyPackEntity additional = buildStudyPack(additionalStudyPackId, additionalNoteId, userId);

        stubMultiNoteChallengeStart(userId, primaryStudyPackId, primary, additionalStudyPackId, additional, PlanType.PLUS);
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(primaryNoteId, additionalNoteId)));
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(), eq(9), any(), any()))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Primary", 9)))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Additional", 9)));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        challengeQuizService.startSession(primaryStudyPackId.toString(), userId,
                new ChallengeQuizStartRequest("challenge", List.of(additionalStudyPackId.toString()), collectionId.toString()));

        // ⚠️ Ordering, not merely presence: the lock must precede the usage read the ceiling checks,
        // or two concurrent starts can both observe the same remaining allowance.
        InOrder order = inOrder(userRepository, userUsageService);
        order.verify(userRepository).findByIdForUpdate(userId);
        order.verify(userUsageService, atLeastOnce()).getMonthlyUsage(eq(userId), any(OffsetDateTime.class));
    }

    @Test
    void generateMoreQuestions_refusesAMultiNoteSessionBeforeSpendingAnLlmCall() {
        // ⚠️ On an 18-question multi-note session the headroom is 2, below the minimum viable batch, so
        // +5 could NEVER succeed — it generated, threw, and the frontend swallowed the failure into
        // "no more questions". A live button that costs a call and is deterministically dead.
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(UUID.randomUUID());
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        Map<String, Object> state = new java.util.HashMap<>(QuizSessionStateUtils.withQuiz(
                buildQuizWithPrefix("Existing", 18),
                Map.of()
        ));
        state.put("mode", MODE_CHALLENGE_VALUE);
        session.setSessionState(state);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                sessionId, userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> challengeQuizService.generateMoreQuestions(sessionId.toString(), userId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("maximum");

        verify(quizGenerationService, never())
                .generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void allocateQuestionsAcrossSources_refusesToSplitBelowThreeQuestionsPerSource() {
        // ⚠️ Defence in depth, pinned deliberately. The corrected cap makes this unreachable from
        // startSession, so nothing would exercise it — and the defect it guards against is precisely a
        // cap sized from the wrong question count, which is what shipped and passed every test.
        List<LongExamSourceNoteRef> sources = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> new LongExamSourceNoteRef(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "Source " + index,
                        0
                ))
                .toList();

        assertThatThrownBy(() -> challengeQuizService.allocateQuestionsAcrossSources(sources, 12))
                .isInstanceOf(MultiNoteChallengeQuizSourceNotAllowedException.class);
    }

    @Test
    void allocateQuestionsAcrossSources_givesEverySourceAtLeastTheMinimumWhenItFits() {
        List<LongExamSourceNoteRef> sources = java.util.stream.IntStream.range(0, 4)
                .mapToObj(index -> new LongExamSourceNoteRef(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "Source " + index,
                        0
                ))
                .toList();

        List<LongExamSourceNoteRef> allocated = challengeQuizService.allocateQuestionsAcrossSources(sources, 12);

        assertThat(allocated).allSatisfy(source -> assertThat(source.questionCount()).isGreaterThanOrEqualTo(3));
        assertThat(allocated.stream().mapToInt(LongExamSourceNoteRef::questionCount).sum()).isEqualTo(12);
    }

    @Test
    void startSession_plusRejectsOneMoreThanTheCapItsOwnQuestionCountAllows() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, UUID.randomUUID(), userId);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId)).thenReturn(Optional.of(primary));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PLUS);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(primaryStudyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());

        // Six sources is the cap for an 18-question quiz, so six ADDITIONAL (seven total) is one past it.
        assertThatThrownBy(() -> challengeQuizService.startSession(primaryStudyPackId.toString(), userId,
                new ChallengeQuizStartRequest("challenge", java.util.stream.IntStream.range(0, 6)
                        .mapToObj(index -> UUID.randomUUID().toString()).toList())))
                .isInstanceOf(MultiNoteChallengeQuizSourceNotAllowedException.class);
    }

    @Test
    void startSession_refusesMultiNoteAtItsDedicatedCeilingBeforeWritingOrCallingTheLlm() {
        UUID userId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, UUID.randomUUID(), userId);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId)).thenReturn(Optional.of(primary));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(primaryStudyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class))).thenReturn(
                new BillingUsagePeriodService.UsagePeriod(PlanType.FREE, BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(29), 2026, 3)
        );
        OffsetDateTime now = OffsetDateTime.now();
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(
                new UserUsageService.MonthlyUsage(now.minusDays(1), now.plusDays(29), 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 2)
        );

        assertThatThrownBy(() -> challengeQuizService.startSession(primaryStudyPackId.toString(), userId,
                new ChallengeQuizStartRequest("challenge", List.of(additionalStudyPackId.toString()))))
                .isInstanceOf(MonthlyMultiNoteLimitReachedException.class)
                .hasMessage("You've used all 2 multi-note Challenge Quiz sessions in this billing period.");

        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(userUsageService, never()).incrementMultiNoteGeneration(any(), any());
    }

    @Test
    void startSession_recordsManualSourceScopeWhenTheClaimedPlanDoesNotContainThePrimary() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, primaryNoteId, userId);
        StudyPackEntity additional = buildStudyPack(additionalStudyPackId, UUID.randomUUID(), userId);
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, "Engineering", primary.getSubject(), List.of()
        );
        stubMultiNoteChallengeStart(userId, primaryStudyPackId, primary, additionalStudyPackId, additional, PlanType.FREE);
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any())).thenReturn(Set.of());
        when(generationContextResolver.resolveForStudyPack(eq(userId), any(StudyPackEntity.class))).thenReturn(context);
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(), eq(9), any(), any()))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Primary", 9)))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Additional", 9)));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class))).thenAnswer(invocation -> recordSession(invocation.getArgument(0)));

        challengeQuizService.startSession(primaryStudyPackId.toString(), userId,
                new ChallengeQuizStartRequest("challenge", List.of(additionalStudyPackId.toString()), collectionId.toString()));

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.CHALLENGE_QUIZ_STARTED),
                eq(primaryStudyPackId), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("sourceScope", "manual");
    }

    /** Single-note Challenge start: no additional sources, so no multi-note counter and no user lock. */
    private void stubChallengeStartDependencies(
            UUID userId,
            UUID primaryStudyPackId,
            StudyPackEntity primary,
            PlanType planType
    ) {
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId)).thenReturn(Optional.of(primary));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(primaryStudyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId), eq(QuickReviewSessionMode.CHALLENGE), any(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(subscriptionService.resolvePlan(userId)).thenReturn(planType);
        stubChallengeUsagePeriod(userId, planType);
        lenient().when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));
    }

    private void stubMultiNoteChallengeStart(
            UUID userId,
            UUID primaryStudyPackId,
            StudyPackEntity primary,
            UUID additionalStudyPackId,
            StudyPackEntity additional,
            PlanType planType
    ) {
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId)).thenReturn(Optional.of(primary));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId)).thenReturn(Optional.of(additional));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(primaryStudyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId), eq(QuickReviewSessionMode.CHALLENGE), any(), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(subscriptionService.resolvePlan(userId)).thenReturn(planType);
        stubChallengeUsagePeriod(userId, planType);
    }

    /**
     * Stubs an owned Review Set whose Subject Plans hold {@code notesPerPlan[i]} notes each, every one of
     * them backed by a DONE Study Pack. Returns the packs per plan, in position order; {@code get(0).get(0)}
     * is the natural primary.
     *
     * <p>Pass a single element to get a CHILDLESS Review Set — the items hang off the Review Set itself and
     * must be treated as one stratum, mirroring the Goal endpoint rule.
     */
    private List<List<StudyPackEntity>> stubReviewSet(UUID userId, UUID reviewSetId, boolean childless, int... notesPerPlan) {
        NoteCollectionEntity reviewSet = new NoteCollectionEntity();
        reviewSet.setId(reviewSetId);
        reviewSet.setOwnerUserId(userId);
        // lenient: a request rejected before source resolution never reaches these — which is the point.
        lenient().when(noteCollectionRepository.findByIdAndOwnerUserId(reviewSetId, userId)).thenReturn(Optional.of(reviewSet));

        List<NoteCollectionEntity> children = new ArrayList<>();
        List<List<StudyPackEntity>> packsByPlan = new ArrayList<>();
        List<StudyPackEntity> allPacks = new ArrayList<>();
        for (int planIndex = 0; planIndex < notesPerPlan.length; planIndex++) {
            UUID stratumId = childless ? reviewSetId : UUID.randomUUID();
            if (!childless) {
                NoteCollectionEntity child = new NoteCollectionEntity();
                child.setId(stratumId);
                child.setOwnerUserId(userId);
                child.setParentCollectionId(reviewSetId);
                children.add(child);
            }
            List<NoteCollectionItemEntity> items = new ArrayList<>();
            List<StudyPackEntity> packs = new ArrayList<>();
            for (int position = 0; position < notesPerPlan[planIndex]; position++) {
                UUID noteId = UUID.randomUUID();
                UUID packId = UUID.randomUUID();
                StudyPackEntity pack = buildStudyPack(packId, noteId, userId);
                pack.setTitle("Plan " + planIndex + " note " + position);
                pack.setStatus(StudyPackStatus.DONE);
                packs.add(pack);
                allPacks.add(pack);
                NoteCollectionItemEntity item = new NoteCollectionItemEntity();
                item.setId(UUID.randomUUID());
                item.setCollectionId(stratumId);
                item.setNoteId(noteId);
                item.setPosition(position);
                items.add(item);
                lenient().when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(packId, userId))
                        .thenReturn(Optional.of(pack));
            }
            lenient().when(noteCollectionItemRepository.findByCollectionIdOrderByPositionAsc(stratumId))
                    .thenReturn(items);
            packsByPlan.add(packs);
        }
        lenient().when(noteCollectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(reviewSetId, userId))
                .thenReturn(children);
        lenient().when(studyPackRepository.findByOwnerUserIdAndNoteIdInAndStatus(
                eq(userId), anyCollection(), eq(StudyPackStatus.DONE)
        )).thenAnswer(invocation -> {
            Collection<UUID> noteIds = invocation.getArgument(1);
            // Honours the status the test set on each pack: a note whose Study Pack is not DONE is a
            // member of the Review Set but is NOT eligible material, which is the rule under test.
            return allPacks.stream()
                    .filter(pack -> noteIds.contains(pack.getNoteId()))
                    .filter(pack -> pack.getStatus() == StudyPackStatus.DONE)
                    .toList();
        });
        return packsByPlan;
    }

    /** Which Subject Plan (by index into the {@code stubReviewSet} result) each sampled source came from. */
    private static List<Integer> planIndexesOf(List<LongExamSourceNoteRef> refs, List<List<StudyPackEntity>> packsByPlan) {
        return refs.stream()
                .map(ref -> {
                    for (int planIndex = 0; planIndex < packsByPlan.size(); planIndex++) {
                        boolean inPlan = packsByPlan.get(planIndex).stream()
                                .anyMatch(pack -> pack.getId().toString().equals(ref.studyPackId()));
                        if (inPlan) {
                            return planIndex;
                        }
                    }
                    return -1;
                })
                .toList();
    }

    /**
     * Makes only the named source titles generate; every other source throws, so the resilient fan-out
     * records a partial assembly exactly the way a real per-source LLM failure does.
     */

    @Test
    void startSession_boardExamFailsOnTheSOURCEFloorEvenWhenItHasEnoughQuestions() {
        // ⚠️ THE TWO FLOORS MUST BE PINNED SEPARATELY. The only below-floor test fails BOTH at once ("5
        // questions and 1 source"), so deleting either floor alone left the suite green — the other one
        // still fired. This case clears the QUESTION floor and fails only the SOURCE floor.
        // Arithmetic: a 2-note pool samples 2, so each source is asked for 30/2 = 15. The primary alone
        // yields 15 questions — comfortably over the floor of 10 — while contributing 1 source against a
        // minimum of min(2, 2) = 2.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 1, 1);
        StudyPackEntity primary = packsByPlan.get(0).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);
        stubBoardExamSourceOutcomes(Set.of(primary.getTitle()));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primary.getId().toString(), userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString()));
        dispatchedTask.run();

        // The per-source ask proves the QUESTION floor was never the trigger: the primary alone was asked
        // for 15, well over the floor of 10. A FAILED session holds no quiz, so the ask is the evidence.
        ArgumentCaptor<Integer> perSourceCount = ArgumentCaptor.forClass(Integer.class);
        verify(quizGenerationService, atLeastOnce()).generateBoardExamQuiz(
                any(), any(), any(), any(), perSourceCount.capture(), any(), any(StudyPackGenerationContext.class));
        assertThat(perSourceCount.getAllValues().get(0)).isGreaterThanOrEqualTo(10);

        QuickReviewSessionEntity persisted = savedSessionsById.get(UUID.fromString(response.sessionId()));
        assertThat(persisted.getStatus()).isEqualTo(QuickReviewSessionStatus.FAILED);
        verify(userUsageService).reverseBoardExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
    }

    @Test
    void startSession_boardExamFailsOnTheQUESTIONFloorEvenWhenEnoughSourcesContribute() {
        // The mirror case: clears the SOURCE floor and fails only the QUESTION floor.
        // Arithmetic: a 10-note pool samples 10, so each source is asked for 30/10 = 3. Two succeeding
        // sources clear the minimum of min(10, 2) = 2 while yielding only 6 questions, under the floor of 10.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 5, 5);
        StudyPackEntity primary = packsByPlan.get(0).get(0);
        StudyPackEntity second = packsByPlan.get(1).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);
        stubBoardExamSourceOutcomes(Set.of(primary.getTitle(), second.getTitle()));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primary.getId().toString(), userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString()));
        dispatchedTask.run();

        QuickReviewSessionEntity persisted = savedSessionsById.get(UUID.fromString(response.sessionId()));
        assertThat(persisted.getStatus()).isEqualTo(QuickReviewSessionStatus.FAILED);
        verify(userUsageService).reverseBoardExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
    }

    private void stubBoardExamSourceOutcomes(Set<String> succeedingTitles) {
        lenient().when(quizGenerationService.generateBoardExamQuiz(
                any(), any(), any(), any(), anyInt(), any(), any(StudyPackGenerationContext.class)
        )).thenAnswer(invocation -> {
            String title = invocation.getArgument(0, String.class);
            if (!succeedingTitles.contains(title)) {
                throw new IllegalStateException("source generation failed for " + title);
            }
            return buildQuizWithPrefix("Src" + title, invocation.getArgument(4, Integer.class));
        });
    }

    /** Generation context plus a per-source Board Exam quiz stub sized to whatever the sampler asks for. */

    @Test
    void startSession_boardExamNeverEmitsAQuestionAlreadyOnTheNotesQuizTab() {
        // ⚠️ THE PACK'S SAVED QUIZ IS A HARD FILTER, NOT A PROMPT HINT. Passing those questions to the
        // generator only ASKS it not to repeat them. The synchronous path also added them to the dedup set;
        // moving generation off the transaction dropped that, leaving only the request. It matters beyond
        // duplication: those questions sit on the note's Quiz tab WITH their answers, and Board Exam writes
        // ConceptHealth — so a leak both hands over the answer key and corrupts a mastery signal locked
        // since v0.37.0 to genuine assessment.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 4, 4);
        StudyPackEntity primary = packsByPlan.get(0).get(0);
        String leakedQuestion = "Question already visible on the Quiz tab";
        for (List<StudyPackEntity> plan : packsByPlan) {
            for (StudyPackEntity pack : plan) {
                pack.setQuiz(List.of(new QuizItem(leakedQuestion, List.of("A", "B", "C", "D"), 0, "Cells", "Explanation")));
            }
        }

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);
        // The generator returns the learner's own saved question first — exactly what the filter must stop.
        lenient().when(quizGenerationService.generateBoardExamQuiz(
                any(), any(), any(), any(), anyInt(), any(), any(StudyPackGenerationContext.class)
        )).thenAnswer(invocation -> {
            int count = invocation.getArgument(4, Integer.class);
            List<QuizItem> generated = new ArrayList<>();
            generated.add(new QuizItem(leakedQuestion, List.of("A", "B", "C", "D"), 0, "Cells", "Explanation"));
            generated.addAll(buildQuizWithPrefix("Fresh" + UUID.randomUUID(), Math.max(0, count - 1)));
            return generated;
        });

        challengeQuizService.startSession(
                primary.getId().toString(), userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString()));
        // Generation is dispatched, not run inline — the capture is deliberate so the GENERATING hand-off
        // is observable. Run it here so this test exercises the real assembled exam.
        assertThat(dispatchedTask).isNotNull();
        dispatchedTask.run();

        QuickReviewSessionEntity persisted = savedSessionsById.values().stream()
                .reduce((first, second) -> second)
                .orElseThrow();
        // ⚠️ THE FIRST TWO ASSERTIONS ARE WHAT STOP THIS PASSING VACUOUSLY. If generation had failed the
        // session would hold an EMPTY quiz and doesNotContain would be trivially true — the exact
        // "claims more than it proves" shape this release keeps tripping over.
        assertThat(persisted.getStatus()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        List<QuizItem> persistedQuiz = QuizSessionStateUtils.extractQuiz(persisted.getSessionState());
        assertThat(persistedQuiz).isNotEmpty();
        assertThat(persistedQuiz).extracting(QuizItem::question).doesNotContain(leakedQuestion);
    }

    @Test
    void startSession_boardExamStillEnforcesTheAiRateLimit() {
        // ⚠️ The rate limit lived inside the try-block that Board Exam no longer reaches: moving generation
        // off the transaction added an early return, which made the old assertAllowed call site DEAD code.
        // The mode silently lost a cost and abuse control on a PRO path and nothing failed.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 4, 4);
        StudyPackEntity primary = packsByPlan.get(0).get(0);

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);

        challengeQuizService.startSession(
                primary.getId().toString(), userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString()));

        verify(aiRateLimitService).assertAllowed(eq(userId), any(PlanType.class), eq("challenge-quiz"));
    }


    @Test
    void startSession_boardExamChargesInsideTheRequestTransactionNotInAnAfterCommitCallback() {
        // ⚠️ THIS TEST EXISTS BECAUSE THE WHOLE UNIT SUITE WAS STRUCTURALLY BLIND TO A PRODUCTION-BREAKING
        // DEFECT. This class is @Transactional at CLASS level, so startSession ALWAYS runs with an active
        // transaction and the afterCommit branch ALWAYS fires in production. The charge used to live in
        // that callback, where a PROPAGATION_REQUIRED write joins the already-committed transaction and
        // throws — so every Board Exam start returned 500, the session row committed as GENERATING carrying
        // boardExamQuotaReserved=true, and the sweeper then refunded BOTH meters for a charge that never
        // happened, handing back quota spent on genuine Challenge Quiz sessions.
        // ⚠️ MockitoExtension has no transaction manager, so isSynchronizationActive() is false and every
        // other test takes the inline fallback — the branch that NEVER runs in production. Binding a real
        // synchronization here makes the production branch executable, which is the only reason this test
        // can see the defect at all. Deleting the charge from the transactional body fails it.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 4, 4);
        StudyPackEntity primary = packsByPlan.get(0).get(0);
        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            challengeQuizService.startSession(
                    primary.getId().toString(), userId,
                    new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString()));

            // Both meters must already be charged when startSession returns — i.e. inside the transaction —
            // NOT deferred to a callback that cannot legally write.
            verify(userUsageService).incrementChallengeQuizGeneration(eq(userId), any(OffsetDateTime.class));
            verify(userUsageService).incrementBoardExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
            // Generation is still deferred: nothing dispatched until the transaction commits.
            verify(studyPackGenerationTaskDispatcher, never()).execute(any(Runnable.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }


    @Test
    void startSession_boardExamFromASubjectPlanResolvesUpToItsParentReviewSet() {
        // ⚠️ THIS IS THE RELEASE'S HEADLINE MECHANISM AND IT HAD ZERO COVERAGE. A learner reaches Board Exam
        // from whichever collection page they were on, which is normally a CHILD Subject Plan. Using the
        // claimed collection directly makes the childless branch fire and ships "assess across the plan you
        // came from" — which is LONG EXAM's job. Board Exam's identity is the whole Review Set.
        // Every other test passes the top-level set id, so none of them exercises the walk; replacing the
        // parent lookup with `reviewSet = claimed` left the entire suite green.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        List<List<StudyPackEntity>> packsByPlan = stubReviewSet(userId, reviewSetId, false, 4, 4);
        StudyPackEntity primary = packsByPlan.get(0).get(0);

        // The learner launches from the FIRST Subject Plan, not from the Review Set.
        UUID subjectPlanId = UUID.randomUUID();
        NoteCollectionEntity subjectPlan = new NoteCollectionEntity();
        subjectPlan.setId(subjectPlanId);
        subjectPlan.setOwnerUserId(userId);
        subjectPlan.setParentCollectionId(reviewSetId);
        when(noteCollectionRepository.findByIdAndOwnerUserId(subjectPlanId, userId))
                .thenReturn(Optional.of(subjectPlan));

        stubBoardExamStartDependencies(userId, primary.getId(), primary);
        stubReviewSetBoardExamGeneration(userId);

        challengeQuizService.startSession(
                primary.getId().toString(), userId,
                new ChallengeQuizStartRequest("board_exam", null, subjectPlanId.toString()));

        // The walk happened: the PARENT's children were read as strata. Had the claim been used directly,
        // findOrderedChildren would have been asked for the Subject Plan's (empty) children instead.
        verify(noteCollectionRepository).findOrderedChildrenByParentCollectionIdAndOwnerUserId(reviewSetId, userId);
        // And the sample spans both Subject Plans, not just the one launched from.
        QuickReviewSessionEntity persisted = savedSessionsById.values().stream()
                .reduce((first, second) -> second).orElseThrow();
        assertThat(persisted.getSessionState()).containsKey("sourceNoteRefs");
    }

    @Test
    void startSession_boardExamFromASubjectPlanWhoseParentIsNotOwnedIsRefused() {
        // ⚠️ The parent walk re-verifies ownership. A child of a Review Set you do not own must never be a
        // route into someone else's curriculum, and the code comment claims exactly that — so it is pinned.
        UUID userId = UUID.randomUUID();
        UUID foreignReviewSetId = UUID.randomUUID();
        UUID subjectPlanId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(UUID.randomUUID(), UUID.randomUUID(), userId);

        NoteCollectionEntity subjectPlan = new NoteCollectionEntity();
        subjectPlan.setId(subjectPlanId);
        subjectPlan.setOwnerUserId(userId);
        subjectPlan.setParentCollectionId(foreignReviewSetId);
        when(noteCollectionRepository.findByIdAndOwnerUserId(subjectPlanId, userId))
                .thenReturn(Optional.of(subjectPlan));
        // The parent belongs to someone else, so the owner-scoped lookup finds nothing.
        when(noteCollectionRepository.findByIdAndOwnerUserId(foreignReviewSetId, userId))
                .thenReturn(Optional.empty());
        stubBoardExamStartDependencies(userId, primary.getId(), primary);

        assertThatThrownBy(() -> challengeQuizService.startSession(
                primary.getId().toString(), userId,
                new ChallengeQuizStartRequest("board_exam", null, subjectPlanId.toString())
        )).isInstanceOf(InvalidBoardExamSourceException.class);

        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
    }


    @Test
    void startSession_boardExamCountsANoteSharedByTwoSubjectPlansOnlyOnce() {
        // ⚠️ A note can belong to TWO Subject Plans of the same Review Set. candidateNoteIds is distinct,
        // but the eligible POOL was not: the shared note produced two entries carrying the SAME StudyPack,
        // and round-robin drew it from both buckets — so the exam reports more sources than it has and
        // contributingSourceCount double-counts one note, letting a SINGLE note satisfy the
        // two-contributing-sources assembly floor that exists to prevent exactly that.
        // ⚠️ HONEST LIMITATION: this test documents the property but does NOT yet discriminate — removing
        // the dedupe leaves it green, so the fixture is not producing the two-plans-one-note pool shape it
        // intends. The production fix is correct by construction (first occurrence wins, keyed by study
        // pack id); the guard is not earned. Recorded rather than claimed, and listed in RELEASES.md.
        UUID userId = UUID.randomUUID();
        UUID reviewSetId = UUID.randomUUID();
        UUID sharedNoteId = UUID.randomUUID();
        UUID otherNoteId = UUID.randomUUID();
        StudyPackEntity shared = buildStudyPack(UUID.randomUUID(), sharedNoteId, userId);
        StudyPackEntity other = buildStudyPack(UUID.randomUUID(), otherNoteId, userId);

        NoteCollectionEntity reviewSet = new NoteCollectionEntity();
        reviewSet.setId(reviewSetId);
        reviewSet.setOwnerUserId(userId);
        when(noteCollectionRepository.findByIdAndOwnerUserId(reviewSetId, userId)).thenReturn(Optional.of(reviewSet));

        List<NoteCollectionEntity> plans = new ArrayList<>();
        List<List<UUID>> noteIdsPerPlan = List.of(List.of(sharedNoteId), List.of(sharedNoteId, otherNoteId));
        for (List<UUID> planNoteIds : noteIdsPerPlan) {
            UUID planId = UUID.randomUUID();
            NoteCollectionEntity plan = new NoteCollectionEntity();
            plan.setId(planId);
            plan.setOwnerUserId(userId);
            plan.setParentCollectionId(reviewSetId);
            plans.add(plan);
            List<NoteCollectionItemEntity> items = new ArrayList<>();
            for (int position = 0; position < planNoteIds.size(); position++) {
                NoteCollectionItemEntity item = new NoteCollectionItemEntity();
                item.setNoteId(planNoteIds.get(position));
                item.setPosition(position);
                items.add(item);
            }
            when(noteCollectionItemRepository.findByCollectionIdOrderByPositionAsc(planId)).thenReturn(items);
        }
        when(noteCollectionRepository.findOrderedChildrenByParentCollectionIdAndOwnerUserId(reviewSetId, userId))
                .thenReturn(plans);
        when(studyPackRepository.findByOwnerUserIdAndNoteIdInAndStatus(eq(userId), any(), any()))
                .thenReturn(List.of(shared, other));

        stubBoardExamStartDependencies(userId, shared.getId(), shared);
        stubReviewSetBoardExamGeneration(userId);

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                shared.getId().toString(), userId,
                new ChallengeQuizStartRequest("board_exam", null, reviewSetId.toString()));

        // Two distinct notes exist, so the exam must draw exactly two distinct sources — never the shared
        // note twice.
        assertThat(response.sourceNoteRefs())
                .extracting(LongExamSourceNoteRef::studyPackId)
                .doesNotHaveDuplicates();
        assertThat(response.sourceNoteRefs()).hasSize(2);
    }

    private void stubReviewSetBoardExamGeneration(UUID userId) {
        lenient().when(generationContextResolver.resolveForStudyPack(eq(userId), any(StudyPackEntity.class)))
                .thenReturn(new StudyPackGenerationContext(
                        LearnerLevel.BOARD_EXAM_REVIEW,
                        "Nursing",
                        "Nursing",
                        List.of()
                ));
        lenient().when(quizGenerationService.generateBoardExamQuiz(
                any(), any(), any(), any(), anyInt(), any(), any(StudyPackGenerationContext.class)
        )).thenAnswer(invocation -> buildQuizWithPrefix(
                "Src" + UUID.randomUUID(),
                invocation.getArgument(4, Integer.class)
        ));
        lenient().when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> recordSession(invocation.getArgument(0)));
    }

    private void stubBoardExamStartDependencies(UUID userId, UUID studyPackId, StudyPackEntity studyPack) {
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.countByUserIdAndSessionModeAndStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any(),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.PRO,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
    }

    private void stubChallengeUsagePeriod(UUID userId, PlanType planType) {
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        planType,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(UserUsageService.MonthlyUsage.zero());
    }
}
