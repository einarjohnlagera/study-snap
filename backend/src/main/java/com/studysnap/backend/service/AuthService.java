package com.studysnap.backend.service;

import com.studysnap.backend.dto.AuthResponse;
import com.studysnap.backend.dto.LoginRequest;
import com.studysnap.backend.dto.LogoutRequest;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.OnboardingProfileTypeRequest;
import com.studysnap.backend.dto.RefreshTokenRequest;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.dto.SignupRequest;
import com.studysnap.backend.dto.VerifyEmailRequest;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.RefreshTokenEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.JwtService;
import com.studysnap.backend.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityProperties securityProperties;

    public AuthResponse signup(SignupRequest request, String ipAddress, String userAgent) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException("EMAIL_ALREADY_EXISTS", "This email is already registered.", HttpStatus.CONFLICT);
        }

        OffsetDateTime now = OffsetDateTime.now();
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setDisplayName(resolveDisplayName(request.displayName(), request.firstName()));
        user.setCountryCode(null);
        user.setProfileType(null);
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setLastPasswordChangeAt(now);

        UserEntity saved = userRepository.save(user);
        subscriptionService.createDefaultFreeSubscription(saved);
        sendVerificationEmailPlaceholder(saved);
        return buildAuthResponse(saved, PlanType.FREE, false, null, ipAddress, userAgent);
    }

    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String email = normalizeEmail(request.email());
        UserEntity user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            throw invalidCredentials();
        }
        if (isLocked(user)) {
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedLogin(user);
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidCredentials();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        boolean keepSignedIn = Boolean.TRUE.equals(request.keepSignedIn());

        PlanType planType = subscriptionService.resolvePlan(user.getId());
        return buildAuthResponse(user, planType, keepSignedIn, null, ipAddress, userAgent);
    }

    public AuthResponse refresh(RefreshTokenRequest request, String ipAddress, String userAgent) {
        RefreshTokenEntity existing = refreshTokenService.requireValid(request.refreshToken());
        UserEntity user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new AppException("INVALID_REFRESH_TOKEN", "Invalid refresh token.", HttpStatus.UNAUTHORIZED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException("INVALID_REFRESH_TOKEN", "Invalid refresh token.", HttpStatus.UNAUTHORIZED);
        }

        refreshTokenService.revoke(request.refreshToken());
        refreshTokenService.touch(existing);
        PlanType planType = subscriptionService.resolvePlan(user.getId());
        return buildAuthResponse(
                user,
                planType,
                Boolean.TRUE.equals(existing.getKeepSignedIn()),
                existing.getDeviceName(),
                ipAddress,
                userAgent
        );
    }

    public SimpleMessageResponse logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        return new SimpleMessageResponse("Logged out successfully.");
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));

        return new MeResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getCountryCode(),
                user.getProfileType(),
                user.getEmailVerifiedAt(),
                user.getRole(),
                user.getStatus(),
                subscriptionService.resolvePlan(user.getId())
        );
    }

    public MeResponse completeOnboarding(UUID userId, OnboardingProfileTypeRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));

        user.setProfileType(request.profileType());
        user.setUpdatedAt(OffsetDateTime.now());
        PlanType planType = subscriptionService.resolvePlan(user.getId());

        return new MeResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getCountryCode(),
                user.getProfileType(),
                user.getEmailVerifiedAt(),
                user.getRole(),
                user.getStatus(),
                planType
        );
    }

    public SimpleMessageResponse requestEmailVerification(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));
        sendVerificationEmailPlaceholder(user);
        return new SimpleMessageResponse("Verification email sent. Please check your inbox.");
    }

    public MeResponse verifyEmail(UUID userId, VerifyEmailRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));

        if (!request.token().trim().equals(user.getId().toString())) {
            throw new AppException("INVALID_VERIFICATION_TOKEN", "Invalid verification token.", HttpStatus.BAD_REQUEST);
        }

        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(OffsetDateTime.now());
            user.setUpdatedAt(OffsetDateTime.now());
        }

        return new MeResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getCountryCode(),
                user.getProfileType(),
                user.getEmailVerifiedAt(),
                user.getRole(),
                user.getStatus(),
                subscriptionService.resolvePlan(user.getId())
        );
    }

    @Transactional(readOnly = true)
    public void requireEmailVerified(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));
        if (user.getEmailVerifiedAt() == null) {
            throw new AppException(
                    "EMAIL_NOT_VERIFIED",
                    "Verify your email to start generating a Study Pack.",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private AuthResponse buildAuthResponse(
            UserEntity user,
            PlanType planType,
            boolean keepSignedIn,
            String deviceName,
            String ipAddress,
            String userAgent
    ) {
        String accessToken = jwtService.generateAccessToken(user);
        OffsetDateTime accessExpiresAt = jwtService.resolveAccessTokenExpiry();
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(
                user,
                keepSignedIn,
                deviceName,
                ipAddress,
                userAgent
        );
        return new AuthResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getProfileType(),
                user.getEmailVerifiedAt(),
                user.getRole(),
                planType,
                accessToken,
                refreshToken.rawToken(),
                accessExpiresAt,
                refreshToken.expiresAt()
        );
    }

    private void registerFailedLogin(UserEntity user) {
        int attempts = user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts();
        attempts += 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= securityProperties.getAuth().getMaxFailedAttempts()) {
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(securityProperties.getAuth().getLockMinutes()));
        }
        user.setUpdatedAt(OffsetDateTime.now());
    }

    private boolean isLocked(UserEntity user) {
        OffsetDateTime lockedUntil = user.getLockedUntil();
        return lockedUntil != null && lockedUntil.isAfter(OffsetDateTime.now());
    }

    private AppException invalidCredentials() {
        return new AppException("INVALID_CREDENTIALS", "Invalid email or password.", HttpStatus.UNAUTHORIZED);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveDisplayName(String displayName, String firstName) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return firstName == null ? null : firstName.trim();
    }

    private void sendVerificationEmailPlaceholder(UserEntity user) {
        log.info(
                "email_verification_placeholder userId={} email={} token={} (future: replace with token table + email provider)",
                user.getId(),
                user.getEmail(),
                user.getId()
        );
    }
}
