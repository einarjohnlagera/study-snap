package com.studysnap.backend.controller;

import com.studysnap.backend.dto.LongExamCompleteRequest;
import com.studysnap.backend.dto.LongExamMasteryReportResponse;
import com.studysnap.backend.dto.LongExamProgressRequest;
import com.studysnap.backend.dto.LongExamSessionResponse;
import com.studysnap.backend.dto.LongExamStartRequest;
import com.studysnap.backend.dto.LongExamStartResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.LongExamService;
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
@RequestMapping("/long-exam")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class LongExamController {
    private final LongExamService longExamService;

    @PostMapping("/study-packs/{studyPackId}/start")
    public LongExamStartResponse startSession(
            @PathVariable String studyPackId,
            @RequestBody(required = false) LongExamStartRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return longExamService.startSession(studyPackId, user.userId(), request);
    }

    @GetMapping("/study-packs/{studyPackId}/active")
    public LongExamStartResponse getActiveSession(
            @PathVariable String studyPackId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return longExamService.getActiveSession(studyPackId, user.userId());
    }

    @GetMapping("/sessions/{sessionId}")
    public LongExamSessionResponse getSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return longExamService.getSession(sessionId, user.userId());
    }

    @PostMapping("/sessions/{sessionId}/progress")
    public LongExamSessionResponse saveProgress(
            @PathVariable UUID sessionId,
            @Valid @RequestBody LongExamProgressRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return longExamService.saveProgress(sessionId, user.userId(), request);
    }

    @PostMapping("/sessions/{sessionId}/pause")
    public LongExamSessionResponse pauseSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return longExamService.pauseSession(sessionId, user.userId());
    }

    @PostMapping("/sessions/{sessionId}/resume")
    public LongExamSessionResponse resumeSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return longExamService.resumeSession(sessionId, user.userId());
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public LongExamMasteryReportResponse completeSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody LongExamCompleteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return longExamService.completeSession(sessionId, user.userId(), request);
    }

    @PostMapping("/sessions/{sessionId}/forfeit")
    public SimpleMessageResponse forfeitSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return longExamService.forfeitSession(sessionId, user.userId());
    }
}
