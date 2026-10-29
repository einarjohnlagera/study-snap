package com.studysnap.backend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.exception.PublicLibraryRankingBusyException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicLibraryRankingSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** A bounded, single-flight read snapshot. Acquisition always precedes the listing transaction. */
@Component
public class PublicLibraryRankingSnapshotCache {
    private static final Logger log = LoggerFactory.getLogger(PublicLibraryRankingSnapshotCache.class);
    private static final String KEY = "ranked-listing";
    private static final int BUILD_TIMEOUT_SECONDS = 50;
    // Backstop for a build thread stuck on a dead socket (the transaction timeout cannot cancel one): it precedes the
    // 60 s Hikari leak detection and frees the single-flight slot so the next request is not frozen behind it.
    private static final Duration BUILD_WAIT_TIMEOUT = Duration.ofSeconds(55);
    private static final String RESULT_HIT = "hit";
    private static final String RESULT_COLD_HIT = "coldHit";
    // Non-hit outcomes are logged at most once a minute: a burst must not turn one log line per listing
    // request into a CPU and log-volume problem on a 0.1 CPU backend.
    private static final long ACCESS_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(60);

    private final NoteRepository repository;
    private final StudySnapProperties.PublicLibraryRankingSnapshot settings;
    private final TransactionTemplate readOnlyTransaction;
    private final AsyncTaskExecutor executor;
    private final Ticker ticker;
    private final Cache<String, Published> cache = Caffeine.newBuilder().maximumSize(1).build();
    private final AtomicReference<CompletableFuture<PublicLibraryRankingSnapshot>> inFlight = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong fallbacks = new AtomicLong();
    private final AtomicLong rejections = new AtomicLong();
    private final AtomicLong bypasses = new AtomicLong();
    private final AtomicLong coldWaiters = new AtomicLong();
    private final AtomicLong lastAccessLogNanos = new AtomicLong();
    private final Semaphore bulkhead;
    private volatile long retryAfterNanos;
    private volatile Duration buildWaitTimeout = BUILD_WAIT_TIMEOUT;

    public PublicLibraryRankingSnapshotCache(NoteRepository repository,
            PlatformTransactionManager transactionManager,
            @Qualifier("publicLibraryRankingSnapshotExecutor") AsyncTaskExecutor executor,
            Ticker ticker, StudySnapProperties properties) {
        this.repository = repository;
        this.settings = properties.getPublicLibraryRankingSnapshot();
        this.executor = executor;
        this.ticker = ticker;
        this.bulkhead = new Semaphore(Math.max(1, settings.getBulkheadMaxConcurrent()));
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.readOnlyTransaction.setTimeout(BUILD_TIMEOUT_SECONDS);
        this.readOnlyTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (settings.getTtlSeconds() > 0 && settings.isWarmUpOnStartup()) {
            startLoad();
        }
    }

    public Resolution resolve() {
        if (settings.getTtlSeconds() <= 0) {
            bypasses.incrementAndGet();
            logCounters("bypass");
            return new Resolution(null, null);
        }
        Published previous = cache.getIfPresent(KEY);
        // The publish time travels WITH the snapshot, so a concurrent publish can never pair a new snapshot with an
        // old age (or the reverse).
        long age = previous == null ? Long.MAX_VALUE : ticker.read() - previous.publishedNanos();
        if (previous != null && age >= TimeUnit.SECONDS.toNanos(settings.getTtlSeconds())) {
            startLoad();
        }
        if (previous != null && age < TimeUnit.SECONDS.toNanos(settings.getMaxAgeSeconds())) {
            hits.incrementAndGet();
            logCounters(RESULT_HIT);
            return new Resolution(previous.snapshot(), null);
        }
        if (previous == null) {
            CompletableFuture<PublicLibraryRankingSnapshot> load = startLoad();
            if (load != null) {
                coldWaiters.incrementAndGet();
                try {
                    PublicLibraryRankingSnapshot loaded = load.get(
                            Math.max(0, settings.getColdStartWaitMs()), TimeUnit.MILLISECONDS);
                    hits.incrementAndGet();
                    logCounters(RESULT_COLD_HIT);
                    return new Resolution(loaded, null);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException | TimeoutException failure) {
                    // The old SQL remains available, subject to the bounded fallback bulkhead.
                } finally {
                    coldWaiters.decrementAndGet();
                }
            }
        }
        fallbacks.incrementAndGet();
        if (!bulkhead.tryAcquire()) {
            rejections.incrementAndGet();
            logCounters("rejected");
            throw new PublicLibraryRankingBusyException();
        }
        logCounters("fallback");
        return new Resolution(null, bulkhead);
    }

