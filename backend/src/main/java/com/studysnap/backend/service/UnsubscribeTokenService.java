package com.studysnap.backend.service;

import com.studysnap.backend.exception.InvalidUnsubscribeTokenException;
import com.studysnap.backend.security.SecurityProperties;
import io.jsonwebtoken.io.Decoders;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

@Service
public class UnsubscribeTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_SEPARATOR = ".";

    private final byte[] signingKey;

    public UnsubscribeTokenService(SecurityProperties securityProperties) {
        this.signingKey = resolveSigningKey(securityProperties.getJwt().getSecret());
    }

    public String sign(UUID userId, UnsubscribeCategory category) {
        String payload = userId + ":" + category.name();
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = encode(signPayload(encodedPayload));
        return encodedPayload + TOKEN_SEPARATOR + encodedSignature;
    }

    public VerifiedUnsubscribeToken verify(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidUnsubscribeTokenException();
        }
        String[] parts = token.trim().split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new InvalidUnsubscribeTokenException();
        }

        byte[] expectedSignature = signPayload(parts[0]);
        byte[] suppliedSignature = decode(parts[1]);
        if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
            throw new InvalidUnsubscribeTokenException();
        }

        String payload = new String(decode(parts[0]), StandardCharsets.UTF_8);
        String[] payloadParts = payload.split(":", -1);
        if (payloadParts.length != 2) {
            throw new InvalidUnsubscribeTokenException();
        }

        try {
            return new VerifiedUnsubscribeToken(
                    UUID.fromString(payloadParts[0]),
                    UnsubscribeCategory.valueOf(payloadParts[1])
            );
        } catch (IllegalArgumentException ex) {
            throw new InvalidUnsubscribeTokenException();
        }
    }

    private byte[] signPayload(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign unsubscribe token.", ex);
        }
    }

    private byte[] resolveSigningKey(String secret) {
        if (secret != null && secret.matches("^[A-Za-z0-9+/=]+$") && secret.length() % 4 == 0) {
            return Decoders.BASE64.decode(secret);
        }
        return (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8);
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] decode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidUnsubscribeTokenException();
        }
    }

    public record VerifiedUnsubscribeToken(UUID userId, UnsubscribeCategory category) {
    }
}
