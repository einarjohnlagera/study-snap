package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AdaptivePracticeCompleteResponse;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.util.QuizSessionStateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
class QuickReviewAdaptivePracticeServiceTest {

    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
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
    private AuthService authService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private AiRateLimitService aiRateLimitService;
    @Mock
    private StudyPackGenerationContextResolver generationContextResolver;
    @Mock
    private ConceptHealthService conceptHealthService;

    private QuickReviewAdaptivePracticeService adaptivePracticeService;

    @BeforeEach
    void setUp() {
        adaptivePracticeService = new QuickReviewAdaptivePracticeService(
                studyPackRepository,
                quickReviewSessionRepository,
                quizGenerationService,
                activityTrackingService,
                subscriptionService,
                featureGateService,
                new StudySnapProperties(),
                userUsageService,
                authService,
                analyticsService,
                aiRateLimitService,
                generationContextResolver,
                conceptHealthService
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
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);

        QuickReviewAdaptiveQuizResponse response = adaptivePracticeService.generateAdaptiveQuiz(studyPackId.toString(), userId);

        verify(authService).requireEmailVerified(userId);
        verify(featureGateService).checkFeatureAccess(PlanType.PRO, Feature.ADAPTIVE_QUIZ);
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
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);

        QuickReviewAdaptiveQuizResponse response = adaptivePracticeService.generateAdaptiveQuiz(studyPackId.toString(), userId);

