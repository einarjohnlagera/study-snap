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

    private static StudySnapProperties properties() {
        return new StudySnapProperties();
    }

    /**
     * ⚠️ This test silently stopped running for one release. A helper was inserted BETWEEN the
     * {@code @Test} annotation and the method, so the annotation landed on a private static helper —
     * JUnit warned and skipped it, the class reported "Tests run: 0", and the build stayed GREEN.
     * The only guard on "a poisoned relationship does not stop the sweep" was gone with no signal.
     * Found by the v0.98.0 pre-signoff cold agent, which counted executed tests rather than reading
     * the source.
     */
    @Test
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
