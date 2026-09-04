package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdaptivePracticeCompleteRequest;
import com.studysnap.backend.dto.AdaptivePracticeCompleteResponse;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/adaptive-practice")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class AdaptivePracticeController {
    private final QuickReviewAdaptivePracticeService quickReviewAdaptivePracticeService;

    @PostMapping("/study-packs/{studyPackId}/start")
    @Deprecated
    public QuickReviewAdaptiveQuizResponse startAdaptivePractice(
            @PathVariable String studyPackId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewAdaptivePracticeService.generateAdaptiveQuiz(studyPackId, userId);
    }

    @GetMapping("/sessions/{sessionId}")
    public QuickReviewAdaptiveQuizResponse getAdaptivePracticeSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return quickReviewAdaptivePracticeService.getAdaptiveSessionById(sessionId, user.userId());
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public AdaptivePracticeCompleteResponse completeAdaptivePractice(
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
                request.correctConceptNames(),
                request.selectedChoices(),
                request.selectedMultiChoices()
        );
    }

    @PostMapping("/sessions/{sessionId}/forfeit")
    public SimpleMessageResponse forfeitAdaptivePractice(
            @PathVariable String sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewAdaptivePracticeService.forfeitAdaptiveSession(sessionId, userId);
    }
}
