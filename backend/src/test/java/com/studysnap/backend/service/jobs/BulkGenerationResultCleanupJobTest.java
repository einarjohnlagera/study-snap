package com.studysnap.backend.service.jobs;

import com.studysnap.backend.service.BulkGenerationResultService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BulkGenerationResultCleanupJobTest {
    @Mock
    private BulkGenerationResultService bulkGenerationResultService;

    @Test
    void run_deletesExpiredReceipts() {
        BulkGenerationResultCleanupJob job = new BulkGenerationResultCleanupJob(bulkGenerationResultService);

        job.run();

        verify(bulkGenerationResultService).deleteExpiredReceipts(any(OffsetDateTime.class));
    }
}
