package com.studysnap.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PoolSaturationDetectorTest {
    @Test
    void logsEveryTrackedPathAfterTwoConsecutiveRealPoolSaturationSamples(CapturedOutput output) throws Exception {
        InFlightRequestRegistry registry = new InFlightRequestRegistry();
        try (SaturatedPoolFixture fixture = SaturatedPoolFixture.create()) {
            PoolSaturationDetector detector = new PoolSaturationDetector(fixture.dataSource(), registry);
            registry.registerCurrentThread("/api/notes/public/saturated-note");
            try {
                detector.checkAndLogIfSaturated();
                assertThat(output).doesNotContain(PoolSaturationDetector.SATURATION_LOG_MARKER);

                detector.checkAndLogIfSaturated();

                assertThat(output)
                        .contains("WARN")
                        .contains("activeConnections=2")
                        .contains("maximumPoolSize=2")
                        .contains("threadsAwaitingConnection=1")
                        .contains(PoolSaturationDetector.SATURATION_LOG_MARKER)
                        .contains("/api/notes/public/saturated-note")
                        .contains("thread=" + Thread.currentThread().getName());
            } finally {
                registry.removeCurrentThread();
            }
        }
    }

    @Test
    void doesNotLogAfterOnlyOneSaturatedSample(CapturedOutput output) throws Exception {
        InFlightRequestRegistry registry = new InFlightRequestRegistry();
        try (SaturatedPoolFixture fixture = SaturatedPoolFixture.create()) {
            PoolSaturationDetector detector = new PoolSaturationDetector(fixture.dataSource(), registry);
            registry.registerCurrentThread("/api/notes/public/transient-spike");
            try {
                detector.checkAndLogIfSaturated();

                assertThat(output).doesNotContain(PoolSaturationDetector.SATURATION_LOG_MARKER);
            } finally {
                registry.removeCurrentThread();
            }
        }
    }

    @Test
    void doesNotLogDuringOrdinaryPoolUse(CapturedOutput output) throws Exception {
        try (HikariDataSource dataSource = dataSource(2);
             Connection ignored = dataSource.getConnection()) {
            PoolSaturationDetector detector = new PoolSaturationDetector(
                    dataSource,
                    new InFlightRequestRegistry()
            );

            detector.checkAndLogIfSaturated();
            detector.checkAndLogIfSaturated();

            assertThat(output).doesNotContain(PoolSaturationDetector.SATURATION_LOG_MARKER);
        }
    }

    @Test
    void doesNotLogUnderOrdinaryConcurrentLoad(CapturedOutput output) throws Exception {
        InFlightRequestRegistry registry = new InFlightRequestRegistry();
        try (HikariDataSource dataSource = dataSource(3)) {
            PoolSaturationDetector detector = new PoolSaturationDetector(dataSource, registry);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                List<Runnable> tasks = IntStream.range(0, 2)
                        .<Runnable>mapToObj(i -> () -> simulateOrdinaryRequest(dataSource, registry, detector, i))
                        .toList();
                List<Future<?>> futures = tasks.stream().map(executor::submit).toList();
                for (Future<?> future : futures) {
                    future.get(5, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdown();
            }

            assertThat(output).doesNotContain(PoolSaturationDetector.SATURATION_LOG_MARKER);
        }
    }

    private static void simulateOrdinaryRequest(
            HikariDataSource dataSource,
            InFlightRequestRegistry registry,
            PoolSaturationDetector detector,
            int index
    ) {
        registry.registerCurrentThread("/api/notes/concurrent-" + index);
        try (Connection connection = dataSource.getConnection()) {
            detector.checkAndLogIfSaturated();
            detector.checkAndLogIfSaturated();
            detector.checkAndLogIfSaturated();
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        } finally {
            registry.removeCurrentThread();
        }
    }

    @Test
    void rearmsAfterRecovery(CapturedOutput output) {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
        when(dataSource.getHikariPoolMXBean()).thenReturn(pool);
        when(dataSource.getMaximumPoolSize()).thenReturn(2);
        InFlightRequestRegistry registry = new InFlightRequestRegistry();
        PoolSaturationDetector detector = new PoolSaturationDetector(dataSource, registry);

        registry.registerCurrentThread("/api/first-episode");
        when(pool.getActiveConnections()).thenReturn(2);
        when(pool.getThreadsAwaitingConnection()).thenReturn(1);
        detector.checkAndLogIfSaturated();
        detector.checkAndLogIfSaturated();
        detector.checkAndLogIfSaturated();
        assertThat(saturationLogLineCount(output)).as("episode 1 logs exactly once").isEqualTo(1);

        when(pool.getActiveConnections()).thenReturn(0);
        when(pool.getThreadsAwaitingConnection()).thenReturn(0);
        detector.checkAndLogIfSaturated();
        registry.removeCurrentThread();

        registry.registerCurrentThread("/api/second-episode");
        when(pool.getActiveConnections()).thenReturn(2);
        when(pool.getThreadsAwaitingConnection()).thenReturn(1);
        detector.checkAndLogIfSaturated();
        assertThat(saturationLogLineCount(output)).as("one saturated poll into episode 2, not yet").isEqualTo(1);
        detector.checkAndLogIfSaturated();
        assertThat(saturationLogLineCount(output)).as("episode 2 re-arms and logs again").isEqualTo(2);
        assertThat(output).contains("/api/second-episode");
        registry.removeCurrentThread();
    }

    private static long saturationLogLineCount(CapturedOutput output) {
        return output.toString().lines()
                .filter(line -> line.contains(PoolSaturationDetector.SATURATION_LOG_MARKER))
                .count();
    }

    @Test
    void aPoolStateReadFailureIsWarnedAndDoesNotEscapeThePoll(CapturedOutput output) {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getHikariPoolMXBean()).thenThrow(new IllegalStateException("MXBean unavailable"));
        PoolSaturationDetector detector = new PoolSaturationDetector(
                dataSource,
                new InFlightRequestRegistry()
        );

        assertThatCode(detector::checkAndLogIfSaturated).doesNotThrowAnyException();
        assertThat(output)
                .contains("pool saturation detector poll failed; skipping this cycle")
                .doesNotContain(PoolSaturationDetector.SATURATION_LOG_MARKER);
    }

    private static HikariDataSource dataSource(int maximumPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:pool-saturation-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(maximumPoolSize);
        config.setConnectionTimeout(5_000);
        return new HikariDataSource(config);
    }

    private static void await(String description, Duration timeout, BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).as(description).isTrue();
    }

    private record SaturatedPoolFixture(
            HikariDataSource dataSource,
            Connection firstHeldConnection,
            Connection secondHeldConnection,
            ExecutorService waitingExecutor,
            Future<Connection> waitingConnection
    ) implements AutoCloseable {
        static SaturatedPoolFixture create() throws Exception {
            HikariDataSource dataSource = PoolSaturationDetectorTest.dataSource(2);
            Connection first = dataSource.getConnection();
            Connection second = dataSource.getConnection();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Connection> waiting = executor.submit(() -> {
                return dataSource.getConnection();
            });
            await(
                    "the third acquisition must actually be waiting on the exhausted Hikari pool",
                    Duration.ofSeconds(2),
                    () -> dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection() == 1
            );
            return new SaturatedPoolFixture(dataSource, first, second, executor, waiting);
        }

        @Override
        public void close() throws Exception {
            SQLException closeFailure = null;
            try {
                firstHeldConnection.close();
                waitingConnection.get(2, TimeUnit.SECONDS).close();
            } catch (SQLException exception) {
                closeFailure = exception;
            } finally {
                secondHeldConnection.close();
                waitingExecutor.shutdownNow();
                dataSource.close();
            }
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
