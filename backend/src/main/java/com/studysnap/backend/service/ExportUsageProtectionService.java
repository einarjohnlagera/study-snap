package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.MonthlyExportLimitReachedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportUsageProtectionService {
    private final StudySnapProperties properties;
    private final UserUsageService userUsageService;

    public void assertQuotaAvailable(UUID userId, PlanType planType) {
        Integer allowed = properties.getPricing().resolveMonthlyExportLimit(planType);
        if (allowed == null) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int usedThisPeriod = userUsageService.getMonthlyUsage(userId, now).exportsCount();
        if (usedThisPeriod < allowed) {
            return;
        }

        throw MonthlyExportLimitReachedException.forPlan(planType);
    }

    public void recordUsage(UUID userId, OffsetDateTime occurredAt) {
        userUsageService.incrementExport(userId, occurredAt);
    }
}
