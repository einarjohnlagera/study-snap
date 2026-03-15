package com.studysnap.backend.service;

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

    public SubscriptionEntity createDefaultFreeSubscription(UserEntity user) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setPlanType(PlanType.FREE);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartAt(OffsetDateTime.now());
        subscription.setCreatedAt(OffsetDateTime.now());
        subscription.setUpdatedAt(OffsetDateTime.now());
        return subscriptionRepository.save(subscription);
    }

    @Transactional(readOnly = true)
    public PlanType resolvePlan(UUID userId) {
        return subscriptionRepository.findFirstByUser_IdAndStatusOrderByCreatedAtDesc(userId, SubscriptionStatus.ACTIVE)
                .map(SubscriptionEntity::getPlanType)
                .orElse(PlanType.FREE);
    }

    public String ensureStripeCustomerId(UserEntity user, Supplier<String> customerIdSupplier) {
        SubscriptionEntity subscription = ensureActiveSubscription(user);
        String existingCustomerId = normalizeStripeId(subscription.getStripeCustomerId());
        if (existingCustomerId != null) {
            return existingCustomerId;
        }

        String createdCustomerId = normalizeStripeId(customerIdSupplier.get());
        subscription.setStripeCustomerId(createdCustomerId);
        subscription.setUpdatedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);
        return createdCustomerId;
    }

    public void activatePremium(UUID userId, String stripeCustomerId, String stripeSubscriptionId) {
        Optional<SubscriptionEntity> existing = subscriptionRepository.findFirstByUser_IdAndStatusOrderByCreatedAtDesc(
                userId,
                SubscriptionStatus.ACTIVE
        );
        SubscriptionEntity target = existing.orElseGet(() -> userRepository.findById(userId)
                .map(this::createDefaultFreeSubscription)
                .orElseThrow(() -> new AppException(
                        "USER_NOT_FOUND",
                        "User not found.",
                        HttpStatus.NOT_FOUND
                )));

        target.setPlanType(PlanType.PREMIUM);
        target.setStatus(SubscriptionStatus.ACTIVE);
        if (normalizeStripeId(stripeCustomerId) != null) {
            target.setStripeCustomerId(normalizeStripeId(stripeCustomerId));
        }
        target.setStripeSubscriptionId(normalizeStripeId(stripeSubscriptionId));
        target.setEndAt(null);
        target.setUpdatedAt(OffsetDateTime.now());
        subscriptionRepository.save(target);
    }

    public void activatePremiumByStripeCustomer(String stripeCustomerId, String stripeSubscriptionId) {
        String normalizedCustomerId = normalizeStripeId(stripeCustomerId);
        if (normalizedCustomerId == null) {
            return;
        }

        subscriptionRepository.findFirstByStripeCustomerIdOrderByUpdatedAtDesc(normalizedCustomerId)
                .ifPresent(subscription -> {
                    subscription.setPlanType(PlanType.PREMIUM);
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    subscription.setStripeSubscriptionId(normalizeStripeId(stripeSubscriptionId));
                    subscription.setEndAt(null);
                    subscription.setUpdatedAt(OffsetDateTime.now());
                    subscriptionRepository.save(subscription);
                });
    }

    public void revertToFreeByStripeCustomer(String stripeCustomerId) {
        String normalizedCustomerId = normalizeStripeId(stripeCustomerId);
        if (normalizedCustomerId == null) {
            return;
        }

        subscriptionRepository.findFirstByStripeCustomerIdOrderByUpdatedAtDesc(normalizedCustomerId)
                .ifPresent(subscription -> {
                    subscription.setPlanType(PlanType.FREE);
                    subscription.setStatus(SubscriptionStatus.ACTIVE);
                    subscription.setStripeSubscriptionId(null);
                    subscription.setEndAt(OffsetDateTime.now());
                    subscription.setUpdatedAt(OffsetDateTime.now());
                    subscriptionRepository.save(subscription);
                });
    }

    private SubscriptionEntity ensureActiveSubscription(UserEntity user) {
        return subscriptionRepository.findFirstByUser_IdAndStatusOrderByCreatedAtDesc(
                        user.getId(),
                        SubscriptionStatus.ACTIVE
                )
                .orElseGet(() -> createDefaultFreeSubscription(user));
    }

    private String normalizeStripeId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
