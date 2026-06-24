package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.PasswordResetTokenEntity;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.exception.InvalidPasswordResetTokenException;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.PasswordResetTokenRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String GENERIC_SUCCESS_MESSAGE = "If this email is registered, you will receive a reset link shortly.";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final StudySnapProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final EmailLogRepository emailLogRepository;

    public SimpleMessageResponse sendPasswordResetEmail(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();

        Optional<UserEntity> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (userOpt.isEmpty()) {
            return new SimpleMessageResponse(GENERIC_SUCCESS_MESSAGE);
        }

        UserEntity user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            return new SimpleMessageResponse(GENERIC_SUCCESS_MESSAGE);
        }

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            return new SimpleMessageResponse(GENERIC_SUCCESS_MESSAGE);
        }

        OffsetDateTime now = OffsetDateTime.now();
        invalidateActiveTokens(user.getId(), now);

        String rawToken = generateRawToken();
        PasswordResetTokenEntity tokenEntity = new PasswordResetTokenEntity();
        tokenEntity.setId(UUID.randomUUID());
        tokenEntity.setUserId(user.getId());
        tokenEntity.setTokenHash(hash(rawToken));
        tokenEntity.setCreatedAt(now);
        tokenEntity.setExpiresAt(now.plusMinutes(Math.max(1, properties.getEmail().getPasswordResetTokenMinutes())));
        passwordResetTokenRepository.save(tokenEntity);

        String resetUrl = buildResetUrl(rawToken);
        EmailTemplateService.RenderedEmailTemplate renderedTemplate = emailTemplateService.render(
                "password-reset-email",
                Map.of(
                        "firstName", resolveFirstName(user),
                        "reset_url", resetUrl,
                        "expiration_text", resolveExpirationText()
                )
        );

        boolean sent = emailService.sendEmail(new EmailMessage(
                user.getEmail(),
                renderedTemplate.subject(),
                renderedTemplate.htmlBody(),
                renderedTemplate.textBody()
        ));
        if (sent) {
            logEmailSent(user.getId(), now);
        }

        return new SimpleMessageResponse(GENERIC_SUCCESS_MESSAGE);
    }

    public SimpleMessageResponse resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidPasswordResetTokenException();
        }

        OffsetDateTime now = OffsetDateTime.now();
        PasswordResetTokenEntity token = passwordResetTokenRepository.findByTokenHash(hash(rawToken.trim()))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidPasswordResetTokenException();
        }

        UserEntity user = userRepository.findById(token.getUserId())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidPasswordResetTokenException();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
        }
        user.setUpdatedAt(now);

        token.setUsedAt(now);
        invalidateOtherActiveTokens(user.getId(), token.getId(), now);

        return new SimpleMessageResponse("Your password has been reset. You can now log in.");
    }

    private void invalidateActiveTokens(UUID userId, OffsetDateTime now) {
        List<PasswordResetTokenEntity> activeTokens = passwordResetTokenRepository.findByUserIdAndUsedAtIsNull(userId);
        for (PasswordResetTokenEntity activeToken : activeTokens) {
            activeToken.setUsedAt(now);
        }
    }

    private void invalidateOtherActiveTokens(UUID userId, UUID currentTokenId, OffsetDateTime now) {
        List<PasswordResetTokenEntity> activeTokens = passwordResetTokenRepository.findByUserIdAndUsedAtIsNull(userId);
        for (PasswordResetTokenEntity activeToken : activeTokens) {
            if (!activeToken.getId().equals(currentTokenId)) {
                activeToken.setUsedAt(now);
            }
        }
    }

    private String buildResetUrl(String rawToken) {
        String baseUrl = resolveAppBaseUrl();
        return baseUrl + "/reset-password?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String resolveAppBaseUrl() {
        String configured = properties.getEmail().getAppBaseUrl();
        if (configured == null || configured.isBlank()) {
            return "http://localhost:3000";
        }
        String normalized = configured.trim();
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String resolveExpirationText() {
        int minutes = Math.max(1, properties.getEmail().getPasswordResetTokenMinutes());
        if (minutes < 60) {
            return "This link expires in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".";
        }
        int hours = minutes / 60;
        return "This link expires in " + hours + " hour" + (hours == 1 ? "" : "s") + ".";
    }

    private String resolveFirstName(UserEntity user) {
        if (user.getFirstName() == null || user.getFirstName().isBlank()) {
            return "there";
        }
        return user.getFirstName().trim();
    }

    private void logEmailSent(UUID userId, OffsetDateTime now) {
        EmailLogEntity entity = new EmailLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setEmailType(RetentionEmailType.PASSWORD_RESET);
        entity.setSentAt(now);
        emailLogRepository.save(entity);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
