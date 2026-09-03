package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.InterviewPracticeAnswerRequest;
import com.studysnap.backend.dto.InterviewPracticeAnswerResponse;
import com.studysnap.backend.dto.InterviewPracticeStartRequest;
import com.studysnap.backend.dto.InterviewPracticeStartResponse;
import com.studysnap.backend.dto.InterviewReadinessReportResponse;
import com.studysnap.backend.dto.InterviewSourceNoteRef;
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
import com.studysnap.backend.exception.InvalidInterviewPracticeRequestException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.service.model.InterviewPracticeCritique;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizSessionStateUtils;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    @Mock
    private ConceptHealthService conceptHealthService;

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
                generationContextResolver,
                conceptHealthService
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
        when(studyPackRepository.findByOwnerUserIdAndNoteIdForUpdate(userId, noteId)).thenReturn(Optional.of(studyPack));
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
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)).thenReturn(Optional.of(studyPack));
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

        InterviewPracticeStartResponse response = service.startSession(userId, new InterviewPracticeStartRequest(noteId, 5, null));

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.questionCount()).isEqualTo(5);
        assertThat(response.question()).isNotNull();
        assertThat(response.sourceNoteRefs()).isEmpty();
        verify(featureGateService).checkFeatureAccess(PlanType.PRO, Feature.INTERVIEW_PRACTICE);
        verify(userUsageService).incrementInterviewPracticeGeneration(eq(userId), any());
    }

    @Test
    void startSession_twoSourcesGeneratesAndMergesProportionally() {
        UUID userId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(
                userId,
                primaryNoteId,
                primaryStudyPackId,
                "System Design Interview Prep",
                StudyPackStatus.DONE
        );
        StudyPackEntity additionalStudyPack = buildStudyPack(
                userId,
                additionalNoteId,
                additionalStudyPackId,
                "Behavioral Interview Prep",
                StudyPackStatus.DONE
        );

        stubReadyStart(userId, primaryNoteId, primaryStudyPackId, primaryStudyPack);
        when(studyPackRepository.findByOwnerUserIdAndNoteIdForUpdate(userId, additionalNoteId))
                .thenReturn(Optional.of(additionalStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
                .thenReturn(Optional.of(primaryStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId))
                .thenReturn(Optional.of(additionalStudyPack));
        when(generationContextResolver.resolveForStudyPack(eq(userId), any()))
                .thenReturn(new StudyPackGenerationContext(null, null, "Backend", List.of()));
        when(quizGenerationService.generateInterviewPracticeQuiz(
                eq(primaryStudyPack.getTitle()),
                eq(primaryStudyPack.getSummary()),
                eq(primaryStudyPack.getKeyConcepts()),
                any(),
                eq(5),
                any()
        )).thenReturn(buildQuiz("Primary", 5));
        when(quizGenerationService.generateInterviewPracticeQuiz(
                eq(additionalStudyPack.getTitle()),
                eq(additionalStudyPack.getSummary()),
                eq(additionalStudyPack.getKeyConcepts()),
                any(),
                eq(5),
                any()
        )).thenReturn(buildQuiz("Additional", 5));

        InterviewPracticeStartResponse response = service.startSession(
                userId,
                new InterviewPracticeStartRequest(primaryNoteId, 10, List.of(additionalNoteId))
        );

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.questionCount()).isEqualTo(10);
        assertThat(response.sourceNoteRefs())
                .extracting("noteId", "questionCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(primaryNoteId.toString(), 5),
                        org.assertj.core.groups.Tuple.tuple(additionalNoteId.toString(), 5)
                );
        verify(quizGenerationService, times(2))
                .generateInterviewPracticeQuiz(any(), any(), any(), any(), anyInt(), any());
        verify(userUsageService).incrementInterviewPracticeGeneration(eq(userId), any());
    }

    @Test
    void startSession_threeSourcesGivesRemainderToPrimary() {
        UUID userId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        UUID thirdNoteId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        UUID thirdStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(userId, primaryNoteId, primaryStudyPackId, "System Design", StudyPackStatus.DONE);
        StudyPackEntity additionalStudyPack = buildStudyPack(userId, additionalNoteId, additionalStudyPackId, "Algorithms", StudyPackStatus.DONE);
        StudyPackEntity thirdStudyPack = buildStudyPack(userId, thirdNoteId, thirdStudyPackId, "Behavioral", StudyPackStatus.DONE);

        stubReadyStart(userId, primaryNoteId, primaryStudyPackId, primaryStudyPack);
        when(studyPackRepository.findByOwnerUserIdAndNoteIdForUpdate(userId, additionalNoteId))
                .thenReturn(Optional.of(additionalStudyPack));
        when(studyPackRepository.findByOwnerUserIdAndNoteIdForUpdate(userId, thirdNoteId))
                .thenReturn(Optional.of(thirdStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(primaryStudyPackId, userId))
                .thenReturn(Optional.of(primaryStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(additionalStudyPackId, userId))
                .thenReturn(Optional.of(additionalStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(thirdStudyPackId, userId))
                .thenReturn(Optional.of(thirdStudyPack));
        when(generationContextResolver.resolveForStudyPack(eq(userId), any()))
                .thenReturn(new StudyPackGenerationContext(null, null, "Backend", List.of()));
        when(quizGenerationService.generateInterviewPracticeQuiz(
                eq(primaryStudyPack.getTitle()), any(), any(), any(), eq(4), any()
        )).thenReturn(buildQuiz("Primary", 4));
        when(quizGenerationService.generateInterviewPracticeQuiz(
                eq(additionalStudyPack.getTitle()), any(), any(), any(), eq(3), any()
        )).thenReturn(buildQuiz("Additional", 3));
        when(quizGenerationService.generateInterviewPracticeQuiz(
                eq(thirdStudyPack.getTitle()), any(), any(), any(), eq(3), any()
        )).thenReturn(buildQuiz("Third", 3));

        InterviewPracticeStartResponse response = service.startSession(
                userId,
                new InterviewPracticeStartRequest(primaryNoteId, 10, List.of(additionalNoteId, thirdNoteId))
        );

        assertThat(response.sourceNoteRefs())
                .extracting("questionCount")
                .containsExactly(4, 3, 3);
        assertThat(response.questionCount()).isEqualTo(10);
    }

    @Test
    void startSession_moreThanTwoAdditionalNotesThrowsInvalidRequest() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InterviewPracticeStartRequest request = new InterviewPracticeStartRequest(
                noteId,
                5,
                List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        );

        assertThatThrownBy(() -> service.startSession(userId, request))
                .isInstanceOf(InvalidInterviewPracticeRequestException.class)
                .hasMessageContaining("A maximum of 2 additional notes is allowed.");
    }

    @Test
    void startSession_additionalNoteCannotEqualPrimary() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InterviewPracticeStartRequest request = new InterviewPracticeStartRequest(noteId, 5, List.of(noteId));

        assertThatThrownBy(() -> service.startSession(userId, request))
                .isInstanceOf(InvalidInterviewPracticeRequestException.class)
                .hasMessageContaining("Primary note cannot be included as an additional source.");
    }

    @Test
    void startSession_duplicateAdditionalNotesThrowsInvalidRequest() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        InterviewPracticeStartRequest request = new InterviewPracticeStartRequest(
                noteId,
                5,
                List.of(additionalNoteId, additionalNoteId)
        );

        assertThatThrownBy(() -> service.startSession(userId, request))
                .isInstanceOf(InvalidInterviewPracticeRequestException.class)
                .hasMessageContaining("Duplicate additional notes are not allowed.");
    }

    @Test
    void startSession_nullAdditionalNoteThrowsInvalidRequest() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        List<UUID> additionalNoteIds = new ArrayList<>();
        additionalNoteIds.add(null);
        InterviewPracticeStartRequest request = new InterviewPracticeStartRequest(noteId, 5, additionalNoteIds);

        assertThatThrownBy(() -> service.startSession(userId, request))
                .isInstanceOf(InvalidInterviewPracticeRequestException.class)
                .hasMessageContaining("Additional notes cannot include empty values.");
    }

    @Test
    void startSession_additionalNoteNotOwnedThrowsInvalidRequest() {
        UUID userId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(userId, primaryNoteId, primaryStudyPackId);
        stubReadyValidation(userId, primaryNoteId, primaryStudyPackId, primaryStudyPack);
        when(studyPackRepository.findByOwnerUserIdAndNoteIdForUpdate(userId, additionalNoteId))
                .thenReturn(Optional.empty());
        InterviewPracticeStartRequest request = new InterviewPracticeStartRequest(
                primaryNoteId,
                5,
                List.of(additionalNoteId)
        );

        assertThatThrownBy(() -> service.startSession(userId, request))
                .isInstanceOf(InvalidInterviewPracticeRequestException.class)
                .hasMessageContaining("One or more selected notes do not have a Study Pack.");
    }

    @Test
    void startSession_additionalNoteWithoutReadyStudyPackThrowsInvalidRequest() {
        UUID userId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        StudyPackEntity primaryStudyPack = buildStudyPack(userId, primaryNoteId, primaryStudyPackId);
        StudyPackEntity additionalStudyPack = buildStudyPack(
                userId,
                additionalNoteId,
                additionalStudyPackId,
                "Failed Interview Prep",
                StudyPackStatus.FAILED
        );
        stubReadyValidation(userId, primaryNoteId, primaryStudyPackId, primaryStudyPack);
        when(studyPackRepository.findByOwnerUserIdAndNoteIdForUpdate(userId, additionalNoteId))
                .thenReturn(Optional.of(additionalStudyPack));
        InterviewPracticeStartRequest request = new InterviewPracticeStartRequest(
                primaryNoteId,
                5,
                List.of(additionalNoteId)
        );

        assertThatThrownBy(() -> service.startSession(userId, request))
                .isInstanceOf(InvalidInterviewPracticeRequestException.class)
                .hasMessageContaining("One or more selected notes do not have a Study Pack.");
    }

    @Test
    void startSession_quotaExhaustedThrowsNamedExceptionBeforeGeneration() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId, noteId, studyPackId);

        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(studyPackRepository.findByOwnerUserIdAndNoteIdForUpdate(userId, noteId)).thenReturn(Optional.of(studyPack));
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

        InterviewPracticeStartRequest request = new InterviewPracticeStartRequest(noteId, 5, null);
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
        StudyPackEntity studyPack = buildStudyPack(userId, noteId, studyPackId);

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.ADAPTIVE))
                .thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InterviewReadinessReportResponse report = service.completeSession(sessionId, userId);

        assertThat(report.scorePercentage()).isEqualTo(50);
        assertThat(report.band()).isEqualTo("ALMOST_READY");
        assertThat(report.pacingNotes()).containsExactly(0);
        assertThat(report.gaps()).hasSize(1);
        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.COMPLETED);
        verify(conceptHealthService).recordCorrectAnswersForKnownConcepts(
                eq(userId),
                eq(studyPackId),
                eq(List.of("Transactions")),
                eq(List.of("Transactions", "Concurrency")),
                any(OffsetDateTime.class)
        );
        verify(conceptHealthService).recordIncorrectAnswersForKnownConcepts(
                eq(userId),
                eq(studyPackId),
                eq(List.of("Concurrency")),
                eq(List.of("Transactions", "Concurrency")),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void completeSessionRecordsFullyCorrectConceptsAgainstMatchingSourcePacks() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, primaryNoteId, primaryStudyPackId, List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), 0, "Database durability", "Explanation",
                        null, "MCQ", null, null, null, null, "Transactions", null, null),
                new QuizItem("Q2", List.of("A", "B", "C", "D"), 1, "Thread safety", "Explanation",
                        null, "MCQ", null, null, null, null, "Concurrency", null, null),
                new QuizItem("Q3", List.of("A", "B", "C", "D"), 2, "Free-form", "Explanation")
        ));
        session.setSessionState(QuizSessionStateUtils.withInterviewAnswer(session.getSessionState(), 0, 0, 90));
        session.setSessionState(QuizSessionStateUtils.withInterviewAnswer(session.getSessionState(), 1, 1, 80));
        session.setSessionState(QuizSessionStateUtils.withInterviewAnswer(session.getSessionState(), 2, 2, 70));
        session.setSessionState(withInterviewSourceRefs(session.getSessionState(), additionalStudyPackId, additionalNoteId));
        StudyPackEntity primaryStudyPack = buildStudyPack(userId, primaryNoteId, primaryStudyPackId);
        primaryStudyPack.setKeyConcepts(List.of("Transactions"));
        StudyPackEntity additionalStudyPack = buildStudyPack(userId, additionalNoteId, additionalStudyPackId);
        additionalStudyPack.setKeyConcepts(List.of("Concurrency"));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.ADAPTIVE))
                .thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(primaryStudyPackId, userId))
                .thenReturn(Optional.of(primaryStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserId(additionalStudyPackId, userId))
                .thenReturn(Optional.of(additionalStudyPack));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InterviewReadinessReportResponse report = service.completeSession(sessionId, userId);

        assertThat(report.scorePercentage()).isEqualTo(100);
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
    void completeSession_recordsStampedConceptsOnlyForTheirContributingPack() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        UUID thirdNoteId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID additionalStudyPackId = UUID.randomUUID();
        UUID nonContributingStudyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, primaryNoteId, primaryStudyPackId, List.of(
                stampedQuizItem("A X", 0, "Shear Force", primaryStudyPackId),
                stampedQuizItem("A Y", 0, "Bending Moment", primaryStudyPackId),
                stampedQuizItem("B X", 0, "Shear Force", additionalStudyPackId),
                stampedQuizItem("B Y", 0, "Bending Moment", additionalStudyPackId)
        ));
        session.setSessionState(QuizSessionStateUtils.withInterviewAnswer(session.getSessionState(), 0, 0, 90));
        session.setSessionState(QuizSessionStateUtils.withInterviewAnswer(session.getSessionState(), 1, 1, 90));
        session.setSessionState(QuizSessionStateUtils.withInterviewAnswer(session.getSessionState(), 2, 1, 90));
        session.setSessionState(QuizSessionStateUtils.withInterviewAnswer(session.getSessionState(), 3, 0, 90));
        session.setSessionState(withInterviewSourceRefs(session.getSessionState(), additionalStudyPackId, additionalNoteId));
        session.setSessionState(withInterviewSourceRefs(session.getSessionState(), nonContributingStudyPackId, thirdNoteId));
        StudyPackEntity primary = buildStudyPack(userId, primaryNoteId, primaryStudyPackId);
        StudyPackEntity additional = buildStudyPack(userId, additionalNoteId, additionalStudyPackId);
        StudyPackEntity nonContributing = buildStudyPack(userId, thirdNoteId, nonContributingStudyPackId);
        primary.setKeyConcepts(List.of("Shear Force", "Bending Moment"));
        additional.setKeyConcepts(List.of("Shear Force", "Bending Moment"));
        nonContributing.setKeyConcepts(List.of("Shear Force", "Bending Moment"));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.ADAPTIVE))
                .thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(primaryStudyPackId, userId)).thenReturn(Optional.of(primary));
        when(studyPackRepository.findByIdAndOwnerUserId(additionalStudyPackId, userId)).thenReturn(Optional.of(additional));
        // Pack C IS resolvable on purpose, and the stub is lenient() because correct code never
        // looks it up. Without it the never() assertions below pass VACUOUSLY: the lookup would
        // return Optional.empty(), ifPresent could never fire, and a restored broadcast would be
        // caught only by Mockito strictness -- reported as PotentialStubbingProblem, which reads
        // as a test-setup bug rather than as wrong attribution. With the stub, the mutant fails on
        // the assertion that names the behaviour. Verified by mutation 2026-09-03.
        lenient().when(studyPackRepository.findByIdAndOwnerUserId(nonContributingStudyPackId, userId))
                .thenReturn(Optional.of(nonContributing));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.completeSession(sessionId, userId);

        verify(conceptHealthService).recordCorrectAnswersForKnownConcepts(
                eq(userId), eq(primaryStudyPackId), eq(List.of("Shear Force")), any(), any());
        verify(conceptHealthService).recordIncorrectAnswersForKnownConcepts(
                eq(userId), eq(primaryStudyPackId), eq(List.of("Bending Moment")), any(), any());
        verify(conceptHealthService).recordCorrectAnswersForKnownConcepts(
                eq(userId), eq(additionalStudyPackId), eq(List.of("Bending Moment")), any(), any());
        verify(conceptHealthService).recordIncorrectAnswersForKnownConcepts(
                eq(userId), eq(additionalStudyPackId), eq(List.of("Shear Force")), any(), any());
        verify(conceptHealthService, never()).recordCorrectAnswersForKnownConcepts(
                eq(userId), eq(nonContributingStudyPackId), any(), any(), any());
        verify(conceptHealthService, never()).recordIncorrectAnswersForKnownConcepts(
                eq(userId), eq(nonContributingStudyPackId), any(), any(), any());
    }

    @Test
    void completeSessionSkipsMissingSourcePackAndStillReturnsReport() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID primaryNoteId = UUID.randomUUID();
        UUID additionalNoteId = UUID.randomUUID();
        UUID primaryStudyPackId = UUID.randomUUID();
        UUID missingStudyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, primaryNoteId, primaryStudyPackId, List.of(
                new QuizItem("Q1", List.of("A", "B", "C", "D"), 0, "Transactions", "Explanation")
        ));
        session.setSessionState(QuizSessionStateUtils.withInterviewAnswer(session.getSessionState(), 0, 0, 90));
        session.setSessionState(withInterviewSourceRefs(session.getSessionState(), missingStudyPackId, additionalNoteId));
        StudyPackEntity primaryStudyPack = buildStudyPack(userId, primaryNoteId, primaryStudyPackId);

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.ADAPTIVE))
                .thenReturn(Optional.of(session));
        when(studyPackRepository.findByIdAndOwnerUserId(primaryStudyPackId, userId))
                .thenReturn(Optional.of(primaryStudyPack));
        when(studyPackRepository.findByIdAndOwnerUserId(missingStudyPackId, userId))
                .thenReturn(Optional.empty());
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InterviewReadinessReportResponse report = service.completeSession(sessionId, userId);

        assertThat(report.totalQuestions()).isEqualTo(1);
        verify(conceptHealthService).recordCorrectAnswersForKnownConcepts(
                eq(userId),
                eq(primaryStudyPackId),
                eq(List.of("Transactions")),
                eq(List.of("Transactions", "Concurrency")),
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
    void forfeitSessionMarksInProgressSessionWithoutRecordingConceptHealth() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID studyPackId = UUID.randomUUID();
        QuickReviewSessionEntity session = buildSession(userId, noteId, studyPackId, buildQuiz(2));

        when(quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(sessionId, userId, QuickReviewSessionMode.ADAPTIVE))
                .thenReturn(Optional.of(session));
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.forfeitSession(sessionId, userId);

        assertThat(session.getStatus()).isEqualTo(QuickReviewSessionStatus.FORFEITED);
        assertThat(session.getCompletedAt()).isNotNull();
        verify(conceptHealthService, never()).recordCorrectAnswersForKnownConcepts(any(), any(), any(), any(), any());
        verify(conceptHealthService, never()).recordIncorrectAnswersForKnownConcepts(any(), any(), any(), any(), any());
    }

    private StudyPackEntity buildStudyPack(UUID userId, UUID noteId, UUID studyPackId) {
        return buildStudyPack(userId, noteId, studyPackId, "Senior Java Backend Interview Prep", StudyPackStatus.DONE);
    }

    private StudyPackEntity buildStudyPack(
            UUID userId,
            UUID noteId,
            UUID studyPackId,
            String title,
            StudyPackStatus status
    ) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        studyPack.setNoteId(noteId);
        studyPack.setInputType(InputType.TEXT);
        studyPack.setTitle(title);
        studyPack.setSummary("Backend interview notes.");
        studyPack.setKeyConcepts(List.of("Transactions", "Concurrency"));
        studyPack.setQuiz(List.of(new QuizItem("Existing question", List.of("A", "B", "C", "D"), 0, "Existing", "Existing explanation")));
        studyPack.setModelTier(ModelTier.FREE);
        studyPack.setModelUsed("mock");
        studyPack.setStatus(status);
        studyPack.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        studyPack.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        studyPack.setTags(new String[0]);
        return studyPack;
    }

    private void stubReadyStart(
            UUID userId,
            UUID noteId,
            UUID studyPackId,
            StudyPackEntity studyPack
    ) {
        stubReadyValidation(userId, noteId, studyPackId, studyPack);
        when(quickReviewSessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubReadyValidation(
            UUID userId,
            UUID noteId,
            UUID studyPackId,
            StudyPackEntity studyPack
    ) {
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(studyPackRepository.findByOwnerUserIdAndNoteIdForUpdate(userId, noteId)).thenReturn(Optional.of(studyPack));
        when(quickReviewSessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId),
                eq(studyPackId),
                eq(QuickReviewSessionMode.ADAPTIVE),
                any()
        )).thenReturn(Optional.empty());
        when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any()))
                .thenReturn(buildUsagePeriod());
        when(userUsageService.getMonthlyUsage(eq(userId), any())).thenReturn(UserUsageService.MonthlyUsage.zero());
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

    private Map<String, Object> withInterviewSourceRefs(
            Map<String, Object> sessionState,
            UUID studyPackId,
            UUID noteId
    ) {
        return QuizSessionStateUtils.withInterviewSourceNoteRefs(
                sessionState,
                List.of(new InterviewSourceNoteRef(studyPackId.toString(), noteId.toString(), "Additional source", 1))
        );
    }

    private List<QuizItem> buildQuiz(int count) {
        return buildQuiz("Scenario", count);
    }

    private List<QuizItem> buildQuiz(String prefix, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new QuizItem(
                        prefix + " question " + index,
                        List.of("Option A", "Option B", "Option C", "Option D"),
                        index % 2,
                        index % 2 == 0 ? "Transactions" : "Concurrency",
                        "Explanation " + index
                ))
                .toList();
    }

    private QuizItem stampedQuizItem(String question, int correctIndex, String keyConcept, UUID sourceStudyPackId) {
        return new QuizItem(question, List.of("A", "B", "C", "D"), correctIndex, keyConcept, "Explanation",
                null, "MCQ", null, null, null, null, keyConcept, null, null)
                .withSourceStudyPackId(sourceStudyPackId.toString());
    }
}
