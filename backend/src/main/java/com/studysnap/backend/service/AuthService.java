package com.studysnap.backend.service;

import com.studysnap.backend.dto.AuthResponse;
import com.studysnap.backend.dto.CompleteOnboardingRequest;
import com.studysnap.backend.dto.CompleteProductOnboardingRequest;
import com.studysnap.backend.dto.LoginRequest;
import com.studysnap.backend.dto.LogoutRequest;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.OnboardingProfileTypeRequest;
import com.studysnap.backend.dto.RefreshTokenRequest;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.dto.SignupRequest;
import com.studysnap.backend.dto.UpdatePublicProfileVisibilityRequest;
import com.studysnap.backend.dto.UpdateUserProfileRequest;
import com.studysnap.backend.dto.UpdateEngagementModeRequest;
import com.studysnap.backend.dto.UpdateStudyRemindersRequest;
import com.studysnap.backend.dto.UpdateThemePreferenceRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.RefreshTokenEntity;
import com.studysnap.backend.entity.ThemePreference;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.InvalidCredentialsException;
import com.studysnap.backend.exception.InvalidRefreshTokenException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.util.CourseProgramNormalizationUtils;
import com.studysnap.backend.security.JwtService;
import com.studysnap.backend.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {
    private static final String RESERVED_DISPLAY_NAME_MESSAGE = "This display name is reserved. Please choose another name.";
    private static final String USERNAME_TAKEN_MESSAGE = "Username is already taken.";
    private static final String USERNAME_FORMAT_MESSAGE = "Username can only contain letters, numbers, underscores, or hyphens.";
    private static final String USERNAME_LENGTH_MESSAGE = "Username must be 3-30 characters.";
    private static final int USERNAME_MIN_LENGTH = 3;
    private static final int USERNAME_MAX_LENGTH = 30;
    private static final Set<String> RESERVED_USERNAMES = Set.of(
            "admin",
            "root",
            "support",
            "notelib",
            "public",
            "library",
            "settings",
            "login",
            "signup",
            "api"
    );
    private static final Set<String> RESERVED_DISPLAY_NAMES = Set.of(
            "notelib",
            "admin",
            "support",
            "official",
            "moderator",
            "staff",
            "team"
    );

    private final UserRepository userRepository;
    private final StudyPackRepository studyPackRepository;
    private final SubscriptionService subscriptionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityProperties securityProperties;
    private final EmailVerificationService emailVerificationService;
    private final AnalyticsService analyticsService;

    public AuthResponse signup(SignupRequest request, String ipAddress, String userAgent) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException("EMAIL_ALREADY_EXISTS", "This email is already registered.", HttpStatus.CONFLICT);
        }

        OffsetDateTime now = OffsetDateTime.now();
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPendingEmail(null);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setDisplayName(resolveDisplayName(request.displayName()));
        user.setUsername(generateAvailableUsername(user.getDisplayName(), user.getFirstName(), email));
        user.setPublicProfileVisible(true);
        user.setCountryCode(null);
        user.setProfileType(null);
        user.setEngagementMode(EngagementMode.FOCUSED);
        user.setInactivityRemindersEnabled(false);
        user.setWeakConceptRemindersEnabled(false);
        user.setThemePreference(ThemePreference.SYSTEM);
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setCurrentStreak(0);
        user.setLongestStreak(0);
        user.setLastStudyDate(null);
        user.setOnboardingCompletedAt(null);
        user.setProductOnboardingCompletedAt(null);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setLastPasswordChangeAt(now);

        UserEntity saved = userRepository.save(user);
        subscriptionService.createDefaultFreeSubscription(saved);
        emailVerificationService.sendVerificationEmail(saved, false);
        analyticsService.trackEvent(saved.getId(), AnalyticsEventType.SIGNUP, saved.getId(), Map.of("method", "email_password"));
        analyticsService.trackEvent(saved.getId(), AnalyticsEventType.SIGNUP_COMPLETED, saved.getId(), Map.of("method", "email_password"));
        analyticsService.trackEvent(saved.getId(), AnalyticsEventType.EMAIL_VERIFICATION_SENT, saved.getId(), Map.of("source", "signup"));
        return buildAuthResponse(saved, PlanType.FREE, false, null, ipAddress, userAgent);
    }

    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String identifier = normalizeLoginIdentifier(request.email());
        UserEntity user = resolveLoginUser(identifier);

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
        analyticsService.trackEvent(user.getId(), AnalyticsEventType.LOGIN, user.getId(), Map.of(
                "keepSignedIn", keepSignedIn
        ));
        return buildAuthResponse(user, planType, keepSignedIn, null, ipAddress, userAgent);
    }

    public AuthResponse refresh(RefreshTokenRequest request, String ipAddress, String userAgent) {
        RefreshTokenEntity existing = refreshTokenService.requireValid(request.refreshToken());
        UserEntity user = userRepository.findById(existing.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRefreshTokenException();
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
        return toMeResponse(findUserOrThrow(userId));
    }

    public MeResponse updateProfileType(UUID userId, OnboardingProfileTypeRequest request) {
        UserEntity user = findUserOrThrow(userId);

        user.setProfileType(request.profileType());
        user.setUpdatedAt(OffsetDateTime.now());
        return toMeResponse(user);
    }

    public MeResponse completeOnboarding(UUID userId, CompleteOnboardingRequest request) {
        UserEntity user = findUserOrThrow(userId);
        if (user.getEmailVerifiedAt() == null) {
            throw new AppException(
                    "EMAIL_VERIFICATION_REQUIRED",
                    "Verify your email before continuing setup.",
                    null,
                    "RESEND_VERIFICATION",
                    HttpStatus.FORBIDDEN
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        user.setProfileType(request.profileType());
        user.setExamDate(resolveExamDate(request));
        if (user.getOnboardingCompletedAt() == null) {
            user.setOnboardingCompletedAt(now);
        }
        user.setUpdatedAt(now);
        return toMeResponse(user);
    }

    public MeResponse completeProductOnboarding(UUID userId, CompleteProductOnboardingRequest request) {
        UserEntity user = findUserOrThrow(userId);
        if (user.getProductOnboardingCompletedAt() == null) {
            user.setProductOnboardingCompletedAt(OffsetDateTime.now());
            user.setUpdatedAt(OffsetDateTime.now());
        }
        return toMeResponse(user);
    }

    public SimpleMessageResponse requestEmailVerification(UUID userId) {
        UserEntity user = findUserOrThrow(userId);
        if (user.getEmailVerifiedAt() != null && user.getPendingEmail() == null) {
            return new SimpleMessageResponse("Your email is already verified.");
        }
        emailVerificationService.sendVerificationEmail(user, true);
        analyticsService.trackEvent(user.getId(), AnalyticsEventType.EMAIL_VERIFICATION_SENT, user.getId(), Map.of("source", "manual_request"));
        return new SimpleMessageResponse("Verification email sent. Please check your inbox.");
    }

    public SimpleMessageResponse verifyEmailToken(String token) {
        EmailVerificationService.EmailVerificationResult result = emailVerificationService.verifyToken(token);
        if (!result.alreadyVerified()) {
            analyticsService.trackEvent(result.userId(), AnalyticsEventType.EMAIL_VERIFIED, result.userId(), Map.of());
        }
        return new SimpleMessageResponse(result.message());
    }

    public MeResponse updateEngagementMode(UUID userId, UpdateEngagementModeRequest request) {
        UserEntity user = findUserOrThrow(userId);

        user.setEngagementMode(request.engagementMode());
        user.setUpdatedAt(OffsetDateTime.now());

        return toMeResponse(user);
    }

    public MeResponse updateStudyReminders(UUID userId, UpdateStudyRemindersRequest request) {
        UserEntity user = findUserOrThrow(userId);

        user.setInactivityRemindersEnabled(request.inactivityRemindersEnabled());
        user.setWeakConceptRemindersEnabled(request.weakConceptRemindersEnabled());
        user.setUpdatedAt(OffsetDateTime.now());

        return toMeResponse(user);
    }

    public MeResponse updateThemePreference(UUID userId, UpdateThemePreferenceRequest request) {
        UserEntity user = findUserOrThrow(userId);

        user.setThemePreference(request.themePreference());
        user.setUpdatedAt(OffsetDateTime.now());

        return toMeResponse(user);
    }

    public MeResponse updateUserProfile(UUID userId, UpdateUserProfileRequest request) {
        UserEntity user = findUserOrThrow(userId);

        String normalizedFirstName = normalizeRequiredText(request.firstName());
        String normalizedLastName = normalizeOptionalText(request.lastName());
        String normalizedDisplayName = normalizeOptionalText(request.displayName());
        String normalizedUsername = normalizeUsername(request.username());
        String normalizedBio = normalizeOptionalText(request.bio());
        LearnerLevel normalizedLearnerLevel = request.learnerLevel();
        String normalizedCourseProgram = normalizeOptionalCourseProgram(request.courseProgram());
        String normalizedEmail = normalizeEmail(request.email());

        user.setFirstName(normalizedFirstName);
        user.setLastName(normalizedLastName);
        user.setDisplayName(resolveDisplayName(normalizedDisplayName));
        if (!normalizedUsername.equalsIgnoreCase(user.getUsername())) {
            ensureUsernameAvailable(user.getId(), normalizedUsername);
        }
        user.setUsername(normalizedUsername);
        user.setBio(normalizedBio);
        user.setLearnerLevel(normalizedLearnerLevel);
        user.setCourseProgram(normalizedCourseProgram);

        if (normalizedEmail.equalsIgnoreCase(user.getEmail())) {
            user.setPendingEmail(null);
        } else if (!normalizedEmail.equalsIgnoreCase(user.getPendingEmail())) {
            ensureEmailAvailable(user.getId(), normalizedEmail);
            user.setPendingEmail(normalizedEmail);
            emailVerificationService.sendVerificationEmail(user, false);
        }

        user.setUpdatedAt(OffsetDateTime.now());
        return toMeResponse(user);
    }

    public MeResponse updatePublicProfileVisibility(UUID userId, UpdatePublicProfileVisibilityRequest request) {
        UserEntity user = findUserOrThrow(userId);
        user.setPublicProfileVisible(Boolean.TRUE.equals(request.publicProfileVisible()));
        user.setUpdatedAt(OffsetDateTime.now());
        return toMeResponse(user);
    }

    private MeResponse toMeResponse(UserEntity user) {
        SubscriptionService.PlanSnapshot planSnapshot = subscriptionService.getPlanSnapshot(user.getId());
        long studyPackCount = studyPackRepository.countByOwnerUserId(user.getId());
        return new MeResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getPendingEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getUsername(),
                user.getBio(),
                user.getLearnerLevel(),
                user.getCourseProgram(),
                Boolean.TRUE.equals(user.getPublicProfileVisible()),
                user.getCountryCode(),
                user.getProfileType(),
                user.getExamDate(),
                user.getEngagementMode(),
                Boolean.TRUE.equals(user.getInactivityRemindersEnabled()),
                Boolean.TRUE.equals(user.getWeakConceptRemindersEnabled()),
                resolveThemePreference(user),
                user.getEmailVerifiedAt(),
                user.getOnboardingCompletedAt(),
                user.getProductOnboardingCompletedAt(),
                studyPackCount,
                user.getRole(),
                user.getStatus(),
                planSnapshot.planType(),
                planSnapshot.toResponse()
        );
    }

    @Transactional(readOnly = true)
    public void requireEmailVerified(UUID userId) {
        UserEntity user = findUserOrThrow(userId);
        if (user.getEmailVerifiedAt() == null) {
            throw new AppException(
                    "EMAIL_NOT_VERIFIED",
                    "Verify your email before using this feature.",
                    null,
                    "RESEND_VERIFICATION",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private LocalDate resolveExamDate(CompleteOnboardingRequest request) {
        if (request.profileType() != com.studysnap.backend.entity.ProfileType.BOARD_EXAM) {
            return null;
        }
        return request.examDate();
    }

    private String normalizeOptionalCourseProgram(String value) {
        String normalized = CourseProgramNormalizationUtils.normalizeForStorage(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 120) {
            throw new AppException(
                    "INVALID_COURSE_PROGRAM",
                    "Course / Program must be 120 characters or less.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
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
            user.getOnboardingCompletedAt(),
            user.getProductOnboardingCompletedAt(),
            resolveThemePreference(user),
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

    private ThemePreference resolveThemePreference(UserEntity user) {
        return user.getThemePreference() == null ? ThemePreference.SYSTEM : user.getThemePreference();
    }

    private AppException invalidCredentials() {
        return new InvalidCredentialsException();
    }

    private UserEntity findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeLoginIdentifier(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }

    private UserEntity resolveLoginUser(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmailIgnoreCase(identifier).orElse(null);
        }
        return userRepository.findByUsernameIgnoreCase(identifier).orElse(null);
    }

    private String normalizeRequiredText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveDisplayName(String displayName) {
        String normalizedDisplayName = normalizeOptionalText(displayName);
        if (normalizedDisplayName == null) {
            return null;
        }
        validateDisplayName(normalizedDisplayName);
        return normalizedDisplayName;
    }

    private void validateDisplayName(String displayName) {
        String normalized = displayName.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("notelib") || RESERVED_DISPLAY_NAMES.contains(normalized)) {
            throw new AppException("DISPLAY_NAME_RESERVED", RESERVED_DISPLAY_NAME_MESSAGE, HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        validateUsername(normalized);
        return normalized;
    }

    private void validateUsername(String username) {
        if (username.length() < USERNAME_MIN_LENGTH || username.length() > USERNAME_MAX_LENGTH) {
            throw new AppException("USERNAME_INVALID_LENGTH", USERNAME_LENGTH_MESSAGE, HttpStatus.BAD_REQUEST);
        }
        if (!username.matches("[a-z0-9_-]+")) {
            throw new AppException("USERNAME_INVALID_FORMAT", USERNAME_FORMAT_MESSAGE, HttpStatus.BAD_REQUEST);
        }
        if (RESERVED_USERNAMES.contains(username)) {
            throw new AppException("USERNAME_RESERVED", USERNAME_FORMAT_MESSAGE, HttpStatus.BAD_REQUEST);
        }
    }

    private String generateAvailableUsername(String displayName, String firstName, String email) {
        String base = slugifyUsernameCandidate(displayName);
        if (base == null) {
            base = slugifyUsernameCandidate(firstName);
        }
        if (base == null) {
            String emailPrefix = email == null ? null : email.split("@", 2)[0];
            base = slugifyUsernameCandidate(emailPrefix);
        }
        if (base == null || RESERVED_USERNAMES.contains(base)) {
            base = "user";
        }
        if (base.length() > USERNAME_MAX_LENGTH) {
            base = base.substring(0, USERNAME_MAX_LENGTH);
        }

        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsernameIgnoreCase(candidate) || RESERVED_USERNAMES.contains(candidate)) {
            String suffixText = String.valueOf(suffix);
            int baseLength = Math.min(base.length(), USERNAME_MAX_LENGTH - suffixText.length());
            candidate = base.substring(0, baseLength) + suffixText;
            suffix += 1;
        }
        return candidate;
    }

    private String slugifyUsernameCandidate(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "")
                .replaceAll("^[-_]+|[-_]+$", "");
        if (slug.length() < USERNAME_MIN_LENGTH) {
            return null;
        }
        return slug;
    }

    private void ensureEmailAvailable(UUID currentUserId, String email) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new AppException("EMAIL_ALREADY_EXISTS", "This email is already registered.", HttpStatus.CONFLICT);
                });
        userRepository.findByPendingEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new AppException("EMAIL_ALREADY_EXISTS", "This email is already registered.", HttpStatus.CONFLICT);
                });
    }

    private void ensureUsernameAvailable(UUID currentUserId, String username) {
        userRepository.findByUsernameIgnoreCase(username)
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new AppException("USERNAME_ALREADY_EXISTS", USERNAME_TAKEN_MESSAGE, HttpStatus.CONFLICT);
                });
    }
}
