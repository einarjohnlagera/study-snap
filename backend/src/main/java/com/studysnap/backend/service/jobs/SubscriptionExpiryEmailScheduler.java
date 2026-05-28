package com.studysnap.backend.service.jobs;

import com.studysnap.backend.service.SubscriptionExpiryEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryEmailScheduler {
    private final SubscriptionExpiryEmailService subscriptionExpiryEmailService;

    @Scheduled(cron = "${studysnap.billing.expiry-email-cron:0 0 3 * * *}")
    public void run() {
        SubscriptionExpiryEmailService.SubscriptionExpiryEmailDispatchSummary summary =
                subscriptionExpiryEmailService.sendExpiryNotificationEmails();
        log.info(
                "billing.expiry.email sevenDaySent={} oneDaySent={} postExpirySent={}",
                summary.sevenDaySent(),
                summary.oneDaySent(),
                summary.postExpirySent()
        );
    }
}
