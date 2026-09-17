package com.studysnap.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/** Logs the request paths in flight when Hikari reports sustained pool saturation. */
@Component
public class PoolSaturationDetector {
    static final String SATURATION_LOG_MARKER = "requests in flight at saturation";

    private static final Logger log = LoggerFactory.getLogger(PoolSaturationDetector.class);
    private static final int REQUIRED_CONSECUTIVE_SATURATED_POLLS = 2;

    private final InFlightRequestRegistry registry;
    private final HikariDataSource hikariDataSource;

    private int consecutiveSaturatedPolls;
    private boolean loggedForCurrentSaturation;

    public PoolSaturationDetector(DataSource dataSource, InFlightRequestRegistry registry) {
        this.registry = registry;
        this.hikariDataSource = dataSource instanceof HikariDataSource hikari ? hikari : null;
    }

    @PostConstruct
    void reportUnsupportedDataSource() {
        if (hikariDataSource == null) {
            log.warn("pool saturation detector disabled: DataSource is not a HikariDataSource");
        }
    }

    @Scheduled(fixedDelayString = "${studysnap.pool-saturation.poll-interval-ms:2000}")
    public void poll() {
        checkAndLogIfSaturated();
    }

    /**
     * Reads one pool-state sample. Two successful, consecutive saturated samples are required before
     * logging, and only one warning is emitted per continuous saturation episode.
     */
    public synchronized void checkAndLogIfSaturated() {
        if (hikariDataSource == null) {
            return;
        }
        try {
            HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
            if (pool == null) {
                resetSaturationState();
                return;
            }

            int activeConnections = pool.getActiveConnections();
            int maximumPoolSize = hikariDataSource.getMaximumPoolSize();
            int threadsAwaitingConnection = pool.getThreadsAwaitingConnection();
            boolean saturated = activeConnections >= maximumPoolSize && threadsAwaitingConnection > 0;
            if (!saturated) {
                resetSaturationState();
                return;
            }

            consecutiveSaturatedPolls = Math.min(
                    REQUIRED_CONSECUTIVE_SATURATED_POLLS,
                    consecutiveSaturatedPolls + 1
            );
            if (consecutiveSaturatedPolls < REQUIRED_CONSECUTIVE_SATURATED_POLLS
                    || loggedForCurrentSaturation) {
                return;
            }

            log.warn(
                    "pool saturation detected: activeConnections={}, maximumPoolSize={}, "
                            + "threadsAwaitingConnection={}; {}={}",
                    activeConnections,
                    maximumPoolSize,
                    threadsAwaitingConnection,
                    SATURATION_LOG_MARKER,
                    describeInFlightRequests(registry.snapshot())
            );
            loggedForCurrentSaturation = true;
        } catch (Exception exception) {
            resetSaturationState();
            log.warn("pool saturation detector poll failed; skipping this cycle", exception);
        }
    }

    private String describeInFlightRequests(
            Map<Thread, InFlightRequestRegistry.InFlightRequest> inFlightRequests
    ) {
        return inFlightRequests.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<Thread, InFlightRequestRegistry.InFlightRequest> entry) ->
                                entry.getKey().getName())
                        .thenComparingLong(entry -> entry.getKey().threadId()))
                .map(entry -> "{thread=" + sanitize(entry.getKey().getName())
                        + ", path=" + sanitize(entry.getValue().path())
                        + ", startedAt=" + entry.getValue().startedAt() + "}")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String sanitize(String value) {
        return value.replace('\n', '_').replace('\r', '_');
    }

    private void resetSaturationState() {
        consecutiveSaturatedPolls = 0;
        loggedForCurrentSaturation = false;
    }
}
