package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CombinedQuizResponse;
import com.studysnap.backend.dto.CombinedQuizSummaryResponse;
import com.studysnap.backend.dto.CreateCombinedQuizRequest;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.CombinedQuizService;
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
import java.util.List;

@RestController
@RequestMapping("/combined-quizzes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class CombinedQuizController {
    private final CombinedQuizService combinedQuizService;

    @PostMapping
    public CombinedQuizResponse assemble(
            @Valid @RequestBody CreateCombinedQuizRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return combinedQuizService.assemble(request, user.userId());
    }

    @GetMapping
    public List<CombinedQuizSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return combinedQuizService.list(user.userId());
    }

    @GetMapping("/{combinedQuizId}")
    public CombinedQuizResponse getById(
            @PathVariable UUID combinedQuizId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return combinedQuizService.getById(combinedQuizId, user.userId());
    }
}
