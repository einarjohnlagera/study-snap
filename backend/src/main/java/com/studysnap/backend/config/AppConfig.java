package com.studysnap.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import com.studysnap.backend.security.SecurityProperties;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({StudySnapProperties.class, SecurityProperties.class})
public class AppConfig {
    private static final int ANALYTICS_QUEUE_CAPACITY = 500;
    private static final int ANALYTICS_SHUTDOWN_AWAIT_SECONDS = 20;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Analytics writes are queued off-request, so anything still queued at shutdown is lost unless the
     * executor is told to drain. `main` auto-deploys on merge, which means every release silently
     * discarded up to {@link #ANALYTICS_QUEUE_CAPACITY} events — the server-side twin of the frontend
     * bug where an expired token dropped the event while the learner's action succeeded.
     *
     * <p>The await is bounded: draining is best-effort, and a deploy is never blocked longer than
     * {@link #ANALYTICS_SHUTDOWN_AWAIT_SECONDS}. Analytics must not hold a release hostage.
     */
    @Bean
    public TaskExecutor analyticsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("analytics-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(ANALYTICS_QUEUE_CAPACITY);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(ANALYTICS_SHUTDOWN_AWAIT_SECONDS);
        executor.initialize();
        return executor;
    }

    /**
     * ⚠️ SIZED AGAINST THE CONNECTION POOL, NOT AGAINST CPU — v0.112.0, after the 2026-09-04 outage.
     * Threads on this executor hold a JDBC connection for the whole of a generation, because
     * {@code LongExamService.generateLongExamAsync} wraps {@code execute(...)} AROUND the LLM call.
     * "Dispatches after commit" is not the same as "does not hold a connection", and Hikari does not
     * care which thread holds one.
     *
     * <p>⚠️ THE BOUND IS JUSTIFIED BY HOLD DURATION, NOT BY A RATIO AGAINST THE POOL SIZE — do not
     * re-derive it from {@code maximum-pool-size}. Until Phase 3 relocates the LLM call, each of these
     * threads can hold its connection for the full LLM read timeout (180 s), so a handful of them
     * occupy the pool for minutes at a time. Two is conservative BECAUSE the holds are long, and it
     * stays correct if the pool is later raised or lowered. The queue stays at 100: work is delayed,
     * never dropped.
     *
     * <p>⚠️ Raising this is a PHASE 3 decision, not a capacity one. Once the two-short-transactions
     * shape lands and generation no longer holds a connection, the constraint that produced this
     * number is gone and it should be revisited deliberately — raising it before then re-creates the
     * exposure it exists to reduce.
     *
     * <p>⚠️ Max equals core deliberately. A {@link ThreadPoolTaskExecutor} only grows past its core
     * size once the queue is FULL, so with a 100-deep queue a larger max is nearly unreachable anyway
     * — stating 2 makes the real concurrency bound visible instead of implied.
     */
    @Bean
    public AsyncTaskExecutor studyPackGenerationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("study-pack-generation-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    /**
     * ⚠️ THE BULK REGENERATION DRIVER GETS ITS OWN POOL, AND THAT IS THE WHOLE FIX FOR A MEASURED
     * STARVATION. Bulk *generation* dispatches its batch loop onto
     * {@link #studyPackGenerationTaskExecutor()}, so the loop permanently occupies 1 of only 2 threads
     * for the batch's entire duration while every item it produces queues onto that same pool. Bulk
     * regeneration must not repeat it: the driver thread here waits on each item, so it would hold a
     * generation thread for minutes at a time doing nothing but waiting.
     *
     * <p>⚠️ SIZE 2 IS A CONCURRENCY BOUND, NOT A THROUGHPUT SETTING — it caps how many batches can be
     * in flight at once. Items inside a batch stay strictly sequential; the driver never fans out per
     * Note. A queue of 8 means a third batch waits rather than being rejected.
     *
     * <p>⚠️ {@code setWaitForTasksToCompleteOnShutdown} is deliberately NOT set. Verified against
     * spring-context, that flag runs the ENTIRE queue uninterrupted, which is the opposite of what a
     * killed deploy should do — {@code main} auto-deploys on merge, so a deploy mid-batch is routine.
     * The batch is not drained; items already resolved survive in {@code note_bulk_regeneration_item}
     * and unresolved ones are read honestly.
     *
     * <p>⚠️ This does NOT touch {@link #studyPackGenerationTaskExecutor()}'s 2/2/100 bound, which stays
     * a v0.112.0 Phase 3 decision gated on its own checkpoint.
     */
    @Bean
    public AsyncTaskExecutor bulkRegenerationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("bulk-regeneration-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(8);
        executor.initialize();
        return executor;
    }

    @Bean
    public AsyncTaskExecutor llmParallelTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("llm-parallel-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.initialize();
        return executor;
    }

    @Bean
    public TransactionOperations studyPackGenerationTransactionOperations(
            PlatformTransactionManager transactionManager
    ) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public TransactionOperations collectionTransactionOperations(
            PlatformTransactionManager transactionManager
    ) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
