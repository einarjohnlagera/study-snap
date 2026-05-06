package com.studysnap.backend.service;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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

    @Override
    public GoogleIdentity verify(String credential) {
        String clientId = securityProperties.getGoogle().getClientId();
        if (clientId == null || clientId.isBlank()) {
            throw new AppException(
                    "GOOGLE_AUTH_NOT_CONFIGURED",
                    "Google login is not configured yet.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        JsonWebSignature signature = verifyWithIssuer(credential, GOOGLE_ISSUER_HTTPS);
        if (signature == null) {
            signature = verifyWithIssuer(credential, GOOGLE_ISSUER_LEGACY);
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

    private JsonWebSignature verifyWithIssuer(String credential, String issuer) {
        try {
            return TokenVerifier.newBuilder()
                    .setAudience(securityProperties.getGoogle().getClientId())
                    .setIssuer(issuer)
                    .setCertificatesLocation(securityProperties.getGoogle().getCertificatesUrl())
                    .build()
                    .verify(credential);
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
