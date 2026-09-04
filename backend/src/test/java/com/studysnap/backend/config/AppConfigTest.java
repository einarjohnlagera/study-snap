package com.studysnap.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    /**
     * ⚠️ THE STUDY-PACK NUMBERS MOVED FROM 3/6 TO 2/2 IN v0.112.0, AND THAT IS POOL CONTENTION RATHER
     * THAN TUNING. Threads on that executor hold a JDBC connection for the whole of a generation
     * ({@code LongExamService.generateLongExamAsync} wraps a transaction AROUND the LLM call), so at
     * max 6 it could claim six of the ten connections Hikari defaulted to on 2026-09-04 — the day
     * production was killed for failing a health check that had starved on the same exhausted pool.
     *
     * <p>This test failing on that edit is the test working. The numbers are a decision about how much
     * of the pool background generation may hold, so moving them again means editing this assertion
     * deliberately — not discovering afterwards that a default drifted.
     */
    @Test
    void generationExecutorsKeepMainDispatchAndLlmFanOutSeparate() {
        AppConfig config = new AppConfig();
        ThreadPoolTaskExecutor studyPackExecutor = asThreadPool(config.studyPackGenerationTaskExecutor());
        ThreadPoolTaskExecutor llmParallelExecutor = asThreadPool(config.llmParallelTaskExecutor());

        try {
            assertThat(studyPackExecutor)
                    .isNotSameAs(llmParallelExecutor);
            assertThat(studyPackExecutor.getCorePoolSize()).isEqualTo(2);
            assertThat(studyPackExecutor.getMaxPoolSize()).isEqualTo(2);
            assertThat(studyPackExecutor.getQueueCapacity())
                    .as("work is DELAYED when both threads are busy, never dropped — a smaller queue "
                            + "would turn pool protection into lost generations")
                    .isEqualTo(100);

            assertThat(llmParallelExecutor.getCorePoolSize()).isEqualTo(4);
            assertThat(llmParallelExecutor.getMaxPoolSize()).isEqualTo(8);
            assertThat(llmParallelExecutor.getQueueCapacity()).isEqualTo(50);
        } finally {
            studyPackExecutor.shutdown();
            llmParallelExecutor.shutdown();
        }
    }

    @Test
    void analyticsExecutorDrainsItsQueueOnShutdownInsteadOfDiscardingIt() throws Exception {
        // `main` auto-deploys on merge, so without drain-on-shutdown every release silently discarded
        // whatever analytics work was still queued. Asserted behaviourally because
        // waitForTasksToCompleteOnShutdown / awaitTerminationSeconds expose no getters — and because a
        // property assertion would not prove the queue actually flushes.
        ThreadPoolTaskExecutor analyticsExecutor =
                (ThreadPoolTaskExecutor) new AppConfig().analyticsTaskExecutor();
        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();

        analyticsExecutor.execute(() -> {
            firstTaskStarted.countDown();
            sleepQuietly();
            completed.incrementAndGet();
        });
        assertThat(firstTaskStarted.await(2, TimeUnit.SECONDS)).isTrue();
        // Core pool size is 1, so this one is still sitting in the queue when shutdown begins.
        analyticsExecutor.execute(completed::incrementAndGet);

        analyticsExecutor.shutdown();

        assertThat(completed.get()).isEqualTo(2);
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private ThreadPoolTaskExecutor asThreadPool(AsyncTaskExecutor taskExecutor) {
        assertThat(taskExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);
        return (ThreadPoolTaskExecutor) taskExecutor;
    }
}
