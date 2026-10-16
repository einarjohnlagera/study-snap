package com.studysnap.backend.service;

import com.github.benmanes.caffeine.cache.Ticker;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicLibraryRepository;
import com.studysnap.backend.repository.PublicLibraryFilterCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.slf4j.LoggerFactory;

class PublicLibraryDiscoveryRankingCacheTest {
    private static final int CALLERS = 8;

    @Test
    void coldRequestsShareOneRanking() throws Exception {
        try (ExecutorService worker = Executors.newSingleThreadExecutor();
             ExecutorService callers = Executors.newFixedThreadPool(CALLERS)) {
            NoteRepository repository = mock(NoteRepository.class);
            CountDownLatch rankingEntered = new CountDownLatch(1);
            CountDownLatch releaseRanking = new CountDownLatch(1);
            UUID id = UUID.randomUUID();
            when(repository.findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt()))
                    .thenAnswer(invocation -> {
                        assertThat(invocation.getArgument(0, PublicLibraryFilterCriteria.class).viewerUserId())
                                .isNull();
                        rankingEntered.countDown();
                        assertThat(releaseRanking.await(5, TimeUnit.SECONDS)).isTrue();
                        return new PublicLibraryRepository.DiscoveryRankedIds(List.of(id), List.of());
                    });
            when(repository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            PublicLibraryDiscoveryRankingCache cache = cache(repository,
                    mockTransactionManager(), worker, () -> 0L);

            List<CompletableFuture<PublicLibraryDiscoveryRankingCache.Snapshot>> results =
                    java.util.stream.IntStream.range(0, CALLERS)
                            .mapToObj(index -> CompletableFuture.supplyAsync(cache::get, callers))
                            .toList();
            assertThat(rankingEntered.await(5, TimeUnit.SECONDS)).isTrue();
            awaitHits(cache, CALLERS - 1);
            verify(repository, times(1)).findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt());
            releaseRanking.countDown();
            assertThat(results.stream().map(CompletableFuture::join).map(result -> result.featuredIds())
                    .toList()).containsOnly(List.of(id));
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
            Logger logger = (Logger) LoggerFactory.getLogger(PublicLibraryDiscoveryRankingCache.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            try {
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
                assertThat(cache.failures()).isEqualTo(1);
                assertThat(appender.list.stream().filter(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().startsWith("DISCOVERY_RANKING_CACHE failed")))
                        .hasSize(1);
                awaitSuccessfulRetry(cache);
                assertThat(calls).hasValue(2);
            } finally {
                releaseRanking.countDown();
                logger.detachAppender(appender);
                appender.stop();
            }
        }
    }

