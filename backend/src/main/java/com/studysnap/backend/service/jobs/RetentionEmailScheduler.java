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
        // Order IS priority: all retention types draw on one daily budget, and INACTIVITY alone saturates it
        // (60/day observed), so the engaged-learner digest must claim its share before INACTIVITY runs.
        // Running it first must not let its failure cancel INACTIVITY and WEAK_CONCEPT for the day.
        RetentionService.RetentionDispatchResult dueConceptsDigest = null;
        try {
            dueConceptsDigest = retentionService.sendDueConceptsDigestEmails();
        } catch (RuntimeException ex) {
            log.error("retention.email.scheduler.daily dueConceptsDigest failed; continuing with the rest", ex);
        }
        RetentionService.DailyRetentionDispatchSummary summary = retentionService.sendDailyEmails();
        if (dueConceptsDigest != null) {
            log.info(
                    "retention.email.scheduler.daily dueConceptsDigest={} budget={} sentToday={} attempted={} skippedForBudget={}",
                    dueConceptsDigest.sent(),
                    dueConceptsDigest.budget(),
                    dueConceptsDigest.sentToday(),
                    dueConceptsDigest.attempted(),
                    dueConceptsDigest.skippedForBudget()
            );
        }
        log.info(
                "retention.email.scheduler.daily sent inactivity={} weakConcept={} inactivityBudget={} inactivitySentToday={} inactivityAttempted={} inactivitySkippedForBudget={} weakConceptBudget={} weakConceptSentToday={} weakConceptAttempted={} weakConceptSkippedForBudget={}",
                summary.inactivitySent(),
                summary.weakConceptSent(),
                summary.inactivityBudget(),
                summary.sentToday(),
                summary.inactivityAttempted(),
                summary.inactivitySkippedForBudget(),
                summary.weakConceptBudget(),
                summary.weakConceptSentToday(),
                summary.weakConceptAttempted(),
                summary.weakConceptSkippedForBudget()
        );
    }

    @Scheduled(cron = "${studysnap.retention.weekly-cron:0 0 18 * * SUN}", zone = DISPATCH_ZONE)
    public void runWeekly() {
        RetentionService.WeeklyRetentionDispatchSummary summary = retentionService.sendWeeklySummaryEmails();
        log.info(
                "retention.email.scheduler.weekly sent weeklySummary={} budget={} sentToday={} attempted={} skippedForBudget={}",
                summary.weeklySummarySent(),
                summary.budget(),
                summary.sentToday(),
                summary.attempted(),
                summary.skippedForBudget()
        );
    }

    @Scheduled(cron = "${studysnap.retention.knowledge-impact-digest-monthly-cron:0 0 9 1 * *}", zone = DISPATCH_ZONE)
    public void runMonthly() {
        RetentionService.RetentionDispatchResult result = retentionService.sendKnowledgeImpactDigestEmails();
        log.info(
                "retention.email.scheduler.monthly sent knowledgeImpactDigest={} budget={} sentToday={} attempted={} skippedForBudget={}",
                result.sent(),
                result.budget(),
                result.sentToday(),
                result.attempted(),
                result.skippedForBudget()
        );
    }
}
