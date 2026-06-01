package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AdminFunnelMetricsResponse;
import com.studysnap.backend.service.AdminFunnelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/funnel")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFunnelController {
    private final AdminFunnelService adminFunnelService;

    @GetMapping("/metrics")
    public AdminFunnelMetricsResponse getMetrics() {
        return adminFunnelService.getMetrics();
    }
}
