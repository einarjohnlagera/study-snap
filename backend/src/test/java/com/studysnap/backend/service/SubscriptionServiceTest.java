package com.studysnap.backend.service;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PlanType;
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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    void expireSubscriptionAndDowngradeToFree_marksExpiredAndCreatesFreeSubscription() {
        SubscriptionService service = new SubscriptionService(subscriptionRepository, userRepository);
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);

        SubscriptionEntity activePremium = new SubscriptionEntity();
        activePremium.setId(subscriptionId);
        activePremium.setUser(user);
        activePremium.setPlanType(PlanType.PREMIUM);
        activePremium.setStatus(SubscriptionStatus.ACTIVE);
        activePremium.setBillingType(BillingType.SUBSCRIPTION);
        activePremium.setProvider(BillingProvider.PAYMONGO);
        activePremium.setStartAt(OffsetDateTime.now().minusMonths(1));
        activePremium.setEndAt(OffsetDateTime.now().minusDays(1));
        activePremium.setCreatedAt(OffsetDateTime.now().minusMonths(1));
        activePremium.setUpdatedAt(OffsetDateTime.now().minusDays(1));

        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(activePremium));
        when(subscriptionRepository.save(any(SubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.expireSubscriptionAndDowngradeToFree(subscriptionId);

        ArgumentCaptor<SubscriptionEntity> captor = ArgumentCaptor.forClass(SubscriptionEntity.class);
        verify(subscriptionRepository, times(2)).save(captor.capture());
        SubscriptionEntity expiredSaved = captor.getAllValues().get(0);
        SubscriptionEntity freeSaved = captor.getAllValues().get(1);

        assertThat(expiredSaved.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(freeSaved.getPlanType()).isEqualTo(PlanType.FREE);
        assertThat(freeSaved.getBillingType()).isEqualTo(BillingType.NONE);
        assertThat(freeSaved.getProvider()).isEqualTo(BillingProvider.NONE);
    }
}
