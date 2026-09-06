package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ExtractedNoteTextResponse;
import com.studysnap.backend.dto.BulkGenerationResultResponse;
import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicResponse;
import com.studysnap.backend.dto.BulkImportResultResponse;
import com.studysnap.backend.dto.BulkGenerateNotesRequest;
import com.studysnap.backend.dto.BulkGenerateNotesResponse;
import com.studysnap.backend.dto.BulkRegenerateNotesRequest;
import com.studysnap.backend.dto.BulkRegenerateNotesResponse;
import com.studysnap.backend.dto.NoteRegenerationPreflightRequest;
import com.studysnap.backend.dto.NoteRegenerationPreflightResponse;
import com.studysnap.backend.dto.NoteBulkRegenerationReceiptResponse;
import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.NoteStatusResponse;
import com.studysnap.backend.dto.NotesLibraryFilterOptionsResponse;
import com.studysnap.backend.dto.NotesLibraryIdsResponse;
import com.studysnap.backend.dto.NotesLibraryPageResponse;
import com.studysnap.backend.dto.PublicNoteDetailResponse;
import com.studysnap.backend.dto.PublicLibraryDiscoverySectionsResponse;
import com.studysnap.backend.dto.PublicNoteListResponse;
import com.studysnap.backend.dto.PublicNoteLikeResponse;
import com.studysnap.backend.dto.RecentQuizSessionHistoryResponse;
import com.studysnap.backend.dto.RegenerateNoteRequest;
import com.studysnap.backend.dto.SubjectStatsResponse;
import com.studysnap.backend.dto.UpdateNoteVisibilityRequest;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.UnknownNoteRegenerationScopeException;
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
import org.springframework.http.MediaType;
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
import java.util.Map;
import java.util.UUID;

