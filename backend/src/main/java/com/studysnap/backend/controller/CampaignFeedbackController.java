package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CampaignFeedbackStatusResponse;
import com.studysnap.backend.dto.ApiErrorResponse;
import com.studysnap.backend.dto.SubmitCampaignFeedbackRequest;
import com.studysnap.backend.config.RequestIdFilter;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.CampaignFeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/feedback/campaign")
@PreAuthorize("hasAnyRole('USER','ADMIN')")
@RequiredArgsConstructor
public class CampaignFeedbackController {
    private static final Pattern JSON_FIELD_PATH = Pattern.compile("\\[\"([^\"]+)\"\\]");
    private final CampaignFeedbackService campaignFeedbackService;

    @GetMapping
    public CampaignFeedbackStatusResponse getStatus(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return campaignFeedbackService.getStatus(user.userId());
    }

    @PostMapping
    public ResponseEntity<Void> submit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody SubmitCampaignFeedbackRequest request
    ) {
        campaignFeedbackService.submit(user.userId(), request);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        String field = unreadableField(exception);
        String message = field == null ? "Invalid request body." : "Invalid value for " + field + ".";
        Object requestIdAttribute = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR);
        String requestId = requestIdAttribute == null ? "unknown" : requestIdAttribute.toString();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ApiErrorResponse(
                        requestId,
                        new ApiErrorResponse.ApiError("VALIDATION_ERROR", message, null, null)
                )
        );
    }

    private String unreadableField(HttpMessageNotReadableException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause.getMessage() != null) {
                Matcher matcher = JSON_FIELD_PATH.matcher(cause.getMessage());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            cause = cause.getCause();
        }
        return null;
    }
}
