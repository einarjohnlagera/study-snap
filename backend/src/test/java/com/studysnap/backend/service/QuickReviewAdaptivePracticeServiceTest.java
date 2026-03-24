package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.PlanType;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

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
    private LlmStudyPackService llmStudyPackService;
    @Mock
    private ActivityTrackingService activityTrackingService;
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
                llmStudyPackService,
                activityTrackingService,
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

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionMode.ADAPTIVE,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.of(existing));

        QuickReviewAdaptiveQuizResponse response = adaptivePracticeService.generateAdaptiveQuiz(studyPackId.toString(), userId);

        verify(authService).requireEmailVerified(userId);
        verify(featureGateService).checkFeatureAccess(userId, com.studysnap.backend.entity.Feature.ADAPTIVE_QUIZ);
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(llmStudyPackService, never()).generateAdaptivePracticeQuiz(any(), any(), any(), any(), any(), anyInt());
        verify(aiRateLimitService, never()).assertAllowed(any(), any(), any());
        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
        assertThat(response.sessionId()).isEqualTo(sessionId.toString());
        assertThat(response.quiz()).hasSize(1);
    }

    @Test
    void generateAdaptiveQuiz_whenSaveHitsDuplicateInProgress_returnsResumedSession() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID resumedSessionId = UUID.randomUUID();

        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity latestQuickReview = buildCompletedQuickReviewSource(userId, studyPackId, noteId);
        QuickReviewSessionEntity resumed = buildInProgressAdaptiveSession(resumedSessionId, userId, studyPackId, noteId);

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionMode.ADAPTIVE,
                QuickReviewSessionStatus.IN_PROGRESS
        )).thenReturn(Optional.empty(), Optional.of(resumed));
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionMode.QUICK_REVIEW,
                Pageable.ofSize(1)
        )).thenReturn(List.of(latestQuickReview));
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                userId,
                studyPackId,
                QuickReviewSessionMode.CHALLENGE,
                Pageable.ofSize(1)
        )).thenReturn(List.of());
        when(activityEventRepository.countByUserIdAndActivityTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(userId),
                eq(ActivityType.STARTED_ADAPTIVE_PRACTICE),
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
        when(llmStudyPackService.generateAdaptivePracticeQuiz(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt()
        )).thenReturn(List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation"),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), "B", "Concept", "Explanation"),
                new QuizItem("Q3", List.of("A", "B", "C", "D"), "C", "Concept", "Explanation"),
                new QuizItem("Q4", List.of("A", "B", "C", "D"), "D", "Concept", "Explanation"),
                new QuizItem("Q5", List.of("A", "B", "C", "D"), "A", "Concept", "Explanation")
        ));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate in-progress session"));

        QuickReviewAdaptiveQuizResponse response = adaptivePracticeService.generateAdaptiveQuiz(studyPackId.toString(), userId);

        assertThat(response.sessionId()).isEqualTo(resumedSessionId.toString());
        assertThat(response.quiz()).hasSize(1);
        verify(aiRateLimitService).assertAllowed(userId, PlanType.PREMIUM, "adaptive-practice");
        verify(userUsageService, never()).incrementAdaptiveQuizGeneration(any(UUID.class), any(OffsetDateTime.class));
        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
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

    private QuickReviewSessionEntity buildCompletedQuickReviewSource(UUID userId, UUID studyPackId, UUID noteId) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.QUICK_REVIEW);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentRound(QuickReviewRound.RETRY);
        session.setCurrentQuestionIndex(5);
        session.setTotalQuestions(5);
        session.setCorrectAnswers(2);
        session.setScorePercentage(new BigDecimal("40.00"));
        session.setRetryCount(1);
        session.setSessionMetadata(Map.of("weakConcepts", List.of("Concept")));
        session.setCreatedAt(OffsetDateTime.now().minusHours(2));
        session.setCompletedAt(OffsetDateTime.now().minusHours(1));
        return session;
    }
}
