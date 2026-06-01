package com.studysnap.backend.repository;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {
    Optional<SubscriptionEntity> findFirstByUser_IdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUser_IdAndPlanTypeIn(UUID userId, Collection<PlanType> planTypes);

    List<SubscriptionEntity> findByUser_IdAndStatusOrderByUpdatedAtDesc(
            UUID userId,
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
            where s.planType in :planTypes
              and s.status = :status
              and (s.endAt is null or s.endAt > :now)
            order by s.updatedAt desc
            """)
    List<SubscriptionEntity> findCurrentlyActiveByPlanTypeInAndStatus(
            @Param("planTypes") Collection<PlanType> planTypes,
            @Param("status") SubscriptionStatus status,
            @Param("now") OffsetDateTime now
    );

    @Query("""
            select s.user.id
            from SubscriptionEntity s
            where s.planType in :planTypes
              and s.status = :status
              and (s.endAt is null or s.endAt > :now)
            """)
    List<UUID> findActiveUserIdsByPlanTypeInAndStatus(
            @Param("planTypes") Collection<PlanType> planTypes,
            @Param("status") SubscriptionStatus status,
            @Param("now") OffsetDateTime now
    );

    List<SubscriptionEntity> findByPlanTypeInAndStatusAndEndAtBefore(
            Collection<PlanType> planTypes,
            SubscriptionStatus status,
            OffsetDateTime endAt
    );

    List<SubscriptionEntity> findByPlanTypeInAndStatusAndEndAtBetween(
            Collection<PlanType> planTypes,
            SubscriptionStatus status,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd
    );
}
