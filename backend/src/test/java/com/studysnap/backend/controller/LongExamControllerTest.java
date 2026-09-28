package com.studysnap.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AnalyticsService;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.ConceptHealthService;
import com.studysnap.backend.service.ExamQuestionPoolService;
import com.studysnap.backend.service.FeatureGateService;
import com.studysnap.backend.service.GenerationRecoveryRowWriter;
import com.studysnap.backend.service.LongExamPlanSourceSampler;
import com.studysnap.backend.service.LongExamService;
import com.studysnap.backend.service.PlanSourcedExamVerifier;
import com.studysnap.backend.service.QuizGenerationService;
import com.studysnap.backend.service.StudyPackGenerationContextResolver;
import com.studysnap.backend.service.StudyPackGenerationTaskDispatcher;
import com.studysnap.backend.service.SubscriptionService;
import com.studysnap.backend.service.UserUsageService;
import com.studysnap.backend.util.QuizSessionStateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.task.AsyncTaskExecutor;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class LongExamControllerTest {
    private static final List<String> ANSWER_KEY_FIELDS = List.of(
            "correctIndex",
            "correctIndices",
            "explanation",
            "workingSolution",
            "acceptableAnswers",
            "acceptableAnswerGroups"
    );
    private static final String KEY_CONCEPT = "Power distribution";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NoteRepository noteRepository = mock(NoteRepository.class);
    private final PlanSourcedExamVerifier planSourcedExamVerifier = mock(PlanSourcedExamVerifier.class);
    private final StudyPackRepository studyPackRepository = mock(StudyPackRepository.class);
    private final QuickReviewSessionRepository sessionRepository = mock(QuickReviewSessionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final FeatureGateService featureGateService = mock(FeatureGateService.class);
    private final AuthService authService = mock(AuthService.class);
    private final QuizGenerationService quizGenerationService = mock(QuizGenerationService.class);
    private final AnalyticsService analyticsService = mock(AnalyticsService.class);
    private final StudyPackGenerationContextResolver generationContextResolver = mock(StudyPackGenerationContextResolver.class);
    private final StudyPackGenerationTaskDispatcher taskDispatcher = mock(StudyPackGenerationTaskDispatcher.class);
    private final UserUsageService userUsageService = mock(UserUsageService.class);
    private final AsyncTaskExecutor asyncTaskExecutor = mock(AsyncTaskExecutor.class);
    private final ExamQuestionPoolService examQuestionPoolService = mock(ExamQuestionPoolService.class);
    private final ConceptHealthService conceptHealthService = mock(ConceptHealthService.class);
    private final GenerationRecoveryRowWriter generationRecoveryRowWriter = mock(GenerationRecoveryRowWriter.class);

    private UUID userId;
    private UUID studyPackId;
    private AuthenticatedUser authenticatedUser;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        studyPackId = UUID.randomUUID();
        authenticatedUser = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        TransactionOperations transactionOperations = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        LongExamService service = new LongExamService(
                noteRepository,
                planSourcedExamVerifier,
                studyPackRepository,
                sessionRepository,
                userRepository,
                subscriptionService,
                featureGateService,
                authService,
                quizGenerationService,
                analyticsService,
                generationContextResolver,
                new StudySnapProperties(),
                userUsageService,
                taskDispatcher,
                transactionOperations,
                asyncTaskExecutor,
                asyncTaskExecutor,
                examQuestionPoolService,
                conceptHealthService,
                new LongExamPlanSourceSampler(),
                generationRecoveryRowWriter
        );
        mockMvc = standaloneSetup(new LongExamController(service))
                .setCustomArgumentResolvers(authenticatedUserResolver())
                .build();

        lenient().when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        lenient().when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                .thenReturn(UserUsageService.MonthlyUsage.zero());
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(sessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void startAndProgressResponsesRedactEveryAnswerFieldWithoutChangingStoredQuiz() throws Exception {
        QuickReviewSessionEntity session = buildSession(QuickReviewSessionStatus.IN_PROGRESS);
        StudyPackEntity studyPack = buildStudyPack();
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId))
                .thenReturn(Optional.of(studyPack));
        when(sessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.LONG_EXAM), any()
        )).thenReturn(Optional.of(session));
        configureSessionLookup(session);

        MvcResult startResult = mockMvc.perform(post("/long-exam/study-packs/{id}/start", studyPackId))
                .andExpect(status().isOk())
                .andReturn();
        assertResponseQuizIsRedacted(startResult);

        MvcResult progressResult = mockMvc.perform(post("/long-exam/sessions/{id}/progress", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIndex\":0,\"selectedChoiceIndex\":2}"))
                .andExpect(status().isOk())
                .andReturn();
        assertResponseQuizIsRedacted(progressResult);

        List<QuizItem> storedQuiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        assertThat(storedQuiz.get(0).correctIndex()).isEqualTo(1);
        assertThat(storedQuiz.get(0).explanation()).isEqualTo("The MCQ explanation.");
        assertThat(storedQuiz.get(0).workingSolution()).isEqualTo("The worked solution.");
        assertThat(storedQuiz.get(1).correctIndices()).containsExactly(0, 2);
        assertThat(storedQuiz.get(2).acceptableAnswers()).containsExactly("Voltage");
        assertThat(storedQuiz.get(3).acceptableAnswerGroups())
                .containsExactly(List.of("Voltage"), List.of("Current"));
        assertThat(storedQuiz).allSatisfy(item -> assertThat(item.keyConcept()).isEqualTo(KEY_CONCEPT));
    }

    @Test
    void activeResponseRedactsEveryAnswerField() throws Exception {
        QuickReviewSessionEntity session = buildSession(QuickReviewSessionStatus.IN_PROGRESS);
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId))
                .thenReturn(Optional.of(buildStudyPack()));
        when(sessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.LONG_EXAM), any()
        )).thenReturn(Optional.of(session));

        MvcResult result = mockMvc.perform(get("/long-exam/study-packs/{id}/active", studyPackId))
                .andExpect(status().isOk())
                .andReturn();

        assertResponseQuizIsRedacted(result);
    }

    @Test
    void getResponseRedactsEveryAnswerField() throws Exception {
        QuickReviewSessionEntity session = buildSession(QuickReviewSessionStatus.IN_PROGRESS);
        configureSessionLookup(session);

        MvcResult result = mockMvc.perform(get("/long-exam/sessions/{id}", session.getId()))
                .andExpect(status().isOk())
                .andReturn();

        assertResponseQuizIsRedacted(result);
    }

    @Test
    void pauseResponseRedactsEveryAnswerField() throws Exception {
        QuickReviewSessionEntity session = buildSession(QuickReviewSessionStatus.IN_PROGRESS);
        configureSessionLookup(session);

        MvcResult result = mockMvc.perform(post("/long-exam/sessions/{id}/pause", session.getId()))
                .andExpect(status().isOk())
                .andReturn();

        assertResponseQuizIsRedacted(result);
    }

    @Test
    void resumeResponseRedactsEveryAnswerField() throws Exception {
        QuickReviewSessionEntity session = buildSession(QuickReviewSessionStatus.PAUSED);
        configureSessionLookup(session);

        MvcResult result = mockMvc.perform(post("/long-exam/sessions/{id}/resume", session.getId()))
                .andExpect(status().isOk())
                .andReturn();

        assertResponseQuizIsRedacted(result);
    }

    private void configureSessionLookup(QuickReviewSessionEntity session) {
        when(sessionRepository.findByIdAndUserIdAndSessionMode(
                session.getId(), userId, QuickReviewSessionMode.LONG_EXAM
        )).thenReturn(Optional.of(session));
    }

    private QuickReviewSessionEntity buildSession(QuickReviewSessionStatus status) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(UUID.randomUUID());
        session.setSessionMode(QuickReviewSessionMode.LONG_EXAM);
        session.setStatus(status);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(4);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO);
        session.setRetryCount(0);
        session.setCreatedAt(OffsetDateTime.now());
        Map<String, Object> state = QuizSessionStateUtils.withQuiz(buildQuiz(), Map.of("difficulty", "mixed"));
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(state, 0, 0));
        return session;
    }

    private List<QuizItem> buildQuiz() {
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
                        List.of(List.of("Voltage"), List.of("Current")), sourceStudyPackId)
        );
    }

    private StudyPackEntity buildStudyPack() {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        return studyPack;
    }

    private void assertResponseQuizIsRedacted(MvcResult result) throws Exception {
        JsonNode quiz = objectMapper.readTree(result.getResponse().getContentAsString()).path("quiz");
        assertThat(quiz.isArray()).isTrue();
        assertThat(quiz).hasSize(4);
        for (JsonNode item : quiz) {
            for (String field : ANSWER_KEY_FIELDS) {
                JsonNode value = item.path(field);
                assertThat(value.isMissingNode() || value.isNull())
                        .as("response field %s must not carry an answer", field)
                        .isTrue();
            }
            assertThat(item.path("keyConcept").asText()).isEqualTo(KEY_CONCEPT);
        }
    }

    private HandlerMethodArgumentResolver authenticatedUserResolver() {
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
