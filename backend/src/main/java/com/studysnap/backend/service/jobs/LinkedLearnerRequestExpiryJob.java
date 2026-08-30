package com.studysnap.backend.service.jobs;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LinkedLearnerRequestExpiryJob {
    private final LinkedLearnerRelationshipRepository relationshipRepository;
    private final LinkedLearnerRequestExpiryWorker expiryWorker;
    private final StudySnapProperties properties;

    /** Not transactional: every id is delegated to its own worker transaction. */
    @Scheduled(cron = "${studysnap.linked-learners.request-expiry-cron}")
    public void run() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // ⚠️ Bounded read. Oldest deadline first, so a residue is deferred to tomorrow's run rather
        // than lost — expiry is idempotent, and an unbounded read is only harmless until it is not.
        List<UUID> dueIds = relationshipRepository.findDuePendingIds(
                now, properties.getLinkedLearners().getExpirySweepBatchSize());
        for (UUID relationshipId : dueIds) {
            try {
                expiryWorker.expire(relationshipId, now);
            } catch (RuntimeException exception) {
                // A poisoned row rolls back alone and cannot prevent later requests from expiring.
                log.error("linked-learners.request-expiry failed relationshipId={}",
                        relationshipId, exception);
            }
        }
    }
}
