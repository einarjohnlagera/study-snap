package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ThemePreference;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.repository.ActivityEventRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuickReviewAdaptivePracticeServiceTest {

    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private ActivityEventRepository activityEventRepository;
    @Mock
    private QuizGenerationService quizGenerationService;
    @Mock
    private LlmStudyPackService llmStudyPackService;
    @Mock
    private ActivityTrackingService activityTrackingService;
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

    private QuickReviewAdaptivePracticeService adaptivePracticeService;

    @BeforeEach
    void setUp() {
        adaptivePracticeService = new QuickReviewAdaptivePracticeService(
                studyPackRepository,
                quickReviewSessionRepository,
                activityEventRepository,
                quizGenerationService,
                activityTrackingService,
                subscriptionService,
                featureGateService,
                new StudySnapProperties(),
                userUsageService,
                billingUsagePeriodService,
                authService,
                analyticsService,
                aiRateLimitService
        );
    }

    @Test
    void generateAdaptiveQuiz_reusesExistingInProgressSession() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity existing = buildInProgressAdaptiveSession(sessionId, userId, studyPackId, noteId);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.ADAPTIVE),
                any()
        )).thenReturn(Optional.of(existing));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PREMIUM);

        QuickReviewAdaptiveQuizResponse response = adaptivePracticeService.generateAdaptiveQuiz(studyPackId.toString(), userId);

        verify(authService).requireEmailVerified(userId);
        verify(featureGateService).checkFeatureAccess(PlanType.PREMIUM, Feature.ADAPTIVE_QUIZ);
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(quizGenerationService, never()).generateAdaptivePracticeQuiz(any(), any(), any(), any(), any(), anyInt(), any());
        verify(aiRateLimitService, never()).assertAllowed(any(), any(), any());
        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
        assertThat(response.sessionId()).isEqualTo(sessionId.toString());
        assertThat(response.quiz()).hasSize(1);
    }

    @Test
    void generateAdaptiveQuiz_reusesExistingGeneratingSession_withoutCallingLlmAgain() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity generating = buildGeneratingAdaptiveSession(sessionId, userId, studyPackId, noteId);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.ADAPTIVE),
                any()
        )).thenReturn(Optional.of(generating));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PREMIUM);

        QuickReviewAdaptiveQuizResponse response = adaptivePracticeService.generateAdaptiveQuiz(studyPackId.toString(), userId);

        assertThat(response.sessionId()).isEqualTo(sessionId.toString());
        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        assertThat(response.quiz()).isEmpty();
        verify(featureGateService).checkFeatureAccess(PlanType.PREMIUM, Feature.ADAPTIVE_QUIZ);
        verify(aiRateLimitService, never()).assertAllowed(any(), any(), any());
        verify(quizGenerationService, never()).generateAdaptivePracticeQuiz(any(), any(), any(), any(), any(), anyInt(), any());
        verify(userUsageService, never()).incrementAdaptiveQuizGeneration(any(UUID.class), any(OffsetDateTime.class));
        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
    }

    @Test
    void forfeitAdaptiveSession_marksSessionForfeitedWithoutRefundingCreditOrCompletingIt() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressAdaptiveSession(sessionId, userId, studyPackId, noteId);

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId,
                userId,
                QuickReviewSessionMode.ADAPTIVE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = adaptivePracticeService.forfeitAdaptiveSession(sessionId.toString(), userId);

        assertThat(response.message()).isEqualTo("Adaptive Practice session forfeited.");
        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FORFEITED);
        assertThat(session.getCompletedAt()).isNull();
        verify(userUsageService, never()).incrementAdaptiveQuizGeneration(any(UUID.class), any(OffsetDateTime.class));
        verify(activityTrackingService, never()).recordActivity(userId, ActivityType.STARTED_ADAPTIVE_PRACTICE, studyPackId);
    }

    @Test
    void generateAdaptiveQuiz_mockModeCompletesSessionWithoutCallingRealLlm() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        StudySnapProperties properties = new StudySnapProperties();
        properties.getQuizGeneration().setMode("mock");
        QuickReviewAdaptivePracticeService mockModeAdaptivePracticeService = new QuickReviewAdaptivePracticeService(
                studyPackRepository,
                quickReviewSessionRepository,
                activityEventRepository,
                new QuizGenerationService(llmStudyPackService, properties),
                activityTrackingService,
                subscriptionService,
                featureGateService,
                properties,
                userUsageService,
                billingUsagePeriodService,
                authService,
                analyticsService,
                aiRateLimitService
        );
        QuickReviewSessionEntity latestChallenge = new QuickReviewSessionEntity();
        latestChallenge.setId(UUID.randomUUID());
        latestChallenge.setUserId(userId);
        latestChallenge.setStudyPackId(studyPackId);
        latestChallenge.setNoteId(noteId);
        latestChallenge.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        latestChallenge.setStatus(QuickReviewSessionStatus.COMPLETED);
        latestChallenge.setCompletedAt(OffsetDateTime.now().minusMinutes(5));
        latestChallenge.setSessionMetadata(Map.of("weakConcepts", List.of("Electrolyte Imbalance", "Fluid Shift")));

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PREMIUM);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.ADAPTIVE),
                any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.QUICK_REVIEW),
                any()
        )).thenReturn(List.of());
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(List.of(latestChallenge));
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.PREMIUM,
                        BillingCycle.MONTHLY,
                        OffsetDateTime.now().minusDays(5),
                        OffsetDateTime.now().plusDays(25),
                        2026,
                        3
                ));
        when(activityEventRepository.countByUserIdAndActivityTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(ActivityType.STARTED_ADAPTIVE_PRACTICE),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(0L);
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(authService.getMe(userId)).thenReturn(new MeResponse(
                userId.toString(),
                "user@example.com",
                null,
                "Test",
                "User",
                "Test User",
                null,
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Nursing",
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
                PlanType.PREMIUM,
                null
        ));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuickReviewAdaptiveQuizResponse response = mockModeAdaptivePracticeService.generateAdaptiveQuiz(
                studyPackId.toString(),
                userId
        );

        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(response.weakConcepts()).containsExactly("Electrolyte Imbalance", "Fluid Shift");
        assertThat(response.quiz()).hasSize(5);
        verify(llmStudyPackService, never()).generateAdaptivePracticeQuiz(any(), any(), any(), any(), any(), anyInt(), any());
    }

    private StudyPackEntity buildStudyPack(UUID studyPackId, UUID noteId, UUID ownerUserId) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setNoteId(noteId);
        studyPack.setOwnerUserId(ownerUserId);
        studyPack.setTitle("Pack");
        studyPack.setSummary("Summary");
        studyPack.setKeyConcepts(List.of("Concept"));
        studyPack.setQuiz(List.of(new QuizItem(
                "Base Q",
                List.of("A", "B", "C", "D"),
                "A",
                "Concept",
                "Explanation"
        )));
        return studyPack;
    }

    private QuickReviewSessionEntity buildInProgressAdaptiveSession(UUID sessionId, UUID userId, UUID studyPackId, UUID noteId) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.ADAPTIVE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(1);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO);
        session.setRetryCount(0);
        session.setCreatedAt(OffsetDateTime.now().minusMinutes(2));
        session.setSessionMetadata(Map.of("weakConcepts", List.of("Concept")));
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                List.of(new QuizItem("Adaptive Q", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation")),
                null
        ));
        return session;
    }

    private QuickReviewSessionEntity buildGeneratingAdaptiveSession(UUID sessionId, UUID userId, UUID studyPackId, UUID noteId) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.ADAPTIVE);
        session.setStatus(QuickReviewSessionStatus.GENERATING);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(0);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO);
        session.setRetryCount(0);
        session.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        session.setSessionMetadata(Map.of("weakConcepts", List.of("Concept")));
        session.setSessionState(null);
        return session;
    }
}
