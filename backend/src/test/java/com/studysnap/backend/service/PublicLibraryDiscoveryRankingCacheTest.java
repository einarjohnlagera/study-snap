package com.studysnap.backend.service;

import com.github.benmanes.caffeine.cache.Ticker;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicLibraryRepository;
import com.studysnap.backend.repository.PublicLibraryFilterCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class PublicLibraryDiscoveryRankingCacheTest {
    private static final int CALLERS = 8;

    @Test
    void coldRequestsShareOneRankingAndWaitersHoldNoJdbcConnection() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:discovery_guard;DB_CLOSE_DELAY=-1");
        config.setMaximumPoolSize(10);
        try (HikariDataSource dataSource = new HikariDataSource(config);
             ExecutorService worker = Executors.newSingleThreadExecutor();
             ExecutorService callers = Executors.newFixedThreadPool(CALLERS)) {
            NoteRepository repository = mock(NoteRepository.class);
            CountDownLatch rankingEntered = new CountDownLatch(1);
            CountDownLatch releaseRanking = new CountDownLatch(1);
            UUID id = UUID.randomUUID();
            when(repository.findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt()))
                    .thenAnswer(invocation -> {
                        assertThat(invocation.getArgument(0, PublicLibraryFilterCriteria.class).viewerUserId())
                                .isNull();
                        new JdbcTemplate(dataSource).queryForObject("select 1", Integer.class);
                        rankingEntered.countDown();
                        assertThat(releaseRanking.await(5, TimeUnit.SECONDS)).isTrue();
                        return new PublicLibraryRepository.DiscoveryRankedIds(List.of(id), List.of());
                    });
            when(repository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            PublicLibraryDiscoveryRankingCache cache = cache(repository,
                    new DataSourceTransactionManager(dataSource), worker, () -> 0L);

            List<CompletableFuture<PublicLibraryDiscoveryRankingCache.Snapshot>> results =
                    java.util.stream.IntStream.range(0, CALLERS)
                            .mapToObj(index -> CompletableFuture.supplyAsync(cache::get, callers))
                            .toList();
            assertThat(rankingEntered.await(5, TimeUnit.SECONDS)).isTrue();
            awaitHits(cache, CALLERS - 1);
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isLessThanOrEqualTo(1);
            verify(repository, times(1)).findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt());
            releaseRanking.countDown();
            assertThat(results.stream().map(CompletableFuture::join).map(result -> result.featuredIds())
                    .toList()).containsOnly(List.of(id));
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }

    @Test
    void expiryRecomputesOnceAfterThreeHundredSeconds() throws Exception {
        AtomicLong nanos = new AtomicLong();
        NoteRepository repository = mock(NoteRepository.class);
        AtomicInteger calls = new AtomicInteger();
        when(repository.findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    return new PublicLibraryRepository.DiscoveryRankedIds(List.of(), List.of());
                });
        when(repository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        try (ExecutorService worker = Executors.newSingleThreadExecutor();
             ExecutorService callers = Executors.newFixedThreadPool(CALLERS)) {
            PublicLibraryDiscoveryRankingCache cache = cache(repository,
                    mockTransactionManager(), worker, nanos::get);
            runCallers(cache, callers);
            nanos.set(Duration.ofSeconds(299).toNanos());
            runCallers(cache, callers);
            assertThat(calls).hasValue(1);
            nanos.set(Duration.ofSeconds(301).toNanos());
            runCallers(cache, callers);
            assertThat(calls).hasValue(2);
        }
    }

    @Test
    void inFlightRankingSurvivesTtlAndExpiresFromCompletion() throws Exception {
        AtomicLong nanos = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch rankingEntered = new CountDownLatch(1);
        CountDownLatch releaseRanking = new CountDownLatch(1);
        UUID id = UUID.randomUUID();
        NoteRepository repository = mock(NoteRepository.class);
        when(repository.findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    if (calls.incrementAndGet() == 1) {
                        rankingEntered.countDown();
                        assertThat(releaseRanking.await(10, TimeUnit.SECONDS)).isTrue();
                    }
                    return new PublicLibraryRepository.DiscoveryRankedIds(List.of(id), List.of());
                });
        when(repository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        try (ExecutorService worker = Executors.newSingleThreadExecutor();
             ExecutorService callers = Executors.newFixedThreadPool(CALLERS + 1)) {
            PublicLibraryDiscoveryRankingCache cache = cache(repository,
                    mockTransactionManager(), worker, nanos::get);
            List<CompletableFuture<PublicLibraryDiscoveryRankingCache.Snapshot>> results =
                    java.util.stream.IntStream.range(0, CALLERS)
                            .mapToObj(index -> CompletableFuture.supplyAsync(cache::get, callers))
                            .toList();
            try {
                assertThat(rankingEntered.await(5, TimeUnit.SECONDS)).isTrue();
                nanos.set(Duration.ofSeconds(900).toNanos());
                results = new java.util.ArrayList<>(results);
                results.add(CompletableFuture.supplyAsync(cache::get, callers));
                awaitHits(cache, CALLERS);
                assertThat(calls).hasValue(1);
            } finally {
                releaseRanking.countDown();
            }
            assertThat(results.stream().map(CompletableFuture::join).map(result -> result.featuredIds())
                    .toList()).containsOnly(List.of(id));
            nanos.set(Duration.ofSeconds(1199).toNanos());
            runCallers(cache, callers);
            assertThat(calls).hasValue(1);
            nanos.set(Duration.ofSeconds(1201).toNanos());
            runCallers(cache, callers);
            assertThat(calls).hasValue(2);
        }
    }

    @Test
    void failedLoadReachesAllWaitersOnceAndNextRequestRetries() throws Exception {
        NoteRepository repository = mock(NoteRepository.class);
        CountDownLatch rankingEntered = new CountDownLatch(1);
        CountDownLatch releaseRanking = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("ranking failed");
        when(repository.findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    if (calls.incrementAndGet() == 1) {
                        rankingEntered.countDown();
                        assertThat(releaseRanking.await(5, TimeUnit.SECONDS)).isTrue();
                        throw failure;
                    }
                    return new PublicLibraryRepository.DiscoveryRankedIds(List.of(), List.of());
                });
        when(repository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        try (ExecutorService worker = Executors.newSingleThreadExecutor();
             ExecutorService callers = Executors.newFixedThreadPool(CALLERS)) {
            PublicLibraryDiscoveryRankingCache cache = cache(repository,
                    mockTransactionManager(), worker, () -> 0L);
            List<CompletableFuture<PublicLibraryDiscoveryRankingCache.Snapshot>> results =
                    java.util.stream.IntStream.range(0, CALLERS)
                            .mapToObj(index -> CompletableFuture.supplyAsync(cache::get, callers))
                            .toList();
            assertThat(rankingEntered.await(5, TimeUnit.SECONDS)).isTrue();
            awaitHits(cache, CALLERS - 1);
            releaseRanking.countDown();
            for (CompletableFuture<PublicLibraryDiscoveryRankingCache.Snapshot> result : results) {
                assertThatThrownBy(result::join).hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasRootCauseMessage("ranking failed");
            }
            assertThat(calls).hasValue(1);
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (true) {
                try {
                    cache.get();
                    break;
                } catch (java.util.concurrent.CompletionException exception) {
                    assertThat(exception).hasRootCause(failure);
                    assertThat(System.nanoTime()).isLessThan(deadline);
                    Thread.sleep(1);
                }
            }
            assertThat(calls).hasValue(2);
        }
    }

    @Test
    void resetHookInvalidatesSnapshotBetweenFixtures() {
        NoteRepository repository = mock(NoteRepository.class);
        when(repository.findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt()))
                .thenReturn(new PublicLibraryRepository.DiscoveryRankedIds(List.of(), List.of()));
        when(repository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            PublicLibraryDiscoveryRankingCache cache = cache(repository,
                    mockTransactionManager(), worker, () -> 0L);
            cache.get();
            cache.invalidateAll();
            cache.get();
            verify(repository, times(2)).findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt());
        }
    }

    private static PublicLibraryDiscoveryRankingCache cache(NoteRepository repository,
            org.springframework.transaction.PlatformTransactionManager transactions,
            ExecutorService worker, Ticker ticker) {
        return new PublicLibraryDiscoveryRankingCache(repository, transactions,
                new TaskExecutorAdapter(worker), ticker);
    }

    private static org.springframework.transaction.PlatformTransactionManager mockTransactionManager() {
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        return manager;
    }

    private static void runCallers(PublicLibraryDiscoveryRankingCache cache, ExecutorService callers) {
        List<CompletableFuture<PublicLibraryDiscoveryRankingCache.Snapshot>> results =
                java.util.stream.IntStream.range(0, CALLERS)
                        .mapToObj(index -> CompletableFuture.supplyAsync(cache::get, callers))
                        .toList();
        results.forEach(CompletableFuture::join);
    }

    private static void awaitHits(PublicLibraryDiscoveryRankingCache cache, int expected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (cache.stats().hitCount() < expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(cache.stats().hitCount()).isGreaterThanOrEqualTo(expected);
    }
}
