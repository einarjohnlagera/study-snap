package com.studysnap.backend.service.jobs;

import com.studysnap.backend.service.AccountPurgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountPurgeScheduler {
    private final AccountPurgeService accountPurgeService;

    @Scheduled(cron = "${studysnap.account.purge-cron:0 30 3 * * *}")
    public void run() {
        AccountPurgeService.AccountPurgeSummary summary = accountPurgeService.purgeEligibleAccounts(OffsetDateTime.now());
        log.info(
                "account.purge.scheduler purged={} failed={}",
                summary.purgedCount(),
                summary.failedCount()
        );
    }
}
