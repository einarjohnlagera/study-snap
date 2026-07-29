package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CreatorImpactResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.CreatorImpactService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/creator-impact")
@RequiredArgsConstructor
public class CreatorImpactController {
    private final CreatorImpactService creatorImpactService;

    @GetMapping("/me")
    public CreatorImpactResponse getMine(@AuthenticationPrincipal AuthenticatedUser user) {
        return creatorImpactService.getMine(user.userId());
    }
}
