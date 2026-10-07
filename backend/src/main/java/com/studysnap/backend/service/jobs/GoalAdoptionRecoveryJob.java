package com.studysnap.backend.service.jobs;

import com.studysnap.backend.service.GoalAdoptionRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoalAdoptionRecoveryJob {
    private final GoalAdoptionRecoveryService recoveryService;

    @Scheduled(cron = "${studysnap.goal-adoption.recovery-cron:0 * * * * *}")
    public void run() {
        try {
            recoveryService.reenqueueRecoverableJobs();
        } catch (RuntimeException exception) {
            log.error("goal_adoption_recovery_failed", exception);
        }
    }
}
