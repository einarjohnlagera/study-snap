package com.studysnap.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.StudyPackStatus;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.GlobalExceptionHandler;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.NoteShareRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.ActivityTrackingService;
import com.studysnap.backend.service.AnalyticsService;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.ChallengeQuizService;
import com.studysnap.backend.service.ConceptHealthService;
import com.studysnap.backend.service.FeatureGateService;
import com.studysnap.backend.service.NoteService;
import com.studysnap.backend.service.NoteShareService;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import com.studysnap.backend.service.QuickReviewSessionService;
import com.studysnap.backend.service.QuickReviewStudyTipService;
import com.studysnap.backend.service.StudyPackQuizMasteryService;
import com.studysnap.backend.service.SubscriptionService;
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
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class QuickReviewSessionControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuickReviewSessionRepository sessionRepository = mock(QuickReviewSessionRepository.class);
    private final StudyPackRepository studyPackRepository = mock(StudyPackRepository.class);
    private final NoteRepository noteRepository = mock(NoteRepository.class);
    private final NoteShareService noteShareService = mock(NoteShareService.class);
    private final NoteShareRepository noteShareRepository = mock(NoteShareRepository.class);
    private NoteService noteService;
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);

    private UUID recipientUserId;
    private UUID noteId;
    private UUID studyPackId;
    private QuickReviewSessionEntity session;
    private StudyPackEntity studyPack;
    private NoteEntity note;
    private MockMvc sessionMockMvc;
    private MockMvc noteMockMvc;

    @BeforeEach
    void setUp() {
        recipientUserId = UUID.randomUUID();
        noteId = UUID.randomUUID();
        studyPackId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();

        List<QuizItem> quiz = List.of(
                new QuizItem("Answered?", List.of("Yes", "No"), 0, "One", "Answered explanation"),
                new QuizItem("Unanswered?", List.of("Yes", "No"), 1, "Two", "Hidden explanation")
        );
        studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setNoteId(noteId);
        studyPack.setOwnerUserId(ownerUserId);
        studyPack.setTitle("Old pack title");
        studyPack.setKeyConcepts(List.of("One", "Two"));
        studyPack.setQuiz(quiz);
        studyPack.setStatus(StudyPackStatus.DONE);

        note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(ownerUserId);
        note.setTitle("Current note title");

        noteService = buildNoteService();

        session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(recipientUserId);
        session.setStudyPackId(studyPackId);
        session.setNoteId(noteId);
        session.setSessionMode(QuickReviewSessionMode.QUICK_REVIEW);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setRetryCount(0);
        session.setTotalQuestions(2);
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO);
        session.setCreatedAt(OffsetDateTime.now().minusMinutes(5));
        Map<String, Object> state = QuizSessionStateUtils.withRoundSelection(null, 0, 0, 0);
        session.setSessionState(QuizSessionStateUtils.withSelectedChoice(state, 0, 0));

        FeatureGateService featureGateService = new FeatureGateService(subscriptionService, new StudySnapProperties());
        StudyPackQuizMasteryService masteryService = mock(StudyPackQuizMasteryService.class);
        QuickReviewSessionService service = new QuickReviewSessionService(
                sessionRepository,
                studyPackRepository,
                mock(ActivityEventRepository.class),
                mock(ActivityTrackingService.class),
                mock(AnalyticsService.class),
                subscriptionService,
                featureGateService,
                mock(ConceptHealthService.class),
                masteryService,
                noteShareService,
                noteRepository
        );

        AuthenticatedUser user = new AuthenticatedUser(recipientUserId, UserRole.USER, true, 1);
        HandlerMethodArgumentResolver resolver = authenticatedUserResolver(user);
        GlobalExceptionHandler advice = new GlobalExceptionHandler(DataSize.ofMegabytes(10));
        sessionMockMvc = standaloneSetup(new QuickReviewSessionController(
                        service,
                        mock(QuickReviewStudyTipService.class),
                        mock(QuickReviewAdaptivePracticeService.class),
                        mock(ChallengeQuizService.class)))
                .setControllerAdvice(advice)
                .setCustomArgumentResolvers(resolver)
                .build();
        noteMockMvc = standaloneSetup(buildNoteController(service))
                .setControllerAdvice(advice)
                .setCustomArgumentResolvers(resolver)
                .build();

        lenient().when(subscriptionService.resolvePlan(recipientUserId)).thenReturn(PlanType.PRO);
        lenient().when(masteryService.tryResolve(any(), any())).thenReturn(Optional.empty());
        when(noteRepository.findByIdAndOwnerUserId(noteId, recipientUserId)).thenReturn(Optional.empty());
        when(noteShareRepository.existsLiveAuthorizedShare(noteId, recipientUserId)).thenReturn(true);
        when(studyPackRepository.findByNoteId(noteId)).thenReturn(Optional.of(studyPack));
        when(studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, recipientUserId))
                .thenReturn(Optional.empty());
        when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, recipientUserId))
                .thenReturn(Optional.empty());
        when(studyPackRepository.findById(studyPackId)).thenReturn(Optional.of(studyPack));
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(note));
        when(sessionRepository.findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                recipientUserId, studyPackId, QuickReviewSessionMode.QUICK_REVIEW,
                QuickReviewSessionStatus.IN_PROGRESS)).thenReturn(Optional.of(session));
        when(sessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                session.getId(), recipientUserId, QuickReviewSessionMode.QUICK_REVIEW))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any(QuickReviewSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recipientEntryRoutesSerializeOnlyPreviouslyAnsweredKeys() throws Exception {
        List<MvcResult> results = List.of(
                noteMockMvc.perform(post("/notes/{id}/quick-review/start", noteId))
                        .andExpect(status().isOk()).andReturn(),
                noteMockMvc.perform(get("/notes/{id}/quick-review/in-progress", noteId))
                        .andExpect(status().isOk()).andReturn(),
                sessionMockMvc.perform(post("/quick-review/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"studyPackId\":\"" + studyPackId + "\"}"))
                        .andExpect(status().isOk()).andReturn(),
                sessionMockMvc.perform(get("/quick-review/study-packs/{id}/in-progress", studyPackId))
                        .andExpect(status().isOk()).andReturn(),
                sessionMockMvc.perform(post("/quick-review-sessions/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"studyPackId\":\"" + studyPackId + "\"}"))
                        .andExpect(status().isOk()).andReturn(),
                sessionMockMvc.perform(get("/quick-review-sessions/study-packs/{id}/in-progress", studyPackId))
                        .andExpect(status().isOk()).andReturn()
        );

        for (MvcResult result : results) {
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(body.path("noteId").asText()).isEqualTo(noteId.toString());
            assertThat(body.path("title").asText()).isEqualTo("Current note title");
            assertThat(body.path("isOwner").asBoolean()).isFalse();
            assertThat(body.path("quiz").path(0).path("correctIndex").asInt()).isZero();
            assertThat(body.path("quiz").path(0).path("explanation").asText())
                    .isEqualTo("Answered explanation");
            assertThat(body.path("quiz").path(1).path("correctIndex").isNull()).isTrue();
            assertThat(body.path("quiz").path(1).path("correctIndices").isNull()).isTrue();
            assertThat(body.path("quiz").path(1).path("explanation").isNull()).isTrue();
            assertThat(body.path("quiz").path(1).path("workingSolution").isNull()).isTrue();
            assertThat(body.path("quiz").path(1).path("acceptableAnswers").isNull()).isTrue();
        }
        assertThat(studyPack.getQuiz()).allSatisfy(question -> assertThat(question.getCorrectIndex()).isNotNull());
        verify(noteShareRepository, times(2)).existsLiveAuthorizedShare(noteId, recipientUserId);
        verify(noteShareService, atLeastOnce()).requireSharedStudyPackAccess(recipientUserId, studyPackId);
    }

    @Test
    void answerAndProgressRoutesUseTheLockedServerState() throws Exception {
        String answerBody = "{\"questionIndex\":1,\"retryCount\":0,\"selectedChoiceIndex\":1}";
        sessionMockMvc.perform(post("/quick-review/{id}/answer", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.correctIndex").value(1))
                .andExpect(jsonPath("$.question.explanation").value("Hidden explanation"));
        sessionMockMvc.perform(post("/quick-review-sessions/{id}/answer", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody))
                .andExpect(status().isOk());

        sessionMockMvc.perform(post("/quick-review/{id}/progress", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentQuestionIndex": 1,
                                  "currentRound": "INITIAL",
                                  "retryCount": 0,
                                  "sessionState": {
                                    "selectedChoices": {"1": 0},
                                    "roundSelections": {"0": {"1": 0}},
                                    "retryQuestionIndexes": [1],
                                    "activeQuestionIndexes": [0, 1]
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(QuizSessionStateUtils.extractSelectedChoiceIndexes(
                session.getSessionState(), studyPack.getQuiz())).containsEntry(1, 1);
        assertThat(session.getSessionState()).containsEntry("retryQuestionIndexes", List.of(1));

        sessionMockMvc.perform(post("/quick-review/{id}/answer", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIndex\":1,\"retryCount\":0,\"selectedChoiceIndex\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("QUICK_REVIEW_ANSWER_ALREADY_RECORDED"));

        sessionMockMvc.perform(post("/quick-review/{id}/answer", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIndex\":1,\"retryCount\":2,\"selectedChoiceIndex\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        sessionMockMvc.perform(post("/quick-review/{id}/progress", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentQuestionIndex": 1,
                                  "currentRound": "RETRY",
                                  "retryCount": 2,
                                  "sessionState": {}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void recipientRegenerationStartsFreshAndCanAnswerTheSameIndexAgain() throws Exception {
        note.setGenerationEnqueuedAt(session.getCreatedAt().plusMinutes(1));

        MvcResult start = noteMockMvc.perform(post("/notes/{id}/quick-review/start", noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quiz[0].correctIndex").doesNotExist())
                .andReturn();

        String freshSessionId = objectMapper.readTree(start.getResponse().getContentAsString())
                .path("sessionId").asText();
        assertThat(freshSessionId).isNotEqualTo(session.getId().toString());
        ArgumentCaptor<QuickReviewSessionEntity> savedSessions = ArgumentCaptor.forClass(QuickReviewSessionEntity.class);
        verify(sessionRepository, times(2)).save(savedSessions.capture());
        QuickReviewSessionEntity fresh = savedSessions.getAllValues().stream()
                .filter(saved -> freshSessionId.equals(saved.getId().toString()))
                .findFirst()
                .orElseThrow();
        when(sessionRepository.findByIdAndUserIdAndSessionModeForUpdate(
                fresh.getId(), recipientUserId, QuickReviewSessionMode.QUICK_REVIEW))
                .thenReturn(Optional.of(fresh));

        sessionMockMvc.perform(post("/quick-review/{id}/answer", freshSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIndex\":0,\"retryCount\":0,\"selectedChoiceIndex\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.correctIndex").value(0));
    }

    private NoteController buildNoteController(QuickReviewSessionService service) {
        return new NoteController(
                mock(AuthService.class),
                mock(com.studysnap.backend.service.BulkGenerationResultService.class),
                noteService,
                noteShareService,
                mock(com.studysnap.backend.service.NoteBulkImportService.class),
                mock(com.studysnap.backend.service.NoteBulkGenerationService.class),
                mock(com.studysnap.backend.service.NoteBulkRegenerationService.class),
                mock(com.studysnap.backend.service.NoteBulkRegenerationReceiptService.class),
                mock(com.studysnap.backend.service.NoteRegenerationPreflightService.class),
                mock(com.studysnap.backend.service.NoteGenerationService.class),
                mock(com.studysnap.backend.service.NoteTextExtractionService.class),
                mock(com.studysnap.backend.service.StudyPackService.class),
                service,
                mock(QuickReviewStudyTipService.class),
                mock(ChallengeQuizService.class),
                mock(QuickReviewAdaptivePracticeService.class),
                mock(com.studysnap.backend.service.GeneratedQuizService.class),
                mock(com.studysnap.backend.service.QuizSessionHistoryService.class),
                noteRepository,
                new StudySnapProperties()
        );
    }

    private NoteService buildNoteService() {
        return new NoteService(
                noteRepository,
                noteShareRepository,
                mock(com.studysnap.backend.repository.AnalyticsEventRepository.class),
                mock(com.studysnap.backend.repository.PublicNoteLikeRepository.class),
                studyPackRepository,
                mock(com.studysnap.backend.repository.GeneratedQuizRepository.class),
                mock(com.studysnap.backend.repository.UserRepository.class),
                mock(com.studysnap.backend.service.QuizSessionHistoryService.class),
                subscriptionService,
                mock(com.studysnap.backend.service.FeatureGateService.class),
                mock(com.studysnap.backend.service.AnalyticsService.class),
                mock(com.studysnap.backend.service.ContentModerationService.class),
                mock(com.studysnap.backend.service.OnboardingGuardService.class),
                mock(com.studysnap.backend.service.OfficialChallengeQuizTemplateService.class),
                mock(com.studysnap.backend.repository.NoteCourseProgramRepository.class),
                mock(com.studysnap.backend.repository.CourseProgramCatalogRepository.class),
                mock(com.studysnap.backend.service.StudyPackQuizMasteryService.class),
                mock(com.studysnap.backend.service.StudyPackGenerationContextResolver.class)
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
