package com.studysnap.backend.service.jobs;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.service.GenerationRecoveryService;
import com.studysnap.backend.service.GenerationRecoveryService.SurfaceRecoveryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenerationRecoveryJob {
    private final GenerationRecoveryService generationRecoveryService;
    private final StudySnapProperties properties;

    @Scheduled(cron = "${studysnap.generation.recovery-cron:0 */10 * * * *}")
    public void run() {
        if (!properties.getGeneration().isEnabled()) {
            log.info("generation.recovery skipped because it is disabled");
            return;
        }

        SurfaceRecoveryResult pools = recoverPools();
        SurfaceRecoveryResult longExamSessions = recoverLongExamSessions();
        SurfaceRecoveryResult boardExamSessions = recoverBoardExamSessions();
        SurfaceRecoveryResult preparedChallenges = recoverPreparedChallenges();
        SurfaceRecoveryResult startedChallenges = recoverStartedChallenges();
        SurfaceRecoveryResult stuckChallenges = recoverStuckChallenges();
        SurfaceRecoveryResult notes = recoverNotes();
        log.info(
                "generation.recovery completed pools={} poolsMaxAgeMinutes={} longExamSessions={} longExamSessionsMaxAgeMinutes={} boardExamSessions={} boardExamSessionsMaxAgeMinutes={} preparedChallenges={} preparedChallengesMaxAgeMinutes={} startedChallenges={} startedChallengesMaxAgeMinutes={} stuckChallenges={} stuckChallengesMaxAgeMinutes={} notes={} notesMaxAgeMinutes={}",
                pools.recoveredCount(),
                pools.maxRecoveredAge().toMinutes(),
                longExamSessions.recoveredCount(),
                longExamSessions.maxRecoveredAge().toMinutes(),
                boardExamSessions.recoveredCount(),
                boardExamSessions.maxRecoveredAge().toMinutes(),
                preparedChallenges.recoveredCount(),
                preparedChallenges.maxRecoveredAge().toMinutes(),
                startedChallenges.recoveredCount(),
                startedChallenges.maxRecoveredAge().toMinutes(),
                stuckChallenges.recoveredCount(),
                stuckChallenges.maxRecoveredAge().toMinutes(),
                notes.recoveredCount(),
                notes.maxRecoveredAge().toMinutes()
        );
    }

    private SurfaceRecoveryResult recoverPreparedChallenges() {
        try {
            return generationRecoveryService.recoverExpiredPreparedChallengeSessions();
        } catch (RuntimeException ex) {
            log.warn("generation.recovery prepared-challenge sweep failed", ex);
            return SurfaceRecoveryResult.empty();
        }
    }

    private SurfaceRecoveryResult recoverStartedChallenges() {
        try {
            return generationRecoveryService.recoverExpiredStartedChallengeSessions();
        } catch (RuntimeException ex) {
            log.warn("generation.recovery started-challenge sweep failed", ex);
            return SurfaceRecoveryResult.empty();
        }
    }

    private SurfaceRecoveryResult recoverStuckChallenges() {
        try {
            return generationRecoveryService.recoverStuckChallengeGenerations();
        } catch (RuntimeException ex) {
            log.warn("generation.recovery stuck-challenge sweep failed", ex);
            return SurfaceRecoveryResult.empty();
        }
    }

    private SurfaceRecoveryResult recoverPools() {
        try {
            return generationRecoveryService.recoverStaleExamQuestionPools();
        } catch (RuntimeException ex) {
            log.warn("generation.recovery pool sweep failed", ex);
            return SurfaceRecoveryResult.empty();
        }
    }

    private SurfaceRecoveryResult recoverLongExamSessions() {
        try {
            return generationRecoveryService.recoverStaleLongExamSessions();
        } catch (RuntimeException ex) {
            log.warn("generation.recovery long-exam-session sweep failed", ex);
            return SurfaceRecoveryResult.empty();
        }
    }

    private SurfaceRecoveryResult recoverBoardExamSessions() {
        try {
            return generationRecoveryService.recoverStaleBoardExamSessions();
        } catch (RuntimeException ex) {
            log.warn("generation.recovery board-exam-session sweep failed", ex);
            return SurfaceRecoveryResult.empty();
        }
    }

    private SurfaceRecoveryResult recoverNotes() {
        try {
            return generationRecoveryService.recoverStaleNotes();
        } catch (RuntimeException ex) {
            log.warn("generation.recovery note sweep failed", ex);
            return SurfaceRecoveryResult.empty();
        }
    }
}
