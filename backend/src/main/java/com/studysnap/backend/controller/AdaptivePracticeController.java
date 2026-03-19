package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdaptivePracticeCompleteRequest;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
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
    public QuickReviewAdaptiveQuizResponse startAdaptivePractice(
            @PathVariable String studyPackId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quickReviewAdaptivePracticeService.generateAdaptiveQuiz(studyPackId, userId);
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public SimpleMessageResponse completeAdaptivePractice(
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
                request.durationSeconds()
        );
    }
}
