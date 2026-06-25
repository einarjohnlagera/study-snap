package com.studysnap.backend.service;

import com.studysnap.backend.entity.SuppressedEmailEntity;
import com.studysnap.backend.repository.SuppressedEmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SuppressedEmailService {
    private final SuppressedEmailRepository suppressedEmailRepository;

    @Transactional(readOnly = true)
    public boolean isSuppressed(String address) {
        String normalizedAddress = normalize(address);
        return normalizedAddress != null && suppressedEmailRepository.existsByAddressIgnoreCase(normalizedAddress);
    }

    @Transactional
    public void suppress(String address, String reason) {
        suppress(address, reason, OffsetDateTime.now(ZoneOffset.UTC));
    }

    void suppress(String address, String reason, OffsetDateTime now) {
        String normalizedAddress = normalize(address);
        if (normalizedAddress == null) {
            return;
        }
        SuppressedEmailEntity entity = suppressedEmailRepository.findByAddressIgnoreCase(normalizedAddress)
                .orElseGet(() -> {
                    SuppressedEmailEntity created = new SuppressedEmailEntity();
                    created.setAddress(normalizedAddress);
                    created.setCreatedAt(now);
                    return created;
                });
        entity.setReason(normalizeReason(reason));
        suppressedEmailRepository.save(entity);
    }

    private String normalize(String address) {
        if (!StringUtils.hasText(address)) {
            return null;
        }
        return address.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "UNKNOWN";
        }
        return reason.trim().toUpperCase(Locale.ROOT);
    }
}
