package com.studysnap.backend.controller;

import com.studysnap.backend.dto.InterviewPracticeAnswerRequest;
import com.studysnap.backend.dto.InterviewPracticeAnswerResponse;
import com.studysnap.backend.dto.InterviewPracticeStartRequest;
import com.studysnap.backend.dto.InterviewPracticeStartResponse;
import com.studysnap.backend.dto.InterviewReadinessReportResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.InterviewPracticeService;
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
@RequestMapping("/interview-practice")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class InterviewPracticeController {
    private final InterviewPracticeService interviewPracticeService;

    @PostMapping("/start")
    public InterviewPracticeStartResponse start(
            @Valid @RequestBody InterviewPracticeStartRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return interviewPracticeService.startSession(user.userId(), request);
    }

    @PostMapping("/sessions/{sessionId}/answer")
    public InterviewPracticeAnswerResponse answer(
            @PathVariable UUID sessionId,
            @Valid @RequestBody InterviewPracticeAnswerRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return interviewPracticeService.answerQuestion(sessionId, user.userId(), request);
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public InterviewReadinessReportResponse complete(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return interviewPracticeService.completeSession(sessionId, user.userId());
    }

    @PostMapping("/sessions/{sessionId}/forfeit")
    public SimpleMessageResponse forfeit(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return interviewPracticeService.forfeitSession(sessionId, user.userId());
    }
}
