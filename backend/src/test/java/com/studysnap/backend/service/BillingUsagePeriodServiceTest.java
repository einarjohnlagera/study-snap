package com.studysnap.backend.service;

import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingUsagePeriodServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    void resolveUsagePeriod_returnsFreeBillingCycleAnchoredToAccountCreation() {
        BillingUsagePeriodService service = new BillingUsagePeriodService(subscriptionRepository, userRepository);
        UUID userId = UUID.randomUUID();
        OffsetDateTime referenceTime = OffsetDateTime.of(2026, 3, 22, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime accountCreatedAt = OffsetDateTime.of(2026, 3, 10, 9, 0, 0, 0, ZoneOffset.UTC);
        when(subscriptionRepository.findByUser_IdAndPlanTypeAndStatusOrderByUpdatedAtDesc(
                userId,
                PlanType.PREMIUM,
                SubscriptionStatus.ACTIVE
        )).thenReturn(List.of());
        when(userRepository.findCreatedAtById(userId)).thenReturn(java.util.Optional.of(accountCreatedAt));

        BillingUsagePeriodService.UsagePeriod period = service.resolveUsagePeriod(userId, referenceTime);

        assertThat(period.planType()).isEqualTo(PlanType.FREE);
        assertThat(period.periodStart()).isEqualTo(accountCreatedAt);
        assertThat(period.periodEnd()).isEqualTo(accountCreatedAt.plusMonths(1));
    }

    @Test
    void resolveUsagePeriod_advancesFreeBillingCycleFromAccountCreationAnchor() {
        BillingUsagePeriodService service = new BillingUsagePeriodService(subscriptionRepository, userRepository);
        UUID userId = UUID.randomUUID();
        OffsetDateTime referenceTime = OffsetDateTime.of(2026, 5, 12, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime accountCreatedAt = OffsetDateTime.of(2026, 3, 10, 9, 0, 0, 0, ZoneOffset.UTC);
        when(subscriptionRepository.findByUser_IdAndPlanTypeAndStatusOrderByUpdatedAtDesc(
                userId,
                PlanType.PREMIUM,
                SubscriptionStatus.ACTIVE
        )).thenReturn(List.of());
        when(userRepository.findCreatedAtById(userId)).thenReturn(java.util.Optional.of(accountCreatedAt));

        BillingUsagePeriodService.UsagePeriod period = service.resolveUsagePeriod(userId, referenceTime);

        assertThat(period.periodStart()).isEqualTo(accountCreatedAt.plusMonths(2));
        assertThat(period.periodEnd()).isEqualTo(accountCreatedAt.plusMonths(3));
    }

    @Test
    void resolveUsagePeriod_returnsPremiumBillingWindowWhenActiveSubscriptionExists() {
        BillingUsagePeriodService service = new BillingUsagePeriodService(subscriptionRepository, userRepository);
        UUID userId = UUID.randomUUID();
        OffsetDateTime periodStart = OffsetDateTime.of(2026, 3, 15, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime periodEnd = OffsetDateTime.of(2026, 4, 15, 0, 0, 0, 0, ZoneOffset.UTC);
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setPlanType(PlanType.PREMIUM);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartAt(periodStart);
        subscription.setEndAt(periodEnd);
        when(subscriptionRepository.findByUser_IdAndPlanTypeAndStatusOrderByUpdatedAtDesc(
                userId,
                PlanType.PREMIUM,
                SubscriptionStatus.ACTIVE
        )).thenReturn(List.of(subscription));

        BillingUsagePeriodService.UsagePeriod period = service.resolveUsagePeriod(
                userId,
                OffsetDateTime.of(2026, 3, 22, 10, 0, 0, 0, ZoneOffset.UTC)
        );

        assertThat(period.planType()).isEqualTo(PlanType.PREMIUM);
        assertThat(period.periodStart()).isEqualTo(periodStart);
        assertThat(period.periodEnd()).isEqualTo(periodEnd);
        assertThat(period.month()).isEqualTo(3);
        assertThat(period.year()).isEqualTo(2026);
    }
}
