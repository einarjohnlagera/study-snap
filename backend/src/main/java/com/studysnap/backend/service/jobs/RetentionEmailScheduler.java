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
    public void run() {
        RetentionService.RetentionDispatchSummary summary = retentionService.sendDueEmails();
        log.info(
                "retention.email.scheduler sent inactivity={} weakConcept={} unfinishedNote={}",
                summary.inactivitySent(),
                summary.weakConceptSent(),
                summary.unfinishedNoteSent()
        );
    }
}
