package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminDashboardRecentEventsResponse;
import com.studysnap.backend.dto.AdminDashboardSummaryResponse;
import com.studysnap.backend.dto.AdminDashboardTopContentResponse;
import com.studysnap.backend.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;

    @GetMapping("/summary")
    public AdminDashboardSummaryResponse getSummary() {
        return adminDashboardService.getSummary();
    }

    @GetMapping("/top-content")
    public AdminDashboardTopContentResponse getTopContent() {
        return adminDashboardService.getTopContent();
    }

    @GetMapping("/recent-events")
    public AdminDashboardRecentEventsResponse getRecentEvents() {
        return adminDashboardService.getRecentEvents();
    }
}
