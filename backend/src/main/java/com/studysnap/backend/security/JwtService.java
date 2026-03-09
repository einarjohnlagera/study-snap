package com.studysnap.backend.security;

import com.studysnap.backend.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecurityProperties securityProperties;
    private SecretKey signingKey;

    public JwtService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @PostConstruct
    void init() {
        String secret = securityProperties.getJwt().getSecret();
        byte[] keyBytes;
        if (secret.matches("^[A-Za-z0-9+/=]+$") && secret.length() % 4 == 0) {
            keyBytes = Decoders.BASE64.decode(secret);
        } else {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserEntity user) {
        OffsetDateTime issuedAt = OffsetDateTime.now();
        OffsetDateTime expiresAt = issuedAt.plusMinutes(securityProperties.getJwt().getAccessTokenMinutes());
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(securityProperties.getJwt().getIssuer())
                .issuedAt(Date.from(issuedAt.toInstant()))
                .expiration(Date.from(expiresAt.toInstant()))
                .claim("role", user.getRole().name())
                .claim("ev", user.getEmailVerifiedAt() != null)
                .claim("tv", user.getTokenVersion())
                .signWith(signingKey)
                .compact();
    }

    public OffsetDateTime resolveAccessTokenExpiry() {
        return OffsetDateTime.now().plusMinutes(securityProperties.getJwt().getAccessTokenMinutes());
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(securityProperties.getJwt().getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }
}