        assertThat(response.sessionId()).isEqualTo(sessionId.toString());
        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.GENERATING);
        assertThat(response.quiz()).isEmpty();
        verify(featureGateService).checkFeatureAccess(PlanType.PRO, Feature.ADAPTIVE_QUIZ);
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
                new QuizGenerationService(llmStudyPackService, properties),
                activityTrackingService,
                subscriptionService,
                featureGateService,
                properties,
                userUsageService,
                authService,
                analyticsService,
                aiRateLimitService,
                generationContextResolver,
                conceptHealthService
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
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
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
        when(conceptHealthService.getDueConcepts(eq(userId), eq(studyPackId), any(), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Nursing",
                studyPack.getSubject(),
                studyPack.getTags() == null ? List.of() : List.of(studyPack.getTags())
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
        verify(generationContextResolver).resolveForStudyPack(userId, studyPack);
        verify(llmStudyPackService, never()).generateAdaptivePracticeQuiz(any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void generateAdaptiveQuiz_generatesFromDueConceptsWhenWeakConceptsAreEmpty() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        studyPack.setKeyConcepts(List.of("Old Concept"));
        QuickReviewSessionEntity latestQuickReview = buildCompletedSourceSession(
                userId,
                studyPackId,
                noteId,
                List.of()
        );
        List<QuizItem> generatedQuiz = buildGeneratedQuiz("Old Concept", 5);

        stubAdaptiveGeneration(userId, studyPackId, studyPack, latestQuickReview, generatedQuiz);
        when(conceptHealthService.getDueConcepts(eq(userId), eq(studyPackId), eq(List.of("Old Concept")), any(OffsetDateTime.class)))
                .thenReturn(List.of("Old Concept"));

        QuickReviewAdaptiveQuizResponse response = adaptivePracticeService.generateAdaptiveQuiz(
                studyPackId.toString(),
                userId
        );

        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(response.weakConcepts()).containsExactly("Old Concept");
        assertThat(response.quiz()).hasSize(5);
        assertThat(response.conceptSelectionReasons()).containsOnly("DUE");
        verify(quizGenerationService).generateAdaptivePracticeQuiz(
                eq("Pack"),
                eq("Summary"),
                eq(List.of("Old Concept")),
                eq(List.of("Old Concept")),
                any(),
                eq(5),
                any()
        );
    }

    @Test
    void generateAdaptiveQuiz_mergesDueConceptsBeforeWeakConcepts() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        studyPack.setKeyConcepts(List.of("Old Concept", "Current Concept"));
        QuickReviewSessionEntity latestQuickReview = buildCompletedSourceSession(
                userId,
                studyPackId,
                noteId,
                List.of("Weak Concept", "Old Concept")
        );
        List<QuizItem> generatedQuiz = buildGeneratedQuiz("Old Concept", 5);

        stubAdaptiveGeneration(userId, studyPackId, studyPack, latestQuickReview, generatedQuiz);
        when(conceptHealthService.getDueConcepts(eq(userId), eq(studyPackId), eq(List.of("Old Concept", "Current Concept")), any(OffsetDateTime.class)))
                .thenReturn(List.of("Old Concept"));

        QuickReviewAdaptiveQuizResponse response = adaptivePracticeService.generateAdaptiveQuiz(
                studyPackId.toString(),
                userId
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> focusConceptsCaptor = ArgumentCaptor.forClass(List.class);
        verify(quizGenerationService).generateAdaptivePracticeQuiz(
                eq("Pack"),
                eq("Summary"),
                eq(List.of("Old Concept", "Current Concept")),
                focusConceptsCaptor.capture(),
                any(),
                eq(5),
                any()
        );
        assertThat(focusConceptsCaptor.getValue()).containsExactly("Old Concept", "Weak Concept");
        assertThat(response.conceptSelectionReasons()).containsOnly("BOTH");
    }

    @Test
    void generateAdaptiveQuiz_persistsParallelDueWeakBothAndUnmatchedSelectionReasons() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        studyPack.setKeyConcepts(List.of("Due Concept", "Both Concept", "Unmatched Concept"));
        QuickReviewSessionEntity latestQuickReview = buildCompletedSourceSession(
                userId,
                studyPackId,
                noteId,
                List.of("Weak Concept", "Both Concept")
        );
        List<QuizItem> generatedQuiz = buildGeneratedQuiz(List.of(
                "Due Concept",
                "Weak Concept",
                "Both Concept",
                "Unmatched Concept",
                "Due Concept",
                "Weak Concept",
                "Both Concept"
        ));

        stubAdaptiveGeneration(userId, studyPackId, studyPack, latestQuickReview, generatedQuiz);
        when(conceptHealthService.getDueConcepts(
                eq(userId),
                eq(studyPackId),
                eq(List.of("Due Concept", "Both Concept", "Unmatched Concept")),
                any(OffsetDateTime.class)
        )).thenReturn(List.of("Due Concept", "Both Concept"));

        QuickReviewAdaptiveQuizResponse response = adaptivePracticeService.generateAdaptiveQuiz(
                studyPackId.toString(),
                userId
        );

        assertThat(response.conceptSelectionReasons())
                .hasSameSizeAs(response.quiz())
                .containsExactly("DUE", "WEAK", "BOTH", null, "DUE", "WEAK", "BOTH");
        assertThat(QuizSessionStateUtils.extractConceptSelectionReasons(
                responseSessionState(),
                response.quiz().size()
        )).containsExactlyElementsOf(response.conceptSelectionReasons());
    }

    @Test
    void getAdaptiveQuizSession_restoresSelectionReasonsFromSessionState() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity session = buildInProgressAdaptiveSession(
                sessionId,
                userId,
                studyPackId,
                noteId
        );
        session.setSessionState(QuizSessionStateUtils.withConceptSelectionReasons(
                session.getSessionState(),
                List.of("BOTH")
        ));

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.ADAPTIVE),
                any()
        )).thenReturn(Optional.of(session));

        QuickReviewAdaptiveQuizResponse response = adaptivePracticeService.getAdaptiveQuizSession(
                studyPackId.toString(),
                userId
        );

        assertThat(response.sessionId()).isEqualTo(sessionId.toString());
        assertThat(response.conceptSelectionReasons()).containsExactly("BOTH");
    }

    @Test
    void generateAdaptiveQuiz_allowsGenerationWhenUsageIsBelowFreeMonthlyLimit() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity sourceSession = buildCompletedSourceSession(
                userId, studyPackId, noteId, List.of("Weak Concept")
        );
        List<QuizItem> generatedQuiz = buildGeneratedQuiz("Weak Concept", 5);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.ADAPTIVE), any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.QUICK_REVIEW), any()
        )).thenReturn(List.of(sourceSession));
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(List.of());
        when(conceptHealthService.getDueConcepts(eq(userId), eq(studyPackId), any(), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        // freeMonthlyAdaptivePracticeLimit defaults to 3; used = 2 → one slot remaining → should pass
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(
                        OffsetDateTime.now(), OffsetDateTime.now().plusMonths(1),
                        0, 0, 2, 0, 0, 0, 0
                ));
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW, "Nursing", studyPack.getSubject(), List.of()
        ));
        when(quizGenerationService.generateAdaptivePracticeQuiz(any(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(generatedQuiz);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuickReviewAdaptiveQuizResponse response = adaptivePracticeService.generateAdaptiveQuiz(
                studyPackId.toString(), userId
        );

        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
    }

    @Test
    void generateAdaptiveQuiz_throwsWhenFreeMonthlyLimitExhausted() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity sourceSession = buildCompletedSourceSession(
                userId, studyPackId, noteId, List.of("Weak Concept")
        );

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.ADAPTIVE), any()
        )).thenReturn(Optional.empty());
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.QUICK_REVIEW), any()
        )).thenReturn(List.of(sourceSession));
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(List.of());
        when(conceptHealthService.getDueConcepts(eq(userId), eq(studyPackId), any(), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        // freeMonthlyAdaptivePracticeLimit defaults to 3; used = 3 → limit reached → should throw
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new UserUsageService.MonthlyUsage(
                        OffsetDateTime.now(), OffsetDateTime.now().plusMonths(1),
                        0, 0, 3, 0, 0, 0, 0
                ));

        assertThatThrownBy(() -> adaptivePracticeService.generateAdaptiveQuiz(studyPackId.toString(), userId))
                .isInstanceOf(AppException.class);
    }

    @Test
    void completeAdaptiveSession_recordsCorrectConceptNamesWhenPresent() {
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

        adaptivePracticeService.completeAdaptiveSession(
                sessionId.toString(),
                userId,
                1,
                1,
                20,
                List.of("Trigonometric derivatives")
        );

        verify(conceptHealthService).recordCorrectAnswers(
                eq(userId),
                eq(studyPackId),
                eq(List.of("Trigonometric derivatives")),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void completeAdaptiveSession_recordsMissedConceptsFromStoredSelections() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressAdaptiveSession(sessionId, userId, studyPackId, noteId);
        session.setTotalQuestions(2);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                List.of(
                        new QuizItem("Adaptive Q1", List.of("A", "B", "C", "D"), "A", "Mastered", "Explanation"),
                        new QuizItem("Adaptive Q2", List.of("A", "B", "C", "D"), "B", "Needs Practice", "Explanation")
                ),
                Map.of("selectedChoices", Map.of("0", "A", "1", "C"))
        ));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId,
                userId,
                QuickReviewSessionMode.ADAPTIVE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(conceptHealthService.recordIncorrectAnswers(
                eq(userId),
                eq(studyPackId),
                eq(List.of("Needs Practice")),
                any(OffsetDateTime.class)
        )).thenReturn(List.of("Needs Practice"));

        AdaptivePracticeCompleteResponse response = adaptivePracticeService.completeAdaptiveSession(
                sessionId.toString(),
                userId,
                1,
                2,
                20,
                List.of("Ignored fallback")
        );

        verify(conceptHealthService).recordCorrectAnswers(
                eq(userId),
                eq(studyPackId),
                eq(List.of("Mastered")),
                any(OffsetDateTime.class)
        );
        verify(conceptHealthService).recordIncorrectAnswers(
                eq(userId),
                eq(studyPackId),
                eq(List.of("Needs Practice")),
                any(OffsetDateTime.class)
        );
        assertThat(response.twiceMissedConcepts()).containsExactly("Needs Practice");
    }

    @Test
    void completeAdaptiveSession_allWrongRecordsMissesAndIgnoresFrontendCorrectList() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildInProgressAdaptiveSession(sessionId, userId, studyPackId, noteId);
        session.setTotalQuestions(2);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                List.of(
                        new QuizItem("Adaptive Q1", List.of("A", "B", "C", "D"), "A", "Mastered", "Explanation"),
                        new QuizItem("Adaptive Q2", List.of("A", "B", "C", "D"), "B", "Needs Practice", "Explanation")
                ),
                Map.of("selectedChoices", Map.of("0", "B", "1", "C"))
        ));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId,
                userId,
                QuickReviewSessionMode.ADAPTIVE
        )).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        adaptivePracticeService.completeAdaptiveSession(
                sessionId.toString(),
                userId,
                0,
                2,
                20,
                List.of("Ignored fallback")
        );

        verify(conceptHealthService, never()).recordCorrectAnswers(any(), any(), any(), any());
        verify(conceptHealthService).recordIncorrectAnswers(
                eq(userId),
                eq(studyPackId),
                eq(List.of("Mastered", "Needs Practice")),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void completeAdaptiveSession_doesNotRecordConceptHealthWhenCorrectConceptNamesAreNull() {
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

        adaptivePracticeService.completeAdaptiveSession(
                sessionId.toString(),
                userId,
                1,
                1,
                20,
                null
        );

        verify(conceptHealthService, never()).recordCorrectAnswers(any(), any(), any(), any());
        verify(conceptHealthService, never()).recordIncorrectAnswers(any(), any(), any(), any());
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

    private void stubAdaptiveGeneration(
            UUID userId,
            UUID studyPackId,
            StudyPackEntity studyPack,
            QuickReviewSessionEntity latestQuickReview,
            List<QuizItem> generatedQuiz
    ) {
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
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
        )).thenReturn(List.of(latestQuickReview));
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.CHALLENGE),
                any()
        )).thenReturn(List.of());
        when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class))).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Nursing",
                studyPack.getSubject(),
                studyPack.getTags() == null ? List.of() : List.of(studyPack.getTags())
        ));
        when(quizGenerationService.generateAdaptivePracticeQuiz(any(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(generatedQuiz);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private QuickReviewSessionEntity buildCompletedSourceSession(
            UUID userId,
            UUID studyPackId,
            UUID noteId,
            List<String> weakConcepts
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.QUICK_REVIEW);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCompletedAt(OffsetDateTime.now().minusMinutes(5));
        session.setSessionMetadata(Map.of("weakConcepts", weakConcepts));
        return session;
    }

    private List<QuizItem> buildGeneratedQuiz(String concept, int questionCount) {
        List<QuizItem> quiz = new ArrayList<>();
        for (int index = 0; index < questionCount; index++) {
            quiz.add(new QuizItem(
                    "Generated Q " + index,
                    List.of("A", "B", "C", "D"),
                    "A",
                    concept,
                    "Explanation"
            ));
        }
        return quiz;
    }

    private List<QuizItem> buildGeneratedQuiz(List<String> concepts) {
        List<QuizItem> quiz = new ArrayList<>();
        for (int index = 0; index < concepts.size(); index++) {
            quiz.add(new QuizItem(
                    "Generated Q " + index,
                    List.of("A", "B", "C", "D"),
                    "A",
                    concepts.get(index),
                    "Explanation"
            ));
        }
        return quiz;
    }

    private Map<String, Object> responseSessionState() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<QuickReviewSessionEntity> sessionCaptor = ArgumentCaptor.forClass(
                QuickReviewSessionEntity.class
        );
        verify(quickReviewSessionRepository, org.mockito.Mockito.atLeastOnce()).save(sessionCaptor.capture());
        return sessionCaptor.getValue().getSessionState();
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
