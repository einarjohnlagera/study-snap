package com.studysnap.backend.controller;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteQuickReviewLastReviewedResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.NoteShareResponse;
import com.studysnap.backend.dto.NoteStatusResponse;
import com.studysnap.backend.dto.NotesLibraryFilterOptionsResponse;
import com.studysnap.backend.dto.NotesLibraryIdsResponse;
import com.studysnap.backend.dto.NotesLibraryPageResponse;
import com.studysnap.backend.dto.PublicNoteDetailResponse;
import com.studysnap.backend.dto.PublicLibraryDiscoverySectionsResponse;
import com.studysnap.backend.dto.PublicNoteListResponse;
import com.studysnap.backend.dto.PublicNoteLikeResponse;
import com.studysnap.backend.dto.BulkGenerationResultResponse;
import com.studysnap.backend.dto.RecentQuizSessionHistoryResponse;
import com.studysnap.backend.dto.BulkImportResultResponse;
import com.studysnap.backend.dto.BulkGenerateNotesRequest;
import com.studysnap.backend.dto.BulkGenerateNotesResponse;
import com.studysnap.backend.dto.BulkRegenerateNotesRequest;
import com.studysnap.backend.dto.BulkRegenerateNotesResponse;
import com.studysnap.backend.dto.NoteBulkRegenerationReceiptResponse;
import com.studysnap.backend.dto.NoteRegenerationPreflightRequest;
import com.studysnap.backend.dto.NoteRegenerationPreflightResponse;
import com.studysnap.backend.dto.ExtractedNoteTextResponse;
import com.studysnap.backend.dto.GeneratedQuizResponse;
import com.studysnap.backend.dto.GenerateGeneratedQuizRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicResponse;
import com.studysnap.backend.dto.QuickReviewPerformanceSummaryResponse;
import com.studysnap.backend.dto.QuickReviewSessionResponse;
import com.studysnap.backend.dto.QuickReviewSessionStartResponse;
import com.studysnap.backend.dto.QuickReviewStudyTipRequest;
import com.studysnap.backend.dto.QuickReviewStudyTipResponse;
import com.studysnap.backend.dto.QuizSessionReviewResponse;
import com.studysnap.backend.dto.SubjectStatsResponse;
import com.studysnap.backend.dto.SharedNoteResponse;
import com.studysnap.backend.dto.SharedNotesPageResponse;
import com.studysnap.backend.dto.ChallengeQuizPerformanceSummaryResponse;
import com.studysnap.backend.dto.ChallengeQuizSessionSummaryResponse;
import com.studysnap.backend.dto.ChallengeQuizStartRequest;
import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.dto.CopyOnSignupRequest;
import com.studysnap.backend.dto.CopyOnSignupResponse;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.UpdateNoteVisibilityRequest;
import com.studysnap.backend.dto.UpdateNoteSharesRequest;
import com.studysnap.backend.dto.RegenerateNoteRequest;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteRegenerationScope;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.SharedNoteNotFoundException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.BulkGenerationResultService;
import com.studysnap.backend.service.ChallengeQuizService;
import com.studysnap.backend.service.GeneratedQuizService;
import com.studysnap.backend.service.NoteBulkImportService;
import com.studysnap.backend.service.NoteBulkGenerationService;
import com.studysnap.backend.service.NoteBulkRegenerationService;
import com.studysnap.backend.service.NoteBulkRegenerationReceiptService;
import com.studysnap.backend.service.NoteRegenerationPreflightService;
import com.studysnap.backend.service.NoteService;
import com.studysnap.backend.service.NoteShareService;
import com.studysnap.backend.service.NoteGenerationService;
import com.studysnap.backend.service.NoteTextExtractionService;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import com.studysnap.backend.service.QuickReviewSessionService;
import com.studysnap.backend.service.QuickReviewStudyTipService;
import com.studysnap.backend.service.QuizSessionHistoryService;
import com.studysnap.backend.service.StudyPackService;
import com.studysnap.backend.util.UuidParsingUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController {
    private static final String AUTO_APPLY_METADATA_REQUEST_PARAM = "autoApplyMetadata";
    private static final String STUDY_PACK_STATUS_DRAFT = "DRAFT";
    private static final int PUBLIC_NOTES_MIN_SIZE = 1;
    private static final int PUBLIC_NOTES_MAX_SIZE = 50;
    private static final int PRIVATE_NOTES_MIN_LIMIT = 1;
    private static final int PRIVATE_NOTES_MAX_LIMIT = 50;
    private static final int LIBRARY_MIN_PAGE = 0;
    private static final int LIBRARY_MAX_PAGE = Integer.MAX_VALUE;
    private static final int LIBRARY_MIN_PAGE_SIZE = 1;
    private static final int LIBRARY_MAX_PAGE_SIZE = 100;
    private static final String LIBRARY_DEFAULT_PAGE = "0";
    private static final String LIBRARY_DEFAULT_PAGE_SIZE = "20";
    private static final String LIBRARY_DEFAULT_FILTER = "ALL";
    private static final String LIBRARY_DEFAULT_SORT = "RECENTLY_UPDATED";
    private static final String SEARCH_REQUEST_PARAM = "search";
    private static final String READINESS_REQUEST_PARAM = "readiness";
    private static final String COURSE_PROGRAM_REQUEST_PARAM = "courseProgram";
    private static final String SUBJECT_REQUEST_PARAM = "subject";
    private static final String TAG_REQUEST_PARAM = "tag";
    private static final String VISIBILITY_REQUEST_PARAM = "visibility";
    private static final String COLLECTION_REQUEST_PARAM = "collectionId";
    private static final String SORT_REQUEST_PARAM = "sort";
    private static final String PAGE_REQUEST_PARAM = "page";
    private static final String PAGE_SIZE_REQUEST_PARAM = "pageSize";
    private static final String READY_ONLY_REQUEST_PARAM = "readyOnly";
    private static final String SOURCE_REQUEST_PARAM = "source";
    private static final String ENTRY_REQUEST_PARAM = "entry";
    private static final int PUBLIC_LIBRARY_DEFAULT_PAGE = 0;
    private static final int PUBLIC_LIBRARY_DEFAULT_PAGE_SIZE = 20;

    private final AuthService authService;
    private final BulkGenerationResultService bulkGenerationResultService;
    private final NoteService noteService;
    private final NoteShareService noteShareService;
    private final NoteBulkImportService noteBulkImportService;
    private final NoteBulkGenerationService noteBulkGenerationService;
    private final NoteBulkRegenerationService noteBulkRegenerationService;
    private final NoteBulkRegenerationReceiptService noteBulkRegenerationReceiptService;
    private final NoteRegenerationPreflightService noteRegenerationPreflightService;
    private final NoteGenerationService noteGenerationService;
    private final NoteTextExtractionService noteTextExtractionService;
    private final StudyPackService studyPackService;
    private final QuickReviewSessionService quickReviewSessionService;
    private final QuickReviewStudyTipService quickReviewStudyTipService;
    private final ChallengeQuizService challengeQuizService;
    private final QuickReviewAdaptivePracticeService quickReviewAdaptivePracticeService;
    private final GeneratedQuizService generatedQuizService;
    private final QuizSessionHistoryService quizSessionHistoryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse create(
            @Valid @RequestBody UpsertNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.create(request, userId);
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public GenerateNoteFromTopicResponse generateNoteFromTopic(
            @Valid @RequestBody GenerateNoteFromTopicRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        return noteGenerationService.generateFromTopic(request, userId);
    }

    @PostMapping(value = "/extract-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ExtractedNoteTextResponse extractText(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteTextExtractionService.extractText(file, userId);
    }

    @PostMapping(value = "/import-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BulkImportResultResponse importBatch(
            @RequestPart("files") List<MultipartFile> files,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteBulkImportService.importBatch(userId, files);
    }

    /**
     * Deterministic preflight for a bulk regeneration selection: per-Note readiness plus the aggregate
     * consequence and quota counts.
     *
     * <p>⚠️ POST rather than GET because the selection is a list of up to 50 ids, and the existing
     * {@code GET /notes/library/ids} is what produces it. Nothing is written and nothing is dispatched.
     *
     * <p>⚠️ {@code enforceLimits} is the SAME expression bulk generation uses. It is not widened, and it
     * is passed so the preflight tells a metered TEACHER curator the truth about their allowance before
     * they commit.
     */
    @PostMapping("/regenerate/preflight")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteRegenerationPreflightResponse regeneratePreflight(
            @RequestBody NoteRegenerationPreflightRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        boolean enforceLimits = user.role() != UserRole.ADMIN;
        return noteRegenerationPreflightService.preflight(request, userId, enforceLimits);
    }

    /**
     * Progress and result for one batch, read by polling.
     *
     * <p>⚠️ NOT consume-once, unlike the bulk-generation receipt: a regeneration batch runs far past
     * the five minutes that poller tolerates, so the curator may navigate away and come back.
     */
    @GetMapping("/bulk-regenerate/{batchId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteBulkRegenerationReceiptResponse bulkRegenerationReceipt(
            @PathVariable UUID batchId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteBulkRegenerationReceiptService.getReceipt(batchId, user.userId());
    }

    /**
     * Queues a bulk regeneration batch.
     *
     * <p>⚠️ The over-quota rejection is a 422 raised inside {@code queueBatch} BEFORE anything is
     * dispatched, carrying how many notes to remove. It reads only the note-generation meter; the
     * Study Pack meter is a SOFT floor, disclosed by the preflight and never used to refuse a batch.
     *
     * <p>⚠️ Curator-only, enforced by {@code BulkRegenerationAccessGuard} inside the service — the
     * {@code @PreAuthorize} below CANNOT express it, since {@code hasAnyRole('USER','ADMIN')} is
     * satisfied by every authenticated account.
     */
    @PostMapping("/bulk-regenerate")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BulkRegenerateNotesResponse bulkRegenerate(
            @RequestBody BulkRegenerateNotesRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        boolean enforceLimits = user.role() != UserRole.ADMIN;
        return noteBulkRegenerationService.queueBatch(request, userId, enforceLimits);
    }

    @PostMapping("/bulk-generate")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BulkGenerateNotesResponse bulkGenerate(
            @Valid @RequestBody BulkGenerateNotesRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        boolean enforceLimits = user.role() != UserRole.ADMIN;
        return noteBulkGenerationService.queueBatch(request, userId, enforceLimits);
    }

    @GetMapping("/bulk-generate/results/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BulkGenerationResultResponse getBulkGenerationResult(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return bulkGenerationResultService.consumeResult(id, userId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse update(
            @PathVariable String id,
            @Valid @RequestBody UpsertNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.update(id, request, userId);
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse generate(
            @PathVariable String id,
            @RequestParam(value = AUTO_APPLY_METADATA_REQUEST_PARAM, defaultValue = "false") boolean autoApplyMetadata,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        studyPackService.startAsyncGenerationFromNote(id, userId, autoApplyMetadata);
        return noteService.getById(id, userId);
    }

    /**
     * Regenerates a note's Study Pack, and — with {@code scope = NOTE_AND_STUDY_PACK} — its content too,
     * as one operation that preserves note and Study Pack identity.
     *
     * <p>⚠️ {@code POST /notes/{id}/generate} is untouched and keeps its exact semantics. {@code STUDY_PACK}
     * here delegates to the same service call it makes, so the two are behaviourally identical.
     *
     * <p>⚠️ {@code NOTE_AND_STUDY_PACK} is deliberately NOT reachable from the UI yet: the scope selector
     * and its overwrite warnings are a later slice, and content replacement must not reach a learner
     * without them.
     */
    @PostMapping("/{id}/regenerate")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse regenerate(
            @PathVariable String id,
            @RequestBody(required = false) RegenerateNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        NoteRegenerationScope scope = NoteRegenerationScope.parseOrDefault(request == null ? null : request.scope());
        if (scope == NoteRegenerationScope.NOTE_AND_STUDY_PACK) {
            studyPackService.startAsyncNoteAndStudyPackRegeneration(id, userId);
        } else {
            studyPackService.startAsyncGenerationFromNote(id, userId, false);
        }
        return noteService.getById(id, userId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse getById(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.getById(id, userId);
    }

    @GetMapping("/{id}/shares")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<NoteShareResponse> listShares(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteShareService.listShares(user.userId(), id);
    }

    @PutMapping("/{id}/shares")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<NoteShareResponse> replaceShares(
            @PathVariable String id,
            @Valid @RequestBody UpdateNoteSharesRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteShareService.replaceShares(user.userId(), id, request.relationshipIds());
    }

    @GetMapping("/shared-with-me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public SharedNotesPageResponse listSharedWithMe(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteShareService.listSharedWithMe(user.userId(), limit, cursor);
    }

    @GetMapping("/shared/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public SharedNoteResponse getSharedNote(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteShareService.getSharedNote(user.userId(), id);
    }

    @PostMapping("/shared/{id}/copy")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse copySharedNote(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        noteShareService.requireSharedNoteAccess(
                user.userId(),
                UuidParsingUtils.parseUuidOrThrow(id, SharedNoteNotFoundException::new)
        );
        try {
            return noteService.copyNote(id, user.userId(), true);
        } catch (NoteNotFoundException exception) {
            throw new SharedNoteNotFoundException();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public void deleteById(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        noteService.deleteById(id, userId);
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse copyNote(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "true") boolean includeStudyPack,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.copyNote(id, userId, includeStudyPack);
    }

    @PostMapping("/copy-on-signup")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public CopyOnSignupResponse copyNoteOnSignup(
            @Valid @RequestBody CopyOnSignupRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        NoteResponse copied = noteService.copyPublicNoteForSignup(request.publicNoteId(), userId);
        if (STUDY_PACK_STATUS_DRAFT.equals(copied.studyPackStatus())) {
            studyPackService.startAsyncGenerationFromNote(copied.id(), userId);
        }
        return new CopyOnSignupResponse(copied.id());
    }

    @PostMapping("/{id}/quick-review/start")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuickReviewSessionStartResponse startQuickReview(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return quickReviewSessionService.startSession(studyPackId, userId);
    }

    @GetMapping("/{id}/quick-review/in-progress")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuickReviewSessionStartResponse getInProgressQuickReview(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return quickReviewSessionService.getInProgressSession(studyPackId, userId);
    }

    @GetMapping("/{id}/quick-review/recent")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<QuickReviewSessionResponse> listRecentQuickReviewSessions(
            @PathVariable String id,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return quickReviewSessionService.listRecentSessions(studyPackId, userId, limit);
    }

    @GetMapping("/{id}/quick-review/performance-summary")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuickReviewPerformanceSummaryResponse getQuickReviewPerformanceSummary(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return quickReviewSessionService.getPerformanceSummary(studyPackId, userId);
    }

    @GetMapping("/quick-review/last-reviewed")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<NoteQuickReviewLastReviewedResponse> getQuickReviewLastReviewedBatch(
            @RequestParam(value = "noteIds") List<String> noteIds,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<UUID> parsedNoteIds = noteIds.stream()
                .map(noteId -> UuidParsingUtils.parseUuidOrThrow(noteId, NoteNotFoundException::new))
                .toList();
        Map<UUID, OffsetDateTime> lastReviewedAtByNoteId = quickReviewSessionService
                .getLastReviewedAtByNoteIds(parsedNoteIds, user.userId());
        return parsedNoteIds.stream()
                .map(noteId -> new NoteQuickReviewLastReviewedResponse(
                        noteId.toString(),
                        lastReviewedAtByNoteId.get(noteId)
                ))
                .toList();
    }

    @GetMapping("/{id}/quick-review/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuizSessionReviewResponse getQuickReviewSessionReview(
            @PathVariable String id,
            @PathVariable String sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return quickReviewSessionService.getSessionReview(studyPackId, sessionId, userId);
    }

    @PostMapping("/{id}/quick-review/study-tip")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuickReviewStudyTipResponse generateQuickReviewStudyTip(
            @PathVariable String id,
            @Valid @RequestBody QuickReviewStudyTipRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return quickReviewStudyTipService.generateStudyTip(studyPackId, userId, request);
    }

    @PostMapping("/{id}/challenge-quiz/start")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ChallengeQuizStartResponse startChallengeQuiz(
            @PathVariable String id,
            @RequestBody(required = false) ChallengeQuizStartRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return challengeQuizService.startSession(studyPackId, userId, request);
    }

    @PostMapping("/{id}/challenge-quiz/redo-missed")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ChallengeQuizStartResponse startRedoMissedChallengeQuiz(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return challengeQuizService.startRedoMissedSession(studyPackId, userId);
    }

    @GetMapping("/{id}/challenge-quiz/in-progress")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ChallengeQuizStartResponse getInProgressChallengeQuiz(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return challengeQuizService.getInProgressSession(studyPackId, userId);
    }

    @GetMapping("/{id}/challenge-quiz/recent")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<ChallengeQuizSessionSummaryResponse> listRecentChallengeQuizSessions(
            @PathVariable String id,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return challengeQuizService.listRecentSessions(studyPackId, userId, limit);
    }

    @GetMapping("/{id}/quiz-sessions/recent")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<RecentQuizSessionHistoryResponse> listRecentQuizSessions(
            @PathVariable String id,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return quizSessionHistoryService.listRecentSessions(id, userId, limit);
    }

    @GetMapping("/{id}/challenge-quiz/performance-summary")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ChallengeQuizPerformanceSummaryResponse getChallengeQuizPerformanceSummary(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return challengeQuizService.getPerformanceSummary(studyPackId, userId);
    }

    @GetMapping("/{id}/challenge-quiz/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuizSessionReviewResponse getChallengeQuizSessionReview(
            @PathVariable String id,
            @PathVariable String sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return challengeQuizService.getSessionReview(studyPackId, sessionId, userId);
    }

    @GetMapping("/{id}/generated-quiz")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public GeneratedQuizResponse getGeneratedQuiz(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return generatedQuizService.getByNoteId(id, userId);
    }

    @PostMapping("/{id}/generated-quiz")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public GeneratedQuizResponse generateGeneratedQuiz(
            @PathVariable String id,
            @RequestBody(required = false) GenerateGeneratedQuizRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        return generatedQuizService.generate(
                id,
                userId,
                request == null ? null : request.questionCount(),
                request == null ? null : request.targetLearnerLevel()
        );
    }

    @PostMapping("/{id}/adaptive-practice/start")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuickReviewAdaptiveQuizResponse startAdaptivePractice(
            @PathVariable String id,
            @RequestParam(value = ENTRY_REQUEST_PARAM, required = false) String entry,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return quickReviewAdaptivePracticeService.generateAdaptiveQuiz(studyPackId, userId, entry);
    }

    @GetMapping("/{id}/adaptive-practice/in-progress")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuickReviewAdaptiveQuizResponse getInProgressAdaptivePractice(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return quickReviewAdaptivePracticeService.getAdaptiveQuizSession(studyPackId, userId);
    }

    @PostMapping("/{id}/visibility")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse updateVisibility(
            @PathVariable String id,
            @Valid @RequestBody UpdateNoteVisibilityRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        if ("PUBLIC".equalsIgnoreCase(request.visibility())) {
            authService.requireEmailVerified(userId);
        }
        return noteService.updateVisibility(id, request.visibility(), userId);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<NoteListItemResponse> listMine(
            @RequestParam(value = "limit", required = false) Integer limit,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.listMine(
                userId,
                limit == null ? null : Math.clamp(limit, PRIVATE_NOTES_MIN_LIMIT, PRIVATE_NOTES_MAX_LIMIT)
        );
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<NoteStatusResponse> listMineStatuses(@AuthenticationPrincipal AuthenticatedUser user) {
        return noteService.listMineStatuses(user.userId());
    }

    @GetMapping("/library")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NotesLibraryPageResponse listLibraryPage(
            @RequestParam(value = SEARCH_REQUEST_PARAM, required = false) String search,
            @RequestParam(value = READINESS_REQUEST_PARAM, defaultValue = LIBRARY_DEFAULT_FILTER) String readiness,
            @RequestParam(value = COURSE_PROGRAM_REQUEST_PARAM, required = false) String courseProgram,
            @RequestParam(value = SUBJECT_REQUEST_PARAM, required = false) String subject,
            @RequestParam(value = TAG_REQUEST_PARAM, required = false) List<String> tags,
            @RequestParam(value = VISIBILITY_REQUEST_PARAM, defaultValue = LIBRARY_DEFAULT_FILTER) String visibility,
            @RequestParam(value = COLLECTION_REQUEST_PARAM, required = false) UUID collectionId,
            @RequestParam(value = SORT_REQUEST_PARAM, defaultValue = LIBRARY_DEFAULT_SORT) String sort,
            @RequestParam(value = PAGE_REQUEST_PARAM, defaultValue = LIBRARY_DEFAULT_PAGE) Integer page,
            @RequestParam(value = PAGE_SIZE_REQUEST_PARAM, defaultValue = LIBRARY_DEFAULT_PAGE_SIZE) Integer pageSize,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteService.listLibraryPage(
                user.userId(),
                search,
                readiness,
                courseProgram,
                subject,
                tags,
                visibility,
                collectionId,
                sort,
                Math.clamp(page, LIBRARY_MIN_PAGE, LIBRARY_MAX_PAGE),
                Math.clamp(pageSize, LIBRARY_MIN_PAGE_SIZE, LIBRARY_MAX_PAGE_SIZE)
        );
    }

    @GetMapping("/library/ids")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NotesLibraryIdsResponse listLibraryMatchingIds(
            @RequestParam(value = SEARCH_REQUEST_PARAM, required = false) String search,
            @RequestParam(value = READINESS_REQUEST_PARAM, defaultValue = LIBRARY_DEFAULT_FILTER) String readiness,
            @RequestParam(value = COURSE_PROGRAM_REQUEST_PARAM, required = false) String courseProgram,
            @RequestParam(value = SUBJECT_REQUEST_PARAM, required = false) String subject,
            @RequestParam(value = TAG_REQUEST_PARAM, required = false) List<String> tags,
            @RequestParam(value = VISIBILITY_REQUEST_PARAM, defaultValue = LIBRARY_DEFAULT_FILTER) String visibility,
            @RequestParam(value = COLLECTION_REQUEST_PARAM, required = false) UUID collectionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteService.listLibraryMatchingIds(
                user.userId(), search, readiness, courseProgram, subject, tags, visibility, collectionId
        );
    }

    @GetMapping("/library/subject-stats")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public SubjectStatsResponse getLibrarySubjectStats(
            @RequestParam(value = SEARCH_REQUEST_PARAM, required = false) String search,
            @RequestParam(value = READINESS_REQUEST_PARAM, defaultValue = LIBRARY_DEFAULT_FILTER) String readiness,
            @RequestParam(value = COURSE_PROGRAM_REQUEST_PARAM, required = false) String courseProgram,
            @RequestParam(value = TAG_REQUEST_PARAM, required = false) List<String> tags,
            @RequestParam(value = VISIBILITY_REQUEST_PARAM, defaultValue = LIBRARY_DEFAULT_FILTER) String visibility,
            @RequestParam(value = COLLECTION_REQUEST_PARAM, required = false) UUID collectionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteService.getLibrarySubjectStats(
                user.userId(), search, readiness, courseProgram, tags, visibility, collectionId
        );
    }

    @GetMapping("/library/filter-options")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NotesLibraryFilterOptionsResponse getLibraryFilterOptions(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteService.getLibraryFilterOptions(user.userId());
    }

    @GetMapping("/public")
    public PublicNoteListResponse listPublic(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "tag", required = false) List<String> tags,
            @RequestParam(value = "courseProgram", required = false) String courseProgram,
            @RequestParam(value = "creator", required = false) String creator,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = PAGE_REQUEST_PARAM, required = false) Integer page,
            @RequestParam(value = PAGE_SIZE_REQUEST_PARAM, required = false) Integer pageSize,
            @RequestParam(value = READY_ONLY_REQUEST_PARAM, defaultValue = "false") boolean readyOnly,
            @RequestParam(value = SOURCE_REQUEST_PARAM, required = false) List<String> sources,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID viewerUserId = user == null ? null : user.userId();
        boolean paginated = page != null || pageSize != null;
        return noteService.listPublic(
                viewerUserId,
                search,
                sort,
                subject,
                tags,
                courseProgram,
                creator,
                LearnerLevel.fromSlug(level),
                size == null ? null : Math.clamp(size, PUBLIC_NOTES_MIN_SIZE, PUBLIC_NOTES_MAX_SIZE),
                paginated
                        ? Math.clamp(
                                page == null ? PUBLIC_LIBRARY_DEFAULT_PAGE : page,
                                LIBRARY_MIN_PAGE,
                                LIBRARY_MAX_PAGE
                        )
                        : null,
                paginated
                        ? Math.clamp(
                                pageSize == null ? PUBLIC_LIBRARY_DEFAULT_PAGE_SIZE : pageSize,
                                PUBLIC_NOTES_MIN_SIZE,
                                PUBLIC_NOTES_MAX_SIZE
                        )
                        : null,
                readyOnly,
                sources
        );
    }

    @GetMapping("/public/discovery-sections")
    public PublicLibraryDiscoverySectionsResponse getPublicLibraryDiscoverySections(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteService.getPublicLibraryDiscoverySections(user == null ? null : user.userId());
    }

    @GetMapping("/public/learner-levels")
    public List<LearnerLevel> listPublicLearnerLevels() {
        return noteService.listPublicLearnerLevels();
    }

    @GetMapping("/public/{id}")
    public PublicNoteDetailResponse getPublicById(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteService.getPublicById(id, user == null ? null : user.userId());
    }

    @PostMapping("/public/{id}/like")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public PublicNoteLikeResponse togglePublicNoteLike(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteService.togglePublicNoteLike(id, user.userId());
    }

    @GetMapping("/public/seo/{subject}/{slug}")
    public PublicNoteDetailResponse getPublicBySeoPath(
            @PathVariable String subject,
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteService.getPublicBySeoPath(subject, slug, user == null ? null : user.userId());
    }

}
