package com.studysnap.backend.service;

import com.studysnap.backend.entity.RefreshTokenEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.RefreshTokenRepository;
import com.studysnap.backend.security.SecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Transactional
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityProperties securityProperties;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, SecurityProperties securityProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.securityProperties = securityProperties;
    }

    public IssuedRefreshToken issue(UserEntity user, boolean keepSignedIn, String deviceName, String ipAddress, String userAgent) {
        String rawToken = generateToken();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusDays(
                keepSignedIn
                        ? securityProperties.getJwt().getRefreshTokenDaysKeepSignedIn()
                        : securityProperties.getJwt().getRefreshTokenDays()
        );

        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(user.getId());
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(now);
        entity.setLastUsedAt(now);
        entity.setKeepSignedIn(keepSignedIn);
        entity.setDeviceName(deviceName);
        entity.setIpAddress(ipAddress);
        entity.setUserAgent(userAgent);
        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(rawToken, expiresAt);
    }

    public RefreshTokenEntity requireValid(String rawToken) {
        RefreshTokenEntity entity = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AppException("INVALID_REFRESH_TOKEN", "Invalid refresh token.", HttpStatus.UNAUTHORIZED));
        if (entity.getRevokedAt() != null) {
            throw new AppException("INVALID_REFRESH_TOKEN", "Invalid refresh token.", HttpStatus.UNAUTHORIZED);
        }
        if (entity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new AppException("REFRESH_TOKEN_EXPIRED", "Refresh token expired. Please log in again.", HttpStatus.UNAUTHORIZED);
        }
        return entity;
    }

    public void revoke(String rawToken) {
        RefreshTokenEntity entity = refreshTokenRepository.findByTokenHash(hash(rawToken)).orElse(null);
        if (entity == null || entity.getRevokedAt() != null) {
            return;
        }
        entity.setRevokedAt(OffsetDateTime.now());
    }

    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    public void touch(RefreshTokenEntity entity) {
        entity.setLastUsedAt(OffsetDateTime.now());
    }

    private String generateToken() {
        byte[] raw = new byte[48];
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        byte[] a = first.toString().getBytes(StandardCharsets.UTF_8);
        byte[] b = second.toString().getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < raw.length; i++) {
            byte left = i < a.length ? a[i] : 0;
            byte right = i < b.length ? b[i] : 0;
            raw[i] = (byte) (left ^ right ^ (i * 31));
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record IssuedRefreshToken(String rawToken, OffsetDateTime expiresAt) {
    }
}
