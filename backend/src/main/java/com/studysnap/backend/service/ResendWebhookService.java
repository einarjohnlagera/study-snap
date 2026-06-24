package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.exception.InvalidResendWebhookSignatureException;
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
import java.util.Base64;

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
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_TO = "to";
    private static final long SIGNATURE_TOLERANCE_SECONDS = 300L;

    private final StudySnapProperties properties;
    private final ObjectMapper objectMapper;
    private final SuppressedEmailService suppressedEmailService;

    @Transactional
    public void handleWebhook(String payload, String svixId, String svixTimestamp, String svixSignature) {
        verifySignature(payload, svixId, svixTimestamp, svixSignature);

        JsonNode event = parsePayload(payload);
        String eventType = readText(event, FIELD_TYPE);
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
}
