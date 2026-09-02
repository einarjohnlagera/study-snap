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
import com.studysnap.backend.entity.ActivityType;
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
import com.studysnap.backend.exception.MultiNoteChallengeQuizSourceNotAllowedException;
import com.studysnap.backend.service.model.GeneratedChallengeQuizContent;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.service.model.StudyPackQuizMastery;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.util.QuizSessionStateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
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

    private ChallengeQuizService challengeQuizService;

    @BeforeEach
    void setUp() {
        lenient().when(examQuestionPoolService.sampleQuestions(any(UUID.class), any(), anyInt(), any()))
                .thenReturn(Optional.empty());
        lenient().when(challengeQuizQuestionBankService.claimEligibleQuestions(
                any(UUID.class), any(UUID.class), any(), any(UUID.class), any(), anyInt()
        )).thenReturn(List.of());
        lenient().when(officialChallengeQuizTemplateService.copyTemplateQuestions(
                any(UUID.class), any(UUID.class), any(), any(UUID.class), any(), anyInt()
        )).thenReturn(List.of());
        lenient().when(noteRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        challengeQuizService = new ChallengeQuizService(
                studyPackRepository,
                userRepository,
                noteRepository,
                planSourcedExamVerifier,
                quickReviewSessionRepository,
                quizGenerationService,
                subscriptionService,
                new StudySnapProperties(),
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
                studyPackQuizMasteryService
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
            QuickReviewSessionEntity saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

        assertThat(expired.getStatus()).isEqualTo(QuickReviewSessionStatus.FORFEITED);
        assertThat(response.sessionId()).isNotEqualTo(expiredSessionId.toString());
        assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(freshQuiz);
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
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(bankedQuiz);
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
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(templateQuiz);
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
                .thenAnswer(invocation -> invocation.getArgument(0));

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
                .thenAnswer(invocation -> invocation.getArgument(0));
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
        assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(missedQuestions);
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
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubChallengeUsagePeriod(userId, PlanType.FREE);

        ChallengeQuizStartResponse response = challengeQuizService.startRedoMissedSession(studyPackId.toString(), userId);

        assertThat(staleOrdinarySession.getStatus()).isEqualTo(QuickReviewSessionStatus.FORFEITED);
        assertThat(response.sessionId()).isNotEqualTo(staleSessionId.toString());
        assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(missedQuestions);
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
            QuickReviewSessionEntity session = invocation.getArgument(0);
            if (session == staleOrdinarySession) {
                throw new IllegalStateException("save failed");
            }
            return session;
        });
        stubChallengeUsagePeriod(userId, PlanType.FREE);

        ChallengeQuizStartResponse response = challengeQuizService.startRedoMissedSession(studyPackId.toString(), userId);

        assertThat(response.sessionId()).isNotEqualTo(staleSessionId.toString());
        assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(missedQuestions);
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
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.quiz()).hasSize(DEFAULT_ADAPTIVE_QUESTION_COUNT);
        assertThat(response.quiz()).containsAll(bankedQuiz);
        assertThat(response.quiz()).containsAll(generatedQuiz);
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
                .thenAnswer(invocation -> invocation.getArgument(0));

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
                .thenAnswer(invocation -> invocation.getArgument(0));

        // shuffleQuestionOrderPreservingMatchingGroups groups the MATCHING block into a single unit before
        // shuffling, so a correct implementation always keeps it contiguous and in order on every draw — this
        // loop doesn't compute an escape-rate reduction against the current code. It exists to catch a future
        // regression to a flat/naive shuffle, where each of the 30 independently-randomized draws is another
        // chance to expose scattering (a single draw alone
        // would have a real chance of accidentally landing contiguous — the flat-shuffle regression it guards
        // is caught only across many draws, not the first one).
        for (int attempt = 0; attempt < 30; attempt++) {
            ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

            assertThat(response.quiz()).containsExactlyInAnyOrderElementsOf(generatedQuiz);
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
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                studyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null)
        );

        assertThat(response.mode()).isEqualTo("board_exam");
        assertThat(response.selectedDifficulty()).isEqualTo("mixed");
        assertThat(response.quiz()).hasSize(12);
        assertThat(response.timeLimitSeconds()).isEqualTo(12 * 60);
        assertThat(response.monthlyLimit()).isEqualTo(10);
        assertThat(response.usedThisMonth()).isEqualTo(1);
        assertThat(response.boardExamMonthlyLimit()).isEqualTo(10);
        assertThat(response.boardExamUsedThisMonth()).isEqualTo(1);
        verify(generationContextResolver).resolveForStudyPack(userId, studyPack);
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
                .thenAnswer(invocation -> invocation.getArgument(0));

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
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primaryStudyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", List.of(
                        secondStudyPackId.toString(),
                        thirdStudyPackId.toString()
                ))
        );

        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(response.quiz()).hasSize(30);
        assertThat(response.sourceNoteRefs())
                .extracting(LongExamSourceNoteRef::studyPackId)
                .containsExactly(primaryStudyPackId.toString(), secondStudyPackId.toString(), thirdStudyPackId.toString());
        assertThat(response.sourceNoteRefs())
                .extracting(LongExamSourceNoteRef::questionCount)
                .containsExactly(10, 10, 10);
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
                .thenAnswer(invocation -> invocation.getArgument(0));

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
        assertThat(response.quiz()).hasSize(24);
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
    void startSession_boardExamFromAPlanAcceptsMixedSubjectMembers() {
        // ⚠️ THE SAME LIVE DEFECT AS THE LONG EXAM PATH, ON THE CTA THAT BOARD_EXAM PROFILES GET.
        // The plan CTA routes to this screen with ?collectionId=, the prescreen pre-selects the plan's
        // notes regardless of subject, and this service rejected them with "must share the same subject".
        // Fixing only Long Exam would have left the release's headline false for the majority profile.
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, primaryNoteId, userId);
        primary.setSubject("Biology");
        StudyPackEntity additional = buildStudyPack(additionalStudyPackId, additionalNoteId, userId);
        additional.setSubject("Engineering Mathematics");

        stubBoardExamStartDependencies(userId, primaryStudyPackId, primary);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId))
                .thenReturn(Optional.of(additional));
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(primaryNoteId, additionalNoteId)));

        String id = primaryStudyPackId.toString();
        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest(
                "board_exam",
                List.of(additionalStudyPackId.toString()),
                collectionId.toString()
        );

        // Asserts the SOURCE gate specifically, not overall success: this fixture stops short of full
        // generation, and a broader assertion would either fail for an unrelated reason or start passing
        // for one. The property under test is that plan members are no longer refused for their subject.
        Throwable thrown = catchThrowable(() -> challengeQuizService.startSession(id, userId, request));
        assertThat(thrown).isNotInstanceOf(InvalidBoardExamSourceException.class);
    }

    @Test
    void startSession_boardExamFromAPlanStillRejectsANonMemberOfADifferentSubject() {
        // ⚠️ Per-source, exactly as on the Long Exam path: naming a plan must not let one legitimate
        // member smuggle in a note the plan does not contain.
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID outsiderStudyPackId = UUID.randomUUID();
        UUID outsiderNoteId = UUID.randomUUID();
        StudyPackEntity primary = buildStudyPack(primaryStudyPackId, primaryNoteId, userId);
        primary.setSubject("Biology");
        StudyPackEntity outsider = buildStudyPack(outsiderStudyPackId, outsiderNoteId, userId);
        outsider.setSubject("Engineering Mathematics");

        stubBoardExamStartDependencies(userId, primaryStudyPackId, primary);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(outsiderStudyPackId, userId))
                .thenReturn(Optional.of(outsider));
        // The plan contains only the primary.
        when(planSourcedExamVerifier.resolvePlanMemberNoteIds(eq(collectionId.toString()), eq(userId), any()))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(primaryNoteId)));

        String id = primaryStudyPackId.toString();
        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest(
                "board_exam",
                List.of(outsiderStudyPackId.toString()),
                collectionId.toString()
        );

        assertThatThrownBy(() -> challengeQuizService.startSession(id, userId, request))
                .isInstanceOf(InvalidBoardExamSourceException.class);
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
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                studyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", List.of(additionalStudyPackId.toString()))
        );

        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(response.quiz()).hasSize(24);
        assertThat(response.boardExamUsedThisMonth()).isEqualTo(10);
        assertThat(response.sourceNoteRefs())
                .extracting(LongExamSourceNoteRef::questionCount)
                .containsExactly(12, 12);
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
                studyPackQuizMasteryService
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
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeQuizStartResponse response = mockModeChallengeQuizService.startSession(
                studyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("board_exam", null)
        );

        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(response.mode()).isEqualTo("board_exam");
        assertThat(response.selectedDifficulty()).isEqualTo("mixed");
        assertThat(response.quiz()).hasSize(12);
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
                .thenAnswer(invocation -> invocation.getArgument(0));
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
                .thenAnswer(invocation -> invocation.getArgument(0));

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
    void completeSession_recordsOnlyFullyCorrectChallengeConceptsToConceptHealth() {
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
                .thenAnswer(invocation -> invocation.getArgument(0));
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
                .thenAnswer(invocation -> invocation.getArgument(0));
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
                .thenAnswer(invocation -> invocation.getArgument(0));

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
                .thenAnswer(invocation -> invocation.getArgument(0));

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
                .thenAnswer(invocation -> invocation.getArgument(0));

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
                .thenAnswer(invocation -> invocation.getArgument(0));

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
                .thenAnswer(invocation -> invocation.getArgument(0));

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
        when(quickReviewSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

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
        when(quickReviewSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        challengeQuizService.generateMoreQuestions(sessionId.toString(), userId);

        List<QuizItem> finalQuiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        assertThat(finalQuiz).hasSize(10);
        assertThat(finalQuiz.subList(0, 5))
                .as("existing questions must keep their original indices so recorded answers stay attached to the right question")
                .containsExactlyElementsOf(existingQuiz);
        assertThat(finalQuiz.subList(5, 10)).containsExactlyInAnyOrderElementsOf(generatedBatch);

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
        when(quickReviewSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GenerateMoreChallengeQuizResponse response = challengeQuizService.generateMoreQuestions(sessionId.toString(), userId);

        assertThat(response.newQuestions()).containsExactlyInAnyOrderElementsOf(bankedQuiz);
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
        when(quickReviewSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GenerateMoreChallengeQuizResponse response = challengeQuizService.generateMoreQuestions(sessionId.toString(), userId);

        assertThat(response.newQuestions()).containsExactlyInAnyOrderElementsOf(templateQuiz);
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
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(), eq(6), any(), any()))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Primary", 6)))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Additional", 6)));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primaryStudyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("challenge", List.of(additionalStudyPackId.toString()), collectionId.toString())
        );

        assertThat(response.sourceNoteRefs()).hasSize(2);
        assertThat(response.maxSourceNotes()).isEqualTo(3);
        verify(userUsageService).incrementMultiNoteGeneration(eq(userId), any(OffsetDateTime.class));
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.CHALLENGE_QUIZ_STARTED),
                eq(primaryStudyPackId), metadata.capture());
        assertThat(metadata.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "sessionId", response.sessionId(),
                "questionCount", 12,
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
        List<StudyPackEntity> additionalSources = java.util.stream.IntStream.range(0, 3)
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
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                primaryStudyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest("challenge", additionalSources.stream().map(source -> source.getId().toString()).toList())
        );

        // ⚠️ FOUR, not six. This test asserted 6 as delivered, because the cap was derived from the
        // LONG EXAM question count (20/25/30) while a Challenge Quiz is far shorter. At 6 sources a
        // 12-question quiz gives 2 per source — below the three-per-source floor — and the delivered
        // stub encoded exactly that by expecting generateChallengeQuiz(..., 2, ...). A multi-note
        // session now uses a FIXED 12 questions, so the cap is a stable 4 and every source clears the
        // floor regardless of the learner's last Quick Review score.
        assertThat(response.maxSourceNotes()).isEqualTo(4);
        assertThat(response.sourceNoteRefs()).hasSize(4);
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

        assertThat(response.maxSourceNotes()).isEqualTo(4);
        // ⚠️ Structural, not incidental: the score lookup that sizes a single-note quiz is never
        // consulted for the cap at all. Asserting the number alone would still pass if the cap went
        // back to being score-derived and this fixture merely happened to land on 12 questions.
        verify(quickReviewSessionRepository, never())
                .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        any(), any(), any(), any());
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

        // Four sources is the cap for a 12-question quiz, so four ADDITIONAL (five total) is one past it.
        assertThatThrownBy(() -> challengeQuizService.startSession(primaryStudyPackId.toString(), userId,
                new ChallengeQuizStartRequest("challenge", java.util.stream.IntStream.range(0, 4)
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
        when(quizGenerationService.generateChallengeQuiz(any(), any(), any(), any(), eq(6), any(), any()))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Primary", 6)))
                .thenReturn(GeneratedChallengeQuizContent.withoutUsage(buildQuizWithPrefix("Additional", 6)));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        challengeQuizService.startSession(primaryStudyPackId.toString(), userId,
                new ChallengeQuizStartRequest("challenge", List.of(additionalStudyPackId.toString()), collectionId.toString()));

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.CHALLENGE_QUIZ_STARTED),
                eq(primaryStudyPackId), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("sourceScope", "manual");
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
