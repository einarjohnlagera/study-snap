package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ChallengeQuizCompleteRequest;
import com.studysnap.backend.dto.GenerateMoreChallengeQuizResponse;
import com.studysnap.backend.dto.QuizSessionReviewResponse;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.ChallengeQuizStartRequest;
import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.exception.AppException;
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
import com.studysnap.backend.entity.ThemePreference;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.exception.InvalidChallengeQuizDifficultyException;
import com.studysnap.backend.exception.InvalidChallengeQuizModeException;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeQuizServiceTest {

    @Mock
    private StudyPackRepository studyPackRepository;
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

    private ChallengeQuizService challengeQuizService;

    @BeforeEach
    void setUp() {
        challengeQuizService = new ChallengeQuizService(
                studyPackRepository,
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
                activityTrackingService
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
        when(authService.getMe(userId)).thenReturn(buildMeResponse(userId, LearnerLevel.BOARD_EXAM_REVIEW, "Nursing"));
        when(quizGenerationService.generateChallengeQuiz(
                "Pack title",
                "Summary",
                List.of("Concept"),
                List.of("Practice?"),
                5,
                "easy",
                new StudyPackGenerationContext(
                        LearnerLevel.BOARD_EXAM_REVIEW,
                        "Nursing",
                        null,
                        List.of()
                )
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
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.CHALLENGE_QUIZ_STARTED), eq(studyPackId), any());
    }

    @Test
    void startSession_acceptsBoardExamModeForFreePlanUsingStandardChallengeQuota() {
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
                        PlanType.FREE,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(authService.getMe(userId)).thenReturn(buildMeResponse(userId, LearnerLevel.COLLEGE, "Engineering"));
        when(quizGenerationService.generateChallengeQuiz(
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
                new ChallengeQuizStartRequest("hard", "board_exam")
        );

        assertThat(response.mode()).isEqualTo("board_exam");
        assertThat(response.selectedDifficulty()).isEqualTo("mixed");
        assertThat(response.monthlyLimit()).isEqualTo(5);
        verify(featureGateService, never()).checkFeatureAccess(PlanType.FREE, Feature.DIFFICULTY_SELECTION);
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
                activityTrackingService
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
                        PlanType.FREE,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(authService.getMe(userId)).thenReturn(buildMeResponse(userId, LearnerLevel.BOARD_EXAM_REVIEW, "Nursing"));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChallengeQuizStartResponse response = mockModeChallengeQuizService.startSession(
                studyPackId.toString(),
                userId,
                new ChallengeQuizStartRequest(null, "board_exam")
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

    private MeResponse buildMeResponse(UUID userId, LearnerLevel learnerLevel, String courseProgram) {
        return new MeResponse(
                userId.toString(),
                "user@example.com",
                null,
                "Test",
                "User",
                "Test User",
                "testuser",
                null,
                learnerLevel,
                courseProgram,
                true,
                null,
                null,
                null,
                null,
                true,
                true,
                ThemePreference.SYSTEM,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                0L,
                UserRole.USER,
                UserStatus.ACTIVE,
                PlanType.FREE,
                null
        );
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
        when(authService.getMe(userId)).thenReturn(buildMeResponse(userId, LearnerLevel.COLLEGE, "Engineering"));
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
        when(authService.getMe(userId)).thenReturn(buildMeResponse(userId, LearnerLevel.COLLEGE, "Engineering"));
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
        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest("expert", null);
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
        ChallengeQuizStartRequest request = new ChallengeQuizStartRequest(null, "oral_exam");
        assertThatThrownBy(() -> challengeQuizService.startSession(
                id,
                userId,
                request
        ))
                .isInstanceOf(InvalidChallengeQuizModeException.class)
                .hasMessage("Challenge Quiz mode must be challenge or board_exam.");
    }
}
