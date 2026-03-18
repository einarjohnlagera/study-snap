package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.EmailVerificationTokenEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.EmailVerificationTokenRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
public class EmailVerificationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final StudySnapProperties properties;

    public void sendVerificationEmail(UserEntity user, boolean enforceCooldown) {
        if (user.getEmailVerifiedAt() != null) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (enforceCooldown) {
            enforceResendCooldown(user.getId(), now);
        }

        invalidateActiveTokens(user.getId(), now);

        String rawToken = generateRawToken();
        EmailVerificationTokenEntity tokenEntity = new EmailVerificationTokenEntity();
        tokenEntity.setId(UUID.randomUUID());
        tokenEntity.setUserId(user.getId());
        tokenEntity.setTokenHash(hash(rawToken));
        tokenEntity.setCreatedAt(now);
        tokenEntity.setExpiresAt(now.plusHours(Math.max(1, properties.getEmail().getVerificationTokenHours())));
        emailVerificationTokenRepository.save(tokenEntity);

        String verificationUrl = buildVerificationUrl(rawToken);
        EmailTemplateService.RenderedEmailTemplate renderedTemplate = emailTemplateService.render(
                "verification-email",
                Map.of(
                        "app_name", resolveAppName(),
                        "verification_url", verificationUrl,
                        "email_from", resolveEmailFrom(),
                        "expiration_text", resolveExpirationText()
                )
        );

        emailService.sendEmail(new EmailMessage(
                user.getEmail(),
                renderedTemplate.subject(),
                renderedTemplate.htmlBody(),
                renderedTemplate.textBody()
        ));
    }

    public EmailVerificationResult verifyToken(String rawToken) {
        String normalizedRawToken = normalizeRawToken(rawToken);
        OffsetDateTime now = OffsetDateTime.now();

        EmailVerificationTokenEntity token = emailVerificationTokenRepository.findByTokenHash(hash(normalizedRawToken))
                .orElseThrow(this::invalidTokenException);
        UserEntity user = userRepository.findById(token.getUserId())
                .orElseThrow(this::invalidTokenException);

        if (user.getEmailVerifiedAt() != null) {
            markTokenAsUsed(token, now);
            return new EmailVerificationResult(
                    user.getId(),
                    true,
                    "Your email is already verified."
            );
        }

        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw invalidTokenException();
        }

        user.setEmailVerifiedAt(now);
        user.setUpdatedAt(now);
        markTokenAsUsed(token, now);
        markOtherActiveTokensAsUsed(user.getId(), token.getId(), now);

        return new EmailVerificationResult(
                user.getId(),
                false,
                "Email verified successfully. You can continue in NoteLib."
        );
    }

    private void enforceResendCooldown(UUID userId, OffsetDateTime now) {
        Optional<EmailVerificationTokenEntity> latest = emailVerificationTokenRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
        if (latest.isEmpty()) {
            return;
        }

        int cooldownSeconds = Math.max(1, properties.getEmail().getResendCooldownSeconds());
        OffsetDateTime nextAllowedAt = latest.get().getCreatedAt().plusSeconds(cooldownSeconds);
        if (nextAllowedAt.isAfter(now)) {
            throw new AppException(
                    "VERIFICATION_EMAIL_COOLDOWN",
                    "Please wait before requesting another verification email.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    private void invalidateActiveTokens(UUID userId, OffsetDateTime now) {
        List<EmailVerificationTokenEntity> activeTokens = emailVerificationTokenRepository.findByUserIdAndUsedAtIsNull(userId);
        for (EmailVerificationTokenEntity activeToken : activeTokens) {
            activeToken.setUsedAt(now);
        }
    }

    private void markTokenAsUsed(EmailVerificationTokenEntity token, OffsetDateTime usedAt) {
        if (token.getUsedAt() == null) {
            token.setUsedAt(usedAt);
        }
    }

    private void markOtherActiveTokensAsUsed(UUID userId, UUID currentTokenId, OffsetDateTime now) {
        List<EmailVerificationTokenEntity> activeTokens = emailVerificationTokenRepository.findByUserIdAndUsedAtIsNull(userId);
        for (EmailVerificationTokenEntity activeToken : activeTokens) {
            if (!activeToken.getId().equals(currentTokenId)) {
                activeToken.setUsedAt(now);
            }
        }
    }

    private String buildVerificationUrl(String rawToken) {
        String baseUrl = resolveAppBaseUrl();
        return baseUrl + "/verify-email?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
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
        int hours = Math.max(1, properties.getEmail().getVerificationTokenHours());
        if (hours == 1) {
            return "This verification link expires in 1 hour.";
        }
        return "This verification link expires in " + hours + " hours.";
    }

    private String resolveEmailFrom() {
        String emailFrom = properties.getEmail().getFrom();
        if (emailFrom == null) {
            return "";
        }
        return emailFrom.trim();
    }

    private String resolveAppName() {
        String appName = properties.getAppName();
        if (appName == null || appName.isBlank()) {
            return "NoteLib";
        }
        return appName.trim();
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

    private String normalizeRawToken(String rawToken) {
        if (rawToken == null) {
            throw invalidTokenException();
        }
        String normalized = rawToken.trim();
        if (normalized.isEmpty()) {
            throw invalidTokenException();
        }
        return normalized;
    }

    private AppException invalidTokenException() {
        return new AppException(
                "INVALID_VERIFICATION_TOKEN",
                "This verification link is invalid or has expired.",
                HttpStatus.BAD_REQUEST
        );
    }

    public record EmailVerificationResult(
            UUID userId,
            boolean alreadyVerified,
            String message
    ) {
    }
}
