package com.studysnap.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.GlobalExceptionHandler;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AiRateLimitService;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AnalyticsService;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.ChallengeQuizService;
import com.studysnap.backend.service.ConceptHealthService;
import com.studysnap.backend.service.FeatureGateService;
import com.studysnap.backend.service.LongExamPlanSourceSampler;
import com.studysnap.backend.service.NoteCollectionService;
import com.studysnap.backend.service.NoteService;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import com.studysnap.backend.service.QuickReviewSessionService;
import com.studysnap.backend.service.QuickReviewStudyTipService;
import com.studysnap.backend.service.QuizGenerationService;
import com.studysnap.backend.service.StudyPackGenerationContextResolver;
import com.studysnap.backend.service.SubscriptionService;
import com.studysnap.backend.service.UserUsageService;
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

class AdaptivePracticeControllerTest {
    private static final List<String> ANSWER_KEY_FIELDS = List.of(
            "correctIndex",
            "correctIndices",
            "explanation",
            "workingSolution",
            "acceptableAnswers"
    );
    private static final String KEY_CONCEPT = "Differentiation";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StudyPackRepository studyPackRepository = mock(StudyPackRepository.class);
    private final QuickReviewSessionRepository sessionRepository = mock(QuickReviewSessionRepository.class);
    private final QuizGenerationService quizGenerationService = mock(QuizGenerationService.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final FeatureGateService featureGateService = mock(FeatureGateService.class);
    private final UserUsageService userUsageService = mock(UserUsageService.class);
    private final AuthService authService = mock(AuthService.class);
    private final AnalyticsService analyticsService = mock(AnalyticsService.class);
    private final AiRateLimitService aiRateLimitService = mock(AiRateLimitService.class);
    private final StudyPackGenerationContextResolver generationContextResolver =
            mock(StudyPackGenerationContextResolver.class);
    private final ConceptHealthService conceptHealthService = mock(ConceptHealthService.class);
    private final NoteCollectionRepository collectionRepository = mock(NoteCollectionRepository.class);
    private final NoteCollectionItemRepository collectionItemRepository = mock(NoteCollectionItemRepository.class);
    private final NoteService noteService = mock(NoteService.class);

    private UUID userId;
    private UUID noteId;
    private UUID studyPackId;
    private UUID collectionId;
    private QuickReviewSessionEntity noteSession;
    private QuickReviewSessionEntity collectionSession;
    private MockMvc adaptiveMockMvc;
    private MockMvc noteMockMvc;
    private MockMvc collectionMockMvc;
    private MockMvc deprecatedQuickReviewMockMvc;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        noteId = UUID.randomUUID();
        studyPackId = UUID.randomUUID();
        collectionId = UUID.randomUUID();

        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setNoteId(noteId);
        studyPack.setOwnerUserId(userId);
        studyPack.setTitle("Derivatives");

        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(collectionId);
        collection.setOwnerUserId(userId);
        collection.setTitle("Calculus Plan");

        noteSession = buildSession(UUID.randomUUID(), studyPackId, noteId, null);
        collectionSession = buildSession(UUID.randomUUID(), null, null, collectionId);

        QuickReviewAdaptivePracticeService service = new QuickReviewAdaptivePracticeService(
                studyPackRepository,
                sessionRepository,
                quizGenerationService,
                mock(com.studysnap.backend.service.ActivityTrackingService.class),
                subscriptionService,
                featureGateService,
                new StudySnapProperties(),
                userUsageService,
                authService,
                analyticsService,
                aiRateLimitService,
                generationContextResolver,
                conceptHealthService,
                collectionRepository,
                collectionItemRepository,
                new LongExamPlanSourceSampler()
        );

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        HandlerMethodArgumentResolver resolver = authenticatedUserResolver(authenticatedUser);
        GlobalExceptionHandler advice = new GlobalExceptionHandler(DataSize.ofMegabytes(10));
        adaptiveMockMvc = standaloneSetup(new AdaptivePracticeController(service))
                .setControllerAdvice(advice)
                .setCustomArgumentResolvers(resolver)
                .build();
        noteMockMvc = standaloneSetup(buildNoteController(service))
                .setControllerAdvice(advice)
                .setCustomArgumentResolvers(resolver)
                .build();
        collectionMockMvc = standaloneSetup(new NoteCollectionController(
                        mock(NoteCollectionService.class), service))
                .setControllerAdvice(advice)
                .setCustomArgumentResolvers(resolver)
                .build();
        deprecatedQuickReviewMockMvc = standaloneSetup(new QuickReviewSessionController(
                        mock(QuickReviewSessionService.class),
                        mock(QuickReviewStudyTipService.class),
                        service,
                        mock(ChallengeQuizService.class)))
                .setControllerAdvice(advice)
                .setCustomArgumentResolvers(resolver)
                .build();

        lenient().when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PRO);
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId))
                .thenReturn(Optional.of(studyPack));
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId))
                .thenReturn(Optional.of(studyPack));
        when(noteService.getOwnedStudyPackIdOrThrow(noteId.toString(), userId))
                .thenReturn(studyPackId.toString());
        when(collectionRepository.findByIdAndOwnerUserIdForUpdate(collectionId, userId))
                .thenReturn(Optional.of(collection));
        when(sessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(studyPackId), eq(QuickReviewSessionMode.ADAPTIVE), any()))
                .thenReturn(Optional.of(noteSession));
        when(sessionRepository.findByIdAndUserIdAndSessionMode(
                noteSession.getId(), userId, QuickReviewSessionMode.ADAPTIVE))
                .thenReturn(Optional.of(noteSession));
        when(sessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                noteSession.getId(), userId, QuickReviewSessionMode.ADAPTIVE))
                .thenReturn(Optional.of(noteSession));
        when(sessionRepository.findByUserIdAndSessionModeAndStatusInOrderByCreatedAtDesc(
                eq(userId), eq(QuickReviewSessionMode.ADAPTIVE), any()))
                .thenReturn(List.of(collectionSession, noteSession));
        when(sessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void everyAdaptiveQuizRouteSerializesAnsweredItemsRevealedAndUnansweredItemsRedacted() throws Exception {
        List<MvcResult> results = List.of(
                adaptiveMockMvc.perform(post("/adaptive-practice/study-packs/{id}/start", studyPackId))
                        .andExpect(status().isOk()).andReturn(),
                adaptiveMockMvc.perform(get("/adaptive-practice/sessions/{id}", noteSession.getId()))
                        .andExpect(status().isOk()).andReturn(),
                noteMockMvc.perform(post("/notes/{id}/adaptive-practice/start", noteId))
                        .andExpect(status().isOk()).andReturn(),
                noteMockMvc.perform(get("/notes/{id}/adaptive-practice/in-progress", noteId))
                        .andExpect(status().isOk()).andReturn(),
                collectionMockMvc.perform(post("/collections/{id}/adaptive-practice/start", collectionId))
                        .andExpect(status().isOk()).andReturn(),
                deprecatedQuickReviewMockMvc.perform(
                                post("/quick-review-sessions/study-packs/{id}/adaptive-practice", studyPackId))
                        .andExpect(status().isOk()).andReturn(),
                deprecatedQuickReviewMockMvc.perform(
                                post("/quick-review/study-packs/{id}/adaptive-practice", studyPackId))
                        .andExpect(status().isOk()).andReturn()
        );

        for (MvcResult result : results) {
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(body.path("quiz")).hasSize(2);
            assertThat(body.path("quiz").path(0).path("correctIndex").asInt()).isZero();
            assertThat(body.path("quiz").path(0).path("explanation").asText())
                    .isEqualTo("The derivative is cosine.");
            assertThat(body.path("quiz").path(0).path("keyConcept").asText()).isEqualTo(KEY_CONCEPT);
            assertThat(body.path("selectedChoices").path("0").asInt()).isZero();
            for (String field : ANSWER_KEY_FIELDS) {
                assertThat(body.path("quiz").path(1).path(field).isNull())
                        .as("unanswered quiz[1].%s", field)
                        .isTrue();
            }
            assertThat(body.path("quiz").path(1).path("acceptableAnswerGroups").isArray()).isTrue();
            assertThat(body.path("quiz").path(1).path("acceptableAnswerGroups")).isEmpty();
            assertThat(body.path("quiz").path(1).path("keyConcept").asText()).isEqualTo(KEY_CONCEPT);
        }

        assertStoredQuizStillHasFullAnswerKey(noteSession);
        assertStoredQuizStillHasFullAnswerKey(collectionSession);
    }

    @Test
    void answerRouteRevealsOneQuestionAndLocksItWithoutRedactingStoredQuiz() throws Exception {
        MvcResult result = adaptiveMockMvc.perform(post(
                        "/adaptive-practice/sessions/{id}/answer", noteSession.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionIndex": 1,
                                  "selectedMultiChoiceIndices": [0, 2]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("questionIndex").asInt()).isEqualTo(1);
        assertThat(body.path("question").path("correctIndices")).hasSize(2);
        assertThat(body.path("question").path("explanation").asText()).isEqualTo("Both are derivatives.");
        assertThat(QuizSessionStateUtils.extractSelectedMultiChoiceIndexes(
                noteSession.getSessionState(), QuizSessionStateUtils.extractQuiz(noteSession.getSessionState())))
                .containsExactlyEntriesOf(Map.of(1, List.of(0, 2)));
        assertStoredQuizStillHasFullAnswerKey(noteSession);

        adaptiveMockMvc.perform(post(
                        "/adaptive-practice/sessions/{id}/answer", noteSession.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questionIndex": 0,
                                  "selectedChoiceIndex": 1
                                }
                                """))
                .andExpect(status().isConflict());
        assertThat(QuizSessionStateUtils.extractSelectedChoiceIndexes(
                noteSession.getSessionState(), QuizSessionStateUtils.extractQuiz(noteSession.getSessionState())))
                .containsEntry(0, 0);
    }

    private QuickReviewSessionEntity buildSession(
            UUID sessionId,
            UUID anchorStudyPackId,
            UUID anchorNoteId,
            UUID sourceCollectionId
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStudyPackId(anchorStudyPackId);
        session.setNoteId(anchorNoteId);
        session.setSourceCollectionId(sourceCollectionId);
        session.setSessionMode(QuickReviewSessionMode.ADAPTIVE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setCurrentQuestionIndex(0);
        session.setTotalQuestions(2);
        session.setCorrectAnswers(0);
        session.setRetryCount(0);
        session.setScorePercentage(BigDecimal.ZERO);
        session.setCreatedAt(OffsetDateTime.now());
        Map<String, Object> state = QuizSessionStateUtils.withQuiz(fullQuiz(), null);
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(state, 0, 0));
        return session;
    }

    private List<QuizItem> fullQuiz() {
        QuizItem single = QuizItem.fromStoredComponents(
                "What is the derivative of sin(x)?",
                List.of("cos(x)", "-cos(x)", "-sin(x)", "tan(x)"),
                0,
                "Derivatives",
                "The derivative is cosine.",
                null,
                "MCQ",
                "THEORETICAL",
                "Differentiate sin(x).",
                null,
                null,
                KEY_CONCEPT,
                List.of("cos(x)"),
                List.of(List.of("cos(x)")),
                studyPackId.toString()
        );
        QuizItem multi = QuizItem.fromStoredComponents(
                "Which are derivatives?",
                List.of("cos(x)", "2x", "x", "1/x"),
                null,
                "Derivatives",
                "Both are derivatives.",
                null,
                "MULTI_SELECT",
                "THEORETICAL",
                "Apply the derivative rules.",
                List.of(0, 2),
                null,
                KEY_CONCEPT,
                null,
                null,
                studyPackId.toString()
        );
        return List.of(single, multi);
    }

    private void assertStoredQuizStillHasFullAnswerKey(QuickReviewSessionEntity session) {
        List<QuizItem> stored = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        assertThat(stored.get(0).correctIndex()).isZero();
        assertThat(stored.get(0).explanation()).isEqualTo("The derivative is cosine.");
        assertThat(stored.get(0).acceptableAnswers()).containsExactly("cos(x)");
        assertThat(stored.get(0).acceptableAnswerGroups()).containsExactly(List.of("cos(x)"));
        assertThat(stored.get(1).correctIndices()).containsExactly(0, 2);
        assertThat(stored.get(1).workingSolution()).isEqualTo("Apply the derivative rules.");
    }

    private NoteController buildNoteController(QuickReviewAdaptivePracticeService service) {
        return new NoteController(
                authService,
                mock(com.studysnap.backend.service.BulkGenerationResultService.class),
                noteService,
                mock(com.studysnap.backend.service.NoteShareService.class),
                mock(com.studysnap.backend.service.NoteBulkImportService.class),
                mock(com.studysnap.backend.service.NoteBulkGenerationService.class),
                mock(com.studysnap.backend.service.NoteBulkRegenerationService.class),
                mock(com.studysnap.backend.service.NoteBulkRegenerationReceiptService.class),
                mock(com.studysnap.backend.service.NoteRegenerationPreflightService.class),
                mock(com.studysnap.backend.service.NoteGenerationService.class),
                mock(com.studysnap.backend.service.NoteTextExtractionService.class),
                mock(com.studysnap.backend.service.StudyPackService.class),
                mock(QuickReviewSessionService.class),
                mock(QuickReviewStudyTipService.class),
                mock(ChallengeQuizService.class),
                service,
                mock(com.studysnap.backend.service.GeneratedQuizService.class),
                mock(com.studysnap.backend.service.QuizSessionHistoryService.class),
                mock(com.studysnap.backend.repository.NoteRepository.class),
                new StudySnapProperties()
        );
    }

    private HandlerMethodArgumentResolver authenticatedUserResolver(AuthenticatedUser authenticatedUser) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(AuthenticatedUser.class);
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
