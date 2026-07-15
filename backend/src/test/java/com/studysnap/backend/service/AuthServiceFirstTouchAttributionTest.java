package com.studysnap.backend.service;

import com.studysnap.backend.dto.GoogleAuthRequest;
import com.studysnap.backend.dto.SignupRequest;
import com.studysnap.backend.entity.AuthProvider;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.UserAuthProviderEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserAuthProviderRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.security.JwtService;
import com.studysnap.backend.security.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceFirstTouchAttributionTest {

    private static final String GOOGLE_TOKEN = "google-token";
    private static final String GOOGLE_SUBJECT = "google-subject";
    private static final String GOOGLE_EMAIL = "student@example.com";
    private static final String CLIENT_IP = "127.0.0.1";
    private static final String USER_AGENT = "JUnit";

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
        when(jwtService.generateAccessToken(any(UserEntity.class))).thenReturn("access-token");
        when(jwtService.resolveAccessTokenExpiry()).thenReturn(OffsetDateTime.now().plusMinutes(15));
        when(refreshTokenService.issue(any(UserEntity.class), any(Boolean.class), any(), any(), any()))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", OffsetDateTime.now().plusDays(7)));
    }

    @Test
    void signup_persistsFirstTouchAttribution() {
        when(userRepository.existsByEmailIgnoreCase("note@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("note")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.signup(signupRequest(), CLIENT_IP, USER_AGENT);

        UserEntity savedUser = capturedSavedUser();
        assertThat(savedUser.getUtmSource()).isEqualTo("instagram");
        assertThat(savedUser.getUtmMedium()).isEqualTo("paid_social");
        assertThat(savedUser.getUtmCampaign()).isEqualTo("board_review");
        assertThat(savedUser.getUtmContent()).isEqualTo("story");
        assertThat(savedUser.getUtmTerm()).isEqualTo("pnle");
        assertThat(savedUser.getReferrer()).isEqualTo("https://example.com/campaign");
    }

    @Test
    void signup_withoutAttributionLeavesFirstTouchFieldsNull() {
        when(userRepository.existsByEmailIgnoreCase("note@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("note")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.signup(new SignupRequest("note@example.com", "password123", "Note", "note"), CLIENT_IP, USER_AGENT);

        UserEntity savedUser = capturedSavedUser();
        assertThat(savedUser.getUtmSource()).isNull();
        assertThat(savedUser.getUtmMedium()).isNull();
        assertThat(savedUser.getUtmCampaign()).isNull();
        assertThat(savedUser.getUtmContent()).isNull();
        assertThat(savedUser.getUtmTerm()).isNull();
        assertThat(savedUser.getReferrer()).isNull();
    }

    @Test
    void googleSignup_persistsFirstTouchAttribution() {
        stubGoogleIdentity();
        when(userAuthProviderRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, GOOGLE_SUBJECT))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(GOOGLE_EMAIL)).thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase("student")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAuthProviderRepository.findByUserIdAndProvider(any(UUID.class), eq(AuthProvider.GOOGLE)))
                .thenReturn(Optional.empty());
        when(subscriptionService.resolvePlan(any(UUID.class))).thenReturn(PlanType.FREE);

        authService.loginWithGoogle(googleRequest(), CLIENT_IP, USER_AGENT);

        UserEntity savedUser = capturedSavedUser();
        assertThat(savedUser.getUtmSource()).isEqualTo("instagram");
        assertThat(savedUser.getUtmMedium()).isEqualTo("paid_social");
        assertThat(savedUser.getUtmCampaign()).isEqualTo("board_review");
        assertThat(savedUser.getUtmContent()).isEqualTo("story");
        assertThat(savedUser.getUtmTerm()).isEqualTo("pnle");
        assertThat(savedUser.getReferrer()).isEqualTo("https://example.com/campaign");
    }

    @Test
    void googleLogin_doesNotOverwriteExistingFirstTouchAttribution() {
        UUID userId = UUID.randomUUID();
        UserEntity existingUser = activeUser(userId);
        existingUser.setUtmSource("existing-source");
        existingUser.setReferrer("https://first.example.com");
        UserAuthProviderEntity provider = new UserAuthProviderEntity();
        provider.setUserId(userId);
        provider.setProvider(AuthProvider.GOOGLE);
        provider.setProviderUserId(GOOGLE_SUBJECT);

        stubGoogleIdentity();
        when(userAuthProviderRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, GOOGLE_SUBJECT))
                .thenReturn(Optional.of(provider));
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);

        authService.loginWithGoogle(googleRequest(), CLIENT_IP, USER_AGENT);

        assertThat(existingUser.getUtmSource()).isEqualTo("existing-source");
        assertThat(existingUser.getReferrer()).isEqualTo("https://first.example.com");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    private SignupRequest signupRequest() {
        return new SignupRequest(
                "note@example.com",
                "password123",
                "Note",
                "note",
                "instagram",
                "paid_social",
                "board_review",
                "story",
                "pnle",
                "https://example.com/campaign"
        );
    }

    private GoogleAuthRequest googleRequest() {
        return new GoogleAuthRequest(
                GOOGLE_TOKEN,
                false,
                "instagram",
                "paid_social",
                "board_review",
                "story",
                "pnle",
                "https://example.com/campaign"
        );
    }

    private UserEntity capturedSavedUser() {
        ArgumentCaptor<UserEntity> savedUser = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(savedUser.capture());
        return savedUser.getValue();
    }

    private void stubGoogleIdentity() {
        when(googleIdentityTokenVerifier.verify(GOOGLE_TOKEN)).thenReturn(new GoogleIdentityTokenVerifier.GoogleIdentity(
                GOOGLE_SUBJECT,
                GOOGLE_EMAIL,
                true,
                "Student",
                "Student"
        ));
    }

    private UserEntity activeUser(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(GOOGLE_EMAIL);
        user.setDisplayName("Student");
        user.setUsername("student");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(0);
        user.setFailedLoginAttempts(0);
        return user;
    }
}
