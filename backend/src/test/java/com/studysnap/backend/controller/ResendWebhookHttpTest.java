package com.studysnap.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.exception.GlobalExceptionHandler;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.EmailOpenDailyCountRepository;
import com.studysnap.backend.service.ResendWebhookService;
import com.studysnap.backend.service.SuppressedEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.unit.DataSize;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Sends REAL HTTP requests (content type, body, Svix headers) through the controller into the real
 * {@link ResendWebhookService}. The service and controller unit tests call methods directly, which bypasses
 * request mapping, body binding and exception translation entirely.
 */
class ResendWebhookHttpTest {
    private static final String SECRET = "whsec_c2VjcmV0LXRlc3Qta2V5LTEyMzQ1Ng==";
    private static final String SVIX_ID = "msg_http";

    private EmailLogRepository emailLogRepository;
    private EmailOpenDailyCountRepository emailOpenDailyCountRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getEmail().setResendWebhookSecret(SECRET);
        emailLogRepository = mock(EmailLogRepository.class);
        emailOpenDailyCountRepository = mock(EmailOpenDailyCountRepository.class);
        ResendWebhookService service = new ResendWebhookService(
                properties, new ObjectMapper(), mock(SuppressedEmailService.class),
                emailLogRepository, emailOpenDailyCountRepository
        );
        mockMvc = standaloneSetup(new ResendWebhookController(service))
                .setControllerAdvice(new GlobalExceptionHandler(DataSize.ofMegabytes(10)))
                .build();
    }

    @Test
    void signedClickOverHttpRecordsTheClickAndAcknowledges() throws Exception {
        UUID id = UUID.randomUUID();
        EmailLogEntity row = new EmailLogEntity();
        row.setId(id);
        row.setEmailType(RetentionEmailType.INACTIVITY);
        when(emailLogRepository.findById(id)).thenReturn(Optional.of(row));

        mockMvc.perform(signed(clickPayload(id, "2026-09-24T02:00:00Z")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OK"));

        assertThat(row.getClickedAt()).isEqualTo(OffsetDateTime.parse("2026-09-24T02:00:00Z"));
    }

    @Test
    void nonIsoClickTimestampOverHttpIsAcknowledgedNotA5xx() throws Exception {
        mockMvc.perform(signed(clickPayload(UUID.randomUUID(), "1732424457")))
                .andExpect(status().isOk());

        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void signedOpenOverHttpIncrementsTheAggregateDay() throws Exception {
        String payload = "{\"type\":\"email.opened\",\"created_at\":\"2026-09-24T01:02:03Z\",\"data\":{}}";

        mockMvc.perform(signed(payload)).andExpect(status().isOk());

        verify(emailOpenDailyCountRepository).increment(LocalDate.parse("2026-09-24"));
    }

    @Test
    void unsignedRequestIsRejectedBeforeAnyPersistence() throws Exception {
        mockMvc.perform(post("/webhooks/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clickPayload(UUID.randomUUID(), "2026-09-24T02:00:00Z")))
                .andExpect(status().isUnauthorized());

        verify(emailLogRepository, never()).findById(any(UUID.class));
    }

    private static String clickPayload(UUID id, String timestamp) {
        return "{\"type\":\"email.clicked\",\"data\":{\"click\":{\"link\":\"https://www.notelib.app/dashboard"
                + "?source=inactivity&e=" + id + "\",\"timestamp\":\"" + timestamp + "\"}}}";
    }

    private static MockHttpServletRequestBuilder signed(String payload) throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET.substring("whsec_".length())), "HmacSHA256"));
        String signature = "v1," + Base64.getEncoder().encodeToString(
                mac.doFinal((SVIX_ID + "." + timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return post("/webhooks/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .header("svix-id", SVIX_ID)
                .header("svix-timestamp", timestamp)
                .header("svix-signature", signature)
                .content(payload);
    }
}
