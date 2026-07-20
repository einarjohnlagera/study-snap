package com.studysnap.backend.controller;

import com.studysnap.backend.config.FeedbackHeaders;
import com.studysnap.backend.dto.FeedbackPromptContextResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.dto.SubmitFeedbackRequest;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {
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
    public ResponseEntity<SimpleMessageResponse> submitFeedback(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SubmitFeedbackRequest request,
            @RequestHeader(name = FeedbackHeaders.PAGE_URL, required = false) String pageUrl
    ) {
        FeedbackService.FeedbackSubmissionResult result = feedbackService.submitFeedback(
                user.userId(),
                request.message(),
                pageUrl
        );
        return ResponseEntity.ok()
                .header(FeedbackHeaders.FEEDBACK_ID, result.feedbackId().toString())
                .body(new SimpleMessageResponse(result.message()));
    }

    @PostMapping(value = "/{feedbackId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Void> uploadImage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID feedbackId,
            @RequestPart("image") MultipartFile image
    ) {
        feedbackService.uploadImage(user.userId(), feedbackId, image);
        return ResponseEntity.noContent().build();
    }
}
