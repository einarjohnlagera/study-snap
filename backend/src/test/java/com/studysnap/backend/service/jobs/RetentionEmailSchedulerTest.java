package com.studysnap.backend.service.jobs;

import com.studysnap.backend.service.RetentionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionEmailSchedulerTest {
    @Mock
    private RetentionService retentionService;

    @Test
    void runMonthly_dispatchesKnowledgeImpactDigestEmails() {
        when(retentionService.sendKnowledgeImpactDigestEmails()).thenReturn(2);
        RetentionEmailScheduler scheduler = new RetentionEmailScheduler(retentionService);

        scheduler.runMonthly();

        verify(retentionService).sendKnowledgeImpactDigestEmails();
    }
}
