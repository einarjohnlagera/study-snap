package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.EmailVerificationTokenEntity;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.EmailVerificationTokenRepository;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.testutil.builders.UserEntityBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationTokenRepository tokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private EmailLogRepository emailLogRepository;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getEmail().setAppBaseUrl("https://app.notelib.test");
        properties.getEmail().setVerificationTokenHours(24);
        properties.getEmail().setResendCooldownSeconds(60);
        service = new EmailVerificationService(tokenRepository, userRepository, emailService, emailTemplateService, emailLogRepository, properties);
    }

    @Test
    void sendVerificationEmail_createsTokenAndSendsEmail() {
        UserEntity user = UserEntityBuilder.aUser().build();
        user.setEmailVerifiedAt(null);
        when(tokenRepository.findByUserIdAndUsedAtIsNull(user.getId())).thenReturn(List.of());
        when(tokenRepository.save(any(EmailVerificationTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(emailTemplateService.render(any(), any())).thenReturn(new EmailTemplateService.RenderedEmailTemplate(
                "Verify your email for NoteLib",
                "<p>Verify</p>",
                "Verify"
        ));

        service.sendVerificationEmail(user, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateService).render(any(), paramsCaptor.capture());
        Map<String, String> templateParams = paramsCaptor.getValue();
        assertThat(templateParams.get("verification_url")).startsWith("https://app.notelib.test/verify-email?token=");
        assertThat(templateParams).containsEntry("app_name", "NoteLib");

        ArgumentCaptor<EmailVerificationTokenEntity> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationTokenEntity.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        EmailVerificationTokenEntity savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getId()).isNotNull();
        assertThat(savedToken.getUserId()).isEqualTo(user.getId());
        assertThat(savedToken.getTokenHash()).isNotBlank();
        assertThat(savedToken.getExpiresAt()).isAfter(savedToken.getCreatedAt());

        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).sendEmail(emailCaptor.capture());
        EmailMessage sentEmail = emailCaptor.getValue();
        assertThat(sentEmail.to()).isEqualTo(user.getEmail());
        assertThat(sentEmail.subject()).isEqualTo("Verify your email for NoteLib");
        assertThat(sentEmail.htmlBody()).isEqualTo("<p>Verify</p>");
    }

    @Test
    void sendVerificationEmail_enforcesResendCooldown() {
        UserEntity user = UserEntityBuilder.aUser().build();
        user.setEmailVerifiedAt(null);
        EmailVerificationTokenEntity latest = new EmailVerificationTokenEntity();
        latest.setId(UUID.randomUUID());
        latest.setUserId(user.getId());
        latest.setCreatedAt(OffsetDateTime.now().minusSeconds(30));
        when(tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.sendVerificationEmail(user, true))
                .isInstanceOf(AppException.class)
                .hasMessage("Please wait before requesting another verification email.")
                .extracting(ex -> ((AppException) ex).getCode())
                .isEqualTo("VERIFICATION_EMAIL_COOLDOWN");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void sendVerificationEmail_sendsToPendingEmailWhenPresent() {
        UserEntity user = UserEntityBuilder.aUser().build();
        user.setEmailVerifiedAt(OffsetDateTime.now().minusDays(1));
        user.setPendingEmail("updated@example.com");
        when(tokenRepository.findByUserIdAndUsedAtIsNull(user.getId())).thenReturn(List.of());
        when(tokenRepository.save(any(EmailVerificationTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(emailTemplateService.render(any(), any())).thenReturn(new EmailTemplateService.RenderedEmailTemplate(
                "Verify your email for NoteLib",
                "<p>Verify</p>",
                "Verify"
        ));

        service.sendVerificationEmail(user, false);

        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).sendEmail(emailCaptor.capture());
        assertThat(emailCaptor.getValue().to()).isEqualTo("updated@example.com");
    }

    @Test
    void verifyToken_marksUserAsVerified() {
        UserEntity user = UserEntityBuilder.aUser().build();
        user.setEmailVerifiedAt(null);
        user.setUpdatedAt(OffsetDateTime.now().minusDays(1));

        String rawToken = "verify-token";
        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity();
        token.setId(UUID.randomUUID());
        token.setUserId(user.getId());
        token.setUsedAt(null);
        token.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        token.setExpiresAt(OffsetDateTime.now().plusHours(1));
        token.setTokenHash(hash(rawToken));

        when(tokenRepository.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(token));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserIdAndUsedAtIsNull(user.getId())).thenReturn(List.of(token));
        when(emailLogRepository.findTopByUserIdAndEmailTypeOrderBySentAtDesc(user.getId(), RetentionEmailType.WELCOME))
                .thenReturn(Optional.empty());
        when(emailTemplateService.render(any(), any())).thenReturn(new EmailTemplateService.RenderedEmailTemplate(
                "Welcome to NoteLib — Start your first Study Pack",
                "<p>Welcome</p>",
                "Welcome"
        ));

        EmailVerificationService.EmailVerificationResult result = service.verifyToken(rawToken);

        assertThat(result.userId()).isEqualTo(user.getId());
        assertThat(result.alreadyVerified()).isFalse();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(token.getUsedAt()).isNotNull();
        verify(emailService).sendEmail(any(EmailMessage.class));
        verify(emailLogRepository).save(any(EmailLogEntity.class));
    }

    @Test
    void verifyToken_updatesPendingEmailWithoutSendingWelcomeAgain() {
        UserEntity user = UserEntityBuilder.aUser().build();
        user.setEmail("current@example.com");
        user.setPendingEmail("updated@example.com");
        user.setEmailVerifiedAt(OffsetDateTime.now().minusDays(2));
        user.setUpdatedAt(OffsetDateTime.now().minusDays(1));

        String rawToken = "pending-email-token";
        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity();
        token.setId(UUID.randomUUID());
        token.setUserId(user.getId());
        token.setUsedAt(null);
        token.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        token.setExpiresAt(OffsetDateTime.now().plusHours(1));
        token.setTokenHash(hash(rawToken));

        when(tokenRepository.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(token));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserIdAndUsedAtIsNull(user.getId())).thenReturn(List.of(token));

        EmailVerificationService.EmailVerificationResult result = service.verifyToken(rawToken);

        assertThat(result.alreadyVerified()).isFalse();
        assertThat(result.message()).isEqualTo("Your new email address has been verified and updated.");
        assertThat(user.getEmail()).isEqualTo("updated@example.com");
        assertThat(user.getPendingEmail()).isNull();
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void sendWelcomeEmail_doesNotSendTwice() {
        UserEntity user = UserEntityBuilder.aUser().build();
        user.setEmailVerifiedAt(OffsetDateTime.now());
        EmailLogEntity existing = new EmailLogEntity();
        existing.setId(UUID.randomUUID());
        existing.setUserId(user.getId());
        existing.setEmailType(RetentionEmailType.WELCOME);
        existing.setSentAt(OffsetDateTime.now().minusDays(1));

        when(emailLogRepository.findTopByUserIdAndEmailTypeOrderBySentAtDesc(user.getId(), RetentionEmailType.WELCOME))
                .thenReturn(Optional.of(existing));

        service.sendWelcomeEmail(user);

        verify(emailTemplateService, never()).render(any(), any());
        verify(emailService, never()).sendEmail(any());
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void sendWelcomeEmail_includesDashboardLink() {
        UserEntity user = UserEntityBuilder.aUser().build();
        user.setFirstName("Note");
        user.setEmailVerifiedAt(OffsetDateTime.now());

        when(emailLogRepository.findTopByUserIdAndEmailTypeOrderBySentAtDesc(user.getId(), RetentionEmailType.WELCOME))
                .thenReturn(Optional.empty());
        when(emailTemplateService.render(any(), any())).thenReturn(new EmailTemplateService.RenderedEmailTemplate(
                "Welcome to NoteLib — Start your first Study Pack",
                "<p>Welcome</p>",
                "Welcome"
        ));

        service.sendWelcomeEmail(user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateService).render(any(), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue()).containsEntry("dashboard_url", "https://app.notelib.test/dashboard");
    }

    private String hash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
