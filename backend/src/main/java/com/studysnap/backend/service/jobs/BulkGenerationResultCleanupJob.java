package com.studysnap.backend.service.jobs;

import com.studysnap.backend.service.BulkGenerationResultService;
import com.studysnap.backend.service.NoteBulkRegenerationReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class BulkGenerationResultCleanupJob {
    private final BulkGenerationResultService bulkGenerationResultService;
    private final NoteBulkRegenerationReceiptService noteBulkRegenerationReceiptService;

    /**
     * ⚠️ ONE JOB, TWO RECEIPTS, ON PURPOSE. Bulk regeneration's per-item rows carry the SAME 24 h TTL as
     * the bulk generation receipt and are swept on the same hourly schedule — mirroring rather than
     * inventing a lifetime. Neither is permanent audit history.
     */
    @Scheduled(cron = "${studysnap.generation.bulk-result-cleanup-cron:0 45 * * * *}")
    public void run() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long deletedCount = bulkGenerationResultService.deleteExpiredReceipts(now);
        long deletedRegenerationItems = noteBulkRegenerationReceiptService.deleteExpiredItems(now);
        log.info(
                "bulk.generation.result.cleanup deleted {} expired receipts and {} expired regeneration items",
                deletedCount,
                deletedRegenerationItems
        );
    }
}
