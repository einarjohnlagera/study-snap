package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GoogleIdentityTokenVerifierService implements GoogleIdentityTokenVerifier {
    private static final String GOOGLE_ISSUER_HTTPS = "https://accounts.google.com";
    private static final String GOOGLE_ISSUER_LEGACY = "accounts.google.com";
    private static final String EMAIL_CLAIM = "email";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";
    private static final String NAME_CLAIM = "name";
    private static final String GIVEN_NAME_CLAIM = "given_name";

    private final SecurityProperties securityProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Override
    public GoogleIdentity verify(String code) {
        SecurityProperties.Google google = securityProperties.getGoogle();
        String clientId = google.getClientId();
        String clientSecret = google.getClientSecret();

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new AppException(
                    "GOOGLE_AUTH_NOT_CONFIGURED",
                    "Google login is not configured yet.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        String idToken = exchangeCodeForIdToken(code, clientId, clientSecret, google.getTokenEndpoint());

        JsonWebSignature signature = verifyWithIssuer(idToken, GOOGLE_ISSUER_HTTPS);
        if (signature == null) {
            signature = verifyWithIssuer(idToken, GOOGLE_ISSUER_LEGACY);
        }
        if (signature == null) {
            throw invalidGoogleCredential();
        }

        JsonWebToken.Payload payload = signature.getPayload();
        String subject = normalizeClaim(payload.getSubject());
        String email = normalizeEmailClaim(payload.get(EMAIL_CLAIM));
        if (subject == null || email == null) {
            throw invalidGoogleCredential();
        }
        return new GoogleIdentity(
                subject,
                email,
                resolveBooleanClaim(payload.get(EMAIL_VERIFIED_CLAIM)),
                normalizeClaim(payload.get(NAME_CLAIM)),
                normalizeClaim(payload.get(GIVEN_NAME_CLAIM))
        );
    }

    private String exchangeCodeForIdToken(String code, String clientId, String clientSecret, String tokenEndpoint) {
        String body = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&redirect_uri=postmessage"
                + "&grant_type=authorization_code";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AppException("GOOGLE_AUTH_FAILED", "Could not reach Google to verify sign-in.", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException("GOOGLE_AUTH_FAILED", "Could not reach Google to verify sign-in.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        if (response.statusCode() != 200) {
            throw invalidGoogleCredential();
        }

        try {
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode idTokenNode = json.get("id_token");
            if (idTokenNode == null || idTokenNode.isNull()) {
                throw invalidGoogleCredential();
            }
            return idTokenNode.asText();
        } catch (IOException e) {
            throw invalidGoogleCredential();
        }
    }

    private JsonWebSignature verifyWithIssuer(String idToken, String issuer) {
        try {
            return TokenVerifier.newBuilder()
                    .setAudience(securityProperties.getGoogle().getClientId())
                    .setIssuer(issuer)
                    .setCertificatesLocation(securityProperties.getGoogle().getCertificatesUrl())
                    .build()
                    .verify(idToken);
        } catch (TokenVerifier.VerificationException ex) {
            return null;
        }
    }

    private String normalizeEmailClaim(Object value) {
        String normalized = normalizeClaim(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeClaim(Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean resolveBooleanClaim(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }

    private AppException invalidGoogleCredential() {
        return new AppException(
                "GOOGLE_AUTH_INVALID",
                "Could not verify Google sign-in. Please try again.",
                HttpStatus.UNAUTHORIZED
        );
    }
}
