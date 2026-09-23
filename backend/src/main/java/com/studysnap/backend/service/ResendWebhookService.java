package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.exception.InvalidResendWebhookSignatureException;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.EmailOpenDailyCountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResendWebhookService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String RESEND_SECRET_PREFIX = "whsec_";
    private static final String SIGNATURE_VERSION_PREFIX = "v1,";
    private static final String EVENT_BOUNCED = "email.bounced";
    private static final String EVENT_COMPLAINED = "email.complained";
    private static final String EVENT_SUPPRESSED = "email.suppressed";
    private static final String EVENT_CLICKED = "email.clicked";
    private static final String EVENT_OPENED = "email.opened";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_CREATED_AT = "created_at";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_TO = "to";
    private static final String FIELD_CLICK = "click";
    private static final String FIELD_LINK = "link";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String EMAIL_LOG_ID_PARAMETER = "e";
    private static final String SOURCE_PARAMETER = "source";
    private static final long SIGNATURE_TOLERANCE_SECONDS = 300L;
    private static final Map<String, RetentionEmailType> RETENTION_TYPE_BY_SOURCE = Map.of(
            "inactivity", RetentionEmailType.INACTIVITY,
            "weak-concept", RetentionEmailType.WEAK_CONCEPT,
            "weekly-summary", RetentionEmailType.WEEKLY_SUMMARY,
            "due-concepts-digest", RetentionEmailType.DUE_CONCEPTS_DIGEST,
            "knowledge-impact-digest", RetentionEmailType.KNOWLEDGE_IMPACT_DIGEST
    );

    private final StudySnapProperties properties;
    private final ObjectMapper objectMapper;
    private final SuppressedEmailService suppressedEmailService;
    private final EmailLogRepository emailLogRepository;
    private final EmailOpenDailyCountRepository emailOpenDailyCountRepository;

    @Transactional
    public void handleWebhook(String payload, String svixId, String svixTimestamp, String svixSignature) {
        verifySignature(payload, svixId, svixTimestamp, svixSignature);

        JsonNode event = parsePayload(payload);
        String eventType = readText(event, FIELD_TYPE);
        if (EVENT_CLICKED.equals(eventType)) {
            handleClicked(event);
            return;
        }
        if (EVENT_OPENED.equals(eventType)) {
            handleOpened(event);
            return;
        }
        if (!isSuppressionEvent(eventType)) {
            return;
        }

        JsonNode recipients = event.path(FIELD_DATA).path(FIELD_TO);
        if (!recipients.isArray()) {
            log.warn("email.resend.webhook.suppression missingRecipients eventType={}", eventType);
            return;
        }
        for (JsonNode recipient : recipients) {
            String address = recipient.asText(null);
            suppressedEmailService.suppress(address, eventType);
        }
    }

    private void handleClicked(JsonNode event) {
        JsonNode click = event.path(FIELD_DATA).path(FIELD_CLICK);
        String link = readText(click, FIELD_LINK);
        String timestamp = readText(click, FIELD_TIMESTAMP);
        Optional<ClickCorrelation> correlation = parseClickCorrelation(link, timestamp);
        if (correlation.isEmpty()) {
            log.warn("email.resend.webhook.click malformedPayload");
            return;
        }

        ClickCorrelation clickCorrelation = correlation.get();
        Optional<EmailLogEntity> emailLog = emailLogRepository.findById(clickCorrelation.emailLogId());
        if (emailLog.isEmpty() || emailLog.get().getEmailType() != clickCorrelation.emailType()) {
            log.warn("email.resend.webhook.click unknownCorrelation");
            return;
        }
        EmailLogEntity entity = emailLog.get();
        if (entity.getClickedAt() == null) {
            entity.setClickedAt(clickCorrelation.clickedAt());
            emailLogRepository.save(entity);
        }
    }

    private void handleOpened(JsonNode event) {
        String createdAt = readText(event, FIELD_CREATED_AT);
        try {
            OffsetDateTime eventTime = OffsetDateTime.parse(createdAt);
            emailOpenDailyCountRepository.increment(eventTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate());
        } catch (DateTimeParseException | NullPointerException exception) {
            log.warn("email.resend.webhook.open malformedPayload");
        }
    }

    private Optional<ClickCorrelation> parseClickCorrelation(String link, String timestamp) {
        if (!StringUtils.hasText(link) || !StringUtils.hasText(timestamp)) {
            return Optional.empty();
        }
        try {
            java.net.URI uri = java.net.URI.create(link);
            Map<String, String> parameters = parseQuery(uri.getRawQuery());
            UUID emailLogId = UUID.fromString(parameters.get(EMAIL_LOG_ID_PARAMETER));
            RetentionEmailType emailType = RETENTION_TYPE_BY_SOURCE.get(parameters.get(SOURCE_PARAMETER));
            if (emailType == null) {
                return Optional.empty();
            }
            return Optional.of(new ClickCorrelation(emailLogId, emailType, OffsetDateTime.parse(timestamp)));
        } catch (IllegalArgumentException | NullPointerException | DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return Map.of();
        }
        Map<String, String> parameters = new java.util.HashMap<>();
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                parameters.put(
                        java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                );
            }
        }
        return parameters;
    }

    private void verifySignature(String payload, String svixId, String svixTimestamp, String svixSignature) {
        if (!StringUtils.hasText(payload)
                || !StringUtils.hasText(svixId)
                || !StringUtils.hasText(svixTimestamp)
                || !StringUtils.hasText(svixSignature)) {
            throw new InvalidResendWebhookSignatureException();
        }
        long timestamp = parseTimestamp(svixTimestamp);
        long nowEpochSeconds = Instant.now().getEpochSecond();
        if (Math.abs(nowEpochSeconds - timestamp) > SIGNATURE_TOLERANCE_SECONDS) {
            throw new InvalidResendWebhookSignatureException();
        }

        String expectedSignature = sign(svixId + "." + svixTimestamp + "." + payload);
        for (String signaturePart : svixSignature.trim().split("\\s+")) {
            if (!signaturePart.startsWith(SIGNATURE_VERSION_PREFIX)) {
                continue;
            }
            String suppliedSignature = signaturePart.substring(SIGNATURE_VERSION_PREFIX.length());
            if (constantTimeEquals(expectedSignature, suppliedSignature)) {
                return;
            }
        }
        throw new InvalidResendWebhookSignatureException();
    }

    private long parseTimestamp(String svixTimestamp) {
        try {
            return Long.parseLong(svixTimestamp.trim());
        } catch (NumberFormatException ex) {
            throw new InvalidResendWebhookSignatureException();
        }
    }

    private String sign(String signedContent) {
        String configuredSecret = properties.getEmail().getResendWebhookSecret();
        if (!StringUtils.hasText(configuredSecret) || !configuredSecret.startsWith(RESEND_SECRET_PREFIX)) {
            throw new InvalidResendWebhookSignatureException();
        }
        try {
            String encodedSecret = configuredSecret.substring(RESEND_SECRET_PREFIX.length());
            byte[] secretBytes = Base64.getDecoder().decode(encodedSecret);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new InvalidResendWebhookSignatureException();
        }
    }

    private boolean constantTimeEquals(String expectedSignature, String suppliedSignature) {
        byte[] expected = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedSignature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, supplied);
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (IOException ex) {
            throw new InvalidResendWebhookSignatureException();
        }
    }

    private boolean isSuppressionEvent(String eventType) {
        return EVENT_BOUNCED.equals(eventType)
                || EVENT_COMPLAINED.equals(eventType)
                || EVENT_SUPPRESSED.equals(eventType);
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String raw = value.asText();
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private record ClickCorrelation(UUID emailLogId, RetentionEmailType emailType, OffsetDateTime clickedAt) {
    }
}
