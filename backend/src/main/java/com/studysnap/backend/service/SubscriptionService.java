package com.studysnap.backend.service;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Transactional
@RequiredArgsConstructor
public class SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    public record ProviderMetadata(
            String providerCustomerId,
            String providerSubscriptionId
    ) {
    }

    public SubscriptionEntity createDefaultFreeSubscription(UserEntity user) {
        OffsetDateTime now = OffsetDateTime.now();
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setPlanType(PlanType.FREE);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingType(BillingType.NONE);
        subscription.setProvider(BillingProvider.NONE);
        subscription.setProviderCustomerId(null);
        subscription.setProviderSubscriptionId(null);
        subscription.setStartAt(now);
        subscription.setEndAt(null);
        subscription.setCreatedAt(now);
        subscription.setUpdatedAt(now);
        return subscriptionRepository.save(subscription);
    }

    @Transactional(readOnly = true)
    public PlanType resolvePlan(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        return subscriptionRepository.findByUser_IdAndPlanTypeAndStatusOrderByUpdatedAtDesc(
                        userId,
                        PlanType.PREMIUM,
                        SubscriptionStatus.ACTIVE
                ).stream()
                .filter(subscription -> hasActivePremiumAccess(subscription, now))
                .findFirst()
                .map(_ -> PlanType.PREMIUM)
                .orElse(PlanType.FREE);
    }

    public String ensureProviderCustomerId(
            UserEntity user,
            BillingProvider provider,
            Supplier<String> customerIdSupplier
    ) {
        if (provider == null || provider == BillingProvider.NONE) {
            throw new AppException(
                    "INVALID_BILLING_PROVIDER",
                    "Billing provider is required.",
                    HttpStatus.BAD_REQUEST
            );
        }

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
            ProviderMetadata providerMetadata
    ) {
        if (billingType == null || billingType == BillingType.NONE) {
            throw new AppException(
                    "INVALID_BILLING_TYPE",
                    "Billing type must be SUBSCRIPTION or PREPAID for Premium activation.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (provider == null || provider == BillingProvider.NONE) {
            throw new AppException(
                    "INVALID_BILLING_PROVIDER",
                    "Billing provider is required for Premium activation.",
                    HttpStatus.BAD_REQUEST
            );
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime effectiveStartAt = startAt == null ? now : startAt;
        if (billingType == BillingType.PREPAID && endAt == null) {
            throw new AppException(
                    "INVALID_PREPAID_SUBSCRIPTION",
                    "Prepaid subscriptions require an end date.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (endAt != null && !endAt.isAfter(effectiveStartAt)) {
            throw new AppException(
                    "INVALID_SUBSCRIPTION_WINDOW",
                    "Subscription end date must be after start date.",
                    HttpStatus.BAD_REQUEST
            );
        }

        UserEntity user = requireUser(userId);
        SubscriptionEntity target = ensureLatestSubscription(user);

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
        target.setUpdatedAt(now);
        return subscriptionRepository.save(target);
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
                providerMetadata
        );
    }

    public SubscriptionEntity downgradeToFree(UUID userId) {
        UserEntity user = requireUser(userId);
        SubscriptionEntity target = ensureLatestSubscription(user);

        OffsetDateTime now = OffsetDateTime.now();
        target.setPlanType(PlanType.FREE);
        target.setStatus(SubscriptionStatus.ACTIVE);
        target.setBillingType(BillingType.NONE);
        target.setProvider(BillingProvider.NONE);
        target.setProviderCustomerId(null);
        target.setProviderSubscriptionId(null);
        target.setStartAt(now);
        target.setEndAt(null);
        target.setUpdatedAt(now);
        return subscriptionRepository.save(target);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findUserIdByProviderCustomerId(BillingProvider provider, String providerCustomerIdRaw) {
        String providerCustomerId = normalizeReference(providerCustomerIdRaw);
        if (providerCustomerId == null || provider == null || provider == BillingProvider.NONE) {
            return Optional.empty();
        }

        Optional<SubscriptionEntity> byProvider = subscriptionRepository
                .findFirstByProviderAndProviderCustomerIdOrderByUpdatedAtDesc(provider, providerCustomerId);
        return byProvider.map(subscription -> subscription.getUser().getId());
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findUserIdByProviderSubscriptionId(BillingProvider provider, String providerSubscriptionIdRaw) {
        String providerSubscriptionId = normalizeReference(providerSubscriptionIdRaw);
        if (providerSubscriptionId == null || provider == null || provider == BillingProvider.NONE) {
            return Optional.empty();
        }

        Optional<SubscriptionEntity> byProvider = subscriptionRepository
                .findFirstByProviderAndProviderSubscriptionIdOrderByUpdatedAtDesc(provider, providerSubscriptionId);
        return byProvider.map(subscription -> subscription.getUser().getId());
    }

    private boolean hasActivePremiumAccess(SubscriptionEntity subscription, OffsetDateTime now) {
        if (subscription.getPlanType() != PlanType.PREMIUM || subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            return false;
        }
        OffsetDateTime endAt = subscription.getEndAt();
        return endAt == null || now.isBefore(endAt);
    }

    private SubscriptionEntity ensureLatestSubscription(UserEntity user) {
        return subscriptionRepository.findFirstByUser_IdOrderByCreatedAtDesc(user.getId())
                .orElseGet(() -> createDefaultFreeSubscription(user));
    }

    private UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(
                        "USER_NOT_FOUND",
                        "User not found.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private String normalizeReference(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
