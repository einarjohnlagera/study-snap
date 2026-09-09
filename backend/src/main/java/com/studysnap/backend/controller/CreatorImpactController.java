package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CreatorImpactPageResponse;
import com.studysnap.backend.dto.CreatorImpactSummaryResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.CreatorImpactService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/creator-impact")
@RequiredArgsConstructor
@Validated
public class CreatorImpactController {
    private final CreatorImpactService creatorImpactService;

    @GetMapping("/me")
    public CreatorImpactPageResponse getMine(
            @RequestParam boolean impacted,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return creatorImpactService.getMine(user.userId(), impacted, page, size);
    }

    @GetMapping("/me/summary")
    public CreatorImpactSummaryResponse getSummary(@AuthenticationPrincipal AuthenticatedUser user) {
        return creatorImpactService.getSummary(user.userId());
    }
}
