package com.studysnap.backend.controller;

import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.PremiumWaitlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/premium")
@RequiredArgsConstructor
public class PremiumWaitlistController {
    private final PremiumWaitlistService premiumWaitlistService;

    @PostMapping("/waitlist")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public SimpleMessageResponse joinWaitlist(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return new SimpleMessageResponse(premiumWaitlistService.joinWaitlist(user.userId()));
    }
}
