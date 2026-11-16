package com.studysnap.backend.benchmark;

import com.studysnap.backend.config.AppConfig;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/** Submission identity and worker cleanup for preregistration sections 5 and 7. */
class BatchTaggingExecutorTest {
    private static final String REUSE_BATCH_ONE = "reuse-batch-1";
    private static final String REUSE_BATCH_TWO = "reuse-batch-2";
    @Test void productionDelegateMatchesProductionBeanSizingAndPrefix() {
        var production = (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor)
                new AppConfig().llmParallelTaskExecutor();
        try (var tagging = BatchTaggingExecutor.production("sizing")) {
            ThreadPoolExecutor expected = production.getThreadPoolExecutor();
            ThreadPoolExecutor actual = (ThreadPoolExecutor) tagging.delegate();
            assertThat(actual.getCorePoolSize()).isEqualTo(expected.getCorePoolSize());
            assertThat(actual.getMaximumPoolSize()).isEqualTo(expected.getMaximumPoolSize());
            assertThat(actual.getQueue().remainingCapacity())
                    .isEqualTo(expected.getQueue().remainingCapacity());
            AtomicReference<String> name = new AtomicReference<>();
            tagging.execute(() -> name.set(Thread.currentThread().getName()));
            assertThat(tagging.awaitQuiescence(Duration.ofSeconds(2))).isTrue();
            assertThat(name.get()).startsWith(production.getThreadNamePrefix());
        } finally {
            production.shutdown();
        }
    }
    @Test void reusedWorkerDoesNotInheritThreadLocalOrMdc() throws Exception {
        var delegate = Executors.newSingleThreadExecutor();
        try (var tagging = new BatchTaggingExecutor("reuse", delegate)) {
            AtomicReference<List<String>> first = new AtomicReference<>();
            AtomicReference<List<String>> between = new AtomicReference<>();
            AtomicReference<List<String>> second = new AtomicReference<>();
            tagging.execute(() -> first.set(List.of(BatchTaggingExecutor.currentBatch(),
                    MDC.get(BatchTaggingExecutor.MDC_KEY))));
            assertThat(tagging.awaitQuiescence(Duration.ofSeconds(2))).isTrue();
            delegate.submit(() -> between.set(java.util.Arrays.asList(BatchTaggingExecutor.currentBatch(),
                    MDC.get(BatchTaggingExecutor.MDC_KEY)))).get(2, TimeUnit.SECONDS);
            tagging.execute(() -> second.set(List.of(BatchTaggingExecutor.currentBatch(),
                    MDC.get(BatchTaggingExecutor.MDC_KEY))));
            assertThat(tagging.awaitQuiescence(Duration.ofSeconds(2))).isTrue();
            assertThat(first.get()).containsExactly(REUSE_BATCH_ONE, REUSE_BATCH_ONE);
            assertThat(between.get()).containsExactly(null, null);
            assertThat(second.get()).containsExactly(REUSE_BATCH_TWO, REUSE_BATCH_TWO);
        }
    }

    @Test void submissionIdsDoNotFollowCompletionOrder() throws Exception {
        var delegate = Executors.newFixedThreadPool(2);
        try (var tagging = new BatchTaggingExecutor("order", delegate)) {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicReference<String> firstId = new AtomicReference<>();
            AtomicReference<String> secondId = new AtomicReference<>();
            tagging.execute(() -> {
                firstId.set(BatchTaggingExecutor.currentBatch());
                firstStarted.countDown();
                await(releaseFirst);
            });
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            tagging.execute(() -> secondId.set(BatchTaggingExecutor.currentBatch()));
            assertThat(tagging.awaitQuiescence(Duration.ofMillis(50))).isFalse();
            assertThat(secondId.get()).isEqualTo("order-batch-2");
            releaseFirst.countDown();
            assertThat(tagging.awaitQuiescence(Duration.ofSeconds(2))).isTrue();
            assertThat(firstId.get()).isEqualTo("order-batch-1");
        }
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }
}
