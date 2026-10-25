package com.studysnap.backend.service;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.studysnap.backend.model.PublicLibrarySort;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicLibraryFilterCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PublicLibraryDiscoveryRankingCache {
    private static final Logger log = LoggerFactory.getLogger(PublicLibraryDiscoveryRankingCache.class);
    private static final String COMPUTED_LOG_PREFIX = "DISCOVERY_RANKING_CACHE computed";
    private static final String FAILED_LOG_PREFIX = "DISCOVERY_RANKING_CACHE failed";
    private static final String SNAPSHOT_KEY = "discovery";
    private static final int SECTION_LIMIT = 6;
    private static final Duration TTL = Duration.ofSeconds(300);
    // Both bounds precede the 60 s Hikari leak-detection threshold.
    static final int RANKING_TRANSACTION_TIMEOUT_SECONDS = 50;
    static final Duration RANKING_WAIT_TIMEOUT = Duration.ofSeconds(55);

    private final NoteRepository noteRepository;
    private final TransactionTemplate readOnlyTransaction;
    private final AsyncLoadingCache<String, Snapshot> cache;
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong shortSectionResponses = new AtomicLong();
    private volatile Duration waitTimeout;

    @Autowired
    public PublicLibraryDiscoveryRankingCache(
            NoteRepository noteRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("discoveryRankingTaskExecutor") AsyncTaskExecutor executor,
            Ticker ticker
    ) {
        this(noteRepository, transactionManager, executor, ticker,
                RANKING_TRANSACTION_TIMEOUT_SECONDS, RANKING_WAIT_TIMEOUT);
    }

    PublicLibraryDiscoveryRankingCache(NoteRepository noteRepository,
            PlatformTransactionManager transactionManager, AsyncTaskExecutor executor, Ticker ticker,
            int transactionTimeoutSeconds, Duration waitTimeout) {
        this.noteRepository = noteRepository;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.readOnlyTransaction.setTimeout(transactionTimeoutSeconds);
        this.waitTimeout = waitTimeout;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .ticker(ticker)
                .recordStats()
                .executor(executor::execute)
                .buildAsync((key, cacheExecutor) -> load(cacheExecutor));
    }

    private CompletableFuture<Snapshot> load(java.util.concurrent.Executor cacheExecutor) {
        CompletableFuture<Snapshot> result = new CompletableFuture<>();
        AtomicBoolean failureReported = new AtomicBoolean();
        result.orTimeout(waitTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((snapshot, failure) -> {
                    if (failure instanceof TimeoutException) {
                        reportFailure(failure, failureReported);
                    }
                });
        try {
            cacheExecutor.execute(() -> {
                // An expired queued future has already been removed by Caffeine.
                if (result.isDone()) {
                    return;
                }
                long started = System.nanoTime();
                try {
                    Snapshot snapshot = compute(failureReported);
                    // Capture before publishing: released waiters may record short sections against the new snapshot.
                    long shortResponses = shortSectionResponses.getAndSet(0);
                    if (result.complete(snapshot)) {
                        var stats = cache.synchronous().stats();
                        log.info("{} durationMs={} featuredIds={} popularIds={} recentIds={} hits={} misses={} failures={} shortSectionResponses={}",
                                COMPUTED_LOG_PREFIX, Duration.ofNanos(System.nanoTime() - started).toMillis(),
                                snapshot.featuredIds().size(), snapshot.popularIds().size(), snapshot.recentIds().size(),
                                stats.hitCount(), stats.missCount(), failures.get(), shortResponses);
                    } else {
                        shortSectionResponses.addAndGet(shortResponses);
                    }
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException rejection) {
            if (result.completeExceptionally(rejection)) {
                reportFailure(rejection, failureReported);
            }
        }
        return result;
    }

    public Snapshot get() {
        return cache.get(SNAPSHOT_KEY).join();
    }

    public void recordResponse(Snapshot snapshot, int featuredSize, int popularSize, int recentSize) {
        if (featuredSize < snapshot.featuredIds().size()
                || popularSize < snapshot.popularIds().size()
                || recentSize < snapshot.recentIds().size()) {
            shortSectionResponses.incrementAndGet();
        }
    }

    private Snapshot compute(AtomicBoolean failureReported) {
        try {
            return readOnlyTransaction.execute(status -> rank());
        } catch (Throwable exception) {
            reportFailure(exception, failureReported);
            throw exception;
        }
    }

    private void reportFailure(Throwable failure, AtomicBoolean failureReported) {
        if (failureReported.compareAndSet(false, true)) {
            failures.incrementAndGet();
            log.warn("{} exception={} message={}", FAILED_LOG_PREFIX,
                    failure.getClass().getName(), failure.getMessage());
        }
    }

    private Snapshot rank() {
        PublicLibraryFilterCriteria criteria = new PublicLibraryFilterCriteria(
                null, AccountPurgeService.DELETED_USER_ID, null, null, List.of(),
                null, null, null, false, List.of());
        OffsetDateTime rankedAt = OffsetDateTime.now();
        var ranked = noteRepository.findPublicLibraryFeaturedAndPopularIds(criteria, rankedAt, SECTION_LIMIT);
        List<UUID> featured = List.copyOf(ranked.featuredIds());
        List<UUID> popular = List.copyOf(ranked.popularIds());
        List<UUID> excluded = new ArrayList<>(featured);
        excluded.addAll(popular);
        List<UUID> recent = noteRepository.findPublicLibraryRankedPageIds(
                criteria, PublicLibrarySort.RECENT, rankedAt, excluded, 0, SECTION_LIMIT);
        return new Snapshot(featured, popular, List.copyOf(recent), rankedAt);
    }

    void invalidateAll() {
        cache.synchronous().invalidateAll();
    }

    CacheStats stats() {
        return cache.synchronous().stats();
    }

    long failures() {
        return failures.get();
    }

    long shortSectionResponses() {
        return shortSectionResponses.get();
    }

    void setTimeoutsForTest(int transactionTimeoutSeconds, Duration waitTimeout) {
        readOnlyTransaction.setTimeout(transactionTimeoutSeconds);
        this.waitTimeout = waitTimeout;
    }

    public record Snapshot(List<UUID> featuredIds, List<UUID> popularIds,
                           List<UUID> recentIds, OffsetDateTime rankedAt) {
    }
}
