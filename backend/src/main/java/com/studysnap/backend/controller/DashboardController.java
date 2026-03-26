package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ContinueStudyingResponse;
import com.studysnap.backend.dto.DashboardOverviewResponse;
import com.studysnap.backend.dto.MasterySnapshotResponse;
import com.studysnap.backend.dto.StudyEngagementResponse;
import com.studysnap.backend.dto.TodayFocusResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/continue-studying")
    public ContinueStudyingResponse getContinueStudyingRecommendation(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return dashboardService.getContinueStudyingRecommendation(userId);
    }

    @GetMapping("/today-focus")
    public TodayFocusResponse getTodayFocus(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return dashboardService.getTodayFocus(userId);
    }

    @GetMapping("/study-engagement")
    public StudyEngagementResponse getStudyEngagement(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return dashboardService.getStudyEngagement(userId);
    }

    @GetMapping("/mastery-snapshot")
    public MasterySnapshotResponse getMasterySnapshot(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return dashboardService.getMasterySnapshot(userId);
    }

    @GetMapping("/overview")
    public DashboardOverviewResponse getOverview(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID userId = user.userId();
        return dashboardService.getOverview(userId);
    }
}
