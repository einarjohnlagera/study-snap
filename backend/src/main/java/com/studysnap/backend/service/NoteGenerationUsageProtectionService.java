package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.MonthlyNoteGenerationLimitReachedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteGenerationUsageProtectionService {
    private final StudySnapProperties properties;
    private final UserUsageService userUsageService;

    public void assertQuotaAvailable(UUID userId, PlanType planType) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long usedThisPeriod = userUsageService.getMonthlyUsage(userId, now).noteGenerations();
        int allowed = Math.max(1, properties.getPricing().resolveMonthlyNoteGenerationLimit(planType));
        if (usedThisPeriod < allowed) {
            return;
        }
        throw new MonthlyNoteGenerationLimitReachedException();
    }

    public void recordUsage(UUID userId, OffsetDateTime occurredAt) {
        userUsageService.incrementNoteGeneration(userId, occurredAt);
    }
}