import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private static final String ADAPTIVE_PRACTICE_ENTRY_DASHBOARD_FOCUS_AREAS = "dashboard-focus-areas";

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
    private com.studysnap.backend.service.NoteBulkRegenerationService noteBulkRegenerationService;
    private com.studysnap.backend.service.NoteRegenerationPreflightService noteRegenerationPreflightService;
    private com.studysnap.backend.service.NoteBulkRegenerationReceiptService noteBulkRegenerationReceiptService;

    @BeforeEach
    void setUp() {
        noteBulkRegenerationService =
                org.mockito.Mockito.mock(com.studysnap.backend.service.NoteBulkRegenerationService.class);
        noteRegenerationPreflightService =
                org.mockito.Mockito.mock(com.studysnap.backend.service.NoteRegenerationPreflightService.class);
        noteBulkRegenerationReceiptService =
                org.mockito.Mockito.mock(com.studysnap.backend.service.NoteBulkRegenerationReceiptService.class);
        noteController = new NoteController(
                authService,
                bulkGenerationResultService,
                noteService,
                org.mockito.Mockito.mock(com.studysnap.backend.service.NoteShareService.class),
                noteBulkImportService,
                noteBulkGenerationService,
                noteBulkRegenerationService,
                noteBulkRegenerationReceiptService,
                noteRegenerationPreflightService,
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

    /**
     * ⚠️ THE CONTROLLER'S {@code enforceLimits} EXPRESSION WAS UNGUARDED. The `v0.119.0` pressure test
     * mutated it to a flat {@code false} — granting every TEACHER curator unlimited free note-generation
     * and Study Pack units on a paid account, which owner decision 1 forbids outright — and the ENTIRE
     * suite stayed green. The service-layer guards could not see it because the harness writes the
     * boolean into the test itself, so the expression is unreachable from them.
     *
     * <p>The twin for bulk GENERATION has existed all along (below); it was simply not copied when
     * bulk regeneration was added. This is the `v0.115.0` lesson — a bare role substitution surviving
     * 99 tests — reproduced on a new endpoint.
     */
    @Test
    void bulkRegenerate_delegatesWithAdminQuotaBypass() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.ADMIN, true, 1);
        BulkRegenerateNotesRequest request =
                new BulkRegenerateNotesRequest(List.of(UUID.randomUUID()), "NOTE_AND_STUDY_PACK");
        BulkRegenerateNotesResponse expected =
                new BulkRegenerateNotesResponse(UUID.randomUUID(), "NOTE_AND_STUDY_PACK", 1);
        when(noteBulkRegenerationService.queueBatch(request, userId, false)).thenReturn(expected);

        assertThat(noteController.bulkRegenerate(request, user)).isEqualTo(expected);
        verify(noteBulkRegenerationService).queueBatch(request, userId, false);
    }

    @Test
    void bulkRegenerate_delegatesWithQuotaEnforcementForANonAdminCurator() {
        UUID userId = UUID.randomUUID();
        // A TEACHER curator carries UserRole.USER — the profile is what makes them a curator, and the
        // bypass is keyed on ROLE. This is the leg that must never become a bypass.
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        BulkRegenerateNotesRequest request =
                new BulkRegenerateNotesRequest(List.of(UUID.randomUUID()), "STUDY_PACK");
        BulkRegenerateNotesResponse expected =
                new BulkRegenerateNotesResponse(UUID.randomUUID(), "STUDY_PACK", 1);
        when(noteBulkRegenerationService.queueBatch(request, userId, true)).thenReturn(expected);

        assertThat(noteController.bulkRegenerate(request, user)).isEqualTo(expected);
        verify(noteBulkRegenerationService).queueBatch(request, userId, true);
    }

    @Test
    void regeneratePreflight_appliesTheSameQuotaExpressionAsTheBatch() {
        UUID adminId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        NoteRegenerationPreflightRequest request =
                new NoteRegenerationPreflightRequest(List.of(UUID.randomUUID()), "NOTE_AND_STUDY_PACK");

        noteController.regeneratePreflight(request, new AuthenticatedUser(adminId, UserRole.ADMIN, true, 1));
        noteController.regeneratePreflight(request, new AuthenticatedUser(teacherId, UserRole.USER, true, 1));

        // Disclosure must not describe a different allowance than enforcement applies.
        verify(noteRegenerationPreflightService).preflight(request, adminId, false);
        verify(noteRegenerationPreflightService).preflight(request, teacherId, true);
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
                null,
                null
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
    void create_ignoresLegacyTargetProfileTypeJsonField() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        NoteResponse expected = buildNoteResponse(UUID.randomUUID().toString(), "DRAFT");
        when(noteService.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(userId)))
                .thenReturn(expected);

        buildMockMvc(user).perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Legacy client note",
                                  "domainContext": null,
                                  "learnerLevel": null,
                                  "targetProfileType": "BOARD_TAKER",
                                  "content": "Safe content"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(expected.id()));

        org.mockito.ArgumentCaptor<com.studysnap.backend.dto.UpsertNoteRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.studysnap.backend.dto.UpsertNoteRequest.class);
        verify(noteService).create(captor.capture(), org.mockito.ArgumentMatchers.eq(userId));
        assertThat(captor.getValue().title()).isEqualTo("Legacy client note");
        assertThat(captor.getValue().content()).isEqualTo("Safe content");
    }

    @Test
    void startAdaptivePractice_forwardsEntryToService() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        String noteId = UUID.randomUUID().toString();
        String studyPackId = UUID.randomUUID().toString();
        when(noteService.getOwnedStudyPackIdOrThrow(noteId, userId)).thenReturn(studyPackId);

        noteController.startAdaptivePractice(noteId, ADAPTIVE_PRACTICE_ENTRY_DASHBOARD_FOCUS_AREAS, user);

        verify(authService).requireEmailVerified(userId);
        verify(quickReviewAdaptivePracticeService).generateAdaptiveQuiz(
                studyPackId,
                userId,
                ADAPTIVE_PRACTICE_ENTRY_DASHBOARD_FOCUS_AREAS
        );
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
        MockMvc mockMvc = buildMockMvc(routeUser);
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
    void libraryRoutesResolveToDedicatedHandlersInsteadOfNoteIdHandler() throws Exception {
        AuthenticatedUser routeUser = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);
        MockMvc mockMvc = buildMockMvc(routeUser);
        when(noteService.listLibraryPage(
                routeUser.userId(), null, "ALL", null, null, null, "ALL", null, "RECENTLY_UPDATED", 0, 20
        )).thenReturn(new NotesLibraryPageResponse(List.of(), 0, 20, 0, false));
        when(noteService.listLibraryMatchingIds(
                routeUser.userId(), null, "ALL", null, null, null, "ALL", null
        )).thenReturn(new NotesLibraryIdsResponse(List.of(), 0, false));
        when(noteService.getLibrarySubjectStats(
                routeUser.userId(), null, "ALL", null, null, "ALL", null
        )).thenReturn(new SubjectStatsResponse(List.of(), 0, 0));
        when(noteService.getLibraryFilterOptions(routeUser.userId()))
                .thenReturn(new NotesLibraryFilterOptionsResponse(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/notes/library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(20));
        mockMvc.perform(get("/notes/library/ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteIds").isArray())
                .andExpect(jsonPath("$.truncated").value(false));
        mockMvc.perform(get("/notes/library/subject-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topSubjects").isArray())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/notes/library/filter-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects").isArray())
                .andExpect(jsonPath("$.coursePrograms").isArray())
                .andExpect(jsonPath("$.tags").isArray());

        verify(noteService).listLibraryPage(
                routeUser.userId(), null, "ALL", null, null, null, "ALL", null, "RECENTLY_UPDATED", 0, 20
        );
        verify(noteService).listLibraryMatchingIds(
                routeUser.userId(), null, "ALL", null, null, null, "ALL", null
        );
        verify(noteService).getLibrarySubjectStats(
                routeUser.userId(), null, "ALL", null, null, "ALL", null
        );
        verify(noteService).getLibraryFilterOptions(routeUser.userId());
        verify(noteService, never()).getById("library", routeUser.userId());
    }

    @Test
    void listLibraryPageClampsPageAndPageSizeBeforeDelegating() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        NotesLibraryPageResponse expected = new NotesLibraryPageResponse(List.of(), 0, 100, 0, false);
        when(noteService.listLibraryPage(
                userId, null, "ALL", null, null, null, "ALL", null, "RECENTLY_UPDATED", 0, 100
        )).thenReturn(expected);

        NotesLibraryPageResponse response = noteController.listLibraryPage(
                null, "ALL", null, null, null, "ALL", null, "RECENTLY_UPDATED", -1, 101, user
        );

        assertThat(response).isEqualTo(expected);
        verify(noteService).listLibraryPage(
                userId, null, "ALL", null, null, null, "ALL", null, "RECENTLY_UPDATED", 0, 100
        );
    }

    @Test
    void quickReviewLastReviewedRouteReturnsRequestedNotesInOrderWithNullForMissingCompletion() throws Exception {
        AuthenticatedUser routeUser = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);
        MockMvc mockMvc = buildMockMvc(routeUser);
        UUID reviewedNoteId = UUID.randomUUID();
        UUID neverReviewedNoteId = UUID.randomUUID();
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-07-18T10:15:00Z");
        List<UUID> requestedNoteIds = List.of(reviewedNoteId, neverReviewedNoteId);
        when(quickReviewSessionService.getLastReviewedAtByNoteIds(requestedNoteIds, routeUser.userId()))
                .thenReturn(Map.of(reviewedNoteId, completedAt));

        mockMvc.perform(get("/notes/quick-review/last-reviewed")
                        .queryParam("noteIds", reviewedNoteId.toString(), neverReviewedNoteId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].noteId").value(reviewedNoteId.toString()))
                .andExpect(jsonPath("$[0].lastReviewedAt").value("2026-07-18T10:15:00Z"))
                .andExpect(jsonPath("$[1].noteId").value(neverReviewedNoteId.toString()))
                .andExpect(jsonPath("$[1].lastReviewedAt").value(nullValue()));

        verify(quickReviewSessionService).getLastReviewedAtByNoteIds(requestedNoteIds, routeUser.userId());
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
                null,
                null,
                null,
                true,
                2,
                1,
                List.of("Prenatal Care"),
                List.of(),
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
                null,
                null,
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
                null,
                null,
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
                false
        );
        when(noteService.getById("note-1", userId)).thenReturn(expected);

        NoteResponse response = noteController.generate("note-1", true, user);

        verify(authService).requireEmailVerified(userId);
        verify(studyPackService).startAsyncGenerationFromNote("note-1", userId, true);
        verify(noteService).getById("note-1", userId);
        assertThat(response).isEqualTo(expected);
    }

    /**
     * GUARD 10. POST /notes/{id}/regenerate with STUDY_PACK scope is a TRUE PASSTHROUGH: it makes the same
     * service call POST /notes/{id}/generate makes, so one Study Pack unit is charged and the
     * note-generation meter is never involved. An absent body and an absent scope resolve the same way.
     */
    @Test
    void regenerate_studyPackScopeDelegatesToTheExistingGenerationPathUnchanged() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        NoteResponse expected = regenerationNoteResponse();
        when(noteService.getById("note-1", userId)).thenReturn(expected);

        assertThat(noteController.regenerate("note-1", new RegenerateNoteRequest("STUDY_PACK"), user))
                .isEqualTo(expected);
        assertThat(noteController.regenerate("note-1", new RegenerateNoteRequest(null), user)).isEqualTo(expected);
        assertThat(noteController.regenerate("note-1", null, user)).isEqualTo(expected);

        verify(authService, times(3)).requireEmailVerified(userId);
        verify(studyPackService, times(3)).startAsyncGenerationFromNote("note-1", userId, false);
        verify(studyPackService, never()).startAsyncNoteAndStudyPackRegeneration(anyString(), any(UUID.class));
    }

    @Test
    void regenerate_noteAndStudyPackScopeTakesTheCombinedPath() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        NoteResponse expected = regenerationNoteResponse();
        when(noteService.getById("note-1", userId)).thenReturn(expected);

        assertThat(noteController.regenerate("note-1", new RegenerateNoteRequest("NOTE_AND_STUDY_PACK"), user))
                .isEqualTo(expected);

        verify(authService).requireEmailVerified(userId);
        verify(studyPackService).startAsyncNoteAndStudyPackRegeneration("note-1", userId);
        verify(studyPackService, never()).startAsyncGenerationFromNote(anyString(), any(UUID.class), anyBoolean());
    }

    @Test
    void regenerate_rejectsAnUnknownScopeWithoutStartingAnything() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);

        assertThatThrownBy(() -> noteController.regenerate("note-1", new RegenerateNoteRequest("EVERYTHING"), user))
                .isInstanceOf(UnknownNoteRegenerationScopeException.class)
                .extracting(error -> ((AppException) error).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(studyPackService, never()).startAsyncGenerationFromNote(anyString(), any(UUID.class), anyBoolean());
        verify(studyPackService, never()).startAsyncNoteAndStudyPackRegeneration(anyString(), any(UUID.class));
    }

    @Test
    void regenerate_requiresEmailVerificationBeforeStartingAnything() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, false, 1);
        AppException verificationError = new AppException(
                "EMAIL_VERIFICATION_REQUIRED", "Email verification required.", HttpStatus.FORBIDDEN);
        doThrow(verificationError).when(authService).requireEmailVerified(userId);

        assertThatThrownBy(() ->
                noteController.regenerate("note-1", new RegenerateNoteRequest("NOTE_AND_STUDY_PACK"), user))
                .isSameAs(verificationError);

        verify(studyPackService, never()).startAsyncNoteAndStudyPackRegeneration(anyString(), any(UUID.class));
    }

    private NoteResponse regenerationNoteResponse() {
        return new NoteResponse(
                "note-1", "Draft note", "Biology", "Nursing", null, null, List.of("tag"), "content",
                "PRIVATE", OffsetDateTime.now(), OffsetDateTime.now(), null, null, null, false, null, null,
                "GENERATING", null, List.of(), List.of(), null, 0, false, false, false
        );
    }

    @Test
    void generateNoteFromTopic_callsEmailVerificationBeforeGeneration() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        GenerateNoteFromTopicRequest request = new GenerateNoteFromTopicRequest(
                "Newton's Laws of Motion", null, null
        );
        GenerateNoteFromTopicResponse expected = new GenerateNoteFromTopicResponse("Generated note content");
        when(noteGenerationService.generateFromTopic(request, userId)).thenReturn(expected);

        GenerateNoteFromTopicResponse response = noteController.generateNoteFromTopic(request, user);

        verify(authService).requireEmailVerified(userId);
        verify(noteGenerationService).generateFromTopic(request, userId);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void listPublic_ignoresLegacyAudienceQueryAndReturnsUnfilteredResults() throws Exception {
        AuthenticatedUser routeUser = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);
        MockMvc mockMvc = buildMockMvc(routeUser);
        when(noteService.listPublic(routeUser.userId(), "cinco", "recent", "history", List.of("mexican-history"), "nursing", CREATOR_USERNAME, null, 4, null, null, false, null))
                .thenReturn(new PublicNoteListResponse(List.of(), 0));

        mockMvc.perform(get("/notes/public")
                        .param("search", "cinco")
                        .param("sort", "recent")
                        .param("subject", "history")
                        .param("tag", "mexican-history")
                        .param("courseProgram", "nursing")
                        .param("creator", CREATOR_USERNAME)
                        .param("audience", "BOARD_TAKER")
                        .param("targetProfileType", "PROFESSIONAL")
                        .param("level", "NONSENSE")
                        .param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(0));

        verify(noteService).listPublic(
                routeUser.userId(),
                "cinco",
                "recent",
                "history",
                List.of("mexican-history"),
                "nursing",
                CREATOR_USERNAME,
                null,
                4,
                null,
                null,
                false,
                null
        );
    }

    @Test
    void listPublic_clampsLargeSizeBeforeDelegating() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        when(noteService.listPublic(userId, null, null, null, null, null, null, null, 50, null, null, false, null))
                .thenReturn(new PublicNoteListResponse(List.of(), 0));

        PublicNoteListResponse response = noteController.listPublic(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                200,
                null,
                null,
                false,
                null,
                user
        );

        verify(noteService).listPublic(userId, null, null, null, null, null, null, null, 50, null, null, false, null);
        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
    }

    @Test
    void publicLibraryPaginatedRouteClampsBoundsAndPassesNewFilters() throws Exception {
        AuthenticatedUser routeUser = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);
        MockMvc mockMvc = buildMockMvc(routeUser);
        PublicNoteListResponse expected = new PublicNoteListResponse(List.of(), 0, 0, 50, 0L, false);
        when(noteService.listPublic(
                routeUser.userId(), null, "most_copied", null, null, null, null,
                LearnerLevel.JUNIOR_HIGH, null, 0, 50, true, List.of("BY_YOU", "OFFICIAL")
        )).thenReturn(expected);

        mockMvc.perform(get("/notes/public")
                        .param("sort", "most_copied")
                        .param("level", "JUNIOR_HIGH")
                        .param("page", "-4")
                        .param("pageSize", "500")
                        .param("readyOnly", "true")
                        .param("source", "BY_YOU", "OFFICIAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(50))
                .andExpect(jsonPath("$.totalMatching").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));

        verify(noteService).listPublic(
                routeUser.userId(), null, "most_copied", null, null, null, null,
                LearnerLevel.JUNIOR_HIGH, null, 0, 50, true, List.of("BY_YOU", "OFFICIAL")
        );
    }

    @Test
    void publicLibraryLegacyRouteOmitsPaginationFieldsWhenPagingParamsAreAbsent() throws Exception {
        AuthenticatedUser routeUser = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);
        MockMvc mockMvc = buildMockMvc(routeUser);
        when(noteService.listPublic(
                routeUser.userId(), null, null, null, null, null, null,
                null, null, null, null, false, null
        )).thenReturn(new PublicNoteListResponse(List.of(), 0));

        mockMvc.perform(get("/notes/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.page").doesNotExist())
                .andExpect(jsonPath("$.pageSize").doesNotExist())
                .andExpect(jsonPath("$.totalMatching").doesNotExist())
                .andExpect(jsonPath("$.hasMore").doesNotExist());

        verify(noteService).listPublic(
                routeUser.userId(), null, null, null, null, null, null,
                null, null, null, null, false, null
        );
    }

    @Test
    void publicDiscoverySectionsRouteResolvesToLiteralHandler() throws Exception {
        AuthenticatedUser routeUser = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);
        MockMvc mockMvc = buildMockMvc(routeUser);
        when(noteService.getPublicLibraryDiscoverySections(routeUser.userId()))
                .thenReturn(new PublicLibraryDiscoverySectionsResponse(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/notes/public/discovery-sections").param("audience", "board-taker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured").isArray())
                .andExpect(jsonPath("$.popular").isArray())
                .andExpect(jsonPath("$.recent").isArray());

        verify(noteService).getPublicLibraryDiscoverySections(routeUser.userId());
        verify(noteService, never()).getPublicById("discovery-sections", routeUser.userId());
    }

    @Test
    void publicRouteAcceptsSlugShapedAuthoredDepthAndStillIgnoresGarbage() throws Exception {
        // ?subject= and ?courseProgram= are slug-shaped; ?level= now matches them via
        // LearnerLevel.fromSlug. Garbage must still degrade to an unfiltered library, never a 400.
        AuthenticatedUser routeUser = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);
        MockMvc mockMvc = buildMockMvc(routeUser);
        when(noteService.listPublic(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new PublicNoteListResponse(List.of(), 0));

        mockMvc.perform(get("/notes/public").param("level", "senior-high"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/notes/public").param("level", "NONSENSE"))
                .andExpect(status().isOk());

        ArgumentCaptor<LearnerLevel> levelCaptor = ArgumentCaptor.forClass(LearnerLevel.class);
        verify(noteService, times(2)).listPublic(
                any(), any(), any(), any(), any(), any(), any(),
                levelCaptor.capture(), any(), any(), any(), anyBoolean(), any()
        );
        assertThat(levelCaptor.getAllValues().get(0)).isEqualTo(LearnerLevel.SENIOR_HIGH);
        assertThat(levelCaptor.getAllValues().get(1)).isNull();
    }

    @Test
    void publicLearnerLevelsRouteReturnsOnlyServiceProvidedDepths() throws Exception {
        MockMvc mockMvc = buildMockMvc(null);
        when(noteService.listPublicLearnerLevels())
                .thenReturn(List.of(LearnerLevel.JUNIOR_HIGH, LearnerLevel.SENIOR_HIGH));

        mockMvc.perform(get("/notes/public/learner-levels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("JUNIOR_HIGH"))
                .andExpect(jsonPath("$[1]").value("SENIOR_HIGH"));

        verify(noteService).listPublicLearnerLevels();
        verify(noteService, never()).getPublicById("learner-levels", null);
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
                        null,
                        null,
                        "Biology",
                        List.of("cells"),
                        "preview",
                        "summary preview",
                        "PUBLIC",
                        null,
                        "STUDY_PACK_READY",
                        4,
                        2,
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
                        false,
                        List.of("Nursing")
                )
        );
        when(noteService.listPublic(userId, null, null, null, null, null, null, null, null, null, null, false, null))
                .thenReturn(new PublicNoteListResponse(expected, expected.size()));

        PublicNoteListResponse response = noteController.listPublic(
                null, null, null, null, null, null, null, null, null, null, false, null, user
        );

        assertThat(response.items()).isEqualTo(expected);
        assertThat(response.total()).isEqualTo(expected.size());
        verify(noteService).listPublic(
                userId, null, null, null, null, null, null, null, null, null, null, false, null
        );
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
                null,
                null,
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
                false
        );
    }

    /**
     * ⚠️ THESE EXIST BECAUSE A GREEN SUITE SAID NOTHING ABOUT THE ONE THING THAT WAS BROKEN.
     *
     * Bulk regeneration shipped with both JSON POSTs sending no {@code Content-Type}, so Spring rejected
     * every request with {@code HttpMediaTypeNotSupportedException} BEFORE the controller was entered —
     * and the feature could not make one successful request while the whole suite passed. Nothing
     * executed the transport: the frontend tests mock the API layer wholesale, and the controller tests
     * called these methods DIRECTLY, which bypasses content negotiation entirely.
     *
     * <p>⚠️ SO THESE GO THROUGH MockMvc WITH A REAL BODY, not a direct method call. A direct-call test
     * passes under the defect and proves nothing about whether the endpoint is reachable. The three-agent
     * pressure test could not catch it either — it reads code, it does not exercise transport.
     */
    @Test
    void bulkRegenerationEndpointsAcceptARealJsonRequest() throws Exception {
        AuthenticatedUser routeUser = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);
        MockMvc mockMvc = buildMockMvc(routeUser);
        UUID noteId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        when(noteRegenerationPreflightService.preflight(any(), eq(routeUser.userId()), eq(true)))
                .thenReturn(new NoteRegenerationPreflightResponse(
                        "STUDY_PACK", 1, 1, 0, 0, 0, 0, 0, 10, 1, 10, false, 0, 50, List.of()));
        when(noteBulkRegenerationService.queueBatch(any(), eq(routeUser.userId()), eq(true)))
                .thenReturn(new BulkRegenerateNotesResponse(batchId, "STUDY_PACK", 1));

        mockMvc.perform(post("/notes/regenerate/preflight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noteIds\":[\"" + noteId + "\"],\"scope\":\"STUDY_PACK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyCount").value(1));

        mockMvc.perform(post("/notes/bulk-regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noteIds\":[\"" + noteId + "\"],\"scope\":\"STUDY_PACK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId.toString()));
    }

    /**
     * Retry is addressed by BATCH ID and carries no body, so it must be reachable without a content
     * type — the mirror of the guard above, and the reason retry cannot be "fixed" into taking a note
     * list without someone noticing.
     */
    @Test
    void retryEndpointIsReachableWithNoRequestBody() throws Exception {
        AuthenticatedUser routeUser = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);
        UUID batchId = UUID.randomUUID();
        UUID retryBatchId = UUID.randomUUID();
        when(noteBulkRegenerationService.retryFailedItems(batchId, routeUser.userId(), true))
                .thenReturn(new BulkRegenerateNotesResponse(retryBatchId, "STUDY_PACK", 1));

        buildMockMvc(routeUser)
                .perform(post("/notes/bulk-regenerate/" + batchId + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(retryBatchId.toString()));
    }

    /**
     * The receipt read is a GET and must resolve to its own handler rather than the {@code /notes/{id}}
     * one — the same route-collision class {@code libraryRoutesResolveToDedicatedHandlers...} pins.
     */
    @Test
    void bulkRegenerationReceiptResolvesToItsOwnHandler() throws Exception {
        AuthenticatedUser routeUser = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, 1);
        UUID batchId = UUID.randomUUID();
        when(noteBulkRegenerationReceiptService.getReceipt(batchId, routeUser.userId()))
                .thenReturn(new NoteBulkRegenerationReceiptResponse(
                        batchId, "STUDY_PACK", 1, 1, 0, 0, 0, 0, true, false, List.of(), List.of()));

        buildMockMvc(routeUser)
                .perform(get("/notes/bulk-regenerate/" + batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finished").value(true));
        verify(noteService, never()).getById("bulk-regenerate", routeUser.userId());
    }

    private MockMvc buildMockMvc(AuthenticatedUser routeUser) {
        return standaloneSetup(noteController)
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
    }
}
