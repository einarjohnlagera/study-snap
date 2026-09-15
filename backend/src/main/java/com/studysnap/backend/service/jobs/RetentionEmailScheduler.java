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
    // Pinned so the cron's notion of "today" matches RetentionService's EMAIL_BUDGET_ZONE. Unpinned,
    // cron used ZoneId.systemDefault() while the review-day filter used Asia/Manila, so on a UTC host
    // the two disagreed about which weekday it was.
    private static final String DISPATCH_ZONE = "Asia/Manila";

    private final RetentionService retentionService;

    // The due-concepts digest dispatches DAILY and selects recipients by their chosen review days.
    // It previously rode the weekly Sunday job, which meant the day filter could only ever match one
    // weekday: any learner whose review days omitted it was silently dropped forever. The daily sweep
    // only decides WHICH day a digest lands on; frequency is throttled per learner by
    // RetentionService.dueConceptsDigestCooldownDays -- 1 day for a learner with chosen review days
    // (isEligibleReviewDay still gates on those exact days), 6 days for a learner with none (v0.148.0:
    // isEligibleReviewDay assigns those learners a deterministic hash-based day instead of matching
    // every day, so they too land on one day a week; 6, not 7, only to leave slack for the day
    // assignment shifting during the first week post-deploy without producing two sends inside 7 days).
    @Scheduled(cron = "${studysnap.retention.daily-cron:0 45 2 * * *}", zone = DISPATCH_ZONE)
    public void runDaily() {
        RetentionService.DailyRetentionDispatchSummary summary = retentionService.sendDailyEmails();
        int dueConceptsDigestSent = retentionService.sendDueConceptsDigestEmails();
        log.info("retention.email.scheduler.daily dueConceptsDigest={}", dueConceptsDigestSent);
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

    @Scheduled(cron = "${studysnap.retention.weekly-cron:0 0 18 * * SUN}", zone = DISPATCH_ZONE)
    public void runWeekly() {
        RetentionService.WeeklyRetentionDispatchSummary summary = retentionService.sendWeeklySummaryEmails();
        log.info("retention.email.scheduler.weekly sent weeklySummary={}", summary.weeklySummarySent());
    }

    @Scheduled(cron = "${studysnap.retention.knowledge-impact-digest-monthly-cron:0 0 9 1 * *}")
    public void runMonthly() {
        int knowledgeImpactDigestSent = retentionService.sendKnowledgeImpactDigestEmails();
        log.info("retention.email.scheduler.monthly sent knowledgeImpactDigest={}", knowledgeImpactDigestSent);
    }
}
