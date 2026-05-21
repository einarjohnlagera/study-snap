package com.studysnap.backend.service;

import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.exception.MonthlyExportLimitReachedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportUsageProtectionService {
    private final FeatureGateService featureGateService;
    private final UserUsageService userUsageService;

    public void assertDocxQuotaAvailable(UUID userId, PlanType planType, ProfileType profileType) {
        Integer allowed = featureGateService.resolveMonthlyDocxExportLimit(planType, profileType);
        assertQuotaAvailable(userId, planType, allowed, true);
    }

    public void assertPdfQuotaAvailable(UUID userId, PlanType planType) {
        Integer allowed = featureGateService.resolveMonthlyPdfExportLimit(planType);
        assertQuotaAvailable(userId, planType, allowed, false);
    }

    public void recordDocxUsage(UUID userId, OffsetDateTime occurredAt) {
        userUsageService.incrementDocxExport(userId, occurredAt);
    }

    public void recordPdfUsage(UUID userId, OffsetDateTime occurredAt) {
        userUsageService.incrementPdfExport(userId, occurredAt);
    }

    private void assertQuotaAvailable(UUID userId, PlanType planType, Integer allowed, boolean docx) {
        if (allowed == null) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserUsageService.MonthlyUsage usage = userUsageService.getMonthlyUsage(userId, now);
        int usedThisPeriod = docx ? usage.docxExportsCount() : usage.pdfExportsCount();
        if (usedThisPeriod < allowed) {
            return;
        }

        throw MonthlyExportLimitReachedException.forPlan(planType);
    }
}
