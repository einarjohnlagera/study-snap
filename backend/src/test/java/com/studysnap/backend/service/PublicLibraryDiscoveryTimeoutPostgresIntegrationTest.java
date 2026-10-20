package com.studysnap.backend.service;

import org.postgresql.util.PSQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real JPA native-query timeout and Caffeine eviction under a PostgreSQL table lock. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
@Testcontainers
@ExtendWith(PublicLibraryDiscoveryTimeoutPostgresIntegrationTest.DockerRequiredUnlessOptedOut.class)
class PublicLibraryDiscoveryTimeoutPostgresIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NoteService noteService;
    @Autowired private PublicLibraryDiscoveryRankingCache cache;
    @Autowired @Qualifier("discoveryRankingTaskExecutor") private org.springframework.core.task.AsyncTaskExecutor rankingExecutor;

    @Test
    void lockedNativeRankingIsCancelledServerSideAndNextRequestRetries() throws Exception {
        cache.invalidateAll();
        cache.setTimeoutsForTest(2, Duration.ofSeconds(5));
        ThreadPoolTaskExecutor worker = (ThreadPoolTaskExecutor) rankingExecutor;
        long missesBefore = cache.stats().missCount();
        long hitsBefore = cache.stats().hitCount();
        try (Connection locker = dataSource.getConnection();
             Statement lockStatement = locker.createStatement()) {
            locker.setAutoCommit(false);
            lockStatement.execute("LOCK TABLE notes IN ACCESS EXCLUSIVE MODE");
            ExecutorService callers = Executors.newFixedThreadPool(3);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<CompletableFuture<Void>> results = java.util.stream.IntStream.range(0, 3)
                        .mapToObj(index -> CompletableFuture.runAsync(() -> {
                            try {
                                start.await();
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(exception);
                            }
                            noteService.getPublicLibraryDiscoverySections(null);
                        }, callers)).toList();
                long started = System.nanoTime();
                start.countDown();
                awaitUntil(() -> cache.stats().hitCount() - hitsBefore >= 2, Duration.ofSeconds(2));
                awaitUntil(() -> activeRankingStatements() == 1, Duration.ofSeconds(2));
                for (CompletableFuture<Void> result : results) {
                    assertThatThrownBy(() -> result.get(7, TimeUnit.SECONDS))
                            .as("each caller sees PostgreSQL cancellation before the 5 s backstop")
                            .hasRootCauseInstanceOf(PSQLException.class);
                }
                assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
                awaitUntil(() -> activeRankingStatements() == 0, Duration.ofSeconds(1));
                assertThat(activeRankingStatements())
                        .as("PostgreSQL has no active ranking statement after transaction cancellation")
                        .isZero();
                assertThat(worker.getActiveCount())
                        .as("ranking worker freed while the table lock is still held")
                        .isZero();
                assertThat(cache.stats().missCount() - missesBefore).isEqualTo(1);
                locker.rollback();
                assertThat(noteService.getPublicLibraryDiscoverySections(null).recent()).isEmpty();
                assertThat(cache.stats().missCount() - missesBefore).isEqualTo(2);
            } finally {
                try {
                    locker.rollback();
                } finally {
                    callers.shutdownNow();
                    callers.awaitTermination(3, TimeUnit.SECONDS);
                }
            }
        } finally {
            cache.setTimeoutsForTest(PublicLibraryDiscoveryRankingCache.RANKING_TRANSACTION_TIMEOUT_SECONDS,
                    PublicLibraryDiscoveryRankingCache.RANKING_WAIT_TIMEOUT);
            cache.invalidateAll();
        }
    }

    private int activeRankingStatements() {
        return jdbc.queryForObject("""
                select count(*) from pg_stat_activity
                where state = 'active' and query like 'with featured_ids as (%'
                """, Integer.class);
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition, Duration bound) {
        long deadline = System.nanoTime() + bound.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    static final class DockerRequiredUnlessOptedOut implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (Boolean.getBoolean("nativequery.pg.skip")) {
                return ConditionEvaluationResult.disabled("PostgreSQL Testcontainers opted out");
            }
            return ConditionEvaluationResult.enabled("PostgreSQL Testcontainers required");
        }
    }
}
