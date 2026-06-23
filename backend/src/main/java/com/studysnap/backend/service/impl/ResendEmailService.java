package com.studysnap.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.EmailMessage;
import com.studysnap.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ResendEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);

    private static final String DEFAULT_RESEND_BASE_URL = "https://api.resend.com";
    private static final String ERROR_PROVIDER_UNREACHABLE = "EMAIL_PROVIDER_UNREACHABLE";
    private static final String ERROR_DELIVERY_FAILED = "EMAIL_DELIVERY_FAILED";
    private static final String ERROR_PAYLOAD_INVALID = "EMAIL_PAYLOAD_INVALID";
    private static final String GENERIC_RETRY_MESSAGE = "Could not send verification email right now. Please try again.";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 500L;
    private static final long MAX_BACKOFF_MILLIS = 5_000L;

    private final StudySnapProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Override
    public void sendEmail(EmailMessage message) {
        ensureConfigured();

        String payload = serializePayload(message);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveResendBaseUrl() + "/emails"))
                .header("Authorization", "Bearer " + properties.getEmail().getResendApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        // Retry only on HTTP 429 (rate limit) so a transactional email landing during a retention/marketing
        // send burst isn't dropped. IO/non-429 failures keep their existing behavior.
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response = executeSend(request);
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return;
            }
            if (statusCode == HTTP_TOO_MANY_REQUESTS && attempt < MAX_SEND_ATTEMPTS) {
                waitBeforeRetry(response, attempt);
                continue;
            }
            throw deliveryFailed(response.body());
        }
    }

    private HttpResponse<String> executeSend(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new AppException(ERROR_PROVIDER_UNREACHABLE, GENERIC_RETRY_MESSAGE, HttpStatus.BAD_GATEWAY);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(ERROR_PROVIDER_UNREACHABLE, GENERIC_RETRY_MESSAGE, HttpStatus.BAD_GATEWAY);
        }
    }

    private void waitBeforeRetry(HttpResponse<String> response, int attempt) {
        long delayMillis = resolveRetryDelayMillis(response, attempt);
        log.warn("resend.email.rate_limited attempt={} retryInMs={}", attempt, delayMillis);
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(ERROR_PROVIDER_UNREACHABLE, GENERIC_RETRY_MESSAGE, HttpStatus.BAD_GATEWAY);
        }
    }

    private long resolveRetryDelayMillis(HttpResponse<String> response, int attempt) {
        Optional<Long> retryAfterMillis = response.headers().firstValue(RETRY_AFTER_HEADER)
                .map(String::trim)
                .flatMap(this::parseRetryAfterSeconds)
                .map(seconds -> seconds * 1000L);
        long delayMillis = retryAfterMillis.orElseGet(() -> BASE_BACKOFF_MILLIS * (1L << (attempt - 1)));
        return Math.clamp(delayMillis, 0L, MAX_BACKOFF_MILLIS);
    }

    private Optional<Long> parseRetryAfterSeconds(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private AppException deliveryFailed(String body) {
        String providerMessage = extractProviderMessage(body);
        return new AppException(
                ERROR_DELIVERY_FAILED,
                providerMessage == null ? GENERIC_RETRY_MESSAGE : providerMessage,
                HttpStatus.BAD_GATEWAY
        );
    }

    private String serializePayload(EmailMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", properties.getEmail().getFrom());
        payload.put("to", List.of(message.to()));
        payload.put("subject", message.subject());
        payload.put("html", message.htmlBody());
        payload.put("text", message.textBody());
        if (message.headers() != null && !message.headers().isEmpty()) {
            payload.put("headers", message.headers());
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new AppException(ERROR_PAYLOAD_INVALID, GENERIC_RETRY_MESSAGE, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String extractProviderMessage(String body) {
        try {
            JsonNode parsed = objectMapper.readTree(body == null ? "" : body);
            JsonNode messageNode = parsed.path("message");
            if (messageNode.isMissingNode() || messageNode.isNull()) {
                return null;
            }
            String raw = messageNode.asText();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return raw.trim();
        } catch (IOException ignored) {
            return null;
        }
    }

    private String resolveResendBaseUrl() {
        String configured = properties.getEmail().getResendApiBaseUrl();
        if (configured == null || configured.isBlank()) {
            return DEFAULT_RESEND_BASE_URL;
        }
        String trimmed = configured.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private void ensureConfigured() {
        StudySnapProperties.Email email = properties.getEmail();
        if (email.getResendApiKey() == null || email.getResendApiKey().isBlank()
                || email.getFrom() == null || email.getFrom().isBlank()) {
            throw new AppException(
                    "EMAIL_NOT_CONFIGURED",
                    "Email verification is not configured yet. Please contact support.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }
}
