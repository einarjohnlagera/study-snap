package com.studysnap.backend.service;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionCancellationReason;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AnalyticsService analyticsService;

    @Test
    void expireSubscriptionAndDowngradeToFree_marksExpiredAndCreatesFreeSubscription() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, userRepository, analyticsService);
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);

        SubscriptionEntity activePremium = new SubscriptionEntity();
        activePremium.setId(subscriptionId);
        activePremium.setUser(user);
        activePremium.setPlanType(PlanType.PREMIUM);
        activePremium.setStatus(SubscriptionStatus.ACTIVE);
        activePremium.setBillingType(BillingType.SUBSCRIPTION);
        activePremium.setProvider(BillingProvider.XENDIT);
        activePremium.setStartAt(OffsetDateTime.now().minusMonths(1));
        activePremium.setEndAt(OffsetDateTime.now().minusDays(1));
        activePremium.setCreatedAt(OffsetDateTime.now().minusMonths(1));
        activePremium.setUpdatedAt(OffsetDateTime.now().minusDays(1));

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(activePremium));
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.expireSubscriptionAndDowngradeToFree(subscriptionId);

        ArgumentCaptor<SubscriptionEntity> captor = ArgumentCaptor.forClass(SubscriptionEntity.class);
        verify(subscriptionRepository, times(2)).save(captor.capture());
        SubscriptionEntity expiredSaved = captor.getAllValues().get(0);
        SubscriptionEntity freeSaved = captor.getAllValues().get(1);

        assertThat(expiredSaved.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(freeSaved.getPlanType()).isEqualTo(PlanType.FREE);
        assertThat(freeSaved.getBillingType()).isEqualTo(BillingType.NONE);
        assertThat(freeSaved.getProvider()).isEqualTo(BillingProvider.NONE);
    }

    @Test
    void scheduleCancellationAtPeriodEnd_persistsReasonAndFeedback() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, userRepository, analyticsService);
        UUID userId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);

        SubscriptionEntity activePremium = new SubscriptionEntity();
        activePremium.setId(UUID.randomUUID());
        activePremium.setUser(user);
        activePremium.setPlanType(PlanType.PREMIUM);
        activePremium.setStatus(SubscriptionStatus.ACTIVE);
        activePremium.setBillingType(BillingType.SUBSCRIPTION);
        activePremium.setProvider(BillingProvider.XENDIT);
        activePremium.setStartAt(OffsetDateTime.now().minusDays(10));
        activePremium.setEndAt(OffsetDateTime.now().plusDays(20));
        activePremium.setCreatedAt(OffsetDateTime.now().minusDays(10));
        activePremium.setUpdatedAt(OffsetDateTime.now().minusDays(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(activePremium));
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionEntity saved = service.scheduleCancellationAtPeriodEnd(
                userId,
                SubscriptionCancellationReason.MISSING_FEATURES,
                "Need better export options."
        );

        assertThat(saved.isCancelAtPeriodEnd()).isTrue();
        assertThat(saved.getCancelledAt()).isNotNull();
        assertThat(saved.getCancellationReason()).isEqualTo(SubscriptionCancellationReason.MISSING_FEATURES);
        assertThat(saved.getCancellationFeedback()).isEqualTo("Need better export options.");
        verify(subscriptionRepository).save(activePremium);
    }

    @Test
    void activatePremiumSubscription_clearsScheduledCancellationWhenRenewed() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, userRepository, analyticsService);
        UUID userId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);

        SubscriptionEntity activePremium = new SubscriptionEntity();
        activePremium.setId(UUID.randomUUID());
        activePremium.setUser(user);
        activePremium.setPlanType(PlanType.PREMIUM);
        activePremium.setStatus(SubscriptionStatus.ACTIVE);
        activePremium.setBillingType(BillingType.SUBSCRIPTION);
        activePremium.setProvider(BillingProvider.XENDIT);
        activePremium.setStartAt(OffsetDateTime.now().minusDays(5));
        activePremium.setEndAt(OffsetDateTime.now().plusDays(10));
        activePremium.setCancelAtPeriodEnd(true);
        activePremium.setCancelledAt(OffsetDateTime.now().minusDays(1));
        activePremium.setCancellationReason(SubscriptionCancellationReason.TOO_EXPENSIVE);
        activePremium.setCancellationFeedback("Too much.");
        activePremium.setCreatedAt(OffsetDateTime.now().minusDays(5));
        activePremium.setUpdatedAt(OffsetDateTime.now().minusDays(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(activePremium));
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionEntity saved = service.activatePremiumSubscription(
                userId,
                BillingType.SUBSCRIPTION,
                BillingProvider.XENDIT,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(30),
                false,
                new SubscriptionService.ProviderMetadata("cus_123", "sub_123")
        );

        assertThat(saved.isCancelAtPeriodEnd()).isFalse();
        assertThat(saved.getCancelledAt()).isNull();
        assertThat(saved.getCancellationReason()).isNull();
        assertThat(saved.getCancellationFeedback()).isNull();
        verify(analyticsService, never()).trackEvent(any(), eq(AnalyticsEventType.SUBSCRIPTION_STARTED), any(), any());
    }
}
