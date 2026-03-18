package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AuthResponse;
import com.studysnap.backend.dto.LoginRequest;
import com.studysnap.backend.dto.LogoutRequest;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.OnboardingProfileTypeRequest;
import com.studysnap.backend.dto.RefreshTokenRequest;
import com.studysnap.backend.dto.SignupRequest;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.dto.UpdateEngagementModeRequest;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.security.AuthRateLimitService;
import com.studysnap.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final AuthRateLimitService authRateLimitService;

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request, HttpServletRequest servletRequest) {
        authRateLimitService.assertAllowed("signup", resolveClientIp(servletRequest));
        return authService.signup(request, resolveClientIp(servletRequest), servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String clientIp = resolveClientIp(servletRequest);
        authRateLimitService.assertAllowed("login", clientIp + ":" + (request.email() == null ? "" : request.email().trim().toLowerCase()));
        return authService.login(request, clientIp, servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
        authRateLimitService.assertAllowed("refresh", resolveClientIp(servletRequest));
        return authService.refresh(request, resolveClientIp(servletRequest), servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/logout")
    public SimpleMessageResponse logout(@Valid @RequestBody LogoutRequest request) {
        return authService.logout(request);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.getMe(user.userId());
    }

    @PostMapping("/onboarding/profile-type")
    @PreAuthorize("isAuthenticated()")
    public MeResponse completeOnboarding(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody OnboardingProfileTypeRequest request
    ) {
        return authService.completeOnboarding(user.userId(), request);
    }

    @PostMapping("/verify-email/request")
    @PreAuthorize("isAuthenticated()")
    public SimpleMessageResponse requestEmailVerification(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.requestEmailVerification(user.userId());
    }

    @PostMapping("/resend-verification")
    @PreAuthorize("isAuthenticated()")
    public SimpleMessageResponse resendVerification(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.requestEmailVerification(user.userId());
    }

    @GetMapping("/verify-email")
    public SimpleMessageResponse verifyEmail(@RequestParam("token") String token) {
        return authService.verifyEmailToken(token);
    }

    @PostMapping("/preferences/engagement-mode")
    @PreAuthorize("isAuthenticated()")
    public MeResponse updateEngagementMode(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateEngagementModeRequest request
    ) {
        return authService.updateEngagementMode(user.userId(), request);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
