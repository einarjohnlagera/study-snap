package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.exception.InvalidResendWebhookSignatureException;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.EmailOpenDailyCountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ResendWebhookServiceTest {
    private static final String WEBHOOK_SECRET = "whsec_c2VjcmV0LXRlc3Qta2V5LTEyMzQ1Ng==";
    private static final String SVIX_ID = "msg_test";

    private SuppressedEmailService suppressedEmailService;
    private EmailLogRepository emailLogRepository;
    private EmailOpenDailyCountRepository emailOpenDailyCountRepository;
    private ResendWebhookService resendWebhookService;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getEmail().setResendWebhookSecret(WEBHOOK_SECRET);
        suppressedEmailService = mock(SuppressedEmailService.class);
        emailLogRepository = mock(EmailLogRepository.class);
        emailOpenDailyCountRepository = mock(EmailOpenDailyCountRepository.class);
        resendWebhookService = new ResendWebhookService(
                properties,
                new ObjectMapper(),
                suppressedEmailService,
                emailLogRepository,
                emailOpenDailyCountRepository
        );
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

    @Test
    void handleWebhook_clickedCorrelatesTheLinkToItsEmailLogRow() {
        UUID emailLogId = UUID.fromString("ad30a9a2-b66e-4a74-98f6-f84ee909cf57");
        EmailLogEntity emailLog = new EmailLogEntity();
        emailLog.setId(emailLogId);
        emailLog.setEmailType(RetentionEmailType.INACTIVITY);
        when(emailLogRepository.findById(emailLogId)).thenReturn(Optional.of(emailLog));
        String payload = """
                {
                  "type":"email.clicked",
                  "created_at":"2026-09-23T02:00:01Z",
                  "data":{"click":{
                    "link":"https://www.notelib.app/dashboard?source=inactivity&e=%s",
                    "timestamp":"2026-09-23T02:00:00Z"
                  }}
                }
                """.formatted(emailLogId).trim();

        handle(payload);

        assertThat(emailLog.getClickedAt()).isEqualTo(OffsetDateTime.parse("2026-09-23T02:00:00Z"));
        verify(emailLogRepository).save(emailLog);
    }

    @Test
    void handleWebhook_clickedUnknownRowIsLoggedAndSkipped(CapturedOutput output) {
        UUID missingId = UUID.fromString("ca85fcb8-88e3-4388-bfce-58fcf0ce3f78");
        when(emailLogRepository.findById(missingId)).thenReturn(Optional.empty());
        String payload = """
                {"type":"email.clicked","data":{"click":{
                  "link":"https://www.notelib.app/dashboard?source=weekly-summary&e=%s",
                  "timestamp":"2026-09-23T02:00:00Z"
                }}}
                """.formatted(missingId).trim();

        assertThatCode(() -> handle(payload)).doesNotThrowAnyException();

        assertThat(output).contains("email.resend.webhook.click unknownCorrelation");
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void handleWebhook_clickedTypeMarkerCannotBeUsedToMarkAnotherEmailType() {
        UUID emailLogId = UUID.fromString("8f48a877-2d72-4a87-8a4d-117f896458e3");
        EmailLogEntity emailLog = new EmailLogEntity();
        emailLog.setId(emailLogId);
        emailLog.setEmailType(RetentionEmailType.WEEKLY_SUMMARY);
        when(emailLogRepository.findById(emailLogId)).thenReturn(Optional.of(emailLog));
        String payload = """
                {"type":"email.clicked","data":{"click":{
                  "link":"https://www.notelib.app/dashboard?source=inactivity&e=%s",
                  "timestamp":"2026-09-23T02:00:00Z"
                }}}
                """.formatted(emailLogId).trim();

        assertThatCode(() -> handle(payload)).doesNotThrowAnyException();

        assertThat(emailLog.getClickedAt()).isNull();
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void handleWebhook_clickedWithNonIsoTimestampIsLoggedAndSkippedNotThrown(CapturedOutput output) {
        UUID emailLogId = UUID.fromString("0d5a3c58-4c0e-4a5b-9d61-3f0b2f4b9a10");
        String payload = """
                {"type":"email.clicked","data":{"click":{
                  "link":"https://www.notelib.app/dashboard?source=inactivity&e=%s",
                  "timestamp":"1732424457"
                }}}
                """.formatted(emailLogId).trim();

        assertThatCode(() -> handle(payload)).doesNotThrowAnyException();

        assertThat(output).contains("email.resend.webhook.click malformedPayload");
        verify(emailLogRepository, never()).findById(any(UUID.class));
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void handleWebhook_secondClickKeepsTheFirstRecordedClickTime() {
        UUID emailLogId = UUID.fromString("5f2c1c9e-2f0e-4f6b-8f0a-7b1d2c3e4a55");
        EmailLogEntity emailLog = new EmailLogEntity();
        emailLog.setId(emailLogId);
        emailLog.setEmailType(RetentionEmailType.INACTIVITY);
        OffsetDateTime first = OffsetDateTime.parse("2026-09-23T02:00:00Z");
        emailLog.setClickedAt(first);
        when(emailLogRepository.findById(emailLogId)).thenReturn(Optional.of(emailLog));
        String payload = """
                {"type":"email.clicked","data":{"click":{
                  "link":"https://www.notelib.app/dashboard?source=inactivity&e=%s",
                  "timestamp":"2026-09-24T09:00:00Z"
                }}}
                """.formatted(emailLogId).trim();

        handle(payload);

        assertThat(emailLog.getClickedAt()).isEqualTo(first);
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void handleWebhook_openedIncrementsOnlyTheAggregateDay() {
        String payload = """
                {"type":"email.opened","created_at":"2026-09-23T23:41:12.126Z","data":{
                  "email_id":"resend-id-not-correlated"
                }}
                """.trim();

        handle(payload);

        verify(emailOpenDailyCountRepository).increment(LocalDate.parse("2026-09-23"));
        verify(emailLogRepository, never()).findById(any(UUID.class));
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void handleWebhook_malformedEngagementPayloadsAreLoggedAndSkipped(CapturedOutput output) {
        String malformedClick = """
                {"type":"email.clicked","data":{"click":{"link":"not a URL","timestamp":null}}}
                """.trim();
        String malformedOpen = """
                {"type":"email.opened","created_at":"not a timestamp","data":{}}
                """.trim();

        assertThatCode(() -> handle(malformedClick)).doesNotThrowAnyException();
        assertThatCode(() -> handle(malformedOpen)).doesNotThrowAnyException();

        assertThat(output)
                .contains("email.resend.webhook.click malformedPayload")
                .contains("email.resend.webhook.open malformedPayload");
        verify(emailLogRepository, never()).findById(any(UUID.class));
        verify(emailOpenDailyCountRepository, never()).increment(any(LocalDate.class));
    }

    private void handle(String payload) {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        resendWebhookService.handleWebhook(payload, SVIX_ID, timestamp, sign(payload, timestamp));
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
