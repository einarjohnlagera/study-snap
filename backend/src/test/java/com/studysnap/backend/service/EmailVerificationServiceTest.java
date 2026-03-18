package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.EmailVerificationTokenEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.EmailVerificationTokenRepository;
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

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getEmail().setAppBaseUrl("https://app.notelib.test");
        properties.getEmail().setVerificationTokenHours(24);
        properties.getEmail().setResendCooldownSeconds(60);
        service = new EmailVerificationService(tokenRepository, userRepository, emailService, emailTemplateService, properties);
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
        assertThat(templateParams.get("app_name")).isEqualTo("NoteLib");

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

        EmailVerificationService.EmailVerificationResult result = service.verifyToken(rawToken);

        assertThat(result.userId()).isEqualTo(user.getId());
        assertThat(result.alreadyVerified()).isFalse();
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(token.getUsedAt()).isNotNull();
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
