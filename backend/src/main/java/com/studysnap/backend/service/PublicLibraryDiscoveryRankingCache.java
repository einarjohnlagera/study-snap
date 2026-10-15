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
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PublicLibraryDiscoveryRankingCache {
    private static final Logger log = LoggerFactory.getLogger(PublicLibraryDiscoveryRankingCache.class);
    private static final String COMPUTED_LOG_PREFIX = "DISCOVERY_RANKING_CACHE computed";
    private static final String FAILED_LOG_PREFIX = "DISCOVERY_RANKING_CACHE failed";
    private static final String SNAPSHOT_KEY = "discovery";
    private static final int SECTION_LIMIT = 6;
    private static final Duration TTL = Duration.ofSeconds(300);

    private final NoteRepository noteRepository;
    private final TransactionTemplate readOnlyTransaction;
    private final AsyncLoadingCache<String, Snapshot> cache;
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong shortSectionResponses = new AtomicLong();

    public PublicLibraryDiscoveryRankingCache(
            NoteRepository noteRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("discoveryRankingTaskExecutor") AsyncTaskExecutor executor,
            Ticker ticker
    ) {
        this.noteRepository = noteRepository;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .ticker(ticker)
                .recordStats()
                .executor(executor::execute)
                .buildAsync((key, cacheExecutor) -> CompletableFuture.supplyAsync(this::compute, cacheExecutor));
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

    private Snapshot compute() {
        long started = System.nanoTime();
        try {
            Snapshot snapshot = readOnlyTransaction.execute(status -> rank());
            var stats = cache.synchronous().stats();
            log.info("{} durationMs={} featuredIds={} popularIds={} recentIds={} hits={} misses={} failures={} shortSectionResponses={}",
                    COMPUTED_LOG_PREFIX, Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    snapshot.featuredIds().size(), snapshot.popularIds().size(), snapshot.recentIds().size(),
                    stats.hitCount(), stats.missCount(), failures.get(), shortSectionResponses.getAndSet(0));
            return snapshot;
        } catch (RuntimeException exception) {
            failures.incrementAndGet();
            log.warn("{} exception={} message={}", FAILED_LOG_PREFIX,
                    exception.getClass().getName(), exception.getMessage());
            throw exception;
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

    public record Snapshot(List<UUID> featuredIds, List<UUID> popularIds,
                           List<UUID> recentIds, OffsetDateTime rankedAt) {
    }
}
