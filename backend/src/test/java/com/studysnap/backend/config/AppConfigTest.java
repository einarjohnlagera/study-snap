package com.studysnap.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
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
        ThreadPoolTaskExecutor studyPackExecutor = asThreadPool(
                config.studyPackGenerationTaskExecutor(taskDecorator(new InFlightRequestRegistry()))
        );
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
    void customSchedulerUsesTwoDistinctlyNamedThreads() {
        ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) new AppConfig().taskScheduler(
                taskDecorator(new InFlightRequestRegistry())
        );

        try {
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
            assertThat(scheduler.getThreadNamePrefix()).isEqualTo("scheduled-task-");
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void analyticsExecutorDrainsItsQueueOnShutdownInsteadOfDiscardingIt() throws Exception {
        // `main` auto-deploys on merge, so without drain-on-shutdown every release silently discarded
        // whatever analytics work was still queued. Asserted behaviourally because
        // waitForTasksToCompleteOnShutdown / awaitTerminationSeconds expose no getters — and because a
        // property assertion would not prove the queue actually flushes.
        ThreadPoolTaskExecutor analyticsExecutor =
                (ThreadPoolTaskExecutor) new AppConfig().analyticsTaskExecutor(
                        taskDecorator(new InFlightRequestRegistry())
                );
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

    @Test
    void notificationFanOutExecutorKeepsItsConnectionPoolBoundAndObservableRejectionPolicy() {
        ThreadPoolTaskExecutor executor =
                (ThreadPoolTaskExecutor) new AppConfig().notificationFanOutExecutor(
                        taskDecorator(new InFlightRequestRegistry())
                );

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getQueueCapacity()).isEqualTo(100);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void decoratedGenerationTaskIsVisibleWhileRunningAndRemovedAfterCompletion() throws Exception {
        InFlightRequestRegistry registry = new InFlightRequestRegistry();
        ThreadPoolTaskExecutor executor = asThreadPool(
                new AppConfig().studyPackGenerationTaskExecutor(taskDecorator(registry))
        );
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<?> task = executor.submit(() -> {
                started.countDown();
                awaitUnchecked(release);
            });

            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.snapshot().values())
                    .singleElement()
                    .extracting(InFlightRequestRegistry.InFlightRequest::path)
                    .asString()
                    .startsWith("study-pack-generation-");

            release.countDown();
            task.get(2, TimeUnit.SECONDS);
            assertThat(registry.snapshot()).isEmpty();
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void decoratedGenerationTaskIsRemovedWhenItThrows() throws Exception {
        InFlightRequestRegistry registry = new InFlightRequestRegistry();
        ThreadPoolTaskExecutor executor = asThreadPool(
                new AppConfig().studyPackGenerationTaskExecutor(taskDecorator(registry))
        );
        CountDownLatch running = new CountDownLatch(1);

        try {
            Future<?> task = executor.submit(() -> {
                running.countDown();
                throw new IllegalStateException("generation failed");
            });

            assertThat(running.await(2, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> task.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(registry.snapshot()).isEmpty();
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void everyDbTouchingExecutorIsDecoratedAndTheLlmFanOutExecutorIsNot() throws Exception {
        InFlightRequestRegistry registry = new InFlightRequestRegistry();
        InFlightThreadRegisteringTaskDecorator decorator = taskDecorator(registry);
        AppConfig config = new AppConfig();
        Map<String, ThreadPoolTaskExecutor> decorated = Map.of(
                "study-pack-generation-", asThreadPool(config.studyPackGenerationTaskExecutor(decorator)),
                "bulk-regeneration-", asThreadPool(config.bulkRegenerationTaskExecutor(decorator)),
                "notification-fan-out-", (ThreadPoolTaskExecutor) config.notificationFanOutExecutor(decorator),
                "analytics-", (ThreadPoolTaskExecutor) config.analyticsTaskExecutor(decorator)
        );
        ThreadPoolTaskExecutor llmParallel = asThreadPool(config.llmParallelTaskExecutor());

        try {
            for (Map.Entry<String, ThreadPoolTaskExecutor> entry : decorated.entrySet()) {
                assertThat(pathsWhileRunning(entry.getValue(), registry))
                        .as(entry.getKey())
                        .singleElement()
                        .asString()
                        .startsWith(entry.getKey());
            }
            // Holds no DB connection, so registering it would only add registry churn.
            assertThat(pathsWhileRunning(llmParallel, registry)).isEmpty();
        } finally {
            decorated.values().forEach(ThreadPoolTaskExecutor::shutdown);
            llmParallel.shutdown();
        }
    }

    private static List<String> pathsWhileRunning(ThreadPoolTaskExecutor executor, InFlightRequestRegistry registry)
            throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> task = executor.submit(() -> {
            started.countDown();
            awaitUnchecked(release);
        });
        try {
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            return registry.snapshot().values().stream()
                    .map(InFlightRequestRegistry.InFlightRequest::path)
                    .toList();
        } finally {
            release.countDown();
            task.get(2, TimeUnit.SECONDS);
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitUnchecked(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static InFlightThreadRegisteringTaskDecorator taskDecorator(
            InFlightRequestRegistry registry
    ) {
        return new InFlightThreadRegisteringTaskDecorator(registry);
    }

    private ThreadPoolTaskExecutor asThreadPool(AsyncTaskExecutor taskExecutor) {
        assertThat(taskExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);
        return (ThreadPoolTaskExecutor) taskExecutor;
    }
}
