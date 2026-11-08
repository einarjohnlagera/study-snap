package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.exception.PublicLibraryRankingBusyException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicLibraryRankingSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicLibraryRankingSnapshotCacheTest {
    @Test
    void concurrentColdCallersShareOneLoadAndWarmupUsesTheSameFlight() throws Exception {
        NoteRepository repository = mock(NoteRepository.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(repository.buildPublicLibraryRankingSnapshot(anyLong(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    return snapshot(1, 1);
                });
        try (ExecutorService worker = Executors.newSingleThreadExecutor();
             ExecutorService callers = Executors.newFixedThreadPool(8)) {
            var cache = cache(repository, new StudySnapProperties(), worker, new AtomicLong());
            cache.warmUp();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var results = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> CompletableFuture.supplyAsync(() -> {
                        try (var resolution = cache.resolve()) {
                            return resolution.snapshot();
                        }
                    }, callers)).toList();
            release.countDown();
            assertThat(results.stream().map(CompletableFuture::join).map(PublicLibraryRankingSnapshot::generation))
                    .containsOnly(1L);
            verify(repository, times(1)).buildPublicLibraryRankingSnapshot(anyLong(), anyInt(), anyInt());
        }
    }

    @Test
    void refreshFailureKeepsPreviousAndBacksOffUntilCeilingThenBulkheads() throws Exception {
        NoteRepository repository = mock(NoteRepository.class);
        AtomicInteger calls = new AtomicInteger();
        when(repository.buildPublicLibraryRankingSnapshot(anyLong(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    if (calls.incrementAndGet() == 1) return snapshot(1, 1);
                    throw new IllegalStateException("database unavailable");
                });
        AtomicLong now = new AtomicLong();
        StudySnapProperties properties = new StudySnapProperties();
        properties.getPublicLibraryRankingSnapshot().setBulkheadMaxConcurrent(1);
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            var cache = cache(repository, properties, worker, now);
            try (var first = cache.resolve()) {
                assertThat(first.snapshot().generation()).isEqualTo(1);
            }
            now.set(TimeUnit.SECONDS.toNanos(301));
            try (var stale = cache.resolve()) {
                assertThat(stale.snapshot().generation()).isEqualTo(1);
            }
            awaitCalls(calls, 2);
            for (int index = 0; index < 5; index++) {
                try (var stale = cache.resolve()) {
                    assertThat(stale.snapshot().generation()).isEqualTo(1);
                }
            }
            assertThat(calls).hasValue(2);
            now.set(TimeUnit.SECONDS.toNanos(901));
            try (var fallback = cache.resolve()) {
                assertThat(fallback.snapshot()).isNull();
                assertThatThrownBy(cache::resolve).isInstanceOf(PublicLibraryRankingBusyException.class);
            }
        }
    }

    @Test
    void emptySnapshotIsRefusedAndNeverPublished() throws Exception {
        NoteRepository repository = mock(NoteRepository.class);
        when(repository.buildPublicLibraryRankingSnapshot(anyLong(), anyInt(), anyInt()))
                .thenReturn(snapshot(1, 0));
        when(repository.countPublicNotesForRankingSnapshot()).thenReturn(1L);
        StudySnapProperties properties = new StudySnapProperties();
        properties.getPublicLibraryRankingSnapshot().setColdStartWaitMs(1000);
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            var cache = cache(repository, properties, worker, new AtomicLong());
            try (var fallback = cache.resolve()) {
                assertThat(fallback.snapshot()).isNull();
            }
            verify(repository).countPublicNotesForRankingSnapshot();
        }
    }

    @Test
    void capRefusalFallsBackAndBypassNeverTakesBulkhead() throws Exception {
        NoteRepository repository = mock(NoteRepository.class);
        when(repository.buildPublicLibraryRankingSnapshot(anyLong(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("Public tag entry cap exceeded"));
        StudySnapProperties properties = new StudySnapProperties();
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            var cache = cache(repository, properties, worker, new AtomicLong());
            try (var fallback = cache.resolve()) {
                assertThat(fallback.snapshot()).isNull();
            }
            properties.getPublicLibraryRankingSnapshot().setTtlSeconds(0);
            try (var first = cache.resolve(); var second = cache.resolve(); var third = cache.resolve()) {
                assertThat(first.snapshot()).isNull();
                assertThat(second.snapshot()).isNull();
                assertThat(third.snapshot()).isNull();
            }
        }
    }

    @Test
    void coldWaitDeadlineFallsBackAndRejectsExcessWithoutStartingAnotherBuild() throws Exception {
        NoteRepository repository = mock(NoteRepository.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(repository.buildPublicLibraryRankingSnapshot(anyLong(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    return snapshot(1, 1);
                });
        StudySnapProperties properties = new StudySnapProperties();
        properties.getPublicLibraryRankingSnapshot().setColdStartWaitMs(1);
        properties.getPublicLibraryRankingSnapshot().setBulkheadMaxConcurrent(1);
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            var cache = cache(repository, properties, worker, new AtomicLong());
            try (var fallback = cache.resolve()) {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(fallback.snapshot()).isNull();
                assertThatThrownBy(cache::resolve).isInstanceOf(PublicLibraryRankingBusyException.class);
                verify(repository, times(1)).buildPublicLibraryRankingSnapshot(anyLong(), anyInt(), anyInt());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void stuckBuildIsReleasedByTheWaitBackstopSoTheSlotAndWaitersAreNotFrozen() throws Exception {
        NoteRepository repository = mock(NoteRepository.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        when(repository.buildPublicLibraryRankingSnapshot(anyLong(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    neverReleased.await(10, TimeUnit.SECONDS); // a thread stuck on a dead socket
                    return snapshot(1, 1);
                });
        StudySnapProperties properties = new StudySnapProperties();
        properties.getPublicLibraryRankingSnapshot().setColdStartWaitMs(5_000);
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            var cache = cache(repository, properties, worker, new AtomicLong());
            cache.setBuildWaitTimeoutForTest(java.time.Duration.ofMillis(200));
            long started = System.nanoTime();
            // The cold waiter is released by the backstop (about 200 ms), not by its own 5 s deadline.
            try (var fallback = cache.resolve()) {
                assertThat(fallback.snapshot()).isNull();
            }
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(3_000);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(cache.failuresForTest()).isEqualTo(1);
            neverReleased.countDown();
        }
    }

    private static PublicLibraryRankingSnapshotCache cache(NoteRepository repository,
            StudySnapProperties properties, ExecutorService worker, AtomicLong now) {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new PublicLibraryRankingSnapshotCache(repository, transactions,
                new TaskExecutorAdapter(worker), now::get, properties);
    }

    private static PublicLibraryRankingSnapshot snapshot(long generation, long notes) {
        return new PublicLibraryRankingSnapshot(generation, OffsetDateTime.now(), Map.of(), Map.of(),
                Map.of(), Map.of("a-b", Set.of(UUID.randomUUID())), notes, 1);
    }

    private static void awaitCalls(AtomicInteger calls, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (calls.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(calls).hasValue(expected);
    }
}
