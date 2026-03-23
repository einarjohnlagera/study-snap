package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AnalyticsEventRequest;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @PostMapping("/events")
    public SimpleMessageResponse trackEvent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AnalyticsEventRequest request
    ) {
        analyticsService.trackEvent(
                user == null ? null : user.userId(),
                request.eventType(),
                request.entityId(),
                request.metadata()
        );
        return new SimpleMessageResponse("Tracked.");
    }
}
