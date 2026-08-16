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

    @Bean
    public AsyncTaskExecutor studyPackGenerationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("study-pack-generation-");
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(100);
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
