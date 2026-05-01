package com.studysnap.backend.service;

import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingUsagePeriodService {
    private static final long YEARLY_DURATION_THRESHOLD_DAYS = 300;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UsagePeriod resolveUsagePeriod(UUID userId, OffsetDateTime referenceTime) {
        OffsetDateTime nowUtc = normalize(referenceTime);
        UsagePeriod paidUsagePeriod = subscriptionRepository.findByUser_IdAndStatusOrderByUpdatedAtDesc(
                        userId,
                        SubscriptionStatus.ACTIVE
                ).stream()
                .filter(subscription -> subscription.getPlanType().isPaid())
                .filter(subscription -> isWithinActiveWindow(subscription, nowUtc))
                .min(java.util.Comparator.comparingInt(subscription -> switch (subscription.getPlanType()) {
                    case PRO -> 0;
                    case PLUS -> 1;
                    case FREE -> 2;
                }))
                .map(this::toPaidUsagePeriod)
                .orElse(null);
        if (paidUsagePeriod != null) {
            return paidUsagePeriod;
        }

        return toFreeUsagePeriod(userId, nowUtc);
    }

    public UsagePeriod toPaidUsagePeriod(SubscriptionEntity subscription) {
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
                subscription.getPlanType(),
                billingCycle,
                start,
                end,
                start.getYear(),
                start.getMonthValue()
        );
    }

    public UsagePeriod toFreeUsagePeriod(UUID userId, OffsetDateTime referenceTime) {
        OffsetDateTime nowUtc = normalize(referenceTime);
        OffsetDateTime anchor = userRepository.findCreatedAtById(userId)
                .map(this::normalize)
                .orElseThrow(UserNotFoundException::new);
        OffsetDateTime periodStart = resolveCycleStart(anchor, nowUtc);
        OffsetDateTime periodEnd = periodStart.plusMonths(1);
        return new UsagePeriod(
                PlanType.FREE,
                BillingCycle.MONTHLY,
                periodStart,
                periodEnd,
                periodStart.getYear(),
                periodStart.getMonthValue()
        );
    }

    private OffsetDateTime resolveCycleStart(OffsetDateTime anchor, OffsetDateTime referenceTime) {
        if (referenceTime.isBefore(anchor)) {
            return anchor;
        }

        long monthOffset = ChronoUnit.MONTHS.between(
                anchor.toLocalDate().withDayOfMonth(1),
                referenceTime.toLocalDate().withDayOfMonth(1)
        );
        OffsetDateTime candidate = anchor.plusMonths(monthOffset);

        while (candidate.isAfter(referenceTime)) {
            monthOffset -= 1;
            candidate = anchor.plusMonths(monthOffset);
        }
        while (!candidate.plusMonths(1).isAfter(referenceTime)) {
            monthOffset += 1;
            candidate = anchor.plusMonths(monthOffset);
        }
        return candidate;
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
