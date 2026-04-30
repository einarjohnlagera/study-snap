package com.studysnap.backend.repository;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
    Optional<SubscriptionEntity> findFirstByUser_IdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUser_IdAndPlanType(UUID userId, PlanType planType);

    List<SubscriptionEntity> findByUser_IdAndStatusOrderByUpdatedAtDesc(
            UUID userId,
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

    @Query("""
            select s
            from SubscriptionEntity s
            where s.planType = :planType
              and s.status = :status
              and (s.endAt is null or s.endAt > :now)
            order by s.updatedAt desc
            """)
    List<SubscriptionEntity> findCurrentlyActiveByPlanTypeAndStatus(
            @Param("planType") PlanType planType,
            @Param("status") SubscriptionStatus status,
            @Param("now") OffsetDateTime now
    );

    List<SubscriptionEntity> findByPlanTypeAndStatusAndEndAtBefore(
            PlanType planType,
            SubscriptionStatus status,
            OffsetDateTime endAt
    );
}
