package com.studysnap.backend.service.jobs;

import com.studysnap.backend.service.RetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetentionEmailScheduler {
    private final RetentionService retentionService;

    @Scheduled(cron = "${studysnap.retention.daily-cron:0 45 2 * * *}")
    public void runDaily() {
        RetentionService.DailyRetentionDispatchSummary summary = retentionService.sendDailyEmails();
        log.info(
                "retention.email.scheduler.daily sent inactivity={} weakConcept={} inactivityBudget={} sentToday={} inactivityAttempted={} inactivitySkippedForBudget={}",
                summary.inactivitySent(),
                summary.weakConceptSent(),
                summary.inactivityBudget(),
                summary.sentToday(),
                summary.inactivityAttempted(),
                summary.inactivitySkippedForBudget()
        );
    }

    @Scheduled(cron = "${studysnap.retention.weekly-cron:0 0 18 * * SUN}")
    public void runWeekly() {
        RetentionService.WeeklyRetentionDispatchSummary summary = retentionService.sendWeeklySummaryEmails();
        log.info("retention.email.scheduler.weekly sent weeklySummary={}", summary.weeklySummarySent());
    }
}