    private CompletableFuture<PublicLibraryRankingSnapshot> startLoad() {
        CompletableFuture<PublicLibraryRankingSnapshot> current = inFlight.get();
        if (current != null) return current;
        if (ticker.read() < retryAfterNanos) return null;
        CompletableFuture<PublicLibraryRankingSnapshot> next = new CompletableFuture<>();
        if (!inFlight.compareAndSet(null, next)) return inFlight.get();
        next.orTimeout(buildWaitTimeout.toMillis(), TimeUnit.MILLISECONDS);
        next.whenComplete((value, failure) -> {
            // Only the backstop arrives here with the slot still held: fail() clears it before completing.
            if (failure instanceof TimeoutException && inFlight.get() == next) {
                fail(next, failure);
            }
        });
        try {
            executor.execute(() -> build(next));
        } catch (RuntimeException rejected) {
            fail(next, rejected);
        }
        return next;
    }

    private void build(CompletableFuture<PublicLibraryRankingSnapshot> pending) {
        long started = System.nanoTime();
        try {
            Published previous = cache.getIfPresent(KEY);
            PublicLibraryRankingSnapshot computed = readOnlyTransaction.execute(status ->
                    repository.buildPublicLibraryRankingSnapshot(generation.incrementAndGet(),
                            settings.getMaxNotes(), settings.getMaxTagEntries()));
            if (computed == null) throw new IllegalStateException("Ranking snapshot transaction returned null");
            // This liveness check is deliberately after the repeatable-read build transaction: a
            // second read inside it could only see the same suspicious zero again.
            if (computed.publicNotes() == 0 && (previous != null && previous.snapshot().publicNotes() > 0
                    || repository.countPublicNotesForRankingSnapshot() > 0)) {
                throw new IllegalStateException("Empty public ranking snapshot refused");
            }
            cache.put(KEY, new Published(computed, ticker.read()));
            pending.complete(computed);
            inFlight.compareAndSet(pending, null);
            logBuilt(computed, started);
        } catch (Throwable failure) {
            fail(pending, failure);
        }
    }

    private void logBuilt(PublicLibraryRankingSnapshot computed, long started) {
        try {
            log.info("PUBLIC_LIBRARY_RANKING_SNAPSHOT computed durationMs={} notes={} tagSlugs={} tagEntries={} copies={} likes={} views={} generation={}",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), computed.publicNotes(),
                    computed.tagIds().size(), computed.tagEntries(), computed.copies().size(),
                    computed.likes().size(), computed.views().size(), computed.generation());
        } catch (RuntimeException loggingFailure) {
            // A logging problem must never turn a successful publish into a recorded failure.
        }
    }

    private void fail(CompletableFuture<PublicLibraryRankingSnapshot> pending, Throwable failure) {
        failures.incrementAndGet();
        retryAfterNanos = ticker.read() + TimeUnit.SECONDS.toNanos(
                Math.max(0, settings.getRefreshBackoffSeconds()));
        // State first, logging last: a failing logger must not leave the single-flight slot held forever.
        inFlight.compareAndSet(pending, null);
        pending.completeExceptionally(failure);
        try {
            log.warn("PUBLIC_LIBRARY_RANKING_SNAPSHOT failed exception={} message={} failures={}",
                    failure.getClass().getName(), failure.getMessage(), failures.get());
        } catch (RuntimeException loggingFailure) {
            // Counters and slot are already updated.
        }
    }

    private void logCounters(String result) {
        if (RESULT_HIT.equals(result) || RESULT_COLD_HIT.equals(result)) {
            return;
        }
        long now = System.nanoTime();
        long last = lastAccessLogNanos.get();
        if (last != 0 && now - last < ACCESS_LOG_INTERVAL_NANOS) {
            return;
        }
        if (!lastAccessLogNanos.compareAndSet(last, now)) {
            return;
        }
        try {
            log.info("PUBLIC_LIBRARY_RANKING_SNAPSHOT access result={} hits={} fallbacks={} bulkheadRejections={} bypasses={} failures={}",
                    result, hits.get(), fallbacks.get(), rejections.get(), bypasses.get(), failures.get());
        } catch (RuntimeException loggingFailure) {
            // Never let a log line leak a bulkhead permit between tryAcquire and the return.
        }
    }

    void invalidateAllForTest() {
        CompletableFuture<PublicLibraryRankingSnapshot> current = inFlight.get();
        if (current != null) {
            try {
                current.get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // Tests only: the previous warm-up may fail on H2's PostgreSQL-only tag SQL.
            }
        }
        cache.invalidateAll();
        retryAfterNanos = 0;
    }

    void setBuildWaitTimeoutForTest(Duration timeout) {
        this.buildWaitTimeout = timeout;
    }

    long failuresForTest() {
        return failures.get();
    }

    long coldWaitersForTest() {
        return coldWaiters.get();
    }

    private record Published(PublicLibraryRankingSnapshot snapshot, long publishedNanos) {
    }

    public record Resolution(PublicLibraryRankingSnapshot snapshot, Semaphore permit) implements AutoCloseable {
        @Override
        public void close() {
            if (permit != null) permit.release();
        }
    }
}
