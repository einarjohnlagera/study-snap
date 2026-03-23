package com.studysnap.backend.repository;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
    Optional<SubscriptionEntity> findFirstByUser_IdAndStatusOrderByCreatedAtDesc(UUID userId, SubscriptionStatus status);
    Optional<SubscriptionEntity> findFirstByUser_IdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUser_IdAndPlanType(UUID userId, PlanType planType);

    Optional<SubscriptionEntity> findFirstByUser_IdAndPlanTypeAndStatusOrderByUpdatedAtDesc(
            UUID userId,
            PlanType planType,
            SubscriptionStatus status
    );

    List<SubscriptionEntity> findByUser_IdAndPlanTypeAndStatusOrderByUpdatedAtDesc(
            UUID userId,
            PlanType planType,
            SubscriptionStatus status
    );

    Optional<SubscriptionEntity> findFirstByProviderAndProviderCustomerIdOrderByUpdatedAtDesc(
            BillingProvider provider,
            String providerCustomerId
    );

    Optional<SubscriptionEntity> findFirstByProviderAndProviderSubscriptionIdOrderByUpdatedAtDesc(
            BillingProvider provider,
            String providerSubscriptionId
    );

    List<SubscriptionEntity> findByPlanTypeAndStatusAndEndAtBefore(
            PlanType planType,
            SubscriptionStatus status,
            OffsetDateTime endAt
    );
}
