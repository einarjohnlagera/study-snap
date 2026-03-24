package com.studysnap.backend.controller;

import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.PublicNoteDetailResponse;
import com.studysnap.backend.dto.ExtractedNoteTextResponse;
import com.studysnap.backend.dto.CreateStudyPackRequest;
import com.studysnap.backend.dto.StudyPackResponse;
import com.studysnap.backend.dto.QuickReviewPerformanceSummaryResponse;
import com.studysnap.backend.dto.QuickReviewSessionResponse;
import com.studysnap.backend.dto.QuickReviewSessionStartResponse;
import com.studysnap.backend.dto.QuickReviewStudyTipRequest;
import com.studysnap.backend.dto.QuickReviewStudyTipResponse;
import com.studysnap.backend.dto.ChallengeQuizPerformanceSummaryResponse;
import com.studysnap.backend.dto.ChallengeQuizSessionSummaryResponse;
import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.UpdateNoteVisibilityRequest;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.ChallengeQuizService;
import com.studysnap.backend.service.NoteService;
import com.studysnap.backend.service.NoteTextExtractionService;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import com.studysnap.backend.service.QuickReviewSessionService;
import com.studysnap.backend.service.QuickReviewStudyTipService;
import com.studysnap.backend.service.StudyPackService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController {

    private final AuthService authService;
    private final NoteService noteService;
    private final NoteTextExtractionService noteTextExtractionService;
    private final StudyPackService studyPackService;
    private final QuickReviewSessionService quickReviewSessionService;
    private final QuickReviewStudyTipService quickReviewStudyTipService;
    private final ChallengeQuizService challengeQuizService;
    private final QuickReviewAdaptivePracticeService quickReviewAdaptivePracticeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public NoteResponse create(
            @Valid @RequestBody UpsertNoteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.create(request, userId);
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
    public StudyPackResponse generate(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        return studyPackService.createFromText(new CreateStudyPackRequest(null, id), userId);
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
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.copyNote(id, userId);
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
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return challengeQuizService.startSession(studyPackId, userId);
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

    @PostMapping("/{id}/adaptive-practice/start")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuickReviewAdaptiveQuizResponse startAdaptivePractice(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        authService.requireEmailVerified(userId);
        String studyPackId = noteService.getOwnedStudyPackIdOrThrow(id, userId);
        return quickReviewAdaptivePracticeService.generateAdaptiveQuiz(studyPackId, userId);
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
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return noteService.listMine(userId);
    }

    @GetMapping("/public")
    public List<NoteListItemResponse> listPublic(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID viewerUserId = user == null ? null : user.userId();
        return noteService.listPublic(viewerUserId);
    }

    @GetMapping("/public/{id}")
    public PublicNoteDetailResponse getPublicById(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return noteService.getPublicById(id, user == null ? null : user.userId());
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
