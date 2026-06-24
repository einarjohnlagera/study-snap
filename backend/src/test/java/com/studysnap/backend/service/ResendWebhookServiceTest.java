package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.exception.InvalidResendWebhookSignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ResendWebhookServiceTest {
    private static final String WEBHOOK_SECRET = "whsec_c2VjcmV0LXRlc3Qta2V5LTEyMzQ1Ng==";
    private static final String SVIX_ID = "msg_test";

    private SuppressedEmailService suppressedEmailService;
    private ResendWebhookService resendWebhookService;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getEmail().setResendWebhookSecret(WEBHOOK_SECRET);
        suppressedEmailService = mock(SuppressedEmailService.class);
        resendWebhookService = new ResendWebhookService(properties, new ObjectMapper(), suppressedEmailService);
    }

    @Test
    void handleWebhook_validBounceSignatureSuppressesRecipient() {
        String payload = """
                {"type":"email.bounced","data":{"to":["bounce@example.com"]}}
                """.trim();
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = sign(payload, timestamp);

        resendWebhookService.handleWebhook(payload, SVIX_ID, timestamp, signature);

        verify(suppressedEmailService).suppress("bounce@example.com", "email.bounced");
    }

    @Test
    void handleWebhook_badSignatureRejectsWithoutSuppressionWrite() {
        String payload = """
                {"type":"email.complained","data":{"to":["complaint@example.com"]}}
                """.trim();
        String timestamp = Long.toString(Instant.now().getEpochSecond());

        assertThatThrownBy(() -> resendWebhookService.handleWebhook(payload, SVIX_ID, timestamp, "v1,bad"))
                .isInstanceOf(InvalidResendWebhookSignatureException.class)
                .extracting(ex -> ((InvalidResendWebhookSignatureException) ex).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(suppressedEmailService, never()).suppress("complaint@example.com", "email.complained");
    }

    private String sign(String payload, String timestamp) {
        try {
            String encodedSecret = WEBHOOK_SECRET.substring("whsec_".length());
            byte[] secretBytes = Base64.getDecoder().decode(encodedSecret);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] signature = mac.doFinal((SVIX_ID + "." + timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(signature);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
