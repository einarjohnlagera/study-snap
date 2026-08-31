package com.studysnap.backend.service.jobs;

import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryJob {
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "${studysnap.billing.subscription-expiry-cron:0 30 2 * * *}")
    public void run() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<SubscriptionEntity> expiredPaidSubscriptions = subscriptionRepository.findByPlanTypeInAndStatusAndEndAtBefore(
                List.of(PlanType.PLUS, PlanType.PRO),
                SubscriptionStatus.ACTIVE,
                now
        );

        for (SubscriptionEntity subscription : expiredPaidSubscriptions) {
            subscriptionService.expireSubscriptionAndDowngradeToFree(subscription.getId());
            log.info(
                    "billing.subscription.expiry downgraded userId={} subscriptionId={} endAt={}",
                    subscription.getUser().getId(),
                    subscription.getId(),
                    subscription.getEndAt()
            );
        }
    }
}
