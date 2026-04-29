package com.studysnap.backend.service;

import com.studysnap.backend.dto.SubscriptionPlanStatusResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionCancellationReason;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Service
@Transactional
@RequiredArgsConstructor
public class SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    public record ProviderMetadata(
            String providerCustomerId,
            String providerSubscriptionId
    ) {
    }

    public record PlanSnapshot(
            PlanType planType,
            boolean cancelAtPeriodEnd,
            OffsetDateTime premiumEndsAt,
            OffsetDateTime cancelledAt
    ) {
        public SubscriptionPlanStatusResponse toResponse() {
            return new SubscriptionPlanStatusResponse(cancelAtPeriodEnd, premiumEndsAt, cancelledAt);
        }
    }

    public SubscriptionEntity createDefaultFreeSubscription(UserEntity user) {
        OffsetDateTime now = OffsetDateTime.now();
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        applyFreeAccess(subscription, now);
        subscription.setCreatedAt(now);
        subscription.setUpdatedAt(now);
        SubscriptionEntity saved = subscriptionRepository.save(subscription);
        syncDirectPremiumFlags(user, false, null, null, now);
        return saved;
    }

    @Transactional(readOnly = true)
    public PlanType resolvePlan(UUID userId) {
        return getPlanSnapshot(userId).planType();
    }

    @Transactional(readOnly = true)
    public PlanSnapshot getPlanSnapshot(UUID userId) {
        UserEntity user = requireUser(userId);
        OffsetDateTime now = OffsetDateTime.now();
        Optional<PlanSnapshot> subscriptionSnapshot = subscriptionRepository.findByUser_IdAndPlanTypeAndStatusOrderByUpdatedAtDesc(
                        userId,
                        PlanType.PREMIUM,
                        SubscriptionStatus.ACTIVE
                ).stream()
                .filter(subscription -> hasActivePremiumAccess(subscription, now))
                .findFirst()
                .map(subscription -> new PlanSnapshot(
                        PlanType.PREMIUM,
                        subscription.isCancelAtPeriodEnd(),
                        subscription.getEndAt(),
                        subscription.getCancelledAt()
                ));
        if (subscriptionSnapshot.isPresent()) {
            return subscriptionSnapshot.get();
        }
        if (hasDirectPremiumAccess(user, now)) {
            return new PlanSnapshot(
                    PlanType.PREMIUM,
                    false,
                    user.getPremiumExpiresAt(),
                    null
            );
        }
        return new PlanSnapshot(PlanType.FREE, false, null, null);
    }

    public String ensureProviderCustomerId(
            UserEntity user,
            BillingProvider provider,
            Supplier<String> customerIdSupplier
    ) {
        requireBillableProvider(provider, "Billing provider is required.");

        SubscriptionEntity target = ensureLatestSubscription(user);
        String existingCustomerId = normalizeReference(target.getProviderCustomerId());
        if (provider == target.getProvider() && existingCustomerId != null) {
            return existingCustomerId;
        }

        String createdCustomerId = normalizeReference(customerIdSupplier.get());
        if (createdCustomerId == null) {
            throw new AppException(
                    "PROVIDER_CUSTOMER_ID_MISSING",
                    "Could not create billing customer.",
                    HttpStatus.BAD_GATEWAY
            );
        }

        target.setProvider(provider);
        target.setProviderCustomerId(createdCustomerId);
        target.setUpdatedAt(OffsetDateTime.now());
        subscriptionRepository.save(target);
        return createdCustomerId;
    }

    public SubscriptionEntity activatePremiumSubscription(
            UUID userId,
            BillingType billingType,
            BillingProvider provider,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean cancelAtPeriodEnd,
            ProviderMetadata providerMetadata
    ) {
        if (billingType == null || billingType == BillingType.NONE) {
            throw new AppException(
                    "INVALID_BILLING_TYPE",
                    "Billing type must be SUBSCRIPTION or PREPAID for Premium activation.",
                    HttpStatus.BAD_REQUEST
            );
        }
        requireBillableProvider(provider, "Billing provider is required for Premium activation.");

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime effectiveStartAt = startAt == null ? now : startAt;
        if (endAt != null && !endAt.isAfter(effectiveStartAt)) {
            throw new AppException(
                    "INVALID_SUBSCRIPTION_WINDOW",
                    "Subscription end date must be after start date.",
                    HttpStatus.BAD_REQUEST
            );
        }

        UserEntity user = requireUser(userId);
        SubscriptionEntity target = ensureLatestSubscription(user);
        boolean wasActivePremium = hasActivePremiumAccess(target, now);

        String normalizedProviderCustomerId = providerMetadata == null
                ? null
                : normalizeReference(providerMetadata.providerCustomerId());
        String normalizedProviderSubscriptionId = providerMetadata == null
                ? null
                : normalizeReference(providerMetadata.providerSubscriptionId());

        target.setPlanType(PlanType.PREMIUM);
        target.setStatus(SubscriptionStatus.ACTIVE);
        target.setBillingType(billingType);
        target.setProvider(provider);
        if (normalizedProviderCustomerId != null) {
            target.setProviderCustomerId(normalizedProviderCustomerId);
        }
        if (normalizedProviderSubscriptionId != null) {
            target.setProviderSubscriptionId(normalizedProviderSubscriptionId);
        }
        target.setStartAt(effectiveStartAt);
        target.setEndAt(endAt);
        target.setCancelAtPeriodEnd(cancelAtPeriodEnd);
        if (!cancelAtPeriodEnd) {
            clearCancellationDetails(target);
        } else if (target.getCancelledAt() == null) {
            target.setCancelledAt(now);
        }
        target.setUpdatedAt(now);
        SubscriptionEntity saved = subscriptionRepository.save(target);
        syncDirectPremiumFlags(user, true, effectiveStartAt, endAt, now);
        if (!wasActivePremium) {
            analyticsService.trackEvent(userId, AnalyticsEventType.SUBSCRIPTION_STARTED, saved.getId(), buildSubscriptionMetadata(
                    billingType,
                    provider,
                    cancelAtPeriodEnd
            ));
        }
        return saved;
    }

    public SubscriptionEntity activatePrepaidSubscription(
            UUID userId,
            int durationDays,
            BillingProvider provider,
            ProviderMetadata providerMetadata
    ) {
        if (durationDays <= 0) {
            throw new AppException(
                    "INVALID_PREPAID_DURATION",
                    "Prepaid duration must be greater than zero.",
                    HttpStatus.BAD_REQUEST
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        return activatePremiumSubscription(
                userId,
                BillingType.PREPAID,
                provider,
                now,
                now.plusDays(durationDays),
                true,
                providerMetadata
        );
    }

    public SubscriptionEntity scheduleCancellationAtPeriodEnd(
            UUID userId,
            SubscriptionCancellationReason reason,
            String feedback
    ) {
        UserEntity user = requireUser(userId);
        SubscriptionEntity target = ensureLatestSubscription(user);
        OffsetDateTime now = OffsetDateTime.now();

        if (target.getPlanType() != PlanType.PREMIUM
                || target.getStatus() != SubscriptionStatus.ACTIVE
                || !hasActivePremiumAccess(target, now)) {
            throw new AppException(
                    "PREMIUM_SUBSCRIPTION_NOT_ACTIVE",
                    "There is no active Premium subscription to cancel.",
                    HttpStatus.CONFLICT
            );
        }

        if (target.getEndAt() == null) {
            throw new AppException(
                    "SUBSCRIPTION_PERIOD_END_UNAVAILABLE",
                    "Could not determine the current billing period end.",
                    HttpStatus.CONFLICT
            );
        }

        target.setCancelAtPeriodEnd(true);
        target.setCancelledAt(now);
        target.setCancellationReason(reason);
        target.setCancellationFeedback(normalizeFeedback(feedback));
        target.setUpdatedAt(now);
        return subscriptionRepository.save(target);
    }

    public SubscriptionEntity downgradeToFree(UUID userId) {
        UserEntity user = requireUser(userId);
        SubscriptionEntity target = ensureLatestSubscription(user);

        OffsetDateTime now = OffsetDateTime.now();
        applyFreeAccess(target, now);
        target.setUpdatedAt(now);
        SubscriptionEntity saved = subscriptionRepository.save(target);
        syncDirectPremiumFlags(user, false, null, null, now);
        return saved;
    }

    public void expireSubscriptionAndDowngradeToFree(UUID subscriptionId) {
        SubscriptionEntity subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            return;
        }

        if (subscription.getPlanType() != PlanType.PREMIUM || subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (subscription.getEndAt() == null || !subscription.getEndAt().isBefore(now)) {
            return;
        }

        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setUpdatedAt(now);
        subscriptionRepository.save(subscription);

        createDefaultFreeSubscription(subscription.getUser());
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findUserIdByProviderCustomerId(BillingProvider provider, String providerCustomerIdRaw) {
        return findUserIdByProviderReference(
                provider,
                providerCustomerIdRaw,
                subscriptionRepository::findFirstByProviderAndProviderCustomerIdOrderByUpdatedAtDesc
        );
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findUserIdByProviderSubscriptionId(BillingProvider provider, String providerSubscriptionIdRaw) {
        return findUserIdByProviderReference(
                provider,
                providerSubscriptionIdRaw,
                subscriptionRepository::findFirstByProviderAndProviderSubscriptionIdOrderByUpdatedAtDesc
        );
    }

    private boolean hasActivePremiumAccess(SubscriptionEntity subscription, OffsetDateTime now) {
        if (subscription.getPlanType() != PlanType.PREMIUM || subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            return false;
        }
        OffsetDateTime endAt = subscription.getEndAt();
        return endAt == null || now.isBefore(endAt);
    }

    private boolean hasDirectPremiumAccess(UserEntity user, OffsetDateTime now) {
        if (!Boolean.TRUE.equals(user.getIsPremium())) {
            return false;
        }
        OffsetDateTime premiumExpiresAt = user.getPremiumExpiresAt();
        return premiumExpiresAt == null || now.isBefore(premiumExpiresAt);
    }

    private SubscriptionEntity ensureLatestSubscription(UserEntity user) {
        return subscriptionRepository.findFirstByUser_IdOrderByCreatedAtDesc(user.getId())
                .orElseGet(() -> createDefaultFreeSubscription(user));
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    private String normalizeReference(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private java.util.Map<String, Object> buildSubscriptionMetadata(
            BillingType billingType,
            BillingProvider provider,
            boolean cancelAtPeriodEnd
    ) {
        java.util.LinkedHashMap<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (billingType != null) {
            metadata.put("billingType", billingType.name());
        }
        if (provider != null) {
            metadata.put("provider", provider.name());
        }
        metadata.put("cancelAtPeriodEnd", cancelAtPeriodEnd);
        return metadata;
    }

    private void requireBillableProvider(BillingProvider provider, String message) {
        if (!isBillableProvider(provider)) {
            throw new AppException(
                    "INVALID_BILLING_PROVIDER",
                    message,
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private boolean isBillableProvider(BillingProvider provider) {
        return provider != null && provider != BillingProvider.NONE;
    }

    private Optional<UUID> findUserIdByProviderReference(
            BillingProvider provider,
            String rawReference,
            BiFunction<BillingProvider, String, Optional<SubscriptionEntity>> lookup
    ) {
        String normalizedReference = normalizeReference(rawReference);
        if (!isBillableProvider(provider) || normalizedReference == null) {
            return Optional.empty();
        }
        return lookup.apply(provider, normalizedReference)
                .map(subscription -> subscription.getUser().getId());
    }

    private void applyFreeAccess(SubscriptionEntity target, OffsetDateTime now) {
        target.setPlanType(PlanType.FREE);
        target.setStatus(SubscriptionStatus.ACTIVE);
        target.setBillingType(BillingType.NONE);
        target.setProvider(BillingProvider.NONE);
        target.setProviderCustomerId(null);
        target.setProviderSubscriptionId(null);
        target.setStartAt(now);
        target.setEndAt(null);
        clearCancellationDetails(target);
    }

    private void syncDirectPremiumFlags(
            UserEntity user,
            boolean premiumActive,
            OffsetDateTime premiumActivatedAt,
            OffsetDateTime premiumExpiresAt,
            OffsetDateTime updatedAt
    ) {
        user.setIsPremium(premiumActive);
        user.setPremiumActivatedAt(premiumActive ? premiumActivatedAt : null);
        user.setPremiumExpiresAt(premiumActive ? premiumExpiresAt : null);
        user.setUpdatedAt(updatedAt);
        userRepository.save(user);
    }

    private void clearCancellationDetails(SubscriptionEntity target) {
        target.setCancelAtPeriodEnd(false);
        target.setCancelledAt(null);
        target.setCancellationReason(null);
        target.setCancellationFeedback(null);
    }

    private String normalizeFeedback(String feedback) {
        if (feedback == null) {
            return null;
        }
        String normalized = feedback.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
