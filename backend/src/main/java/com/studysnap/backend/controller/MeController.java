package com.studysnap.backend.controller;

import com.studysnap.backend.dto.MePlanResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.MePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {
    private final MePlanService mePlanService;

    @GetMapping("/plan")
    @PreAuthorize("isAuthenticated()")
    public MePlanResponse getPlan(@AuthenticationPrincipal AuthenticatedUser user) {
        return mePlanService.getPlan(user.userId());
    }
}
