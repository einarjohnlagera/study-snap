package com.studysnap.backend.service.jobs;

import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryJobTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionService subscriptionService;

    @Test
    void run_downgradesExpiredActivePremiumSubscriptions() {
        SubscriptionExpiryJob job = new SubscriptionExpiryJob(subscriptionRepository, subscriptionService);

        SubscriptionEntity expired = new SubscriptionEntity();
        expired.setId(UUID.randomUUID());
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        expired.setUser(user);
        expired.setPlanType(PlanType.PREMIUM);
        expired.setStatus(SubscriptionStatus.ACTIVE);
        expired.setEndAt(OffsetDateTime.now().minusDays(1));

        when(subscriptionRepository.findByPlanTypeAndStatusAndEndAtBefore(
                eq(PlanType.PREMIUM),
                eq(SubscriptionStatus.ACTIVE),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(expired));

        job.run();

        verify(subscriptionService).expireSubscriptionAndDowngradeToFree(expired.getId());
    }
}
