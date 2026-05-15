package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.InterviewPracticeAnswerRequest;
import com.studysnap.backend.dto.InterviewPracticeAnswerResponse;
import com.studysnap.backend.dto.InterviewPracticeStartRequest;
import com.studysnap.backend.dto.InterviewPracticeStartResponse;
import com.studysnap.backend.dto.InterviewReadinessReportResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.exception.InterviewPracticeQuotaExhaustedException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.service.model.InterviewPracticeCritique;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizSessionStateUtils;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewPracticeServiceTest {
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private QuizGenerationService quizGenerationService;
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
    private StudyPackGenerationContextResolver generationContextResolver;

    private InterviewPracticeService service;

    @BeforeEach
    void setUp() {
        service = new InterviewPracticeService(
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
                generationContextResolver
        );
    }

    @Test
    void startSession_createsAdaptiveSessionWithInterviewSubModeAndIncrementsQuota() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId, noteId, studyPackId);
        List<QuizItem> quiz = buildQuiz(5);

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.ADAPTIVE),
                any()
        )).thenReturn(Optional.empty());
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any()))
                .thenReturn(buildUsagePeriod());
        when(userUsageService.getMonthlyUsage(eq(userId), any())).thenReturn(UserUsageService.MonthlyUsage.zero());
        when(generationContextResolver.resolveForStudyPack(userId, studyPack))
                .thenReturn(new StudyPackGenerationContext(null, null, "Backend", List.of()));
        when(quizGenerationService.generateInterviewPracticeQuiz(
                eq(studyPack.getTitle()),
                eq(studyPack.getSummary()),
                eq(studyPack.getKeyConcepts()),
                eq(List.of("Existing question")),
                eq(5),
                any()
        )).thenReturn(quiz);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InterviewPracticeStartResponse response = service.startSession(userId, new InterviewPracticeStartRequest(noteId, 5));

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.questionCount()).isEqualTo(5);
        assertThat(response.question()).isNotNull();
        verify(featureGateService).checkFeatureAccess(PlanType.PRO, Feature.INTERVIEW_PRACTICE);
        verify(userUsageService).incrementInterviewPracticeGeneration(eq(userId), any());
    }

    @Test
    void startSession_quotaExhaustedThrowsNamedExceptionBeforeGeneration() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId, noteId, studyPackId);

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(studyPackRepository.findByOwnerUserIdAndNoteId(userId, noteId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.ADAPTIVE),
                any()
        )).thenReturn(Optional.empty());
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any()))
                .thenReturn(buildUsagePeriod());
        when(userUsageService.getMonthlyUsage(eq(userId), any()))
                .thenReturn(new UserUsageService.MonthlyUsage(
                        OffsetDateTime.now(ZoneOffset.UTC),
                        OffsetDateTime.now(ZoneOffset.UTC).plusMonths(1),
                        0,
                        0,
                        0,
                        10,
                        0,
                        0,
                        0
                ));

        InterviewPracticeStartRequest request = new InterviewPracticeStartRequest(noteId, 5);
        assertThatThrownBy(() -> service.startSession(userId, request))
                .isInstanceOf(InterviewPracticeQuotaExhaustedException.class);

        verify(quizGenerationService, never()).generateInterviewPracticeQuiz(any(), any(), any(), any(), anyInt(), any());
        verify(userUsageService, never()).incrementInterviewPracticeGeneration(any(), any());
    }

    @Test
    void answerQuestionPersistsTimeAndCritique() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, noteId, studyPackId, buildQuiz(2));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.ADAPTIVE))
                .thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(quizGenerationService.generateInterviewCritique(any(), eq(1)))
                .thenReturn(new InterviewPracticeCritique("WORKABLE", "Good structure, but refine the tradeoff.", "What risk would you mitigate first?"));

        InterviewPracticeAnswerResponse response = service.answerQuestion(
                sessionId,
                userId,
                new InterviewPracticeAnswerRequest(0, "B", 121)
        );

        assertThat(response.verdict()).isEqualTo("WORKABLE");
        assertThat(response.nextQuestion()).isNotNull();
        assertThat(QuizSessionStateUtils.extractInterviewTimeSpentSeconds(session.getSessionState(), buildQuiz(2)))
                .containsEntry(0, 121);
    }

    @Test
    void completeSessionBuildsReadinessReportAndPacingNotes() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, noteId, studyPackId, buildQuiz(2));
        session.setSessionState(QuizSessionStateUtils.withInterviewAnswer(session.getSessionState(), 0, 0, 130));
        session.setSessionState(QuizSessionStateUtils.withInterviewAnswer(session.getSessionState(), 1, 2, 80));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.ADAPTIVE))
                .thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InterviewReadinessReportResponse report = service.completeSession(sessionId, userId);

        assertThat(report.scorePercentage()).isEqualTo(50);
        assertThat(report.band()).isEqualTo("ALMOST_READY");
        assertThat(report.pacingNotes()).containsExactly(0);
        assertThat(report.gaps()).hasSize(1);
        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.COMPLETED);
    }

    private StudyPackEntity buildStudyPack(UUID userId, UUID noteId, UUID studyPackId) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        studyPack.setNoteId(noteId);
        studyPack.setInputType(InputType.TEXT);
        studyPack.setTitle("Senior Java Backend Interview Prep");
        studyPack.setSummary("Backend interview notes.");
        studyPack.setKeyConcepts(List.of("Transactions", "Concurrency"));
        studyPack.setQuiz(List.of(new QuizItem("Existing question", List.of("A", "B", "C", "D"), 0, "Existing", "Existing explanation")));
        studyPack.setModelTier(ModelTier.FREE);
        studyPack.setModelUsed("mock");
        studyPack.setStatus(StudyPackStatus.DONE);
        studyPack.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        studyPack.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        studyPack.setTags(new String[0]);
        return studyPack;
    }

    private BillingUsagePeriodService.UsagePeriod buildUsagePeriod() {
        return new BillingUsagePeriodService.UsagePeriod(
                PlanType.PRO,
                BillingCycle.MONTHLY,
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC).plusMonths(1),
                2026,
                5
        );
    }

    private QuickReviewSessionEntity buildSession(UUID userId, UUID noteId, UUID studyPackId, List<QuizItem> quiz) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setNoteId(noteId);
        session.setStudyPackId(studyPackId);
        session.setSessionMode(QuickReviewSessionMode.ADAPTIVE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setCurrentQuestionIndex(0);
        session.setTotalQuestions(quiz.size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO);
        session.setRetryCount(0);
        session.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        session.setSessionState(QuizSessionStateUtils.withInterviewPracticeState(quiz, "INTERVIEW", 120));
        return session;
    }

    private List<QuizItem> buildQuiz(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new QuizItem(
                        "Scenario question " + index,
                        List.of("Option A", "Option B", "Option C", "Option D"),
                        index % 2,
                        index % 2 == 0 ? "Transactions" : "Concurrency",
                        "Explanation " + index
                ))
                .toList();
    }
}
