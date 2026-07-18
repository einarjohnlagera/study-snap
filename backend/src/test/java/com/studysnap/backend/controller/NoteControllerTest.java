package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ExtractedNoteTextResponse;
import com.studysnap.backend.dto.BulkGenerationResultResponse;
import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicResponse;
import com.studysnap.backend.dto.BulkImportResultResponse;
import com.studysnap.backend.dto.BulkGenerateNotesRequest;
import com.studysnap.backend.dto.BulkGenerateNotesResponse;
import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.NoteStatusResponse;
import com.studysnap.backend.dto.PublicNoteDetailResponse;
import com.studysnap.backend.dto.PublicNoteListResponse;
import com.studysnap.backend.dto.PublicNoteLikeResponse;
import com.studysnap.backend.dto.RecentQuizSessionHistoryResponse;
import com.studysnap.backend.dto.UpdateNoteVisibilityRequest;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.dto.CopyOnSignupRequest;
import com.studysnap.backend.dto.CopyOnSignupResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.BulkGenerationResultService;
import com.studysnap.backend.service.ChallengeQuizService;
import com.studysnap.backend.service.GeneratedQuizService;
import com.studysnap.backend.service.NoteBulkImportService;
import com.studysnap.backend.service.NoteBulkGenerationService;
import com.studysnap.backend.service.NoteService;
import com.studysnap.backend.service.NoteGenerationService;
import com.studysnap.backend.service.NoteTextExtractionService;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import com.studysnap.backend.service.QuickReviewSessionService;
import com.studysnap.backend.service.QuickReviewStudyTipService;
import com.studysnap.backend.service.QuizSessionHistoryService;
import com.studysnap.backend.service.StudyPackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class NoteControllerTest {

    private static final String CREATOR_USERNAME = "einarjohn";
    private static final String USER_ADMIN_ROLE_GATE = "hasAnyRole('USER','ADMIN')";
    private static final String MULTIPART_FIELD_NAME = "files";
    private static final String TEXT_PLAIN_CONTENT_TYPE = "text/plain";
    private static final String STUDY_PACK_STATUS_GENERATING = "GENERATING";

    @Mock
    private AuthService authService;
    @Mock
    private BulkGenerationResultService bulkGenerationResultService;
    @Mock
    private NoteService noteService;
    @Mock
    private NoteBulkImportService noteBulkImportService;
    @Mock
    private NoteBulkGenerationService noteBulkGenerationService;
    @Mock
    private NoteGenerationService noteGenerationService;
    @Mock
    private NoteTextExtractionService noteTextExtractionService;
    @Mock
    private StudyPackService studyPackService;
    @Mock
    private QuickReviewSessionService quickReviewSessionService;
    @Mock
    private QuickReviewStudyTipService quickReviewStudyTipService;
    @Mock
    private ChallengeQuizService challengeQuizService;
    @Mock
    private QuickReviewAdaptivePracticeService quickReviewAdaptivePracticeService;
    @Mock
    private GeneratedQuizService generatedQuizService;
    @Mock
    private QuizSessionHistoryService quizSessionHistoryService;

    private NoteController noteController;

    @BeforeEach
    void setUp() {
        noteController = new NoteController(
                authService,
                bulkGenerationResultService,
                noteService,
                noteBulkImportService,
                noteBulkGenerationService,
                noteGenerationService,
                noteTextExtractionService,
                studyPackService,
                quickReviewSessionService,
                quickReviewStudyTipService,
                challengeQuizService,
                quickReviewAdaptivePracticeService,
                generatedQuizService,
                quizSessionHistoryService
        );
    }

    @Test
    void bulkGenerate_delegatesWithAdminQuotaBypass() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.ADMIN, true, 1);
        BulkGenerateNotesRequest request = new BulkGenerateNotesRequest(
                "Maternal Health",
                List.of("Prenatal Care"),
                true,
                "Nursing",
                NoteTargetProfileType.BOARD_TAKER
        );
        UUID resultId = UUID.randomUUID();
        BulkGenerateNotesResponse expected = new BulkGenerateNotesResponse(resultId, 1, 1, 0);
        when(noteBulkGenerationService.queueBatch(request, userId, false)).thenReturn(expected);

        BulkGenerateNotesResponse response = noteController.bulkGenerate(request, user);

        verify(authService).requireEmailVerified(userId);
        verify(noteBulkGenerationService).queueBatch(request, userId, false);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void listMine_clampsProvidedLimitBeforeDelegating() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        when(noteService.listMine(userId, 1)).thenReturn(List.of());

        List<NoteListItemResponse> response = noteController.listMine(0, user);

        assertThat(response).isEmpty();
        verify(noteService).listMine(userId, 1);
    }

    @Test
    void statusRoute_resolvesToStatusListHandlerInsteadOfNoteIdHandler() throws Exception {
        AuthenticatedUser routeUser = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);
        MockMvc mockMvc = standaloneSetup(noteController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
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
                        return routeUser;
                    }
                })
                .build();
        UUID noteId = UUID.randomUUID();
        when(noteService.listMineStatuses(routeUser.userId())).thenReturn(List.of(
                new NoteStatusResponse(noteId.toString(), STUDY_PACK_STATUS_GENERATING)
        ));

        mockMvc.perform(get("/notes/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(noteId.toString()))
                .andExpect(jsonPath("$[0].studyPackStatus").value(STUDY_PACK_STATUS_GENERATING));

        verify(noteService).listMineStatuses(routeUser.userId());
        verify(noteService, never()).getById("status", routeUser.userId());
    }

    @Test
    void bulkGenerate_delegatesWithUserQuotaEnforcement() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        BulkGenerateNotesRequest request = new BulkGenerateNotesRequest(
                "Maternal Health",
                List.of("Prenatal Care"),
                false,
                null,
                null
        );
        UUID resultId = UUID.randomUUID();
        BulkGenerateNotesResponse expected = new BulkGenerateNotesResponse(resultId, 1, 1, 0);
        when(noteBulkGenerationService.queueBatch(request, userId, true)).thenReturn(expected);

        BulkGenerateNotesResponse response = noteController.bulkGenerate(request, user);

        verify(authService).requireEmailVerified(userId);
        verify(noteBulkGenerationService).queueBatch(request, userId, true);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void bulkGenerate_requiresUserOrAdminRole() throws NoSuchMethodException {
        Method method = NoteController.class.getMethod(
                "bulkGenerate",
                BulkGenerateNotesRequest.class,
                AuthenticatedUser.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(USER_ADMIN_ROLE_GATE);
    }

    @Test
    void getBulkGenerationResult_consumesOwnedResult() {
        UUID userId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.ADMIN, true, 1);
        BulkGenerationResultResponse expected = new BulkGenerationResultResponse(
                resultId,
                "Maternal Health",
                "Nursing",
                NoteTargetProfileType.BOARD_TAKER.name(),
                true,
                2,
                1,
                List.of("Prenatal Care"),
                List.of(),
                OffsetDateTime.now()
        );
        when(bulkGenerationResultService.consumeResult(resultId, userId)).thenReturn(expected);

        BulkGenerationResultResponse response = noteController.getBulkGenerationResult(resultId, user);

        assertThat(response).isEqualTo(expected);
        verify(bulkGenerationResultService).consumeResult(resultId, userId);
    }

    @Test
    void getBulkGenerationResult_requiresUserOrAdminRole() throws NoSuchMethodException {
        Method method = NoteController.class.getMethod(
                "getBulkGenerationResult",
                UUID.class,
                AuthenticatedUser.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(USER_ADMIN_ROLE_GATE);
    }

    @Test
    void importBatch_delegatesToBulkImportService() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FIELD_NAME,
                "biology.txt",
                TEXT_PLAIN_CONTENT_TYPE,
                "content".getBytes()
        );
        UUID noteId = UUID.randomUUID();
        BulkImportResultResponse expected = new BulkImportResultResponse(
                List.of(new BulkImportResultResponse.ImportedNoteResult(noteId, "biology", "biology.txt", false)),
                List.of()
        );
        when(noteBulkImportService.importBatch(userId, List.of(file))).thenReturn(expected);

        BulkImportResultResponse response = noteController.importBatch(List.of(file), user);

        assertThat(response).isEqualTo(expected);
        verify(noteBulkImportService).importBatch(userId, List.of(file));
    }

    @Test
    void importBatch_requiresUserOrAdminRole() throws NoSuchMethodException {
        Method method = NoteController.class.getMethod("importBatch", List.class, AuthenticatedUser.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo(USER_ADMIN_ROLE_GATE);
    }

    @Test
    void generate_callsEmailVerificationBeforeGeneration() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        NoteResponse expected = new NoteResponse(
                "note-1",
                "Draft note",
                "Biology",
                "Nursing",
                "STUDENT",
                List.of("tag"),
                "content",
                "PRIVATE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null,
                null,
                false,
                null,
                null,
                "GENERATING",
                null,
                List.of(),
                List.of(),
                null,
                0,
                false,
                false,
                false,
                false
        );
        when(noteService.getById("note-1", userId)).thenReturn(expected);

        NoteResponse response = noteController.generate("note-1", false, user);

        verify(authService).requireEmailVerified(userId);
        verify(studyPackService).startAsyncGenerationFromNote("note-1", userId, false);
        verify(noteService).getById("note-1", userId);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void generate_passesAutoApplyMetadataWhenRequested() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        NoteResponse expected = new NoteResponse(
                "note-1",
                "Draft note",
                "Biology",
                "Nursing",
                "STUDENT",
                List.of("tag"),
                "content",
                "PRIVATE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null,
                null,
                false,
                null,
                null,
                "GENERATING",
                null,
                List.of(),
                List.of(),
                null,
                0,
                false,
                false,
                false,
                false
        );
        when(noteService.getById("note-1", userId)).thenReturn(expected);

        NoteResponse response = noteController.generate("note-1", true, user);

        verify(authService).requireEmailVerified(userId);
        verify(studyPackService).startAsyncGenerationFromNote("note-1", userId, true);
        verify(noteService).getById("note-1", userId);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void generateNoteFromTopic_callsEmailVerificationBeforeGeneration() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        GenerateNoteFromTopicRequest request = new GenerateNoteFromTopicRequest("Newton's Laws of Motion", null);
        GenerateNoteFromTopicResponse expected = new GenerateNoteFromTopicResponse("Generated note content");
        when(noteGenerationService.generateFromTopic(request, userId)).thenReturn(expected);

        GenerateNoteFromTopicResponse response = noteController.generateNoteFromTopic(request, user);

        verify(authService).requireEmailVerified(userId);
        verify(noteGenerationService).generateFromTopic(request, userId);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void listPublic_mapsAudienceQueryToTargetProfileFilter() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        when(noteService.listPublic(userId, "cinco", "recent", "history", List.of("mexican-history"), "nursing", CREATOR_USERNAME, NoteTargetProfileType.STUDENT, 4))
                .thenReturn(new PublicNoteListResponse(List.of(), 0));

        PublicNoteListResponse response = noteController.listPublic(
                "cinco",
                "recent",
                "history",
                List.of("mexican-history"),
                "nursing",
                CREATOR_USERNAME,
                "student",
                null,
                4,
                user
        );

        verify(noteService).listPublic(
                userId,
                "cinco",
                "recent",
                "history",
                List.of("mexican-history"),
                "nursing",
                CREATOR_USERNAME,
                NoteTargetProfileType.STUDENT,
                4
        );
        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
    }

    @Test
    void listPublic_clampsLargeSizeBeforeDelegating() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        when(noteService.listPublic(userId, null, null, null, null, null, null, null, 50))
                .thenReturn(new PublicNoteListResponse(List.of(), 0));

        PublicNoteListResponse response = noteController.listPublic(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                200,
                user
        );

        verify(noteService).listPublic(userId, null, null, null, null, null, null, null, 50);
        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
    }

    @Test
    void extractText_delegatesToExtractionService() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "Hello".getBytes());
        ExtractedNoteTextResponse expected = new ExtractedNoteTextResponse(
                "txt",
                "Hello",
                new ExtractedNoteTextResponse.ExtractionMeta(null, false)
        );
        when(noteTextExtractionService.extractText(file, userId)).thenReturn(expected);

        ExtractedNoteTextResponse response = noteController.extractText(file, user);

        assertThat(response).isEqualTo(expected);
        verify(noteTextExtractionService).extractText(file, userId);
    }

    @Test
    void generate_blocksUnverifiedUsers() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, false, 1);
        AppException verificationError = new AppException(
                "EMAIL_VERIFICATION_REQUIRED",
                "Email verification required.",
                HttpStatus.FORBIDDEN
        );
        doThrow(verificationError).when(authService).requireEmailVerified(userId);

        assertThatThrownBy(() -> noteController.generate("note-1", false, user))
                .isSameAs(verificationError);

        verify(studyPackService, never()).startAsyncGenerationFromNote("note-1", userId, false);
    }

    @Test
    void copyNoteOnSignup_copiesPublicNoteAndStartsGeneration() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        String publicNoteId = "public-note-1";
        String copiedNoteId = "copied-note-1";
        NoteResponse copied = buildNoteResponse(copiedNoteId, "DRAFT");
        when(noteService.copyPublicNoteForSignup(publicNoteId, userId)).thenReturn(copied);

        CopyOnSignupResponse response = noteController.copyNoteOnSignup(
                new CopyOnSignupRequest(publicNoteId),
                user
        );

        verify(authService).requireEmailVerified(userId);
        verify(noteService).copyPublicNoteForSignup(publicNoteId, userId);
        verify(studyPackService).startAsyncGenerationFromNote(copiedNoteId, userId);
        assertThat(response.noteId()).isEqualTo(copiedNoteId);
    }

    @Test
    void copyNoteOnSignup_rethrowsNoteNotFound() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        String missingNoteId = "missing-note";
        NoteNotFoundException notFound = new NoteNotFoundException();
        when(noteService.copyPublicNoteForSignup(missingNoteId, userId)).thenThrow(notFound);

        assertThatThrownBy(() -> noteController.copyNoteOnSignup(new CopyOnSignupRequest(missingNoteId), user))
                .isSameAs(notFound);

        verify(authService).requireEmailVerified(userId);
        verify(studyPackService, never()).startAsyncGenerationFromNote(missingNoteId, userId);
    }

    @Test
    void copyNoteOnSignup_returnsExistingCopyWithoutStartingDuplicateGeneration() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        String publicNoteId = "public-note-1";
        String existingNoteId = "existing-note-1";
        NoteResponse existingCopy = buildNoteResponse(existingNoteId, "GENERATING");
        when(noteService.copyPublicNoteForSignup(publicNoteId, userId)).thenReturn(existingCopy);

        CopyOnSignupResponse response = noteController.copyNoteOnSignup(
                new CopyOnSignupRequest(publicNoteId),
                user
        );

        verify(authService).requireEmailVerified(userId);
        verify(noteService).copyPublicNoteForSignup(publicNoteId, userId);
        verify(studyPackService, never()).startAsyncGenerationFromNote(existingNoteId, userId);
        assertThat(response.noteId()).isEqualTo(existingNoteId);
    }

    @Test
    void generateGeneratedQuiz_callsEmailVerificationBeforeGeneration() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        com.studysnap.backend.dto.GeneratedQuizResponse expected = new com.studysnap.backend.dto.GeneratedQuizResponse(
                UUID.randomUUID().toString(),
                "note-1",
                List.of(),
                OffsetDateTime.now()
        );
        com.studysnap.backend.dto.GenerateGeneratedQuizRequest request =
                new com.studysnap.backend.dto.GenerateGeneratedQuizRequest(20);
        when(generatedQuizService.generate("note-1", userId, 20, null)).thenReturn(expected);

        com.studysnap.backend.dto.GeneratedQuizResponse response = noteController.generateGeneratedQuiz("note-1", request, user);

        verify(authService).requireEmailVerified(userId);
        verify(generatedQuizService).generate("note-1", userId, 20, null);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void updateVisibility_publicRequiresEmailVerification() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, false, 1);
        NoteResponse updated = null;
        when(noteService.updateVisibility("note-1", "PUBLIC", userId)).thenReturn(updated);

        noteController.updateVisibility("note-1", new UpdateNoteVisibilityRequest("PUBLIC"), user);

        verify(authService).requireEmailVerified(userId);
        verify(noteService).updateVisibility("note-1", "PUBLIC", userId);
    }

    @Test
    void updateVisibility_privateDoesNotRequireEmailVerification() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, false, 1);
        NoteResponse updated = null;
        when(noteService.updateVisibility("note-1", "PRIVATE", userId)).thenReturn(updated);

        noteController.updateVisibility("note-1", new UpdateNoteVisibilityRequest("PRIVATE"), user);

        verify(authService, never()).requireEmailVerified(userId);
        verify(noteService).updateVisibility("note-1", "PRIVATE", userId);
    }

    @Test
    void getPublicBySeoPath_delegatesToService() {
        PublicNoteDetailResponse expected = new PublicNoteDetailResponse(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "Cell Structure",
                "Science",
                List.of("cells"),
                "Full note content",
                "preview",
                "STUDY_PACK_READY",
                "Summary",
                List.of("Nucleus"),
                List.of(),
                "noteguru",
                "noteguru",
                false,
                false,
                OffsetDateTime.now()
        );
        when(noteService.getPublicBySeoPath("science", "cell-structure", null)).thenReturn(expected);

        PublicNoteDetailResponse response = noteController.getPublicBySeoPath("science", "cell-structure", null);

        assertThat(response).isEqualTo(expected);
        verify(noteService).getPublicBySeoPath("science", "cell-structure", null);
    }

    @Test
    void listPublic_delegatesViewerIdentityWithoutExcludingOwnNotes() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        List<NoteListItemResponse> expected = List.of(
                new NoteListItemResponse(
                        UUID.randomUUID().toString(),
                        userId.toString(),
                        "My public note",
                        "Nursing",
                        "STUDENT",
                        "Biology",
                        List.of("cells"),
                        "preview",
                        "summary preview",
                        "PUBLIC",
                        null,
                        "STUDY_PACK_READY",
                        4,
                        2L,
                        0L,
                        1L,
                        5L,
                        "My Notes",
                        "mynotes",
                        false,
                        true,
                        OffsetDateTime.now(),
                        OffsetDateTime.now(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false
                )
        );
        when(noteService.listPublic(userId, null, null, null, null, null, null, null, null))
                .thenReturn(new PublicNoteListResponse(expected, expected.size()));

        PublicNoteListResponse response = noteController.listPublic(null, null, null, null, null, null, null, null, null, user);

        assertThat(response.items()).isEqualTo(expected);
        assertThat(response.total()).isEqualTo(expected.size());
        verify(noteService).listPublic(userId, null, null, null, null, null, null, null, null);
    }

    @Test
    void listRecentQuizSessions_delegatesAfterValidatingOwnedStudyPack() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        List<RecentQuizSessionHistoryResponse> expected = List.of(new RecentQuizSessionHistoryResponse(
                "session-1",
                "LONG_EXAM",
                20,
                16,
                java.math.BigDecimal.valueOf(80),
                0,
                null,
                List.of(),
                2,
                OffsetDateTime.now().minusMinutes(20),
                OffsetDateTime.now()
        ));
        when(noteService.getOwnedStudyPackIdOrThrow("note-1", userId)).thenReturn("study-pack-1");
        when(quizSessionHistoryService.listRecentSessions("note-1", userId, 5)).thenReturn(expected);

        List<RecentQuizSessionHistoryResponse> response = noteController.listRecentQuizSessions("note-1", 5, user);

        assertThat(response).isEqualTo(expected);
        verify(noteService).getOwnedStudyPackIdOrThrow("note-1", userId);
        verify(quizSessionHistoryService).listRecentSessions("note-1", userId, 5);
    }

    @Test
    void togglePublicNoteLike_delegatesToService() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        PublicNoteLikeResponse expected = new PublicNoteLikeResponse(true, 12L);
        when(noteService.togglePublicNoteLike("note-1", userId)).thenReturn(expected);

        PublicNoteLikeResponse response = noteController.togglePublicNoteLike("note-1", user);

        assertThat(response).isEqualTo(expected);
        verify(noteService).togglePublicNoteLike("note-1", userId);
    }

    private static NoteResponse buildNoteResponse(String id, String studyPackStatus) {
        return new NoteResponse(
                id,
                "Copied note",
                "Biology",
                "Nursing",
                "STUDENT",
                List.of("tag"),
                "content",
                "PRIVATE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null,
                null,
                false,
                null,
                null,
                studyPackStatus,
                null,
                List.of(),
                List.of(),
                null,
                0,
                false,
                false,
                false,
                false
        );
    }
}
