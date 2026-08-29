package com.studysnap.backend.service.jobs;

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

    /** Not transactional: every id is delegated to its own worker transaction. */
    @Scheduled(cron = "${studysnap.linked-learners.request-expiry-cron:0 45 2 * * *}")
    public void run() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UUID> dueIds = relationshipRepository.findDuePendingIds(now);
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
