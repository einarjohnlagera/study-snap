package com.studysnap.backend.service;

import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicLibraryRankingSnapshot;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicLibraryRankingSnapshotHttpConnectionTest {
    private static final int CALLERS = 8;

    @LocalServerPort private int port;
    @Autowired private HikariDataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PublicLibraryRankingSnapshotCache cache;
    @MockitoSpyBean private NoteRepository repository;

    @Test
    void eightHttpWaitersHoldNoJdbcConnectionWhileTheSingleSnapshotBuildRuns() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return new PublicLibraryRankingSnapshot(1, OffsetDateTime.now(), Map.of(), Map.of(), Map.of(),
                    Map.of(), 1, 0);
        }).when(repository).buildPublicLibraryRankingSnapshot(anyLong(), anyInt(), anyInt());
        doReturn(0L).when(repository).countPublicLibraryRankedSnapshotMatches(any(), any(), any());
        doReturn(List.of()).when(repository)
                .findPublicLibraryRankedSnapshotPageIds(any(), any(), any(), anyInt(), anyInt(), any());
        cache.invalidateAllForTest();
        // The spy also recorded the application-start warm-up build; the assertion below is about this request burst.
        clearInvocations(repository);
        RestClient client = RestClient.create("http://localhost:" + port);
        try (ExecutorService callers = Executors.newFixedThreadPool(CALLERS)) {
            List<CompletableFuture<Integer>> requests = java.util.stream.IntStream.range(0, CALLERS)
                    .mapToObj(index -> CompletableFuture.supplyAsync(() -> client.get()
                            .uri("/api/notes/public?tag=waiting&sort=recommended&page=0&pageSize=20")
                            .retrieve().toBodilessEntity().getStatusCode().value(), callers)).toList();
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (cache.coldWaitersForTest() < CALLERS && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                assertThat(cache.coldWaitersForTest()).isEqualTo(CALLERS);
                assertThat(dataSource.getHikariPoolMXBean().getActiveConnections())
                        .as("Only the snapshot worker holds a JDBC connection")
                        .isEqualTo(1);
            } finally {
                release.countDown();
            }
            assertThat(requests.stream().map(CompletableFuture::join)).containsOnly(200);
            verify(repository, times(1)).buildPublicLibraryRankingSnapshot(anyLong(), anyInt(), anyInt());
        } finally {
            cache.invalidateAllForTest();
        }
    }
}
