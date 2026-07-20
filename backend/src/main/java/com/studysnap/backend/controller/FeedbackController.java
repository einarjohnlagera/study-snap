package com.studysnap.backend.controller;

import com.studysnap.backend.dto.FeedbackPromptContextResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.dto.SubmitFeedbackRequest;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {
    private static final String PAGE_URL_HEADER = "X-Page-Url";

    private final FeedbackService feedbackService;

    @GetMapping("/context")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public FeedbackPromptContextResponse getPromptContext(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return feedbackService.getPromptContext(user.userId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public SimpleMessageResponse submitFeedback(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SubmitFeedbackRequest request,
            @RequestHeader(name = PAGE_URL_HEADER, required = false) String pageUrl
    ) {
        return new SimpleMessageResponse(feedbackService.submitFeedback(
                user.userId(),
                request.message(),
                pageUrl
        ));
    }
}
