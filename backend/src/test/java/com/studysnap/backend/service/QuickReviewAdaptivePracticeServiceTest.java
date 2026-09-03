package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AdaptivePracticeCompleteResponse;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.model.StudyPackProgressProjection;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AdaptivePracticeSessionNotFoundException;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
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
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuickReviewAdaptivePracticeServiceTest {

    private static final String ANALYTICS_METADATA_ENTRY = "entry";
    private static final String DASHBOARD_TODAY_FOCUS_ENTRY = "dashboard-today-focus";
    private static final String UNKNOWN_ENTRY = "caller-controlled-value";
    private static final String DIRECT_ENTRY = "direct";

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
    @Mock
    private NoteCollectionRepository noteCollectionRepository;
    @Mock
    private NoteCollectionItemRepository noteCollectionItemRepository;
    @Mock
    private LongExamPlanSourceSampler longExamPlanSourceSampler;

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
                conceptHealthService,
                noteCollectionRepository,
                noteCollectionItemRepository,
                longExamPlanSourceSampler
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
                conceptHealthService,
                noteCollectionRepository,
                noteCollectionItemRepository,
                longExamPlanSourceSampler
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
        assertThat(response.focusConcepts()).extracting("concept")
                .containsExactly("Electrolyte Imbalance", "Fluid Shift");
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
        assertThat(response.focusConcepts()).extracting("concept").containsExactly("Old Concept");
        assertThat(response.quiz()).hasSize(5);
        assertThat(response.focusConcepts()).extracting("selectionReason").containsOnly("DUE");
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
    void generateAdaptiveQuiz_recordsKnownEntryInStartedAnalytics() {
        Map<String, Object> metadata = generateAndCaptureAnalyticsMetadata(DASHBOARD_TODAY_FOCUS_ENTRY);

        assertThat(metadata).containsEntry(ANALYTICS_METADATA_ENTRY, DASHBOARD_TODAY_FOCUS_ENTRY);
    }

    @Test
    void generateAdaptiveQuiz_normalizesUnknownEntryToDirect() {
        Map<String, Object> metadata = generateAndCaptureAnalyticsMetadata(UNKNOWN_ENTRY);

        assertThat(metadata)
                .containsEntry(ANALYTICS_METADATA_ENTRY, DIRECT_ENTRY)
                .doesNotContainValue(UNKNOWN_ENTRY);
    }

    @Test
    void generateAdaptiveQuiz_recordsAbsentEntryAsDirect() {
        Map<String, Object> metadata = generateAndCaptureAnalyticsMetadata(null);

        assertThat(metadata).containsEntry(ANALYTICS_METADATA_ENTRY, DIRECT_ENTRY);
    }

    @Test
    void generateAdaptiveQuiz_succeedsWhenAnalyticsTrackingFails() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity sourceSession = buildCompletedSourceSession(
                userId,
                studyPackId,
                noteId,
                List.of("Weak Concept")
        );
        stubAdaptiveGeneration(
                userId,
                studyPackId,
                studyPack,
                sourceSession,
                buildGeneratedQuiz("Weak Concept", 5)
        );
        doThrow(new RuntimeException("analytics unavailable"))
                .when(analyticsService)
                .trackEvent(any(), any(), any(), any());

        QuickReviewAdaptiveQuizResponse response = adaptivePracticeService.generateAdaptiveQuiz(
                studyPackId.toString(),
                userId,
                "challenge-quiz-result"
        );

        assertThat(response.status()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        assertThat(response.quiz()).hasSize(5);
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
        // "Old Concept" is due AND weak -> BOTH; "Weak Concept" is weak only -> WEAK.
        // Asserting the concept->reason PAIRS, not just the reasons: the old assertion read
        // containsOnly("BOTH") because conceptSelectionReasons was parallel to the QUIZ (5 items all
        // about "Old Concept"), which lost the per-concept mapping entirely. The structured list
        // carries it, so pin it.
        assertThat(response.focusConcepts())
                .extracting("concept", "selectionReason")
                .containsExactly(
                        tuple("Old Concept", "BOTH"),
                        tuple("Weak Concept", "WEAK")
                );
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

        assertThat(response.focusConcepts()).extracting("selectionReason")
                .containsExactly("DUE", "BOTH", "WEAK");
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
        assertThat(response.focusConcepts()).extracting("selectionReason").containsExactly((String) null);
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


    // ---------------------------------------------------------------------------------------------
    // Plan- and Review-Set-scoped Adaptive Practice (v0.107.0 item 2).
    // ---------------------------------------------------------------------------------------------

    @Test
    void collectionScoped_keepsTheSameConceptSeparatePerSourcePack() {
        // THE fixture for the aggregation decision: the SAME concept string in TWO packs. A fixture
        // using different names passes under a Set<String> merge and proves nothing.
        CollectionFixture f = collectionFixture(2);
        stubCollectionFocus(f, 0, List.of("Shear Force"), List.of());
        stubCollectionFocus(f, 1, List.of("Shear Force"), List.of());
        stubCollectionGeneration(f, 2);

        QuickReviewAdaptiveQuizResponse response =
                adaptivePracticeService.generateAdaptiveQuizForCollection(
                        f.collectionId.toString(), f.userId, "collection-detail");

        assertThat(response.focusConcepts())
                .extracting("concept", "sourceStudyPackId")
                .containsExactly(
                        tuple("Shear Force", f.packs.get(0).getId().toString()),
                        tuple("Shear Force", f.packs.get(1).getId().toString())
                );
    }

    @Test
    void collectionScoped_reportsTheInterviewSessionWhenEveryEligiblePackIsOccupiedByOne() {
        // ⚠️ REPLACES A VACUOUS TEST. The previous version used a SINGLE-pack fixture whose
        // findTop...ByStudyPackId stub returned empty while the list query returned an active
        // session -- a state no single table can produce -- so it exercised the focusEligible early
        // return, not the guard, and its assertion (sessionId != interviewId) was satisfied by null.
        // Stubs here are CONSISTENT: both queries see the same sessions.
        CollectionFixture f = collectionFixture(1);
        stubCollectionFocus(f, 0, List.of("Shear Force"), List.of());
        QuickReviewSessionEntity interview = interviewSessionOn(f, 0);
        seeActiveSessions(f, interview);

        QuickReviewAdaptiveQuizResponse response =
                adaptivePracticeService.generateAdaptiveQuizForCollection(
                        f.collectionId.toString(), f.userId, "collection-detail");

        // The dedicated message must actually be REACHABLE -- it used to be dead code behind the
        // focusEligible filter, and the learner was told "no weak concepts" when they had some.
        assertThat(response.message()).contains("Interview Practice session in progress");
        assertThat(response.sessionId()).isNull();
        verify(userUsageService, never()).incrementAdaptiveQuizGeneration(any(), any());
    }

    @Test
    void collectionScoped_anchorsOnAnUnoccupiedPackRatherThanBlockingTheWholePlan() {
        CollectionFixture f = collectionFixture(2);
        stubCollectionFocus(f, 0, List.of("Shear Force"), List.of());
        stubCollectionFocus(f, 1, List.of("Bending Moment"), List.of());
        seeActiveSessions(f, interviewSessionOn(f, 0));
        stubCollectionGeneration(f, 1);

        QuickReviewAdaptiveQuizResponse response =
                adaptivePracticeService.generateAdaptiveQuizForCollection(
                        f.collectionId.toString(), f.userId, "collection-detail");

        // Pack 0 is occupied, so the session anchors on pack 1 instead of refusing the whole plan.
        assertThat(response.studyPackId()).isEqualTo(f.packs.get(1).getId().toString());
        verify(userUsageService).incrementAdaptiveQuizGeneration(eq(f.userId), any());
    }

    @Test
    void collectionScoped_neverReturnsAForeignNoteScopedSessionAsThePlanSession() {
        // The blocker: a note-scoped session on the plan's first eligible pack used to come back
        // labelled as the plan's, while the plan's own in-progress endpoint reported nothing --
        // a permanent dead end for that plan.
        CollectionFixture f = collectionFixture(1);
        stubCollectionFocus(f, 0, List.of("Shear Force"), List.of());
        QuickReviewSessionEntity noteScoped = buildInProgressAdaptiveSession(
                UUID.randomUUID(), f.userId, f.packs.get(0).getId(), f.noteIds.get(0));
        seeActiveSessions(f, noteScoped);

        QuickReviewAdaptiveQuizResponse response =
                adaptivePracticeService.generateAdaptiveQuizForCollection(
                        f.collectionId.toString(), f.userId, "collection-detail");

        assertThat(response.sessionId()).isNotEqualTo(noteScoped.getId().toString());
        assertThat(response.sessionId()).isNull();
        assertThat(response.message()).contains("another Adaptive Practice session");
        verify(userUsageService, never()).incrementAdaptiveQuizGeneration(any(), any());
    }

    @Test
    void completeAdaptiveSession_doesNotCompleteAnInterviewPracticeSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        QuickReviewSessionEntity interview =
                buildInProgressAdaptiveSession(sessionId, userId, studyPackId, noteId);
        Map<String, Object> state = new LinkedHashMap<>(interview.getSessionState());
        state.put("subMode", "INTERVIEW");
        interview.setSessionState(state);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId, userId, QuickReviewSessionMode.ADAPTIVE))
                .thenReturn(Optional.of(interview));

        assertThatThrownBy(() -> adaptivePracticeService.completeAdaptiveSession(
                sessionId.toString(), userId, 1, 1, null, null, null, null))
                .isInstanceOf(AdaptivePracticeSessionNotFoundException.class);

        // The destructive half: the interview session must be left completable by its OWN mode.
        assertThat(interview.getStatus()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        verify(conceptHealthService, never()).recordCorrectAnswers(any(), any(), any(), any());
    }

    @Test
    void forfeitAdaptiveSession_doesNotForfeitAnInterviewPracticeSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        QuickReviewSessionEntity interview = buildInProgressAdaptiveSession(
                sessionId, userId, UUID.randomUUID(), UUID.randomUUID());
        Map<String, Object> state = new LinkedHashMap<>(interview.getSessionState());
        state.put("subMode", "INTERVIEW");
        interview.setSessionState(state);
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId, userId, QuickReviewSessionMode.ADAPTIVE))
                .thenReturn(Optional.of(interview));

        assertThatThrownBy(() -> adaptivePracticeService.forfeitAdaptiveSession(sessionId.toString(), userId))
                .isInstanceOf(AdaptivePracticeSessionNotFoundException.class);

        assertThat(interview.getStatus()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
    }

    @Test
    void collectionScoped_boundsTheSampledPackCount() {
        CollectionFixture f = collectionFixture(6);
        for (int i = 0; i < 6; i++) {
            stubCollectionFocus(f, i, List.of("Shear Force"), List.of());
        }
        stubCollectionGeneration(f, 3);

        adaptivePracticeService.generateAdaptiveQuizForCollection(
                f.collectionId.toString(), f.userId, "collection-detail");

        ArgumentCaptor<Integer> maxCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(longExamPlanSourceSampler).sample(any(), any(), maxCaptor.capture(), any());
        // A 6-pack plan must not fan out over all 6 -- the transaction decision depends on this bound.
        assertThat(maxCaptor.getValue()).isEqualTo(3);
    }

    @Test
    void collectionScoped_spendsExactlyOneQuotaUnitRegardlessOfPackCount() {
        CollectionFixture f = collectionFixture(3);
        for (int i = 0; i < 3; i++) {
            stubCollectionFocus(f, i, List.of("Shear Force"), List.of());
        }
        stubCollectionGeneration(f, 3);

        adaptivePracticeService.generateAdaptiveQuizForCollection(
                f.collectionId.toString(), f.userId, "collection-detail");

        verify(userUsageService, times(1)).incrementAdaptiveQuizGeneration(eq(f.userId), any());
    }

    @Test
    void collectionScoped_chargesNothingWhenGenerationFails() {
        CollectionFixture f = collectionFixture(1);
        stubCollectionFocus(f, 0, List.of("Shear Force"), List.of());
        stubCollectionGeneration(f, 1);
        // Override the generator to fail AFTER the session row exists, which is the case that would
        // leave a learner charged for an exam that never generated if the increment moved earlier.
        when(quizGenerationService.generateAdaptivePracticeQuiz(
                any(), any(), any(), any(), any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("llm down"));

        adaptivePracticeService.generateAdaptiveQuizForCollection(
                f.collectionId.toString(), f.userId, "collection-detail");

        verify(userUsageService, never()).incrementAdaptiveQuizGeneration(any(), any());
    }

    @Test
    void collectionScoped_resumesItsOwnSessionByRecordedCollectionIdNotByRecomputedAnchor() {
        CollectionFixture f = collectionFixture(2);
        UUID existingId = UUID.randomUUID();
        // Anchored on the SECOND pack: if resume recomputed the anchor (lowest position = pack 0) it
        // would miss this session and start a second one. Reordering a plan does exactly that.
        QuickReviewSessionEntity existing = buildInProgressAdaptiveSession(
                existingId, f.userId, f.packs.get(1).getId(), f.noteIds.get(1));
        Map<String, Object> existingState = new LinkedHashMap<>(existing.getSessionState());
        existingState.put("sourceCollectionId", f.collectionId.toString());
        existing.setSessionState(existingState);
        when(quickReviewSessionRepository.findByUserIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(f.userId), eq(QuickReviewSessionMode.ADAPTIVE), any()))
                .thenReturn(List.of(existing));
        when(studyPackRepository.findByIdAndOwnerUserId(f.packs.get(1).getId(), f.userId))
                .thenReturn(Optional.of(f.packs.get(1)));

        QuickReviewAdaptiveQuizResponse response =
                adaptivePracticeService.generateAdaptiveQuizForCollection(
                        f.collectionId.toString(), f.userId, "collection-detail");

        assertThat(response.sessionId()).isEqualTo(existingId.toString());
        verify(userUsageService, never()).incrementAdaptiveQuizGeneration(any(), any());
    }



    // ---------------------------------------------------------------------------------------------
    // v0.107.0 item 4 -- Interview Practice and Adaptive Practice share the ADAPTIVE discriminator
    // and the (user, pack, mode) unique index on active sessions. Neither may consume the other's.
    // ---------------------------------------------------------------------------------------------

    @Test
    void generateAdaptiveQuiz_neitherResumesNorForfeitsAnActiveInterviewPracticeSession() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity interview =
                buildInProgressAdaptiveSession(UUID.randomUUID(), userId, studyPackId, noteId);
        Map<String, Object> state = new LinkedHashMap<>(interview.getSessionState());
        state.put("subMode", "INTERVIEW");
        interview.setSessionState(state);

        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId))
                .thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.ADAPTIVE), any()))
                .thenReturn(Optional.of(interview));

        QuickReviewAdaptiveQuizResponse response =
                adaptivePracticeService.generateAdaptiveQuiz(studyPackId.toString(), userId);

        // Must not hand back the interview session...
        assertThat(response.sessionId()).isNull();
        assertThat(response.quiz()).isEmpty();
        // ...and must not END it either. The forfeit branch is the destructive half of this defect:
        // it would terminate a session Adaptive Practice does not own.
        assertThat(interview.getStatus()).isEqualTo(QuickReviewSessionStatus.IN_PROGRESS);
        verify(quickReviewSessionRepository, never()).save(any(QuickReviewSessionEntity.class));
        verify(userUsageService, never()).incrementAdaptiveQuizGeneration(any(), any());
    }

    @Test
    void getAdaptiveQuizSession_doesNotReturnAnInterviewPracticeSessionAsAdaptivePractice() {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity interview =
                buildInProgressAdaptiveSession(UUID.randomUUID(), userId, studyPackId, noteId);
        Map<String, Object> state = new LinkedHashMap<>(interview.getSessionState());
        state.put("subMode", "INTERVIEW");
        interview.setSessionState(state);

        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId))
                .thenReturn(Optional.of(studyPack));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.ADAPTIVE), any()))
                .thenReturn(Optional.of(interview));

        QuickReviewAdaptiveQuizResponse response =
                adaptivePracticeService.getAdaptiveQuizSession(studyPackId.toString(), userId);

        assertThat(response.sessionId()).isNull();
        assertThat(response.quiz()).isEmpty();
    }


    /** An in-progress Interview Practice session anchored on one of the fixture's packs. */
    @Test
    void completeAdaptiveSession_attributesConceptsPerSourcePackWhenSelectionsAreSubmitted() {
        // The substantive half of the wiring fix. Adaptive Practice has NO progress endpoint, so
        // nothing persists selections during the session -- if the client does not submit them the
        // breakdown is empty and everything lands on the anchor pack with no misses recorded.
        // Two packs, the SAME concept in both, NON-UNIFORM answers: A correct, B wrong.
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID packA = UUID.randomUUID();
        UUID packB = UUID.randomUUID();
        UUID noteA = UUID.randomUUID();
        StudyPackEntity a = buildStudyPack(packA, noteA, userId);
        StudyPackEntity b = buildStudyPack(packB, UUID.randomUUID(), userId);
        a.setKeyConcepts(List.of("Shear Force"));
        b.setKeyConcepts(List.of("Shear Force"));

        QuickReviewSessionEntity session = buildInProgressAdaptiveSession(sessionId, userId, packA, noteA);
        session.setSessionState(QuizSessionStateUtils.withQuiz(List.of(
                stampedItem("A1", "Shear Force", packA),
                stampedItem("B1", "Shear Force", packB)
        ), session.getSessionState()));
        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                sessionId, userId, QuickReviewSessionMode.ADAPTIVE)).thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(studyPackRepository.findByIdAndOwnerUserId(packA, userId)).thenReturn(Optional.of(a));
        lenient().when(studyPackRepository.findByIdAndOwnerUserId(packB, userId)).thenReturn(Optional.of(b));

        adaptivePracticeService.completeAdaptiveSession(
                sessionId.toString(), userId, 1, 2, null, null,
                Map.of(0, 0, 1, 1),   // index 0 correct, index 1 wrong
                Map.of());

        // Same concept string, opposite outcomes, kept apart by SOURCE PACK -- which is the whole
        // point: merging them by name would record one pack's success against the other's failure.
        //
        // Note the methods: Adaptive Practice uses recordCorrect/IncorrectAnswers, NOT the
        // ...ForKnownConcepts variants, so it applies no keyConcepts intersection. That asymmetry is
        // pre-existing and shared with Board Exam (see docs/features/exam-hub.md); it is not
        // introduced here, and bucketing by source pack is what keeps it safe.
        verify(conceptHealthService).recordCorrectAnswers(
                eq(userId), eq(packA), eq(List.of("Shear Force")), any());
        verify(conceptHealthService).recordIncorrectAnswers(
                eq(userId), eq(packB), eq(List.of("Shear Force")), any());
        verify(conceptHealthService, never()).recordIncorrectAnswers(
                eq(userId), eq(packA), any(), any());
        verify(conceptHealthService, never()).recordCorrectAnswers(
                eq(userId), eq(packB), any(), any());
    }

    private QuizItem stampedItem(String question, String keyConcept, UUID sourceStudyPackId) {
        return new QuizItem(question, List.of("A", "B", "C", "D"), 0, keyConcept, "Explanation",
                null, "MCQ", null, null, null, null, keyConcept, null, null)
                .withSourceStudyPackId(sourceStudyPackId.toString());
    }

    @Test
    void collectionScoped_loadsFullPacksOnlyForTheFocusEligibleSubset() {
        // ⚠️ THIS IS THE ASSERTION FOR WHAT ITEM 3 ACTUALLY DOES. Eligibility runs on projections so
        // a Review Set's quiz/summary JSON is not materialized to choose three packs; phase 2 then
        // loads entities for the survivors only. Widening phase 2 back to every candidate note left
        // every other test green -- the reduction is invisible unless the narrowed argument is pinned.
        CollectionFixture f = collectionFixture(4);
        // Only pack 1 has anything to practise, so only pack 1 should be loaded in full.
        stubCollectionFocus(f, 1, List.of("Shear Force"), List.of());
        stubCollectionGeneration(f, 1);

        adaptivePracticeService.generateAdaptiveQuizForCollection(
                f.collectionId.toString(), f.userId, "collection-detail");

        ArgumentCaptor<Collection<UUID>> noteIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(studyPackRepository).findByOwnerUserIdAndNoteIdInAndStatus(
                eq(f.userId), noteIdsCaptor.capture(), eq(StudyPackStatus.DONE));
        assertThat(noteIdsCaptor.getValue()).containsExactly(f.noteIds.get(1));
    }

    @Test
    void collectionScoped_neverTreatsAnotherUsersPackAsEligible() {
        // ⚠️ THE ASSERTION THE PRESSURE TEST SAID WAS MISSING. Ownership previously survived only
        // "by stub shape, not by a semantic assertion" -- every fixture had one user, so dropping an
        // owner filter changed nothing any test could see. Phase 1 now filters owner in JAVA, because
        // findProgressViewsByNoteIdIn does not filter it in SQL, so that filter IS the access
        // boundary and needs a fixture that can tell.
        CollectionFixture f = collectionFixture(1);
        stubCollectionFocus(f, 0, List.of("Shear Force"), List.of());
        stubCollectionGeneration(f, 1);

        UUID intruderUserId = UUID.randomUUID();
        UUID intruderNoteId = UUID.randomUUID();
        UUID intruderPackId = UUID.randomUUID();
        StudyPackEntity intruder = buildStudyPack(intruderPackId, intruderNoteId, intruderUserId);
        intruder.setKeyConcepts(List.of("Shear Force"));
        StudyPackProgressProjection foreign = mock(StudyPackProgressProjection.class);
        lenient().when(foreign.getId()).thenReturn(intruderPackId);
        lenient().when(foreign.getNoteId()).thenReturn(intruderNoteId);
        lenient().when(foreign.getOwnerUserId()).thenReturn(intruderUserId);
        lenient().when(foreign.getKeyConcepts()).thenReturn(List.of("Shear Force"));
        lenient().when(foreign.getStatus()).thenReturn(StudyPackStatus.DONE);

        List<StudyPackProgressProjection> withIntruder = new ArrayList<>();
        withIntruder.add(foreign);
        withIntruder.add(projectionOf(f.packs.get(0), f.userId));
        when(studyPackRepository.findProgressViewsByNoteIdIn(any())).thenReturn(withIntruder);

        QuickReviewAdaptiveQuizResponse response =
                adaptivePracticeService.generateAdaptiveQuizForCollection(
                        f.collectionId.toString(), f.userId, "collection-detail");

        // The intruder's pack must never anchor the session nor appear as a focus source.
        assertThat(response.studyPackId()).isNotEqualTo(intruderPackId.toString());
        assertThat(response.focusConcepts())
                .extracting("sourceStudyPackId")
                .doesNotContain(intruderPackId.toString());
    }

    @Test
    void collectionScoped_returnsTheServerDerivedAnchorNoteIdForNavigation() {
        // T3.1 -- noteId was asserted NOWHERE in the stack, yet it is what the new plan-scoped
        // navigation routes on. Nulling it at all 7 construction sites left the whole suite green,
        // and it was inserted by a regex that over-matched once during development.
        CollectionFixture f = collectionFixture(1);
        stubCollectionFocus(f, 0, List.of("Shear Force"), List.of());
        stubCollectionGeneration(f, 1);

        QuickReviewAdaptiveQuizResponse response =
                adaptivePracticeService.generateAdaptiveQuizForCollection(
                        f.collectionId.toString(), f.userId, "collection-detail");

        assertThat(response.noteId()).isEqualTo(f.noteIds.get(0).toString());
        assertThat(response.studyPackId()).isEqualTo(f.packs.get(0).getId().toString());
    }

    @Test
    void collectionScoped_excludesEachPacksOwnSavedQuizAndAccumulatesAcrossPacks() {
        // T3.5 -- the whole dedup mechanism was deletable with 26/26 green, because the fixture
        // generated a fresh UUID per question so nothing could ever collide. Here the generator
        // returns the SAME question text every time, so only real dedup keeps the run honest.
        CollectionFixture f = collectionFixture(2);
        stubCollectionFocus(f, 0, List.of("Shear Force"), List.of());
        stubCollectionFocus(f, 1, List.of("Bending Moment"), List.of());
        lenient().when(longExamPlanSourceSampler.sample(any(), any(), anyInt(), any()))
                .thenAnswer(invocation -> invocation.<List<LongExamPlanSourceSampler.EligiblePlanSource>>getArgument(0));

        ArgumentCaptor<List<String>> disallowedCaptor = ArgumentCaptor.forClass(List.class);
        lenient().when(quizGenerationService.generateAdaptivePracticeQuiz(
                        any(), any(), any(), any(), disallowedCaptor.capture(), anyInt(), any()))
                .thenAnswer(invocation -> List.of(new QuizItem(
                        "Repeated question", List.of("A", "B", "C", "D"), "A", "Shear Force", "Explanation")));

        adaptivePracticeService.generateAdaptiveQuizForCollection(
                f.collectionId.toString(), f.userId, "collection-detail");

        List<List<String>> disallowedPerCall = disallowedCaptor.getAllValues();
        assertThat(disallowedPerCall).hasSizeGreaterThanOrEqualTo(2);
        // Each pack excludes its OWN saved quiz...
        assertThat(disallowedPerCall.get(0)).contains("Base Q");
        // ...and the second call also carries what the first pack already produced.
        assertThat(disallowedPerCall.get(1)).contains("Repeated question");
    }

    @Test
    void collectionScoped_boundsTheFocusConceptListThatFeedsThePrompt() {
        // T3.7 -- MAX_PLAN_FOCUS_CONCEPTS could be raised to 10000 with every test green. The
        // question-count cap bounds the OUTPUT; this list is what goes INTO the prompt.
        CollectionFixture f = collectionFixture(3);
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add("Concept " + i);
        }
        for (int i = 0; i < 3; i++) {
            f.packs.get(i).setKeyConcepts(many);
            stubCollectionFocus(f, i, many, List.of());
        }
        stubCollectionGeneration(f, 3);

        QuickReviewAdaptiveQuizResponse response =
                adaptivePracticeService.generateAdaptiveQuizForCollection(
                        f.collectionId.toString(), f.userId, "collection-detail");

        assertThat(response.focusConcepts()).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void collectionScoped_inProgressReadReturnsThePlansOwnSessionAndSkipsInterviewOnes() {
        // T3.8 -- getAdaptiveQuizSessionForCollection had ZERO tests anywhere.
        CollectionFixture f = collectionFixture(1);
        QuickReviewSessionEntity planSession = buildInProgressAdaptiveSession(
                UUID.randomUUID(), f.userId, f.packs.get(0).getId(), f.noteIds.get(0));
        Map<String, Object> state = new LinkedHashMap<>(planSession.getSessionState());
        state.put("sourceCollectionId", f.collectionId.toString());
        planSession.setSessionState(state);
        // An interview session on the SAME collection, ordered FIRST. Without the sub-mode filter
        // findFirst() picks it and the plan's read hands back interview questions. A fixture without
        // this session cannot distinguish the two, which is why the first version of this test
        // survived deleting the filter.
        QuickReviewSessionEntity interview = interviewSessionOn(f, 0);
        Map<String, Object> interviewState = new LinkedHashMap<>(interview.getSessionState());
        interviewState.put("sourceCollectionId", f.collectionId.toString());
        interview.setSessionState(interviewState);
        lenient().when(quickReviewSessionRepository.findByUserIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                        eq(f.userId), eq(QuickReviewSessionMode.ADAPTIVE), any()))
                .thenReturn(List.of(interview, planSession));
        lenient().when(studyPackRepository.findByIdAndOwnerUserId(f.packs.get(0).getId(), f.userId))
                .thenReturn(Optional.of(f.packs.get(0)));

        QuickReviewAdaptiveQuizResponse response =
                adaptivePracticeService.getAdaptiveQuizSessionForCollection(
                        f.collectionId.toString(), f.userId);

        assertThat(response.sessionId()).isEqualTo(planSession.getId().toString());
        assertThat(response.sessionId()).isNotEqualTo(interview.getId().toString());
    }


    /** A read-path projection over a fixture pack, mirroring findProgressViewsByNoteIdIn's shape. */
    private StudyPackProgressProjection projectionOf(StudyPackEntity pack, UUID ownerUserId) {
        StudyPackProgressProjection projection = mock(StudyPackProgressProjection.class);
        lenient().when(projection.getId()).thenReturn(pack.getId());
        lenient().when(projection.getNoteId()).thenReturn(pack.getNoteId());
        lenient().when(projection.getOwnerUserId()).thenReturn(ownerUserId);
        lenient().when(projection.getKeyConcepts()).thenReturn(pack.getKeyConcepts());
        lenient().when(projection.getStatus()).thenReturn(StudyPackStatus.DONE);
        return projection;
    }

    private QuickReviewSessionEntity interviewSessionOn(CollectionFixture f, int packIndex) {
        QuickReviewSessionEntity interview = buildInProgressAdaptiveSession(
                UUID.randomUUID(), f.userId, f.packs.get(packIndex).getId(), f.noteIds.get(packIndex));
        Map<String, Object> state = new LinkedHashMap<>(interview.getSessionState());
        state.put("subMode", "INTERVIEW");
        interview.setSessionState(state);
        return interview;
    }

    /**
     * Makes BOTH active-session queries see the same sessions.
     *
     * <p>⚠️ Stubbing only one of them produces a state no single table can hold, which is how the
     * previous interview test passed while exercising the wrong branch. Every collection-scoped test
     * that involves an existing session must go through here.
     */
    private void seeActiveSessions(CollectionFixture f, QuickReviewSessionEntity... sessions) {
        List<QuickReviewSessionEntity> all = List.of(sessions);
        lenient().when(quickReviewSessionRepository.findByUserIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                        eq(f.userId), eq(QuickReviewSessionMode.ADAPTIVE), any()))
                .thenReturn(all);
        for (QuickReviewSessionEntity session : all) {
            lenient().when(quickReviewSessionRepository
                            .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                                    eq(f.userId), eq(session.getStudyPackId()),
                                    eq(QuickReviewSessionMode.ADAPTIVE), any()))
                    .thenReturn(Optional.of(session));
            lenient().when(studyPackRepository.findByIdAndOwnerUserId(session.getStudyPackId(), f.userId))
                    .thenReturn(f.packs.stream()
                            .filter(pack -> pack.getId().equals(session.getStudyPackId()))
                            .findFirst());
        }
    }

    private record CollectionFixture(
            UUID userId,
            UUID collectionId,
            List<UUID> noteIds,
            List<StudyPackEntity> packs
    ) {
    }

    /**
     * A flat (non-hierarchical) collection of {@code packCount} ready packs, owned by one user, with
     * every repository/gate stub the collection-scoped path needs before focus resolution.
     */
    private CollectionFixture collectionFixture(int packCount) {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        List<UUID> noteIds = new ArrayList<>();
        List<StudyPackEntity> packs = new ArrayList<>();
        List<NoteCollectionItemEntity> items = new ArrayList<>();
        for (int i = 0; i < packCount; i++) {
            UUID noteId = UUID.randomUUID();
            UUID packId = UUID.randomUUID();
            StudyPackEntity pack = buildStudyPack(packId, noteId, userId);
            pack.setStatus(StudyPackStatus.DONE);
            pack.setKeyConcepts(List.of("Shear Force", "Bending Moment"));
            noteIds.add(noteId);
            packs.add(pack);
            NoteCollectionItemEntity item = new NoteCollectionItemEntity();
            item.setNoteId(noteId);
            item.setPosition(i);
            items.add(item);
        }

        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(collectionId);
        collection.setOwnerUserId(userId);
        collection.setTitle("Structural Engineering");

        when(noteCollectionRepository.findByIdAndOwnerUserId(collectionId, userId))
                .thenReturn(Optional.of(collection));
        lenient().when(noteCollectionRepository
                        .findOrderedChildrenByParentCollectionIdAndOwnerUserId(collectionId, userId))
                .thenReturn(List.of());
        lenient().when(noteCollectionItemRepository.findByCollectionIdOrderByPositionAsc(collectionId))
                .thenReturn(items);
        // Phase 1 is a PROJECTION query now (eligibility needs no quiz/summary JSON); phase 2 loads
        // full entities only for the packs that survive. Both are stubbed, and phase 2 is answered
        // from the requested note ids so a narrowed phase 2 returns a narrowed result -- stubbing it
        // to return every pack regardless would hide whether the narrowing happens at all.
        // Built BEFORE the when(...) call: creating mocks inside a thenReturn argument is nested
        // stubbing and Mockito rejects it as UnfinishedStubbingException.
        List<StudyPackProgressProjection> projections = packs.stream()
                .map(pack -> projectionOf(pack, userId))
                .toList();
        lenient().when(studyPackRepository.findProgressViewsByNoteIdIn(any())).thenReturn(projections);
        lenient().when(studyPackRepository.findByOwnerUserIdAndNoteIdInAndStatus(
                        eq(userId), any(), eq(StudyPackStatus.DONE)))
                .thenAnswer(invocation -> {
                    Collection<UUID> requested = invocation.getArgument(1);
                    return packs.stream().filter(pack -> requested.contains(pack.getNoteId())).toList();
                });
        lenient().when(quickReviewSessionRepository.findByUserIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                        eq(userId), eq(QuickReviewSessionMode.ADAPTIVE), any()))
                .thenReturn(List.of());
        lenient().when(quickReviewSessionRepository
                        .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                                eq(userId), any(), eq(QuickReviewSessionMode.ADAPTIVE), any()))
                .thenReturn(Optional.empty());
        lenient().when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        lenient().when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(UserUsageService.MonthlyUsage.zero());
        lenient().when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return new CollectionFixture(userId, collectionId, noteIds, packs);
    }

    /** Stubs the per-pack due/weak concept reads that drive focus selection for one pack. */
    private void stubCollectionFocus(CollectionFixture f, int packIndex, List<String> due, List<String> weak) {
        UUID packId = f.packs.get(packIndex).getId();
        lenient().when(conceptHealthService.getDueConceptsByStudyPackIds(eq(f.userId), any(), any()))
                .thenAnswer(invocation -> {
                    Map<UUID, List<String>> out = new LinkedHashMap<>();
                    collectionDueByPack.forEach(out::put);
                    return out;
                });
        lenient().when(conceptHealthService.getPersistentlyWeakConceptsByStudyPackIds(eq(f.userId), any()))
                .thenAnswer(invocation -> {
                    Map<UUID, List<String>> out = new LinkedHashMap<>();
                    collectionWeakByPack.forEach(out::put);
                    return out;
                });
        collectionDueByPack.put(packId, due);
        collectionWeakByPack.put(packId, weak);
    }

    /** Sampler passes through the first {@code expectedSampled} eligible sources, and the LLM returns one item per call. */
    private void stubCollectionGeneration(CollectionFixture f, int expectedSampled) {
        lenient().when(longExamPlanSourceSampler.sample(any(), any(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    List<LongExamPlanSourceSampler.EligiblePlanSource> eligible = invocation.getArgument(0);
                    int max = invocation.getArgument(2);
                    return eligible.stream().limit(Math.min(max, expectedSampled)).toList();
                });
        lenient().when(quizGenerationService.generateAdaptivePracticeQuiz(
                        any(), any(), any(), any(), any(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    int count = invocation.getArgument(5);
                    List<QuizItem> generated = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        generated.add(new QuizItem(
                                "Generated " + UUID.randomUUID(),
                                List.of("A", "B", "C", "D"),
                                "A",
                                "Shear Force",
                                "Explanation"));
                    }
                    return generated;
                });
    }

    private final Map<UUID, List<String>> collectionDueByPack = new LinkedHashMap<>();
    private final Map<UUID, List<String>> collectionWeakByPack = new LinkedHashMap<>();

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

    private Map<String, Object> generateAndCaptureAnalyticsMetadata(String entry) {
        UUID userId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(studyPackId, noteId, userId);
        QuickReviewSessionEntity sourceSession = buildCompletedSourceSession(
                userId,
                studyPackId,
                noteId,
                List.of("Weak Concept")
        );
        stubAdaptiveGeneration(
                userId,
                studyPackId,
                studyPack,
                sourceSession,
                buildGeneratedQuiz("Weak Concept", 5)
        );

        adaptivePracticeService.generateAdaptiveQuiz(studyPackId.toString(), userId, entry);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analyticsService).trackEvent(
                eq(userId),
                eq(AnalyticsEventType.ADAPTIVE_PRACTICE_STARTED),
                eq(studyPackId),
                metadataCaptor.capture()
        );
        return metadataCaptor.getValue();
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
