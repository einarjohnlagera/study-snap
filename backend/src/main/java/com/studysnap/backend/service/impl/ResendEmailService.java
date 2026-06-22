package com.studysnap.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.EmailMessage;
import com.studysnap.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class ResendEmailService implements EmailService {
    private static final String DEFAULT_RESEND_BASE_URL = "https://api.resend.com";

    private final StudySnapProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

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

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new AppException(
                    "EMAIL_PROVIDER_UNREACHABLE",
                    "Could not send verification email right now. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(
                    "EMAIL_PROVIDER_UNREACHABLE",
                    "Could not send verification email right now. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }

        String providerMessage = extractProviderMessage(response.body());
        throw new AppException(
                "EMAIL_DELIVERY_FAILED",
                providerMessage == null
                        ? "Could not send verification email right now. Please try again."
                        : providerMessage,
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
            throw new AppException(
                    "EMAIL_PAYLOAD_INVALID",
                    "Could not send verification email right now. Please try again.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
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
