package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AnalyticsEventRequest;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.exception.AuthenticationRequiredException;
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
        if (request.eventType() == AnalyticsEventType.DUE_CONCEPTS_DIGEST_LANDED && user == null) {
            // This event originates on an authenticated note route. When the stored access token has
            // expired, the permitAll analytics endpoint otherwise accepts that bearer as anonymous and
            // returns 200, so the analytics client never gets the 401 it needs to refresh and retry.
            // Anonymous events remain valid for every event type that can actually originate anonymously.
            throw new AuthenticationRequiredException();
        }
        analyticsService.trackEvent(
                user == null ? null : user.userId(),
                request.eventType(),
                request.entityId(),
                request.metadata()
        );
        return new SimpleMessageResponse("Tracked.");
    }
}
