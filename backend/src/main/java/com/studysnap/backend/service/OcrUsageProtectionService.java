package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.InputType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.StudyPackDraftRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OcrUsageProtectionService {
    private final StudySnapProperties properties;
    private final UserUsageService userUsageService;
    private final BillingUsagePeriodService billingUsagePeriodService;
    private final StudyPackRepository studyPackRepository;
    private final StudyPackDraftRepository studyPackDraftRepository;

    public void assertQuotaAvailable(UUID userId, PlanType planType) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BillingUsagePeriodService.UsagePeriod usagePeriod = billingUsagePeriodService.resolveUsagePeriod(userId, now);
        long usedThisPeriod = countUsedThisPeriod(userId, now, usagePeriod);
        int allowed = resolveLimit(planType);
        if (usedThisPeriod < allowed) {
            return;
        }

        throw new AppException(
                "OCR_LIMIT_REACHED",
                "You have reached your OCR limit for now. Please try again later or upgrade to Premium.",
                HttpStatus.FORBIDDEN
        );
    }

    public void recordUsage(UUID userId, OffsetDateTime occurredAt) {
        userUsageService.incrementOcrExtraction(userId, occurredAt);
    }

    private long countUsedThisPeriod(
            UUID userId,
            OffsetDateTime referenceTime,
            BillingUsagePeriodService.UsagePeriod usagePeriod
    ) {
        long trackedUsage = userUsageService.getMonthlyUsage(userId, referenceTime).ocrExtractions();
        long legacyImageStudyPacks = studyPackRepository.countByOwnerUserIdAndInputTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                InputType.IMAGE,
                usagePeriod.periodStart(),
                usagePeriod.periodEnd()
        );
        long legacyPendingDrafts = studyPackDraftRepository.countByOwnerUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                usagePeriod.periodStart(),
                usagePeriod.periodEnd()
        );
        return Math.max(trackedUsage, legacyImageStudyPacks + legacyPendingDrafts);
    }

    private int resolveLimit(PlanType planType) {
        int configured = properties.getPricing().resolveMonthlyOcrLimit(planType);
        return Math.max(1, configured);
    }
}
