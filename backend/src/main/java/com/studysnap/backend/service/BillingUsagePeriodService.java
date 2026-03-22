package com.studysnap.backend.service;

import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingUsagePeriodService {
    private static final long YEARLY_DURATION_THRESHOLD_DAYS = 300;

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public UsagePeriod resolveUsagePeriod(UUID userId, OffsetDateTime referenceTime) {
        OffsetDateTime nowUtc = normalize(referenceTime);
        return subscriptionRepository.findByUser_IdAndPlanTypeAndStatusOrderByUpdatedAtDesc(
                        userId,
                        PlanType.PREMIUM,
                        SubscriptionStatus.ACTIVE
                ).stream()
                .filter(subscription -> isWithinActiveWindow(subscription, nowUtc))
                .findFirst()
                .map(this::toPremiumUsagePeriod)
                .orElseGet(() -> toFreeUsagePeriod(nowUtc));
    }

    public UsagePeriod toPremiumUsagePeriod(SubscriptionEntity subscription) {
        OffsetDateTime start = normalize(subscription.getStartAt());
        if (start == null) {
            start = normalize(OffsetDateTime.now(ZoneOffset.UTC));
        }
        BillingCycle billingCycle = resolveBillingCycle(subscription);
        OffsetDateTime end = normalize(subscription.getEndAt());
        if (end == null || !end.isAfter(start)) {
            end = billingCycle == BillingCycle.YEARLY ? start.plusYears(1) : start.plusMonths(1);
        }
        return new UsagePeriod(
                PlanType.PREMIUM,
                billingCycle,
                start,
                end,
                start.getYear(),
                start.getMonthValue()
        );
    }

    public UsagePeriod toFreeUsagePeriod(OffsetDateTime referenceTime) {
        OffsetDateTime nowUtc = normalize(referenceTime);
        OffsetDateTime monthStart = nowUtc.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        return new UsagePeriod(
                PlanType.FREE,
                BillingCycle.MONTHLY,
                monthStart,
                monthStart.plusMonths(1),
                monthStart.getYear(),
                monthStart.getMonthValue()
        );
    }

    private BillingCycle resolveBillingCycle(SubscriptionEntity subscription) {
        OffsetDateTime start = normalize(subscription.getStartAt());
        OffsetDateTime end = normalize(subscription.getEndAt());
        if (start == null || end == null || !end.isAfter(start)) {
            return BillingCycle.MONTHLY;
        }
        long durationDays = java.time.Duration.between(start, end).toDays();
        return durationDays >= YEARLY_DURATION_THRESHOLD_DAYS ? BillingCycle.YEARLY : BillingCycle.MONTHLY;
    }

    private boolean isWithinActiveWindow(SubscriptionEntity subscription, OffsetDateTime referenceTime) {
        OffsetDateTime startAt = normalize(subscription.getStartAt());
        OffsetDateTime endAt = normalize(subscription.getEndAt());
        if (startAt != null && referenceTime.isBefore(startAt)) {
            return false;
        }
        return endAt == null || referenceTime.isBefore(endAt);
    }

    private OffsetDateTime normalize(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.withOffsetSameInstant(ZoneOffset.UTC);
    }

    public record UsagePeriod(
            PlanType planType,
            BillingCycle billingCycle,
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd,
            int year,
            int month
    ) {
    }
}
