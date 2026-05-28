package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ChallengeQuizCompleteRequest;
import com.studysnap.backend.dto.ChallengeQuizSessionResponse;
import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.dto.AdaptivePracticeCompleteRequest;
import com.studysnap.backend.dto.QuickReviewSessionCompleteRequest;
import com.studysnap.backend.dto.QuickReviewSessionConfidenceRequest;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.QuickReviewPerformanceSummaryResponse;
import com.studysnap.backend.dto.QuickReviewSessionProgressRequest;
import com.studysnap.backend.dto.QuickReviewSessionResponse;
import com.studysnap.backend.dto.QuickReviewSessionStartRequest;
import com.studysnap.backend.dto.QuickReviewSessionStartResponse;
import com.studysnap.backend.dto.QuickReviewStudyTipRequest;
import com.studysnap.backend.dto.QuickReviewStudyTipResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.ChallengeQuizService;
import com.studysnap.backend.service.QuickReviewSessionService;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import com.studysnap.backend.service.QuickReviewStudyTipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/quick-review-sessions", "/quick-review"})
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class QuickReviewSessionController {
    private final QuickReviewSessionService quickReviewSessionService;
    private final QuickReviewStudyTipService quickReviewStudyTipService;
    private final QuickReviewAdaptivePracticeService quickReviewAdaptivePracticeService;
    private final ChallengeQuizService challengeQuizService;

    @PostMapping("/start")
    @Deprecated
    public QuickReviewSessionStartResponse startSession(
            @Valid @RequestBody QuickReviewSessionStartRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewSessionService.startSession(request.studyPackId(), userId);
    }

    @PostMapping("/{sessionId}/complete")
    public QuickReviewSessionResponse completeSession(
            @PathVariable String sessionId,
            @Valid @RequestBody QuickReviewSessionCompleteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewSessionService.completeSession(sessionId, userId, request);
    }

    @PostMapping("/{sessionId}/confidence")
    public QuickReviewSessionResponse saveConfidenceLevel(
            @PathVariable String sessionId,
            @Valid @RequestBody QuickReviewSessionConfidenceRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewSessionService.saveConfidenceLevel(sessionId, userId, request.confidenceLevel());
    }

    @PostMapping("/{sessionId}/progress")
    public QuickReviewSessionResponse updateSessionProgress(
            @PathVariable String sessionId,
            @Valid @RequestBody QuickReviewSessionProgressRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewSessionService.updateSessionProgress(sessionId, userId, request);
    }

    @PostMapping("/{sessionId}/forfeit")
    public SimpleMessageResponse forfeitSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewSessionService.forfeitSession(sessionId, userId);
    }

    @GetMapping("/study-packs/{studyPackId}/in-progress")
    @Deprecated
    public QuickReviewSessionStartResponse getInProgressSession(
            @PathVariable String studyPackId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewSessionService.getInProgressSession(studyPackId, userId);
    }

    @GetMapping("/study-packs/{studyPackId}/recent")
    @Deprecated
    public List<QuickReviewSessionResponse> listRecentSessions(
            @PathVariable String studyPackId,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewSessionService.listRecentSessions(studyPackId, userId, limit);
    }

    @GetMapping("/study-packs/{studyPackId}/performance-summary")
    @Deprecated
    public QuickReviewPerformanceSummaryResponse getPerformanceSummary(
            @PathVariable String studyPackId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewSessionService.getPerformanceSummary(studyPackId, userId);
    }

    @PostMapping("/study-packs/{studyPackId}/study-tip")
    @Deprecated
    public QuickReviewStudyTipResponse generateStudyTip(
            @PathVariable String studyPackId,
            @Valid @RequestBody QuickReviewStudyTipRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewStudyTipService.generateStudyTip(studyPackId, userId, request);
    }

    @PostMapping("/study-packs/{studyPackId}/adaptive-practice")
    @Deprecated
    public QuickReviewAdaptiveQuizResponse generateAdaptivePracticeQuiz(
            @PathVariable String studyPackId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewAdaptivePracticeService.generateAdaptiveQuiz(studyPackId, userId);
    }

    @PostMapping("/adaptive-practice/{sessionId}/complete")
    public SimpleMessageResponse completeAdaptivePracticeQuiz(
            @PathVariable String sessionId,
            @Valid @RequestBody AdaptivePracticeCompleteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewAdaptivePracticeService.completeAdaptiveSession(
                sessionId,
                userId,
                request.correctAnswers(),
                request.totalQuestions(),
                request.durationSeconds(),
                request.correctConceptNames()
        );
    }

    @PostMapping("/adaptive-practice/{sessionId}/forfeit")
    public SimpleMessageResponse forfeitAdaptivePracticeQuiz(
            @PathVariable String sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewAdaptivePracticeService.forfeitAdaptiveSession(sessionId, userId);
    }

    @PostMapping("/study-packs/{studyPackId}/challenge/start")
    @Deprecated
    public ChallengeQuizStartResponse startChallengeQuiz(
            @PathVariable String studyPackId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return challengeQuizService.startSession(studyPackId, userId, null);
    }

    @PostMapping("/challenge/{sessionId}/complete")
    public ChallengeQuizSessionResponse completeChallengeQuiz(
            @PathVariable String sessionId,
            @Valid @RequestBody ChallengeQuizCompleteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return challengeQuizService.completeSession(sessionId, userId, request);
    }

    @PostMapping("/challenge/{sessionId}/forfeit")
    public SimpleMessageResponse forfeitChallengeQuiz(
            @PathVariable String sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return challengeQuizService.forfeitSession(sessionId, userId);
    }
}
