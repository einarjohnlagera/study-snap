package com.studysnap.backend.service.jobs;

import com.studysnap.backend.service.BulkGenerationResultService;
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

    @Scheduled(cron = "${studysnap.generation.bulk-result-cleanup-cron:0 45 * * * *}")
    public void run() {
        long deletedCount = bulkGenerationResultService.deleteExpiredReceipts(OffsetDateTime.now(ZoneOffset.UTC));
        log.info("bulk.generation.result.cleanup deleted {} expired receipts", deletedCount);
    }
}
