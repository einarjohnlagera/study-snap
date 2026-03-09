package com.studysnap.backend.service;

import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;

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
}
