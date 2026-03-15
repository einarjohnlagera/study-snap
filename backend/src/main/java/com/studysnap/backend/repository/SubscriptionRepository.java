package com.studysnap.backend.repository;

import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
    Optional<SubscriptionEntity> findFirstByUser_IdAndStatusOrderByCreatedAtDesc(UUID userId, SubscriptionStatus status);
    Optional<SubscriptionEntity> findFirstByUser_IdOrderByCreatedAtDesc(UUID userId);
    Optional<SubscriptionEntity> findFirstByStripeCustomerIdOrderByUpdatedAtDesc(String stripeCustomerId);
}
