package com.studysnap.backend.service;

import com.studysnap.backend.dto.AuthResponse;
import com.studysnap.backend.dto.CompleteOnboardingRequest;
import com.studysnap.backend.dto.CompleteProductOnboardingRequest;
import com.studysnap.backend.dto.GoogleAuthRequest;
import com.studysnap.backend.dto.GoogleConnectRequest;
import com.studysnap.backend.dto.LoginRequest;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.RefreshTokenRequest;
import com.studysnap.backend.dto.SignupRequest;
import com.studysnap.backend.dto.UpdatePublicProfileVisibilityRequest;
import com.studysnap.backend.dto.UpdateStudyRemindersRequest;
import com.studysnap.backend.dto.UpdateThemePreferenceRequest;
import com.studysnap.backend.dto.UpdateUserProfileRequest;
import com.studysnap.backend.dto.UpdateExamDateRequest;
import com.studysnap.backend.dto.UpdateStudyGoalRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.AuthProvider;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.ThemePreference;
import com.studysnap.backend.entity.UserAuthProviderEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.InvalidCredentialsException;
import com.studysnap.backend.exception.InvalidGoalException;
import com.studysnap.backend.exception.InvalidRefreshTokenException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserAuthProviderRepository;
import com.studysnap.backend.security.JwtService;
import com.studysnap.backend.security.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserAuthProviderRepository userAuthProviderRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private SecurityProperties securityProperties;
    @Mock
    private GoogleIdentityTokenVerifier googleIdentityTokenVerifier;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private PasswordResetService passwordResetService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            userAuthProviderRepository,
            studyPackRepository,
            subscriptionService,
            passwordEncoder,
            jwtService,
            refreshTokenService,
            securityProperties,
            googleIdentityTokenVerifier,
            emailVerificationService,
            analyticsService,
            passwordResetService
        );
        lenient().when(jwtService.generateAccessToken(any(UserEntity.class))).thenReturn("access-token");
        lenient().when(jwtService.resolveAccessTokenExpiry()).thenReturn(OffsetDateTime.now().plusMinutes(15));
        lenient().when(studyPackRepository.countByOwnerUserId(any(UUID.class))).thenReturn(0L);
        lenient().when(refreshTokenService.issue(any(UserEntity.class), any(Boolean.class), any(), any(), any()))
            .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", OffsetDateTime.now().plusDays(7)));
    }

    @Test
    void signup_tracksSignupAndVerificationEvents() {
        when(userRepository.existsByEmailIgnoreCase("[email protected]")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.signup(
            new SignupRequest("[email protected]", "password123", "Note", "note"),
            "127.0.0.1",
            "JUnit"
        );

        assertThat(response.email()).isEqualTo("[email protected]");
        verify(userRepository).existsByUsernameIgnoreCase("note");
        assertThat(response.onboardingCompletedAt()).isNull();
        assertThat(response.productOnboardingCompletedAt()).isNull();
        assertThat(response.themePreference()).isEqualTo(ThemePreference.SYSTEM);
        ArgumentCaptor<UserEntity> savedUser = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getWeeklySummaryRemindersEnabled()).isFalse();
        verify(subscriptionService).createDefaultFreeSubscription(any(UserEntity.class));
        verify(emailVerificationService).sendVerificationEmail(any(UserEntity.class), eq(false));
        verify(analyticsService).trackEvent(any(UUID.class), eq(AnalyticsEventType.SIGNUP), any(UUID.class), any());
        verify(analyticsService).trackEvent(any(UUID.class), eq(AnalyticsEventType.SIGNUP_COMPLETED), any(UUID.class),
            any());
        verify(analyticsService).trackEvent(any(UUID.class), eq(AnalyticsEventType.EMAIL_VERIFICATION_SENT),
            any(UUID.class), any());
    }

    @Test
    void login_tracksLoginEvent() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setDisplayName("note");
        user.setUsername("note");
        user.setPasswordHash("hashed");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);

        when(userRepository.findByEmailIgnoreCase("current@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        AuthResponse response = authService.login(
            new LoginRequest("current@example.com", "password123", true),
            "127.0.0.1",
            "JUnit"
        );

        assertThat(response.email()).isEqualTo("current@example.com");
        assertThat(response.themePreference()).isEqualTo(ThemePreference.SYSTEM);
        verify(analyticsService).trackEvent(userId, AnalyticsEventType.LOGIN, userId, java.util.Map.of(
            "keepSignedIn", true
        ));
    }

    @Test
    void login_allowsUsernameIdentifier() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setUsername("noteguru");
        user.setPasswordHash("hashed");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);

        when(userRepository.findByUsernameIgnoreCase("noteguru")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        AuthResponse response = authService.login(
            new LoginRequest("noteguru", "password123", false),
            "127.0.0.1",
            "JUnit"
        );

        assertThat(response.email()).isEqualTo("current@example.com");
        verify(userRepository, never()).findByEmailIgnoreCase("noteguru");
    }

    @Test
    void googleLogin_createsNewVerifiedUserAndProvider() {
        when(googleIdentityTokenVerifier.verify("google-token")).thenReturn(new GoogleIdentityTokenVerifier.GoogleIdentity(
            "google-sub-1",
            "student@example.com",
            true,
            "Student One",
            "Student"
        ));
        when(userAuthProviderRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase("studentone")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAuthProviderRepository.findByUserIdAndProvider(any(UUID.class), eq(AuthProvider.GOOGLE)))
            .thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(any(UUID.class))).thenReturn(PlanType.FREE);

        AuthResponse response = authService.loginWithGoogle(
            new GoogleAuthRequest("google-token", true),
            "127.0.0.1",
            "JUnit"
        );

        assertThat(response.email()).isEqualTo("student@example.com");
        assertThat(response.emailVerifiedAt()).isNotNull();
        ArgumentCaptor<UserEntity> savedUser = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getWeeklySummaryRemindersEnabled()).isFalse();
        verify(userAuthProviderRepository).save(any(UserAuthProviderEntity.class));
        verify(subscriptionService).createDefaultFreeSubscription(any(UserEntity.class));
        verify(emailVerificationService, never()).sendVerificationEmail(any(UserEntity.class), any(Boolean.class));
        verify(analyticsService).trackEvent(any(UUID.class), eq(AnalyticsEventType.SIGNUP), any(UUID.class), any());
        verify(analyticsService).trackEvent(any(UUID.class), eq(AnalyticsEventType.LOGIN), any(UUID.class), any());
    }

    @Test
    void googleLogin_linksExistingEmailUserWhenGoogleEmailVerified() {
        UUID userId = UUID.randomUUID();
        UserEntity user = activeUser(userId, "student@example.com");
        user.setEmailVerifiedAt(null);

        when(googleIdentityTokenVerifier.verify("google-token")).thenReturn(new GoogleIdentityTokenVerifier.GoogleIdentity(
            "google-sub-1",
            "student@example.com",
            true,
            "Student One",
            "Student"
        ));
        when(userAuthProviderRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.of(user));
        when(userAuthProviderRepository.findByUserIdAndProvider(userId, AuthProvider.GOOGLE)).thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        AuthResponse response = authService.loginWithGoogle(
            new GoogleAuthRequest("google-token", false),
            "127.0.0.1",
            "JUnit"
        );

        assertThat(response.userId()).isEqualTo(userId.toString());
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        verify(userAuthProviderRepository).save(any(UserAuthProviderEntity.class));
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(subscriptionService, never()).createDefaultFreeSubscription(any(UserEntity.class));
    }

    @Test
    void googleLogin_reusesExistingProviderLink() {
        UUID userId = UUID.randomUUID();
        UserEntity user = activeUser(userId, "student@example.com");
        UserAuthProviderEntity provider = new UserAuthProviderEntity();
        provider.setUserId(userId);
        provider.setProvider(AuthProvider.GOOGLE);
        provider.setProviderUserId("google-sub-1");
        provider.setProviderEmail("old@example.com");

        when(googleIdentityTokenVerifier.verify("google-token")).thenReturn(new GoogleIdentityTokenVerifier.GoogleIdentity(
            "google-sub-1",
            "student@example.com",
            true,
            "Student One",
            "Student"
        ));
        when(userAuthProviderRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
            .thenReturn(Optional.of(provider));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        AuthResponse response = authService.loginWithGoogle(
            new GoogleAuthRequest("google-token", false),
            "127.0.0.1",
            "JUnit"
        );

        assertThat(response.userId()).isEqualTo(userId.toString());
        assertThat(provider.getProviderEmail()).isEqualTo("student@example.com");
        verify(userAuthProviderRepository, never()).save(any(UserAuthProviderEntity.class));
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void googleLogin_rejectsUnverifiedGoogleEmail() {
        when(googleIdentityTokenVerifier.verify("google-token")).thenReturn(new GoogleIdentityTokenVerifier.GoogleIdentity(
            "google-sub-1",
            "student@example.com",
            false,
            "Student One",
            "Student"
        ));

        GoogleAuthRequest request = new GoogleAuthRequest("google-token", false);
        assertThatThrownBy(() -> authService.loginWithGoogle(
            request,
            "127.0.0.1",
            "JUnit"
        )).isInstanceOf(AppException.class)
            .hasMessage("Google email must be verified before signing in.");

        verify(userRepository, never()).save(any(UserEntity.class));
        verify(userAuthProviderRepository, never()).save(any(UserAuthProviderEntity.class));
    }

    @Test
    void connectGoogle_rejectsDifferentEmail() {
        UUID userId = UUID.randomUUID();
        UserEntity user = activeUser(userId, "student@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(googleIdentityTokenVerifier.verify("google-token")).thenReturn(new GoogleIdentityTokenVerifier.GoogleIdentity(
            "google-sub-1",
            "other@example.com",
            true,
            "Other User",
            "Other"
        ));

        GoogleConnectRequest request = new GoogleConnectRequest("google-token");
        assertThatThrownBy(() -> authService.connectGoogle(userId, request))
            .isInstanceOf(AppException.class)
            .hasMessage("This Google account uses a different email. Please use the same email as your NoteLib account.");

        verify(userAuthProviderRepository, never()).save(any(UserAuthProviderEntity.class));
    }

    @Test
    void connectGoogle_rejectsProviderAlreadyLinkedToAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserEntity user = activeUser(userId, "student@example.com");
        UserAuthProviderEntity existingProvider = new UserAuthProviderEntity();
        existingProvider.setUserId(otherUserId);
        existingProvider.setProvider(AuthProvider.GOOGLE);
        existingProvider.setProviderUserId("google-sub-1");
        existingProvider.setProviderEmail("student@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(googleIdentityTokenVerifier.verify("google-token")).thenReturn(new GoogleIdentityTokenVerifier.GoogleIdentity(
            "google-sub-1",
            "student@example.com",
            true,
            "Student One",
            "Student"
        ));
        when(userAuthProviderRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
            .thenReturn(Optional.of(existingProvider));

        GoogleConnectRequest request = new GoogleConnectRequest("google-token");
        assertThatThrownBy(() -> authService.connectGoogle(userId, request))
            .isInstanceOf(AppException.class)
            .hasMessage("This Google account is already connected to another NoteLib account.");

        verify(userAuthProviderRepository, never()).save(any(UserAuthProviderEntity.class));
    }

    @Test
    void login_throwsInvalidCredentialsException_whenUserDoesNotExist() {
        LoginRequest request = new LoginRequest("[email protected]", "password123", false);
        assertThatThrownBy(() -> authService.login(
            request,
            "127.0.0.1",
            "JUnit"
        )).isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email, username, or password.");
    }

    @Test
    void getMe_throwsUserNotFoundException_whenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getMe(userId))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessage("User not found.");
    }

    @Test
    void refresh_throwsInvalidRefreshTokenException_whenRefreshTokenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenService.requireValid("refresh-token"))
            .thenReturn(new RefreshTokenEntityBuilder().withUserId(userId).build());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
        assertThatThrownBy(() -> authService.refresh(
            request,
            "127.0.0.1",
            "JUnit"
        )).isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessage("Invalid refresh token.");
    }

    @Test
    void completeOnboarding_savesProfileTypeAndCompletionTimestamp() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setFirstName("Note");
        user.setDisplayName("note");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setEmailVerifiedAt(OffsetDateTime.parse("2026-03-24T08:00:00Z"));
        user.setEngagementMode(EngagementMode.FOCUSED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        MeResponse response = authService.completeOnboarding(
            userId,
            new CompleteOnboardingRequest(
                ProfileType.TEACHER,
                null
            )
        );

        assertThat(response.profileType()).isEqualTo(ProfileType.TEACHER);
        assertThat(response.learnerLevel()).isNull();
        assertThat(response.courseProgram()).isNull();
        assertThat(response.bio()).isNull();
        assertThat(response.engagementMode()).isEqualTo(EngagementMode.FOCUSED);
        assertThat(response.inactivityRemindersEnabled()).isFalse();
        assertThat(response.weakConceptRemindersEnabled()).isFalse();
        assertThat(response.onboardingCompletedAt()).isNotNull();
        assertThat(user.getOnboardingCompletedAt()).isNotNull();
    }

    @Test
    void completeOnboarding_savesOptionalExamDateForBoardExamUsers() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setFirstName("Note");
        user.setDisplayName("note");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setEmailVerifiedAt(OffsetDateTime.parse("2026-03-24T08:00:00Z"));
        user.setEngagementMode(EngagementMode.FOCUSED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        LocalDate examDate = LocalDate.of(2026, 10, 18);
        MeResponse response = authService.completeOnboarding(
            userId,
            new CompleteOnboardingRequest(
                ProfileType.BOARD_EXAM,
                examDate
            )
        );

        assertThat(response.profileType()).isEqualTo(ProfileType.BOARD_EXAM);
        assertThat(response.examDate()).isEqualTo(examDate);
        assertThat(user.getExamDate()).isEqualTo(examDate);
    }

    @Test
    void completeOnboarding_allowsBoardExamUsersWithoutExamDate() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setFirstName("Note");
        user.setDisplayName("note");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setEmailVerifiedAt(OffsetDateTime.parse("2026-03-24T08:00:00Z"));
        user.setEngagementMode(EngagementMode.FOCUSED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        MeResponse response = authService.completeOnboarding(
            userId,
            new CompleteOnboardingRequest(
                ProfileType.BOARD_EXAM,
                null
            )
        );

        assertThat(response.profileType()).isEqualTo(ProfileType.BOARD_EXAM);
        assertThat(response.examDate()).isNull();
        assertThat(user.getExamDate()).isNull();
    }

    @Test
    void updateThemePreference_savesThemeAndReturnsUpdatedProfile() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setFirstName("Note");
        user.setDisplayName("note");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setEngagementMode(EngagementMode.FOCUSED);
        user.setThemePreference(ThemePreference.SYSTEM);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        MeResponse response = authService.updateThemePreference(
            userId,
            new UpdateThemePreferenceRequest(ThemePreference.DARK)
        );

        assertThat(user.getThemePreference()).isEqualTo(ThemePreference.DARK);
        assertThat(response.themePreference()).isEqualTo(ThemePreference.DARK);
    }

    @Test
    void completeProductOnboarding_setsCompletionTimestamp() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setFirstName("Note");
        user.setDisplayName("note");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));
        when(studyPackRepository.countByOwnerUserId(userId)).thenReturn(0L);

        MeResponse response = authService.completeProductOnboarding(
            userId,
            new CompleteProductOnboardingRequest(false)
        );

        assertThat(response.productOnboardingCompletedAt()).isNotNull();
        assertThat(user.getProductOnboardingCompletedAt()).isNotNull();
    }

    @Test
    void updateStudyReminders_persistsReminderPreferences() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("[email protected]");
        user.setFirstName("Note");
        user.setDisplayName("note");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setEngagementMode(EngagementMode.CONSISTENCY);
        user.setInactivityRemindersEnabled(false);
        user.setWeakConceptRemindersEnabled(false);
        user.setWeeklySummaryRemindersEnabled(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        MeResponse response = authService.updateStudyReminders(
            userId,
            new UpdateStudyRemindersRequest(true, true, true)
        );

        assertThat(response.inactivityRemindersEnabled()).isTrue();
        assertThat(response.weakConceptRemindersEnabled()).isTrue();
        assertThat(response.weeklySummaryRemindersEnabled()).isTrue();
        assertThat(user.getInactivityRemindersEnabled()).isTrue();
        assertThat(user.getWeakConceptRemindersEnabled()).isTrue();
        assertThat(user.getWeeklySummaryRemindersEnabled()).isTrue();
    }

    @Test
    void updateUserProfile_updatesIdentityImmediatelyAndKeepsEmailWhenUnchanged() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setFirstName("Old");
        user.setLastName("Name");
        user.setDisplayName("Old Name");
        user.setUsername("studybuddy");
        user.setBio("Old bio");
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram("Biology");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        MeResponse response = authService.updateUserProfile(
            userId,
            new UpdateUserProfileRequest(
                "New",
                "Person",
                "Study Buddy",
                "studybuddy",
                "Focused on anatomy review.",
                LearnerLevel.BOARD_EXAM_REVIEW,
                "Pharmacy",
                "  NoteLib Academy  ",
                "current@example.com"
            )
        );

        assertThat(response.firstName()).isEqualTo("New");
        assertThat(response.lastName()).isEqualTo("Person");
        assertThat(response.email()).isEqualTo("current@example.com");
        assertThat(response.pendingEmail()).isNull();
        assertThat(response.bio()).isEqualTo("Focused on anatomy review.");
        assertThat(response.learnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW);
        assertThat(response.courseProgram()).isEqualTo("Pharmacy");
        assertThat(response.schoolName()).isEqualTo("NoteLib Academy");
        assertThat(response.username()).isEqualTo("studybuddy");
        assertThat(response.publicProfileVisible()).isFalse();
        assertThat(user.getDisplayName()).isEqualTo("Study Buddy");
        assertThat(user.getBio()).isEqualTo("Focused on anatomy review.");
        assertThat(user.getLearnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW);
        assertThat(user.getCourseProgram()).isEqualTo("Pharmacy");
        assertThat(user.getSchoolName()).isEqualTo("NoteLib Academy");
        verify(emailVerificationService, never()).sendVerificationEmail(any(UserEntity.class), eq(false));
    }

    @Test
    void updateUserProfile_storesPendingEmailAndSendsVerification() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setFirstName("Note");
        user.setLastName("User");
        user.setDisplayName("Note User");
        user.setUsername("notehero");
        user.setBio(null);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram("Biology");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setEmailVerifiedAt(OffsetDateTime.parse("2026-03-20T00:00:00Z"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCase("updated@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByPendingEmailIgnoreCase("updated@example.com")).thenReturn(Optional.empty());
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        MeResponse response = authService.updateUserProfile(
            userId,
            new UpdateUserProfileRequest(
                "Note",
                "User",
                "Note Hero",
                "notehero",
                "Weak areas: physiology and pharma.",
                LearnerLevel.PROFESSIONAL,
                "Medicine",
                null,
                "updated@example.com"
            )
        );

        assertThat(response.email()).isEqualTo("current@example.com");
        assertThat(response.pendingEmail()).isEqualTo("updated@example.com");
        assertThat(response.bio()).isEqualTo("Weak areas: physiology and pharma.");
        assertThat(response.learnerLevel()).isEqualTo(LearnerLevel.PROFESSIONAL);
        assertThat(response.courseProgram()).isEqualTo("Medicine");
        assertThat(response.username()).isEqualTo("notehero");
        assertThat(response.publicProfileVisible()).isFalse();
        assertThat(user.getPendingEmail()).isEqualTo("updated@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Note Hero");
        assertThat(user.getBio()).isEqualTo("Weak areas: physiology and pharma.");
        assertThat(user.getLearnerLevel()).isEqualTo(LearnerLevel.PROFESSIONAL);
        assertThat(user.getCourseProgram()).isEqualTo("Medicine");
        verify(emailVerificationService).sendVerificationEmail(user, false);
    }

    @Test
    void updateExamDate_savesDateAndReturnsUpdatedProfile() {
        UUID userId = UUID.randomUUID();
        UserEntity user = activeUser(userId, "current@example.com");
        user.setProfileType(ProfileType.BOARD_EXAM);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        MeResponse response = authService.updateExamDate(
            userId,
            new UpdateExamDateRequest(LocalDate.parse("2026-10-15"))
        );

        assertThat(response.examDate()).isEqualTo(LocalDate.parse("2026-10-15"));
        assertThat(user.getExamDate()).isEqualTo(LocalDate.parse("2026-10-15"));
    }

    @Test
    void updateExamDate_acceptsNullAndClearsDate() {
        UUID userId = UUID.randomUUID();
        UserEntity user = activeUser(userId, "current@example.com");
        user.setProfileType(ProfileType.BOARD_EXAM);
        user.setExamDate(LocalDate.parse("2026-10-15"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        MeResponse response = authService.updateExamDate(
            userId,
            new UpdateExamDateRequest(null)
        );

        assertThat(response.examDate()).isNull();
        assertThat(user.getExamDate()).isNull();
    }

    @Test
    void updateExamGoal_savesValidGoalAndReturnsUpdatedProfile() {
        UUID userId = UUID.randomUUID();
        UserEntity user = activeUser(userId, "current@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        MeResponse response = authService.updateStudyGoal(userId, new UpdateStudyGoalRequest(" ALE "));

        assertThat(response.studyGoal()).isEqualTo("ale");
        assertThat(user.getStudyGoal()).isEqualTo("ale");
    }

    @Test
    void updateExamGoal_acceptsCourseProgramGoal() {
        UUID userId = UUID.randomUUID();
        UserEntity user = activeUser(userId, "current@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        MeResponse response = authService.updateStudyGoal(userId, new UpdateStudyGoalRequest(" Mathematics "));

        assertThat(response.studyGoal()).isEqualTo("Mathematics");
        assertThat(user.getStudyGoal()).isEqualTo("Mathematics");
    }

    @Test
    void updateExamGoal_acceptsNullAndClearsGoal() {
        UUID userId = UUID.randomUUID();
        UserEntity user = activeUser(userId, "current@example.com");
        user.setStudyGoal("pnle");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        MeResponse response = authService.updateStudyGoal(userId, new UpdateStudyGoalRequest(null));

        assertThat(response.studyGoal()).isNull();
        assertThat(user.getStudyGoal()).isNull();
    }

    @Test
    void updateExamGoal_rejectsBlankGoal() {
        UUID userId = UUID.randomUUID();
        UserEntity user = activeUser(userId, "current@example.com");
        UpdateStudyGoalRequest request = new UpdateStudyGoalRequest("  ");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.updateStudyGoal(userId, request))
            .isInstanceOf(InvalidGoalException.class)
            .hasMessage("Goal cannot be blank. Pass null to clear your goal.");
    }

    @Test
    void updateExamGoal_rejectsTooLongGoal() {
        UUID userId = UUID.randomUUID();
        UserEntity user = activeUser(userId, "current@example.com");
        UpdateStudyGoalRequest request = new UpdateStudyGoalRequest("A".repeat(101));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.updateStudyGoal(userId, request))
            .isInstanceOf(InvalidGoalException.class)
            .hasMessage("Goal value is too long.");
    }

    @Test
    void updatePublicProfileVisibility_persistsVisibilitySetting() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setFirstName("Note");
        user.setDisplayName("Note");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setPublicProfileVisible(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.getPlanSnapshot(userId))
            .thenReturn(new SubscriptionService.PlanSnapshot(
                PlanType.FREE,
                false,
                null,
                null
            ));

        MeResponse response = authService.updatePublicProfileVisibility(
            userId,
            new UpdatePublicProfileVisibilityRequest(false)
        );

        assertThat(response.publicProfileVisible()).isFalse();
        assertThat(user.getPublicProfileVisible()).isFalse();
    }

    @Test
    void updateUserProfile_rejectsReservedDisplayName() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setFirstName("Note");
        user.setLastName("User");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
            "Note",
            "User",
            "NoteLib Support",
            "notesupport",
            null,
            LearnerLevel.COLLEGE,
            "Biology",
            null,
            "current@example.com"
        );
        assertThatThrownBy(() -> authService.updateUserProfile(
            userId,
            request
        ))
            .isInstanceOf(AppException.class)
            .extracting(Throwable::getMessage)
            .isEqualTo("This display name is reserved. Please choose another name.");
    }

    @Test
    void updateUserProfile_rejectsInvalidUsernameFormat() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setFirstName("Note");
        user.setDisplayName("Note");
        user.setUsername("noteuser");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
            "Note",
            "User",
            "Note User",
            "note user!",
            null,
            LearnerLevel.COLLEGE,
            "Biology",
            null,
            "current@example.com"
        );

        assertThatThrownBy(() -> authService.updateUserProfile(userId, request))
            .isInstanceOf(AppException.class)
            .extracting(Throwable::getMessage)
            .isEqualTo("Username can only contain letters, numbers, underscores, or hyphens.");
    }

    @Test
    void updateUserProfile_rejectsDuplicateUsername() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setFirstName("Note");
        user.setDisplayName("Note");
        user.setUsername("noteuser");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        UserEntity otherUser = new UserEntity();
        otherUser.setId(otherUserId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByUsernameIgnoreCase("takenname")).thenReturn(Optional.of(otherUser));

        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
            "Note",
            "User",
            "Note User",
            "takenname",
            null,
            LearnerLevel.COLLEGE,
            "Biology",
            null,
            "current@example.com"
        );

        assertThatThrownBy(() -> authService.updateUserProfile(userId, request))
            .isInstanceOf(AppException.class)
            .extracting(Throwable::getMessage)
            .isEqualTo("Username is already taken.");
    }

    @Test
    void requestEmailVerification_allowsResendForPendingEmailChange() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("current@example.com");
        user.setPendingEmail("updated@example.com");
        user.setFirstName("Note");
        user.setDisplayName("Note");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setEmailVerifiedAt(OffsetDateTime.parse("2026-03-20T00:00:00Z"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(authService.requestEmailVerification(userId).message())
            .isEqualTo("Verification email sent. Please check your inbox.");

        verify(emailVerificationService).sendVerificationEmail(user, true);
    }

    private UserEntity activeUser(UUID userId, String email) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(email);
        user.setFirstName("Student");
        user.setDisplayName("Student One");
        user.setUsername("studentone");
        user.setPasswordHash("hashed");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        user.setEmailVerifiedAt(OffsetDateTime.parse("2026-03-20T00:00:00Z"));
        user.setEngagementMode(EngagementMode.FOCUSED);
        user.setThemePreference(ThemePreference.SYSTEM);
        user.setPublicProfileVisible(true);
        user.setInactivityRemindersEnabled(false);
        user.setWeakConceptRemindersEnabled(false);
        return user;
    }

    private static final class RefreshTokenEntityBuilder {

        private UUID userId;

        private RefreshTokenEntityBuilder withUserId(UUID value) {
            this.userId = value;
            return this;
        }

        private com.studysnap.backend.entity.RefreshTokenEntity build() {
            com.studysnap.backend.entity.RefreshTokenEntity entity = new com.studysnap.backend.entity.RefreshTokenEntity();
            entity.setUserId(userId);
            return entity;
        }
    }
}
