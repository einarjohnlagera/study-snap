package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AuthResponse;
import com.studysnap.backend.dto.LoginRequest;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.OnboardingProfileTypeRequest;
import com.studysnap.backend.dto.SignupRequest;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.dto.VerifyEmailRequest;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.UserContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UserContextService userContextService;

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public MeResponse me(@RequestHeader(name = "X-User-Id", required = false) String userIdHeader) {
        UUID userId = userContextService.requireUserId(userIdHeader);
        return authService.getMe(userId);
    }

    @PostMapping("/onboarding/profile-type")
    public MeResponse completeOnboarding(
            @RequestHeader(name = "X-User-Id", required = false) String userIdHeader,
            @Valid @RequestBody OnboardingProfileTypeRequest request
    ) {
        UUID userId = userContextService.requireUserId(userIdHeader);
        return authService.completeOnboarding(userId, request);
    }

    @PostMapping("/verify-email/request")
    public SimpleMessageResponse requestEmailVerification(
            @RequestHeader(name = "X-User-Id", required = false) String userIdHeader
    ) {
        UUID userId = userContextService.requireUserId(userIdHeader);
        return authService.requestEmailVerification(userId);
    }

    @PostMapping("/verify-email/confirm")
    public MeResponse verifyEmail(
            @RequestHeader(name = "X-User-Id", required = false) String userIdHeader,
            @Valid @RequestBody VerifyEmailRequest request
    ) {
        UUID userId = userContextService.requireUserId(userIdHeader);
        return authService.verifyEmail(userId, request);
    }
}
