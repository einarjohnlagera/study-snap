package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ChallengeQuizCompleteRequest;
import com.studysnap.backend.dto.GenerateMoreChallengeQuizResponse;
import com.studysnap.backend.dto.QuizSessionReviewResponse;
import com.studysnap.backend.dto.ChallengeQuizStartRequest;
import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.dto.LongExamSourceNoteRef;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.InvalidBoardExamSourceException;
import com.studysnap.backend.exception.NotEnoughNewQuestionsException;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.InvalidChallengeQuizDifficultyException;
import com.studysnap.backend.exception.InvalidChallengeQuizModeException;
import com.studysnap.backend.exception.MonthlyBoardExamLimitReachedException;
import com.studysnap.backend.exception.MonthlyChallengeQuizLimitReachedException;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.util.QuizSessionStateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeQuizServiceTest {

    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private QuizGenerationService quizGenerationService;
    @Mock
    private LlmStudyPackService llmStudyPackService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private FeatureGateService featureGateService;
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
    private ConceptHealthService conceptHealthService;

    private ChallengeQuizService challengeQuizService;

    @BeforeEach
    void setUp() {
        lenient().when(examQuestionPoolService.sampleQuestions(any(UUID.class), any(), anyInt(), any()))
                .thenReturn(Optional.empty());
        lenient().when(noteRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        challengeQuizService = new ChallengeQuizService(
                studyPackRepository,
                noteRepository,
                quickReviewSessionRepository,
                quizGenerationService,
                subscriptionService,
                featureGateService,
                new StudySnapProperties(),
                userUsageService,
                billingUsagePeriodService,
                authService,
                analyticsService,
                aiRateLimitService,
                activityTrackingService,
                generationContextResolver,
                examQuestionPoolService,
                conceptHealthService
        );
    }

    @Test
    void startSession_reusesExistingInProgressSession_withoutCreatingDuplicate() {
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
                        "timerStartedAtEpochSeconds", 0L,
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
    void startSession_reusesExistingGeneratingSession_withoutCallingLlmAgain() {
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

    @Test
    void startSession_tracksAnalyticsWhenCreatingNewChallengeQuiz() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity previousQuickReview = new QuickReviewSessionEntity();
        previousQuickReview.setScorePercentage(BigDecimal.valueOf(40));

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
                5,
                "easy",
                generationContext
        )).thenReturn(List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q3", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q4", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q5", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation")
        ));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(studyPackId.toString(), userId, null);

        assertThat(response.sessionId()).isNotNull();
        assertThat(response.mode()).isEqualTo("challenge");
        verify(aiRateLimitService).assertAllowed(userId, PlanType.FREE, "challenge-quiz");
        verify(generationContextResolver).resolveForStudyPack(userId, studyPack);
        verify(quizGenerationService, never()).generateBoardExamQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.CHALLENGE_QUIZ_STARTED), eq(studyPackId), any());
    }

    @Test
    void startSession_acceptsBoardExamModeForProPlanUsingStandardChallengeQuotaAndBoardCap() {
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
                new ChallengeQuizStartRequest("hard", "board_exam", null)
        );

        assertThat(response.mode()).isEqualTo("board_exam");
        assertThat(response.selectedDifficulty()).isEqualTo("mixed");
        assertThat(response.monthlyLimit()).isEqualTo(10);
        assertThat(response.usedThisMonth()).isEqualTo(1);
        assertThat(response.boardExamMonthlyLimit()).isEqualTo(10);
        assertThat(response.boardExamUsedThisMonth()).isEqualTo(1);
        verify(generationContextResolver).resolveForStudyPack(userId, studyPack);
        verify(quizGenerationService, never()).generateChallengeQuiz(any(), any(), any(), any(), anyInt(), any(), any());
        verify(userUsageService).incrementChallengeQuizGeneration(eq(userId), any(OffsetDateTime.class));
        verify(userUsageService).incrementBoardExamGenerationBy(eq(userId), eq(1), any(OffsetDateTime.class));
        verify(featureGateService, never()).checkFeatureAccess(PlanType.PRO, Feature.DIFFICULTY_SELECTION);
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
                List.of()
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
                LearnerLevel.COLLEGE
        )).thenReturn(Optional.of(buildQuiz(12)));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeQuizStartResponse response = challengeQuizService.startSession(
                studyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest(null, "board_exam", null)
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
                new ChallengeQuizStartRequest(null, "board_exam", List.of(
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
                new ChallengeQuizStartRequest(null, "board_exam", List.of(
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
                null,
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
                null,
                "board_exam",
                List.of(additionalStudyPackId.toString())
        );

        assertThatThrownBy(() -> challengeQuizService.startSession(id, userId, request))
                .isInstanceOf(InvalidBoardExamSourceException.class)
                .hasMessage("Add a subject to this note before adding more sources");
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
                null,
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

        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest(null, "board_exam", null);
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
                new ChallengeQuizStartRequest(null, "board_exam", List.of(additionalStudyPackId.toString()))
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
        )).thenReturn(50L);
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

        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest(null, "board_exam", null);
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
                noteRepository,
                quickReviewSessionRepository,
                new QuizGenerationService(llmStudyPackService, properties),
                subscriptionService,
                featureGateService,
                properties,
                userUsageService,
                billingUsagePeriodService,
                authService,
                analyticsService,
                aiRateLimitService,
                activityTrackingService,
                generationContextResolver,
                examQuestionPoolService,
                conceptHealthService
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
                new ChallengeQuizStartRequest(null, "board_exam", null)
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

        challengeQuizService.completeSession(
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
        )).thenReturn(List.of(
                new QuizItem("Q6 new", List.of("A", "B", "C", "D"), "A", "Concept2", "Explanation"),
                new QuizItem("Q7 new", List.of("A", "B", "C", "D"), "A", "Concept2", "Explanation"),
                new QuizItem("Q8 new", List.of("A", "B", "C", "D"), "A", "Concept2", "Explanation"),
                new QuizItem("Q9 new", List.of("A", "B", "C", "D"), "A", "Concept2", "Explanation"),
                new QuizItem("Q10 new", List.of("A", "B", "C", "D"), "A", "Concept2", "Explanation")
        ));
        when(quickReviewSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GenerateMoreChallengeQuizResponse response = challengeQuizService.generateMoreQuestions(sessionId.toString(), userId);

        assertThat(response.newQuestions()).hasSize(5);
        verify(generationContextResolver).resolveForStudyPack(userId, studyPack);
        assertThat(response.totalQuestions()).isEqualTo(10);
        assertThat(session.getTotalQuestions()).isEqualTo(10);
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
        )).thenReturn(List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation")
        ));

        String id = sessionId.toString();
        assertThatThrownBy(() -> challengeQuizService.generateMoreQuestions(id, userId))
                .isInstanceOf(NotEnoughNewQuestionsException.class);
    }

    @Test
    void startSession_throwsTypedExceptionForInvalidDifficulty() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);

        String id = studyPackId.toString();
        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest("expert", null, null);
        assertThatThrownBy(() -> challengeQuizService.startSession(
                id,
                userId,
                request
        ))
                .isInstanceOf(InvalidChallengeQuizDifficultyException.class)
                .hasMessage("Difficulty must be easy, medium, or hard.");
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
        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest(null, "oral_exam", null);
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
                List.of(answer, answer.replace("'", ""))
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
}
