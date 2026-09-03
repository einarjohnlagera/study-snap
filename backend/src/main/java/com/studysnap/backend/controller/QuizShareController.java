package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CreateQuizShareLinkRequest;
import com.studysnap.backend.dto.CreateCombinedQuizShareLinkRequest;
import com.studysnap.backend.dto.PublicSharedQuizResponse;
import com.studysnap.backend.dto.QuizShareLinkResponse;
import com.studysnap.backend.dto.SharedQuizResultsRequest;
import com.studysnap.backend.dto.SharedQuizResultsResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.QuizShareLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class QuizShareController {
    private final QuizShareLinkService quizShareLinkService;

    @PostMapping("/quiz-share")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuizShareLinkResponse createShareLink(
            @Valid @RequestBody CreateQuizShareLinkRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quizShareLinkService.createShareLink(request.generatedQuizId(), userId);
    }

    @PostMapping("/combined-quiz-share")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuizShareLinkResponse createCombinedQuizShareLink(
            @Valid @RequestBody CreateCombinedQuizShareLinkRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return quizShareLinkService.createCombinedQuizShareLink(request.combinedQuizId(), user.userId());
    }

    @GetMapping("/combined-quiz-share/{combinedQuizId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuizShareLinkResponse getCombinedQuizShareLink(
            @PathVariable UUID combinedQuizId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return quizShareLinkService.getCombinedQuizShareLink(combinedQuizId, user.userId());
    }

    @GetMapping("/quiz-share/{generatedQuizId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuizShareLinkResponse getShareLinkByQuizId(
            @PathVariable UUID generatedQuizId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return quizShareLinkService.getShareLinkByQuizId(generatedQuizId, user.userId());
    }

    @PatchMapping("/quiz-share/{token}/toggle")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public QuizShareLinkResponse toggleShareLink(
            @PathVariable String token,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return quizShareLinkService.toggleShareLink(token, userId);
    }

    @GetMapping("/quiz/share/{token}")
    public PublicSharedQuizResponse getPublicSharedQuiz(@PathVariable String token) {
        return quizShareLinkService.getActivePublicQuiz(token);
    }

    @PostMapping("/quiz/share/{token}/results")
    public SharedQuizResultsResponse getSharedQuizResults(
            @PathVariable String token,
            @Valid @RequestBody SharedQuizResultsRequest request
    ) {
        return quizShareLinkService.getSharedQuizResults(token, request.answers(), request.multiAnswers());
    }
}
