package com.studysnap.backend.service.jobs;

import com.studysnap.backend.service.RetentionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionEmailSchedulerTest {
    @Mock
    private RetentionService retentionService;

    @Test
    void runDaily_dispatchesTheDueConceptsDigestSoEveryReviewDayIsReachable() {
        // The defect this pins: the digest used to ride runWeekly (cron "0 0 18 * * SUN"), so the
        // review-day filter could only ever match ONE weekday. Any learner whose chosen days omitted it
        // was silently dropped forever. Dispatch must be daily for "remind me on these days" to be true;
        // dueConceptsDigestCooldownDays still caps frequency at one per week.
        when(retentionService.sendDailyEmails()).thenReturn(dailySummary());
        when(retentionService.sendDueConceptsDigestEmails()).thenReturn(3);
        RetentionEmailScheduler scheduler = new RetentionEmailScheduler(retentionService);

        scheduler.runDaily();

        verify(retentionService).sendDueConceptsDigestEmails();
    }

    @Test
    void runWeekly_noLongerDispatchesTheDueConceptsDigest() {
        when(retentionService.sendWeeklySummaryEmails()).thenReturn(weeklySummary());
        RetentionEmailScheduler scheduler = new RetentionEmailScheduler(retentionService);

        scheduler.runWeekly();

        verify(retentionService, never()).sendDueConceptsDigestEmails();
    }

    @Test
    void runMonthly_dispatchesKnowledgeImpactDigestEmails() {
        when(retentionService.sendKnowledgeImpactDigestEmails()).thenReturn(2);
        RetentionEmailScheduler scheduler = new RetentionEmailScheduler(retentionService);

        scheduler.runMonthly();

        verify(retentionService).sendKnowledgeImpactDigestEmails();
    }

    private RetentionService.DailyRetentionDispatchSummary dailySummary() {
        return new RetentionService.DailyRetentionDispatchSummary(0, 0, 0, 0, 0, 0);
    }

    private RetentionService.WeeklyRetentionDispatchSummary weeklySummary() {
        return new RetentionService.WeeklyRetentionDispatchSummary(0);
    }
}
