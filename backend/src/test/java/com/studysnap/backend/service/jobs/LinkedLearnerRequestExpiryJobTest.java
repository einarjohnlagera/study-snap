package com.studysnap.backend.service.jobs;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkedLearnerRequestExpiryJobTest {

    @Test
    private static StudySnapProperties properties() {
        return new StudySnapProperties();
    }

    void oneFailedRelationshipDoesNotStopTheSweep() {
        LinkedLearnerRelationshipRepository repository = mock(LinkedLearnerRelationshipRepository.class);
        LinkedLearnerRequestExpiryWorker worker = mock(LinkedLearnerRequestExpiryWorker.class);
        UUID poisoned = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        when(repository.findDuePendingIds(any(OffsetDateTime.class), anyInt())).thenReturn(List.of(poisoned, healthy));
        when(worker.expire(eq(poisoned), any(OffsetDateTime.class))).thenThrow(new IllegalStateException("poison"));

        new LinkedLearnerRequestExpiryJob(repository, worker, properties()).run();

        verify(worker).expire(eq(poisoned), any(OffsetDateTime.class));
        verify(worker).expire(eq(healthy), any(OffsetDateTime.class));
    }
}
