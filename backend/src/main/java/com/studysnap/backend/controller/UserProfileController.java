package com.studysnap.backend.controller;

import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.UpdateUserProfileRequest;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final AuthService authService;

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public MeResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        return authService.updateUserProfile(user.userId(), request);
    }
}
