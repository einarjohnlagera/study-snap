package com.studysnap.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.ActivityTrackingService;
import com.studysnap.backend.service.AnalyticsService;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.BillingUsagePeriodService;
import com.studysnap.backend.service.ChallengeQuizQuestionBankService;
import com.studysnap.backend.service.ChallengeQuizService;
import com.studysnap.backend.service.ConceptHealthService;
import com.studysnap.backend.service.ExamQuestionPoolService;
import com.studysnap.backend.service.GenerationRecoveryRowWriter;
import com.studysnap.backend.service.LongExamPlanSourceSampler;
import com.studysnap.backend.service.OfficialChallengeQuizTemplateService;
import com.studysnap.backend.service.PlanSourcedExamVerifier;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import com.studysnap.backend.service.QuickReviewSessionService;
import com.studysnap.backend.service.QuickReviewStudyTipService;
import com.studysnap.backend.service.QuizGenerationService;
import com.studysnap.backend.service.StudyPackGenerationContextResolver;
import com.studysnap.backend.service.StudyPackGenerationTaskDispatcher;
import com.studysnap.backend.service.StudyPackQuizMasteryService;
import com.studysnap.backend.service.SubscriptionService;
import com.studysnap.backend.service.UserUsageService;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizSessionStateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ChallengeQuizControllerTest {
    private static final String KEY_CONCEPT = "Power distribution";
    private static final List<String> NULL_REDACTED_FIELDS = List.of(
            "correctIndex",
            "correctIndices",
            "explanation",
            "workingSolution",
            "acceptableAnswers"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StudyPackRepository studyPackRepository = mock(StudyPackRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NoteRepository noteRepository = mock(NoteRepository.class);
    private final PlanSourcedExamVerifier planSourcedExamVerifier = mock(PlanSourcedExamVerifier.class);
    private final QuickReviewSessionRepository sessionRepository = mock(QuickReviewSessionRepository.class);
    private final QuizGenerationService quizGenerationService = mock(QuizGenerationService.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final UserUsageService userUsageService = mock(UserUsageService.class);
    private final BillingUsagePeriodService billingUsagePeriodService = mock(BillingUsagePeriodService.class);
    private final AuthService authService = mock(AuthService.class);
    private final AnalyticsService analyticsService = mock(AnalyticsService.class);
    private final AiRateLimitService aiRateLimitService = mock(AiRateLimitService.class);
    private final ActivityTrackingService activityTrackingService = mock(ActivityTrackingService.class);
    private final StudyPackGenerationContextResolver generationContextResolver =
            mock(StudyPackGenerationContextResolver.class);
    private final ExamQuestionPoolService examQuestionPoolService = mock(ExamQuestionPoolService.class);
    private final ChallengeQuizQuestionBankService questionBankService = mock(ChallengeQuizQuestionBankService.class);
    private final OfficialChallengeQuizTemplateService templateService = mock(OfficialChallengeQuizTemplateService.class);
    private final ConceptHealthService conceptHealthService = mock(ConceptHealthService.class);
    private final StudyPackQuizMasteryService studyPackQuizMasteryService = mock(StudyPackQuizMasteryService.class);
    private final StudyPackGenerationTaskDispatcher taskDispatcher = mock(StudyPackGenerationTaskDispatcher.class);
    private final GenerationRecoveryRowWriter recoveryRowWriter = mock(GenerationRecoveryRowWriter.class);
    private final NoteCollectionRepository noteCollectionRepository = mock(NoteCollectionRepository.class);
    private final NoteCollectionItemRepository noteCollectionItemRepository = mock(NoteCollectionItemRepository.class);

    private UUID userId;
    private UUID studyPackId;
    private StudyPackEntity studyPack;
    private MockMvc challengeQuizMockMvc;
    private MockMvc deprecatedQuickReviewMockMvc;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        studyPackId = UUID.randomUUID();
        studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        studyPack.setTitle("Electrical systems");
        studyPack.setSummary("Summary");
        studyPack.setSubject("Engineering");
        studyPack.setKeyConcepts(List.of(KEY_CONCEPT));
        studyPack.setQuiz(List.of());

        TransactionOperations transactionOperations = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        ChallengeQuizService service = new ChallengeQuizService(
                studyPackRepository,
                userRepository,
                noteRepository,
                planSourcedExamVerifier,
                sessionRepository,
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
                questionBankService,
                templateService,
                conceptHealthService,
                studyPackQuizMasteryService,
                taskDispatcher,
                transactionOperations,
                recoveryRowWriter,
                noteCollectionRepository,
                noteCollectionItemRepository,
                new LongExamPlanSourceSampler()
        );
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        HandlerMethodArgumentResolver argumentResolver = authenticatedUserResolver(authenticatedUser);
        challengeQuizMockMvc = standaloneSetup(new ChallengeQuizController(service))
                .setCustomArgumentResolvers(argumentResolver)
                .build();
        deprecatedQuickReviewMockMvc = standaloneSetup(new QuickReviewSessionController(
                        mock(QuickReviewSessionService.class),
                        mock(QuickReviewStudyTipService.class),
                        mock(QuickReviewAdaptivePracticeService.class),
                        service
                ))
                .setCustomArgumentResolvers(argumentResolver)
                .build();

        lenient().when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        lenient().when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(UserUsageService.MonthlyUsage.zero());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        lenient().when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.PRO, BillingCycle.MONTHLY, now.minusDays(5), now.plusDays(25),
                        now.getYear(), now.getMonthValue()
                ));
        lenient().when(sessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(templateService.copyTemplateQuestions(
                any(UUID.class), any(UUID.class), any(), any(UUID.class), any(), anyInt()
        )).thenReturn(List.of());
        lenient().when(questionBankService.claimEligibleQuestions(
                any(UUID.class), any(UUID.class), any(), any(UUID.class), any(), anyInt()
        )).thenReturn(List.of());
        lenient().when(generationContextResolver.resolveForStudyPack(userId, studyPack))
                .thenReturn(new StudyPackGenerationContext(
                        LearnerLevel.COLLEGE, "Engineering", "Engineering", List.of()
                ));
    }

    @Test
    void activeRoutesRedactButCompletionRevealsAndStorageRetainsAnswerKey() throws Exception {
        QuickReviewSessionEntity session = buildSession("challenge");
        configureOwnedStudyPack();
        configureSessionLookups(session);
        configureActiveSession(session);
        List<QuizItem> appendedQuestions = buildAppendedQuiz();
        when(questionBankService.claimEligibleQuestions(
                eq(userId), eq(studyPackId), eq(LearnerLevel.COLLEGE), eq(session.getId()), any(), eq(5)
        )).thenReturn(appendedQuestions);

        MvcResult startResult = challengeQuizMockMvc.perform(
                        post("/challenge-quiz/study-packs/{id}/start", studyPackId)
                )
                .andExpect(status().isOk())
                .andReturn();
        assertQuizIsRedacted(startResult, "quiz", 5);

        MvcResult progressResult = challengeQuizMockMvc.perform(
                        post("/challenge-quiz/sessions/{id}/progress", session.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "currentQuestionIndex": 3,
                                          "sessionState": {
                                            "selectedChoices": {"0": 1},
                                            "selectedMultiChoices": {"1": [0, 2]},
                                            "selectedIdentificationAnswers": {"2": "Voltage"},
                                            "selectedEnumerationAnswers": {"3": ["Voltage", "Current"]}
                                          }
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andReturn();
        assertQuizIsRedacted(progressResult, "quiz", 5);

        MvcResult generateMoreResult = challengeQuizMockMvc.perform(
                        post("/challenge-quiz/sessions/{id}/generate-more", session.getId())
                )
                .andExpect(status().isOk())
                .andReturn();
        assertQuizIsRedacted(generateMoreResult, "newQuestions", 5);

        List<QuizItem> storedQuiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        assertThat(storedQuiz).hasSize(10);
        assertThat(storedQuiz.get(0).correctIndex()).isEqualTo(1);
        assertThat(storedQuiz.get(0).explanation()).isEqualTo("The MCQ explanation.");
        assertThat(storedQuiz.get(0).workingSolution()).isEqualTo("The worked solution.");
        assertThat(storedQuiz.get(1).correctIndices()).containsExactly(0, 2);
        assertThat(storedQuiz.get(2).acceptableAnswers()).containsExactly("Voltage");
        assertThat(storedQuiz.get(3).acceptableAnswerGroups())
                .containsExactly(List.of("Voltage"), List.of("Current"));
        assertThat(storedQuiz.subList(5, 10))
                .allSatisfy(item -> {
                    assertThat(item.correctIndex()).isNotNull();
                    assertThat(item.explanation()).isNotNull();
                });

        challengeQuizMockMvc.perform(post("/challenge-quiz/sessions/{id}/progress", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentQuestionIndex": 5,
                                  "sessionState": {"selectedChoices": {"0": 1, "5": 0}}
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult completeResult = challengeQuizMockMvc.perform(
                        post("/challenge-quiz/sessions/{id}/complete", session.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{" +
                                        "\"correctAnswers\":5," +
                                        "\"totalQuestions\":10," +
                                        "\"durationSeconds\":120" +
                                        "}")
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode completion = objectMapper.readTree(completeResult.getResponse().getContentAsString());
        JsonNode revealedQuiz = completion.path("quiz");
        assertThat(revealedQuiz).hasSize(10);
        assertThat(revealedQuiz.path(0).path("correctIndex").asInt()).isEqualTo(1);
        assertThat(revealedQuiz.path(0).path("explanation").asText()).isEqualTo("The MCQ explanation.");
        assertThat(revealedQuiz.path(0).path("workingSolution").asText()).isEqualTo("The worked solution.");
        assertThat(revealedQuiz.path(1).path("correctIndices")).hasSize(2);
        assertThat(revealedQuiz.path(2).path("acceptableAnswers").path(0).asText()).isEqualTo("Voltage");
        assertThat(revealedQuiz.path(3).path("acceptableAnswerGroups").path(0).path(0).asText())
                .isEqualTo("Voltage");
        assertThat(revealedQuiz.path(5).path("question").asText()).isEqualTo(storedQuiz.get(5).question());
        assertThat(revealedQuiz.path(5).path("correctIndex").isInt()).isTrue();
        assertThat(completion.path("selectedChoices").path("0").asInt()).isEqualTo(1);
        assertThat(completion.path("selectedChoices").path("5").asInt()).isZero();
        assertThat(completion.path("selectedMultiChoices").path("1")).hasSize(2);
        assertThat(completion.path("selectedIdentificationAnswers").path("2").asText()).isEqualTo("Voltage");
        assertThat(completion.path("selectedEnumerationAnswers").path("3")).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"challenge", "board_exam"})
    void inProgressRouteRedactsChallengeAndBoardExamQuestions(String mode) throws Exception {
        QuickReviewSessionEntity session = buildSession(mode);
        configureOwnedStudyPack();
        configureActiveSession(session);

        MvcResult result = challengeQuizMockMvc.perform(
                        get("/challenge-quiz/study-packs/{id}/in-progress", studyPackId)
                )
                .andExpect(status().isOk())
                .andReturn();

        assertQuizIsRedacted(result, "quiz", 5);
    }

    @Test
    void deprecatedQuickReviewStartAliasesAlsoRedactEveryQuestion() throws Exception {
        QuickReviewSessionEntity session = buildSession("challenge");
        configureOwnedStudyPack();
        configureSessionLookups(session);
        configureActiveSession(session);

        MvcResult sessionsAlias = deprecatedQuickReviewMockMvc.perform(
                        post("/quick-review-sessions/study-packs/{id}/challenge/start", studyPackId)
                )
                .andExpect(status().isOk())
                .andReturn();
        assertQuizIsRedacted(sessionsAlias, "quiz", 5);

        MvcResult singularAlias = deprecatedQuickReviewMockMvc.perform(
                        post("/quick-review/study-packs/{id}/challenge/start", studyPackId)
                )
                .andExpect(status().isOk())
                .andReturn();
        assertQuizIsRedacted(singularAlias, "quiz", 5);
    }

    private void configureOwnedStudyPack() {
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId))
                .thenReturn(Optional.of(studyPack));
    }

    private void configureSessionLookups(QuickReviewSessionEntity session) {
        when(sessionRepository.findByIdAndUserIdAndSessionMode(
                session.getId(), userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
        when(sessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                session.getId(), userId, QuickReviewSessionMode.CHALLENGE
        )).thenReturn(Optional.of(session));
    }

    private void configureActiveSession(QuickReviewSessionEntity session) {
        when(sessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.CHALLENGE), any()
        )).thenReturn(Optional.of(session));
    }

    private QuickReviewSessionEntity buildSession(String mode) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(UUID.randomUUID());
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(5);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO);
        session.setRetryCount(0);
        session.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                buildInitialQuiz(),
                Map.of(
                        "mode", mode,
                        "difficulty", "medium",
                        "timeLimitSeconds", 3600,
                        "timerStartedAtEpochSeconds", OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond(),
                        "completed", false
                )
        ));
        return session;
    }

    private List<QuizItem> buildInitialQuiz() {
        String sourceStudyPackId = studyPackId.toString();
        return List.of(
                QuizItem.fromStoredComponents(
                        "What is voltage?", List.of("Current", "Voltage", "Power"), 1,
                        "Electrical systems", "The MCQ explanation.", null, "MCQ", "COMPUTATIONAL",
                        "The worked solution.", null, null, KEY_CONCEPT, null, null, sourceStudyPackId),
                QuizItem.fromStoredComponents(
                        "Select conductors.", List.of("Copper", "Rubber", "Silver"), 0,
                        "Electrical systems", "The multi-select explanation.", null, "MULTI_SELECT", null,
                        null, List.of(0, 2), null, KEY_CONCEPT, null, null, sourceStudyPackId),
                QuizItem.fromStoredComponents(
                        "Name potential difference.", List.of(), null,
                        "Electrical systems", "The identification explanation.", null, "IDENTIFICATION", null,
                        null, null, null, KEY_CONCEPT, List.of("Voltage"), null, sourceStudyPackId),
                QuizItem.fromStoredComponents(
                        "Name two circuit quantities.", List.of(), null,
                        "Electrical systems", "The enumeration explanation.", null, "ENUMERATION", null,
                        null, null, null, KEY_CONCEPT, null,
                        List.of(List.of("Voltage"), List.of("Current")), sourceStudyPackId),
                QuizItem.fromStoredComponents(
                        "What is current?", List.of("Charge flow", "Resistance"), 0,
                        "Electrical systems", "The second MCQ explanation.", null, "MCQ", null,
                        null, null, null, KEY_CONCEPT, null, null, sourceStudyPackId)
        );
    }

    private List<QuizItem> buildAppendedQuiz() {
        return List.of(
                new QuizItem("Added 1", List.of("A", "B"), 0, "Circuits", "Explanation 1"),
                new QuizItem("Added 2", List.of("A", "B"), 1, "Circuits", "Explanation 2"),
                new QuizItem("Added 3", List.of("A", "B"), 0, "Circuits", "Explanation 3"),
                new QuizItem("Added 4", List.of("A", "B"), 1, "Circuits", "Explanation 4"),
                new QuizItem("Added 5", List.of("A", "B"), 0, "Circuits", "Explanation 5")
        );
    }

    private void assertQuizIsRedacted(MvcResult result, String field, int expectedSize) throws Exception {
        JsonNode quiz = objectMapper.readTree(result.getResponse().getContentAsString()).path(field);
        assertThat(quiz.isArray()).isTrue();
        assertThat(quiz).hasSize(expectedSize);
        for (JsonNode item : quiz) {
            for (String answerField : NULL_REDACTED_FIELDS) {
                JsonNode value = item.path(answerField);
                assertThat(value.isMissingNode() || value.isNull())
                        .as("response field %s must not carry an answer", answerField)
                        .isTrue();
            }
            JsonNode answerGroups = item.path("acceptableAnswerGroups");
            assertThat(answerGroups.isMissingNode()
                    || answerGroups.isNull()
                    || allChildrenAreEmptyArrays(answerGroups)).isTrue();
            assertThat(item.path("answer").isMissingNode()).isTrue();
        }
        if ("quiz".equals(field)) {
            assertThat(quiz.path(0).path("keyConcept").asText()).isEqualTo(KEY_CONCEPT);
        }
    }

    private boolean allChildrenAreEmptyArrays(JsonNode groups) {
        if (!groups.isArray()) {
            return false;
        }
        for (JsonNode group : groups) {
            if (!group.isArray() || !group.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private HandlerMethodArgumentResolver authenticatedUserResolver(AuthenticatedUser authenticatedUser) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == AuthenticatedUser.class;
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return authenticatedUser;
            }
        };
    }
}
