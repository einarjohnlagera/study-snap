package com.studysnap.backend.service;

import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
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

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.of(2026, 4, 29, 4, 0, 0, 0, ZoneOffset.UTC);
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME.toInstant(), ZoneOffset.UTC);

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AnalyticsService analyticsService;

    @Test
    void getPlanSnapshot_returnsPremiumFromActiveSubscriptionOnly() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, userRepository, analyticsService, FIXED_CLOCK);
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        SubscriptionEntity activePremium = buildPremiumSubscription(
                user,
                FIXED_TIME.minusDays(5),
                FIXED_TIME.plusDays(25)
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser_IdAndStatusOrderByUpdatedAtDesc(
                userId,
                SubscriptionStatus.ACTIVE
        )).thenReturn(List.of(activePremium));

        SubscriptionService.PlanSnapshot snapshot = service.getPlanSnapshot(userId);

        assertThat(snapshot.planType()).isEqualTo(PlanType.PREMIUM);
        assertThat(snapshot.premiumEndsAt()).isEqualTo(FIXED_TIME.plusDays(25));
        assertThat(service.hasActiveSubscription(userId, PlanType.PREMIUM)).isTrue();
    }

    @Test
    void getPlanSnapshot_returnsFreeWhenPremiumSubscriptionIsExpired() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, userRepository, analyticsService, FIXED_CLOCK);
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        SubscriptionEntity expiredPremium = buildPremiumSubscription(
                user,
                FIXED_TIME.minusDays(40),
                FIXED_TIME.minusDays(1)
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser_IdAndStatusOrderByUpdatedAtDesc(
                userId,
                SubscriptionStatus.ACTIVE
        )).thenReturn(List.of(expiredPremium));

        SubscriptionService.PlanSnapshot snapshot = service.getPlanSnapshot(userId);

        assertThat(snapshot.planType()).isEqualTo(PlanType.FREE);
        assertThat(service.hasActiveSubscription(userId, PlanType.PREMIUM)).isFalse();
    }

    @Test
    void activatePremiumSubscription_expiresCurrentFreeSubscriptionAndCreatesPremiumHistoryRow() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, userRepository, analyticsService, FIXED_CLOCK);
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        SubscriptionEntity activeFree = buildFreeSubscription(user, FIXED_TIME.minusDays(20));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser_IdAndStatusOrderByUpdatedAtDesc(
                userId,
                SubscriptionStatus.ACTIVE
        )).thenReturn(List.of(activeFree));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionEntity saved = service.activatePremiumSubscription(
                userId,
                BillingType.PREPAID,
                BillingProvider.XENDIT,
                FIXED_TIME,
                FIXED_TIME.plusDays(30),
                false,
                new SubscriptionService.ProviderMetadata(null, "invoice_123")
        );

        assertThat(saved.getPlanType()).isEqualTo(PlanType.PREMIUM);
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getStartAt()).isEqualTo(FIXED_TIME);
        assertThat(saved.getEndAt()).isEqualTo(FIXED_TIME.plusDays(30));
        assertThat(saved.getProvider()).isEqualTo(BillingProvider.XENDIT);
        assertThat(saved.getBillingType()).isEqualTo(BillingType.PREPAID);
        assertThat(saved.getId()).isNotEqualTo(activeFree.getId());
        assertThat(activeFree.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(activeFree.getEndAt()).isEqualTo(FIXED_TIME);
        verify(subscriptionRepository).saveAll(List.of(activeFree));
        verify(subscriptionRepository).flush();
        verify(analyticsService).trackEvent(eq(userId), eq(AnalyticsEventType.SUBSCRIPTION_STARTED), eq(saved.getId()), any());
    }

    @Test
    void activatePremiumSubscription_extendsExistingPremiumInsteadOfCreatingDuplicate() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, userRepository, analyticsService, FIXED_CLOCK);
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        SubscriptionEntity activePremium = buildPremiumSubscription(
                user,
                FIXED_TIME.minusDays(5),
                FIXED_TIME.plusDays(10)
        );
        activePremium.setCancelAtPeriodEnd(true);
        activePremium.setCancelledAt(FIXED_TIME.minusDays(1));
        activePremium.setCancellationReason(SubscriptionCancellationReason.TOO_EXPENSIVE);
        activePremium.setCancellationFeedback("Too much.");
        SubscriptionEntity activeFree = buildFreeSubscription(user, FIXED_TIME.minusMonths(2));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser_IdAndStatusOrderByUpdatedAtDesc(
                userId,
                SubscriptionStatus.ACTIVE
        )).thenReturn(List.of(activePremium, activeFree));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionEntity saved = service.activatePremiumSubscription(
                userId,
                BillingType.PREPAID,
                BillingProvider.XENDIT,
                FIXED_TIME,
                FIXED_TIME.plusDays(30),
                false,
                new SubscriptionService.ProviderMetadata(null, "invoice_456")
        );

        assertThat(saved.getId()).isEqualTo(activePremium.getId());
        assertThat(saved.getStartAt()).isEqualTo(FIXED_TIME.minusDays(5));
        assertThat(saved.getEndAt()).isEqualTo(FIXED_TIME.plusDays(40));
        assertThat(saved.isCancelAtPeriodEnd()).isFalse();
        assertThat(saved.getCancelledAt()).isNull();
        assertThat(saved.getCancellationReason()).isNull();
        assertThat(saved.getCancellationFeedback()).isNull();
        assertThat(activeFree.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(activeFree.getEndAt()).isEqualTo(FIXED_TIME);
        verify(subscriptionRepository).saveAll(List.of(activeFree));
        verify(subscriptionRepository).flush();
        verify(subscriptionRepository, times(1)).save(activePremium);
        verify(analyticsService, never()).trackEvent(any(), eq(AnalyticsEventType.SUBSCRIPTION_STARTED), any(), any());
    }

    @Test
    void scheduleCancellationAtPeriodEnd_persistsReasonAndFeedback() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, userRepository, analyticsService, FIXED_CLOCK);
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        SubscriptionEntity activePremium = buildPremiumSubscription(
                user,
                FIXED_TIME.minusDays(10),
                FIXED_TIME.plusDays(20)
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser_IdAndStatusOrderByUpdatedAtDesc(
                userId,
                SubscriptionStatus.ACTIVE
        )).thenReturn(List.of(activePremium));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionEntity saved = service.scheduleCancellationAtPeriodEnd(
                userId,
                SubscriptionCancellationReason.MISSING_FEATURES,
                "Need better export options."
        );

        assertThat(saved.isCancelAtPeriodEnd()).isTrue();
        assertThat(saved.getCancelledAt()).isEqualTo(FIXED_TIME);
        assertThat(saved.getCancellationReason()).isEqualTo(SubscriptionCancellationReason.MISSING_FEATURES);
        assertThat(saved.getCancellationFeedback()).isEqualTo("Need better export options.");
    }

    @Test
    void expireSubscriptionAndDowngradeToFree_marksExpiredAndCreatesFreeSubscription() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, userRepository, analyticsService, FIXED_CLOCK);
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UserEntity user = buildUser(userId);
        SubscriptionEntity activePremium = buildPremiumSubscription(
                user,
                FIXED_TIME.minusMonths(1),
                FIXED_TIME.minusDays(1)
        );
        activePremium.setId(subscriptionId);

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(activePremium));
        when(subscriptionRepository.findByUser_IdAndStatusOrderByUpdatedAtDesc(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of());
        when(subscriptionRepository.saveAndFlush(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.expireSubscriptionAndDowngradeToFree(subscriptionId);

        ArgumentCaptor<SubscriptionEntity> freeCaptor = ArgumentCaptor.forClass(SubscriptionEntity.class);
        verify(subscriptionRepository).saveAndFlush(activePremium);
        verify(subscriptionRepository).save(freeCaptor.capture());
        SubscriptionEntity freeSaved = freeCaptor.getValue();

        assertThat(activePremium.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(activePremium.getEndAt()).isEqualTo(FIXED_TIME.minusDays(1));
        assertThat(freeSaved.getPlanType()).isEqualTo(PlanType.FREE);
        assertThat(freeSaved.getBillingType()).isEqualTo(BillingType.NONE);
        assertThat(freeSaved.getProvider()).isEqualTo(BillingProvider.NONE);
    }

    @Test
    void getPlanSnapshot_fallsBackToFreeWhenPremiumHistoryExistsButOnlyFreeIsCurrent() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, userRepository, analyticsService, FIXED_CLOCK);
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        SubscriptionEntity expiredPremium = buildPremiumSubscription(
                user,
                FIXED_TIME.minusDays(40),
                FIXED_TIME.minusDays(5)
        );
        SubscriptionEntity activeFree = buildFreeSubscription(user, FIXED_TIME.minusDays(4));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser_IdAndStatusOrderByUpdatedAtDesc(
                userId,
                SubscriptionStatus.ACTIVE
        )).thenReturn(List.of(activeFree, expiredPremium));

        SubscriptionService.PlanSnapshot snapshot = service.getPlanSnapshot(userId);

        assertThat(snapshot.planType()).isEqualTo(PlanType.FREE);
        assertThat(service.hasActiveSubscription(userId, PlanType.FREE)).isTrue();
        assertThat(service.hasActiveSubscription(userId, PlanType.PREMIUM)).isFalse();
    }

    private UserEntity buildUser(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCreatedAt(FIXED_TIME.minusMonths(2));
        return user;
    }

    private SubscriptionEntity buildPremiumSubscription(
            UserEntity user,
            OffsetDateTime startAt,
            OffsetDateTime endAt
    ) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setPlanType(PlanType.PREMIUM);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingType(BillingType.PREPAID);
        subscription.setProvider(BillingProvider.XENDIT);
        subscription.setStartAt(startAt);
        subscription.setEndAt(endAt);
        subscription.setCreatedAt(startAt);
        subscription.setUpdatedAt(startAt);
        return subscription;
    }

    private SubscriptionEntity buildFreeSubscription(UserEntity user, OffsetDateTime startAt) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setPlanType(PlanType.FREE);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingType(BillingType.NONE);
        subscription.setProvider(BillingProvider.NONE);
        subscription.setStartAt(startAt);
        subscription.setEndAt(null);
        subscription.setCreatedAt(startAt);
        subscription.setUpdatedAt(startAt);
        return subscription;
    }
}
