package com.studysnap.backend.controller;

import com.studysnap.backend.dto.OfficialStudyPlanWishlistRequest;
import com.studysnap.backend.dto.OfficialStudyPlanWishlistStatusResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.OfficialStudyPlanWishlistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/official-study-plan-wishlist")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class OfficialStudyPlanWishlistController {
    private final OfficialStudyPlanWishlistService wishlistService;

    @PostMapping
    public OfficialStudyPlanWishlistStatusResponse requestPlan(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody OfficialStudyPlanWishlistRequest request
    ) {
        return new OfficialStudyPlanWishlistStatusResponse(
                wishlistService.requestPlan(user.userId(), request.courseProgram())
        );
    }

    @GetMapping("/status")
    public OfficialStudyPlanWishlistStatusResponse getStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @Size(max = 120) String courseProgram
    ) {
        return new OfficialStudyPlanWishlistStatusResponse(
                wishlistService.hasRequestedPlan(user.userId(), courseProgram)
        );
    }
}
