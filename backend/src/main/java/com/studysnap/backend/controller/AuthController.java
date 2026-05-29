package com.studysnap.backend.controller;

import com.studysnap.backend.dto.AuthResponse;
import com.studysnap.backend.dto.CompleteOnboardingRequest;
import com.studysnap.backend.dto.CompleteProductOnboardingRequest;
import com.studysnap.backend.dto.ForgotPasswordRequest;
import com.studysnap.backend.dto.GoogleAuthRequest;
import com.studysnap.backend.dto.GoogleConnectRequest;
import com.studysnap.backend.dto.LoginRequest;
import com.studysnap.backend.dto.LogoutRequest;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.OnboardingProfileTypeRequest;
import com.studysnap.backend.dto.RefreshTokenRequest;
import com.studysnap.backend.dto.ResetPasswordRequest;
import com.studysnap.backend.dto.SignInMethodsResponse;
import com.studysnap.backend.dto.SignupRequest;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.dto.UpdateEngagementModeRequest;
import com.studysnap.backend.dto.UpdateStudyRemindersRequest;
import com.studysnap.backend.dto.UpdateThemePreferenceRequest;
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

    private static final String USER_AGENT = "User-Agent";

    private final AuthService authService;
    private final AuthRateLimitService authRateLimitService;

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request, HttpServletRequest servletRequest) {
        authRateLimitService.assertAllowed("signup", resolveClientIp(servletRequest));
        return authService.signup(request, resolveClientIp(servletRequest), servletRequest.getHeader(USER_AGENT));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String clientIp = resolveClientIp(servletRequest);
        authRateLimitService.assertAllowed("login", clientIp + ":" + (request.email() == null ? "" : request.email().trim().toLowerCase()));
        return authService.login(request, clientIp, servletRequest.getHeader(USER_AGENT));
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleAuthRequest request, HttpServletRequest servletRequest) {
        String clientIp = resolveClientIp(servletRequest);
        authRateLimitService.assertAllowed("google", clientIp);
        return authService.loginWithGoogle(request, clientIp, servletRequest.getHeader(USER_AGENT));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
        authRateLimitService.assertAllowed("refresh", resolveClientIp(servletRequest));
        return authService.refresh(request, resolveClientIp(servletRequest), servletRequest.getHeader(USER_AGENT));
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

    @GetMapping("/sign-in-methods")
    @PreAuthorize("isAuthenticated()")
    public SignInMethodsResponse signInMethods(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.getSignInMethods(user.userId());
    }

    @PostMapping("/google/connect")
    @PreAuthorize("isAuthenticated()")
    public SignInMethodsResponse connectGoogle(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody GoogleConnectRequest request
    ) {
        return authService.connectGoogle(user.userId(), request);
    }

    @PostMapping("/onboarding/profile-type")
    @PreAuthorize("isAuthenticated()")
    public MeResponse completeOnboarding(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody OnboardingProfileTypeRequest request
    ) {
        return authService.updateProfileType(user.userId(), request);
    }

    @PostMapping("/onboarding")
    @PreAuthorize("isAuthenticated()")
    public MeResponse completeOnboardingSetup(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CompleteOnboardingRequest request
    ) {
        return authService.completeOnboarding(user.userId(), request);
    }

    @PostMapping("/product-onboarding/complete")
    @PreAuthorize("isAuthenticated()")
    public MeResponse completeProductOnboarding(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody(required = false) CompleteProductOnboardingRequest request
    ) {
        return authService.completeProductOnboarding(user.userId(), request);
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

    @PostMapping("/forgot-password")
    public SimpleMessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest servletRequest) {
        authRateLimitService.assertAllowed("forgot-password", resolveClientIp(servletRequest));
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public SimpleMessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest servletRequest) {
        authRateLimitService.assertAllowed("reset-password", resolveClientIp(servletRequest));
        return authService.resetPassword(request);
    }

    @PostMapping("/preferences/engagement-mode")
    @PreAuthorize("isAuthenticated()")
    public MeResponse updateEngagementMode(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateEngagementModeRequest request
    ) {
        return authService.updateEngagementMode(user.userId(), request);
    }

    @PostMapping("/preferences/study-reminders")
    @PreAuthorize("isAuthenticated()")
    public MeResponse updateStudyReminders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateStudyRemindersRequest request
    ) {
        return authService.updateStudyReminders(user.userId(), request);
    }

    @PostMapping("/preferences/theme")
    @PreAuthorize("isAuthenticated()")
    public MeResponse updateThemePreference(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateThemePreferenceRequest request
    ) {
        return authService.updateThemePreference(user.userId(), request);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
