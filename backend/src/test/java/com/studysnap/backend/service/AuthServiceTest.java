package com.studysnap.backend.service;

import com.studysnap.backend.dto.AuthResponse;
import com.studysnap.backend.dto.CompleteOnboardingRequest;
import com.studysnap.backend.dto.CompleteProductOnboardingRequest;
import com.studysnap.backend.dto.LoginRequest;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.SignupRequest;
import com.studysnap.backend.dto.UpdateStudyRemindersRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.InvalidCredentialsException;
import com.studysnap.backend.exception.InvalidRefreshTokenException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.security.JwtService;
import com.studysnap.backend.security.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
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
    private EmailVerificationService emailVerificationService;
    @Mock
    private AnalyticsService analyticsService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                studyPackRepository,
                subscriptionService,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                securityProperties,
                emailVerificationService,
                analyticsService
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
        assertThat(response.onboardingCompletedAt()).isNull();
        assertThat(response.productOnboardingCompletedAt()).isNull();
        verify(subscriptionService).createDefaultFreeSubscription(any(UserEntity.class));
        verify(emailVerificationService).sendVerificationEmail(any(UserEntity.class), eq(false));
        verify(analyticsService).trackEvent(any(UUID.class), eq(AnalyticsEventType.SIGNUP), any(UUID.class), any());
        verify(analyticsService).trackEvent(any(UUID.class), eq(AnalyticsEventType.SIGNUP_COMPLETED), any(UUID.class), any());
        verify(analyticsService).trackEvent(any(UUID.class), eq(AnalyticsEventType.EMAIL_VERIFICATION_SENT), any(UUID.class), any());
    }

    @Test
    void login_tracksLoginEvent() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("[email protected]");
        user.setDisplayName("note");
        user.setPasswordHash("hashed");
        user.setRole(com.studysnap.backend.entity.UserRole.USER);
        user.setStatus(com.studysnap.backend.entity.UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);

        when(userRepository.findByEmailIgnoreCase("[email protected]")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        AuthResponse response = authService.login(
                new LoginRequest("[email protected]", "password123", true),
                "127.0.0.1",
                "JUnit"
        );

        assertThat(response.email()).isEqualTo("[email protected]");
        verify(analyticsService).trackEvent(userId, AnalyticsEventType.LOGIN, userId, java.util.Map.of(
                "keepSignedIn", true
        ));
    }

    @Test
    void login_throwsInvalidCredentialsException_whenUserDoesNotExist() {
        when(userRepository.findByEmailIgnoreCase("[email protected]")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("[email protected]", "password123", false),
                "127.0.0.1",
                "JUnit"
        )).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");
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

        assertThatThrownBy(() -> authService.refresh(
                new com.studysnap.backend.dto.RefreshTokenRequest("refresh-token"),
                "127.0.0.1",
                "JUnit"
        )).isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Invalid refresh token.");
    }

    @Test
    void completeOnboarding_savesProfileTypeLearningStyleAndCompletionTimestamp() {
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
                new CompleteOnboardingRequest(ProfileType.TEACHER, EngagementMode.STREAK)
        );

        assertThat(response.profileType()).isEqualTo(ProfileType.TEACHER);
        assertThat(response.engagementMode()).isEqualTo(EngagementMode.STREAK);
        assertThat(response.onboardingCompletedAt()).isNotNull();
        assertThat(user.getOnboardingCompletedAt()).isNotNull();
    }

    @Test
    void completeProductOnboarding_setsCompletionTimestamp() {
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
                new UpdateStudyRemindersRequest(true, true)
        );

        assertThat(response.inactivityRemindersEnabled()).isTrue();
        assertThat(response.weakConceptRemindersEnabled()).isTrue();
        assertThat(user.getInactivityRemindersEnabled()).isTrue();
        assertThat(user.getWeakConceptRemindersEnabled()).isTrue();
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