    @Test
    void timedOutQueuedLoadSkipsRankingWhenWorkerFrees() throws Exception {
        NoteRepository repository = mock(NoteRepository.class);
        when(repository.findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt()))
                .thenReturn(new PublicLibraryRepository.DiscoveryRankedIds(List.of(), List.of()));
        when(repository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        try (ExecutorService worker = Executors.newSingleThreadExecutor();
             ExecutorService caller = Executors.newSingleThreadExecutor()) {
            CountDownLatch occupied = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            worker.execute(() -> {
                occupied.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(occupied.await(2, TimeUnit.SECONDS)).isTrue();
            PublicLibraryDiscoveryRankingCache cache = new PublicLibraryDiscoveryRankingCache(
                    repository, mockTransactionManager(), new TaskExecutorAdapter(worker), () -> 0L,
                    30, Duration.ofMillis(100));
            try {
                CompletableFuture<PublicLibraryDiscoveryRankingCache.Snapshot> timedOut =
                        CompletableFuture.supplyAsync(cache::get, caller);
                assertThatThrownBy(() -> timedOut.get(2, TimeUnit.SECONDS))
                        .hasRootCauseInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            // FIFO marker proves the timed-out task ran before this assertion.
            worker.submit(() -> {}).get(2, TimeUnit.SECONDS);
            verify(repository, times(0)).findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt());
            cache.get();
            verify(repository, times(1)).findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt());
        }
    }

    @Test
    void lateRankingAfterWaitTimeoutDoesNotLogSuccessOrResetShortResponses() throws Exception {
        NoteRepository repository = mock(NoteRepository.class);
        CountDownLatch rankingEntered = new CountDownLatch(1);
        CountDownLatch releaseRanking = new CountDownLatch(1);
        when(repository.findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    rankingEntered.countDown();
                    assertThat(releaseRanking.await(5, TimeUnit.SECONDS)).isTrue();
                    return new PublicLibraryRepository.DiscoveryRankedIds(List.of(), List.of());
                });
        when(repository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        Logger logger = (Logger) LoggerFactory.getLogger(PublicLibraryDiscoveryRankingCache.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (ExecutorService worker = Executors.newSingleThreadExecutor();
             ExecutorService caller = Executors.newSingleThreadExecutor()) {
            PublicLibraryDiscoveryRankingCache cache = new PublicLibraryDiscoveryRankingCache(
                    repository, mockTransactionManager(), new TaskExecutorAdapter(worker), () -> 0L,
                    30, Duration.ofMillis(100));
            UUID id = UUID.randomUUID();
            cache.recordResponse(new PublicLibraryDiscoveryRankingCache.Snapshot(
                    List.of(id), List.of(), List.of(), java.time.OffsetDateTime.now()), 0, 0, 0);
            try {
                CompletableFuture<PublicLibraryDiscoveryRankingCache.Snapshot> result =
                        CompletableFuture.supplyAsync(cache::get, caller);
                assertThat(rankingEntered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
                        .hasRootCauseInstanceOf(TimeoutException.class);
            } finally {
                releaseRanking.countDown();
            }
            worker.submit(() -> {}).get(2, TimeUnit.SECONDS);
            assertThat(appender.list.stream().filter(event -> event.getFormattedMessage()
                    .startsWith("DISCOVERY_RANKING_CACHE computed"))).isEmpty();
            assertThat(cache.shortSectionResponses()).isEqualTo(1);
            assertThat(cache.failures()).isEqualTo(1);
        } finally {
            releaseRanking.countDown();
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void rejectedRankingSubmissionFailsFastAndReportsOnce() {
        NoteRepository repository = mock(NoteRepository.class);
        Logger logger = (Logger) LoggerFactory.getLogger(PublicLibraryDiscoveryRankingCache.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            PublicLibraryDiscoveryRankingCache cache = new PublicLibraryDiscoveryRankingCache(
                    repository, mockTransactionManager(),
                    new TaskExecutorAdapter(new java.util.concurrent.Executor() {
                        private final AtomicInteger submissions = new AtomicInteger();

                        @Override
                        public void execute(Runnable command) {
                            if (submissions.getAndIncrement() == 0) {
                                throw new RejectedExecutionException("queue full");
                            }
                            command.run();
                        }
                    }), () -> 0L, 30, Duration.ofSeconds(5));
            assertThatThrownBy(cache::get)
                    .hasRootCauseInstanceOf(RejectedExecutionException.class)
                    .hasRootCauseMessage("queue full");
            assertThat(cache.failures()).isEqualTo(1);
            assertThat(appender.list.stream().filter(event -> event.getLevel() == Level.WARN
                    && event.getFormattedMessage().startsWith("DISCOVERY_RANKING_CACHE failed")))
                    .hasSize(1);
            verify(repository, times(0)).findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void shortSectionResponsesResetAtNextComputation() throws Exception {
        NoteRepository repository = mock(NoteRepository.class);
        UUID id = UUID.randomUUID();
        when(repository.findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt()))
                .thenReturn(new PublicLibraryRepository.DiscoveryRankedIds(List.of(id), List.of()));
        when(repository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            PublicLibraryDiscoveryRankingCache cache = cache(repository, mockTransactionManager(), worker, () -> 0L);
            var snapshot = cache.get();
            worker.submit(() -> {}).get(2, TimeUnit.SECONDS);
            cache.recordResponse(snapshot, 0, 0, 0);
            assertThat(cache.shortSectionResponses()).isEqualTo(1);
            cache.invalidateAll();
            cache.get();
            worker.submit(() -> {}).get(2, TimeUnit.SECONDS);
            assertThat(cache.shortSectionResponses()).isZero();
        }
    }

    @Test
    void errorIsCountedLoggedAndNotCached() {
        NoteRepository repository = mock(NoteRepository.class);
        AssertionError failure = new AssertionError("ranking error");
        when(repository.findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt()))
                .thenThrow(failure)
                .thenReturn(new PublicLibraryRepository.DiscoveryRankedIds(List.of(), List.of()));
        when(repository.findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        Logger logger = (Logger) LoggerFactory.getLogger(PublicLibraryDiscoveryRankingCache.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            PublicLibraryDiscoveryRankingCache cache = cache(repository, mockTransactionManager(), worker, () -> 0L);
            assertThatThrownBy(cache::get).hasRootCause(failure);
            assertThat(cache.failures()).isEqualTo(1);
            assertThat(appender.list.stream().filter(event -> event.getFormattedMessage()
                    .startsWith("DISCOVERY_RANKING_CACHE failed"))).hasSize(1);
            awaitSuccessfulRetry(cache);
            verify(repository, times(2)).findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
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

    private static void awaitSuccessfulRetry(PublicLibraryDiscoveryRankingCache cache) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (true) {
            try {
                cache.get();
                return;
            } catch (java.util.concurrent.CompletionException failure) {
                assertThat(System.nanoTime()).isLessThan(deadline);
                Thread.onSpinWait();
            }
        }
    }

    private static void awaitHits(PublicLibraryDiscoveryRankingCache cache, int expected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (cache.stats().hitCount() < expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(cache.stats().hitCount()).isGreaterThanOrEqualTo(expected);
    }
}
