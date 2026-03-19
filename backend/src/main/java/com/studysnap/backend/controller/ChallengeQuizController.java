package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ChallengeQuizCompleteRequest;
import com.studysnap.backend.dto.ChallengeQuizProgressRequest;
import com.studysnap.backend.dto.ChallengeQuizSessionResponse;
import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.ChallengeQuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/challenge-quiz")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class ChallengeQuizController {
    private final ChallengeQuizService challengeQuizService;

    @PostMapping("/study-packs/{studyPackId}/start")
    public ChallengeQuizStartResponse startChallengeQuiz(
            @PathVariable String studyPackId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return challengeQuizService.startSession(studyPackId, userId);
    }

    @GetMapping("/study-packs/{studyPackId}/in-progress")
    public ChallengeQuizStartResponse getInProgressChallengeQuiz(
            @PathVariable String studyPackId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return challengeQuizService.getInProgressSession(studyPackId, userId);
    }

    @PostMapping("/sessions/{sessionId}/progress")
    public ChallengeQuizStartResponse updateChallengeQuizProgress(
            @PathVariable String sessionId,
            @Valid @RequestBody ChallengeQuizProgressRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return challengeQuizService.updateSessionProgress(sessionId, userId, request);
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public ChallengeQuizSessionResponse completeChallengeQuiz(
            @PathVariable String sessionId,
            @Valid @RequestBody ChallengeQuizCompleteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return challengeQuizService.completeSession(sessionId, userId, request);
    }
}
