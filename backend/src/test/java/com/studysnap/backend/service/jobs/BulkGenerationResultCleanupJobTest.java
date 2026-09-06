package com.studysnap.backend.service.jobs;

import com.studysnap.backend.service.BulkGenerationResultService;
import com.studysnap.backend.service.NoteBulkRegenerationReceiptService;
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
    @Mock
    private NoteBulkRegenerationReceiptService noteBulkRegenerationReceiptService;

    @Test
    void run_deletesExpiredReceipts() {
        BulkGenerationResultCleanupJob job = new BulkGenerationResultCleanupJob(
                bulkGenerationResultService, noteBulkRegenerationReceiptService);

        job.run();

        verify(bulkGenerationResultService).deleteExpiredReceipts(any(OffsetDateTime.class));
    }

    /**
     * ⚠️ The bulk regeneration receipt shares this job and this TTL. Without this assertion the second
     * sweep could be dropped and nothing in the build would notice, leaving per-item rows to accumulate
     * as the permanent audit history the plan forbids.
     */
    @Test
    void run_alsoDeletesExpiredBulkRegenerationItems() {
        BulkGenerationResultCleanupJob job = new BulkGenerationResultCleanupJob(
                bulkGenerationResultService, noteBulkRegenerationReceiptService);

        job.run();

        verify(noteBulkRegenerationReceiptService).deleteExpiredItems(any(OffsetDateTime.class));
    }
}
