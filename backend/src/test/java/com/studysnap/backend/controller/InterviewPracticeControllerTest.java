package com.studysnap.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.GlobalExceptionHandler;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AnalyticsService;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.BillingUsagePeriodService;
import com.studysnap.backend.service.ConceptHealthService;
import com.studysnap.backend.service.FeatureGateService;
import com.studysnap.backend.service.InterviewPracticeService;
import com.studysnap.backend.service.QuizGenerationService;
import com.studysnap.backend.service.StudyPackGenerationContextResolver;
import com.studysnap.backend.service.SubscriptionService;
import com.studysnap.backend.service.UserUsageService;
import com.studysnap.backend.service.model.InterviewPracticeCritique;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.QuizSessionStateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class InterviewPracticeControllerTest {
    private static final List<String> ANSWER_KEY_FIELDS = List.of(
            "correctIndex",
            "correctIndices",
            "explanation",
            "workingSolution",
            "acceptableAnswers"
    );
    private static final String KEY_CONCEPT = "Transaction boundaries";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StudyPackRepository studyPackRepository = mock(StudyPackRepository.class);
    private final QuickReviewSessionRepository sessionRepository = mock(QuickReviewSessionRepository.class);
    private final QuizGenerationService quizGenerationService = mock(QuizGenerationService.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final FeatureGateService featureGateService = mock(FeatureGateService.class);
    private final UserUsageService userUsageService = mock(UserUsageService.class);
    private final BillingUsagePeriodService billingUsagePeriodService = mock(BillingUsagePeriodService.class);
    private final AuthService authService = mock(AuthService.class);
    private final AnalyticsService analyticsService = mock(AnalyticsService.class);
    private final AiRateLimitService aiRateLimitService = mock(AiRateLimitService.class);
    private final StudyPackGenerationContextResolver generationContextResolver =
            mock(StudyPackGenerationContextResolver.class);
    private final ConceptHealthService conceptHealthService = mock(ConceptHealthService.class);
    private final List<QuickReviewSessionEntity> savedSessions = new ArrayList<>();

    private UUID userId;
    private UUID noteId;
    private UUID studyPackId;
    private StudyPackEntity studyPack;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        noteId = UUID.randomUUID();
        studyPackId = UUID.randomUUID();
        studyPack = buildStudyPack();
        InterviewPracticeService service = new InterviewPracticeService(
                studyPackRepository,
                sessionRepository,
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
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        mockMvc = standaloneSetup(new InterviewPracticeController(service))
                .setControllerAdvice(new GlobalExceptionHandler(DataSize.ofMegabytes(10)))
                .setCustomArgumentResolvers(authenticatedUserResolver(authenticatedUser))
                .build();

        lenient().when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        lenient().when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(UserUsageService.MonthlyUsage.zero());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        lenient().when(billingUsagePeriodService.resolveUsagePeriod(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(new BillingUsagePeriodService.UsagePeriod(
                        PlanType.PRO,
                        BillingCycle.MONTHLY,
                        now.minusDays(5),
                        now.plusDays(25),
                        now.getYear(),
                        now.getMonthValue()
                ));
        lenient().when(sessionRepository.save(any(QuickReviewSessionEntity.class))).thenAnswer(invocation -> {
            QuickReviewSessionEntity session = invocation.getArgument(0);
            savedSessions.add(session);
            return session;
        });
    }

    @Test
    void freshStartRedactsQuestionWithoutChangingStoredQuiz() throws Exception {
        List<QuizItem> generatedQuiz = buildQuiz(5);
        configureOwnedStudyPack();
        when(sessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.ADAPTIVE), any()
        )).thenReturn(Optional.empty());
        when(generationContextResolver.resolveForStudyPack(userId, studyPack))
                .thenReturn(new StudyPackGenerationContext(null, null, "Backend", List.of()));
        when(quizGenerationService.generateInterviewPracticeQuiz(
                eq(studyPack.getTitle()),
                eq(studyPack.getSummary()),
                eq(studyPack.getKeyConcepts()),
                any(),
                eq(5),
                any()
        )).thenReturn(generatedQuiz);

        MvcResult result = mockMvc.perform(post("/interview-practice/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "noteId", noteId.toString(),
                                "questionCount", 5,
                                "additionalNoteIds", List.of()
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        assertQuestionIsRedacted(objectMapper.readTree(result.getResponse().getContentAsString()).path("question"));
        QuickReviewSessionEntity storedSession = savedSessions.getLast();
        List<QuizItem> storedQuiz = QuizSessionStateUtils.extractQuiz(storedSession.getSessionState());
        assertThat(storedQuiz).hasSize(5);
        assertThat(storedQuiz.getFirst().correctIndex()).isZero();
        assertThat(storedQuiz.getFirst().explanation()).isEqualTo("Explanation 0");
        assertThat(storedQuiz.getFirst().workingSolution()).isEqualTo("Worked solution 0");
        assertThat(storedQuiz.getFirst().keyConcept()).isEqualTo(KEY_CONCEPT);
    }

    @Test
    void resumedStartRedactsTheCurrentUnansweredQuestion() throws Exception {
        QuickReviewSessionEntity session = buildSession(buildQuiz(2));
        session.setCurrentQuestionIndex(1);
        configureOwnedStudyPack();
        when(sessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.ADAPTIVE), any()
        )).thenReturn(Optional.of(session));

        MvcResult result = mockMvc.perform(post("/interview-practice/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "noteId", noteId.toString(),
                                "questionCount", 5,
                                "additionalNoteIds", List.of()
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode question = objectMapper.readTree(result.getResponse().getContentAsString()).path("question");
        assertThat(question.path("question").asText()).isEqualTo("Scenario question 1");
        assertQuestionIsRedacted(question);
        verify(quizGenerationService, never())
                .generateInterviewPracticeQuiz(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void answerRedactsNextQuestionAndDifferentChoiceRetryReturnsConflictWithoutChangingSelection() throws Exception {
        QuickReviewSessionEntity session = buildSession(buildQuiz(2));
        when(sessionRepository.findByIdAndUserIdAndSessionMode(
                session.getId(), userId, QuickReviewSessionMode.ADAPTIVE
        )).thenReturn(Optional.of(session));
        when(quizGenerationService.generateInterviewCritique(any(), eq(1)))
                .thenReturn(new InterviewPracticeCritique(
                        "INCORRECT", "Review the transaction boundary.", "Which operation commits first?"
                ));

        MvcResult firstAnswer = mockMvc.perform(post(
                        "/interview-practice/sessions/{sessionId}/answer", session.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"questionIndex\":0," +
                                "\"selectedChoice\":\"B\"," +
                                "\"timeSpentSeconds\":30" +
                                "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode nextQuestion = objectMapper.readTree(firstAnswer.getResponse().getContentAsString())
                .path("nextQuestion");
        assertThat(nextQuestion.path("question").asText()).isEqualTo("Scenario question 1");
        assertQuestionIsRedacted(nextQuestion);

        mockMvc.perform(post("/interview-practice/sessions/{sessionId}/answer", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"questionIndex\":0," +
                                "\"selectedChoice\":\"A\"," +
                                "\"timeSpentSeconds\":35" +
                                "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INTERVIEW_PRACTICE_ANSWER_ALREADY_RECORDED"))
                .andExpect(jsonPath("$.error.message").value(
                        "This Interview Practice question has already been answered."
                ));

        assertThat(QuizSessionStateUtils.extractSelectedChoiceIndexes(
                session.getSessionState(),
                QuizSessionStateUtils.extractQuiz(session.getSessionState())
        )).containsEntry(0, 1);
        assertThat(QuizSessionStateUtils.extractQuiz(session.getSessionState()).get(1).correctIndex()).isEqualTo(1);
        verify(quizGenerationService, times(1)).generateInterviewCritique(any(), eq(1));
        verify(sessionRepository, times(2)).save(session);
    }

    private void configureOwnedStudyPack() {
        when(studyPackRepository.findByOwnerUserIdAndNoteIdForUpdate(userId, noteId))
                .thenReturn(Optional.of(studyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId))
                .thenReturn(Optional.of(studyPack));
    }

    private StudyPackEntity buildStudyPack() {
        StudyPackEntity pack = new StudyPackEntity();
        pack.setId(studyPackId);
        pack.setOwnerUserId(userId);
        pack.setNoteId(noteId);
        pack.setInputType(InputType.TEXT);
        pack.setTitle("Backend interview prep");
        pack.setSummary("Practice transaction scenarios.");
        pack.setKeyConcepts(List.of(KEY_CONCEPT));
        pack.setQuiz(List.of(new QuizItem(
                "Saved quiz question", List.of("A", "B", "C", "D"), 0, "Transactions", "Explanation"
        )));
        pack.setModelTier(ModelTier.FREE);
        pack.setModelUsed("mock");
        pack.setStatus(StudyPackStatus.DONE);
        pack.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        pack.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        pack.setTags(new String[0]);
        return pack;
    }

    private QuickReviewSessionEntity buildSession(List<QuizItem> quiz) {
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
                .mapToObj(index -> QuizItem.fromStoredComponents(
                        "Scenario question " + index,
                        List.of("Option A", "Option B", "Option C", "Option D"),
                        index % 2,
                        "Transactions",
                        "Explanation " + index,
                        null,
                        null,
                        "COMPUTATIONAL",
                        "Worked solution " + index,
                        List.of(0, 2),
                        null,
                        KEY_CONCEPT,
                        List.of("Accepted answer " + index),
                        null,
                        studyPackId.toString()
                ))
                .toList();
    }

    private void assertQuestionIsRedacted(JsonNode question) {
        assertThat(question.isObject()).isTrue();
        for (String field : ANSWER_KEY_FIELDS) {
            JsonNode value = question.path(field);
            assertThat(value.isMissingNode() || value.isNull())
                    .as("response field %s must not carry an answer", field)
                    .isTrue();
        }
        JsonNode answerGroups = question.path("acceptableAnswerGroups");
        assertThat(answerGroups.isMissingNode()
                || answerGroups.isNull()
                || allChildrenAreEmptyArrays(answerGroups)).isTrue();
        assertThat(question.path("answer").isMissingNode()).isTrue();
        assertThat(question.path("keyConcept").asText()).isEqualTo(KEY_CONCEPT);
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
