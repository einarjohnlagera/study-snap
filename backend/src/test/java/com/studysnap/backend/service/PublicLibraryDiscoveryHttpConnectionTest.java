package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicLibraryRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.security.JwtService;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicLibraryDiscoveryHttpConnectionTest {
    private static final int CALLERS = 8;

    @LocalServerPort private int port;
    @Autowired private HikariDataSource dataSource;
    @Autowired private PublicLibraryDiscoveryRankingCache cache;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;
    @MockitoSpyBean private NoteRepository noteRepository;
    @MockitoSpyBean private UserRepository userRepository;

    @Test
    void concurrentHttpDiscoveryWaitersDoNotHoldJdbcConnections() throws Exception {
        cache.invalidateAll();
        CountDownLatch rankingEntered = new CountDownLatch(1);
        CountDownLatch releaseRanking = new CountDownLatch(1);
        doAnswer(invocation -> {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            rankingEntered.countDown();
            assertThat(releaseRanking.await(15, TimeUnit.SECONDS)).isTrue();
            return new PublicLibraryRepository.DiscoveryRankedIds(List.of(), List.of());
        }).when(noteRepository).findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt());
        doReturn(List.of()).when(noteRepository)
                .findPublicLibraryRankedPageIds(any(), any(), any(), any(), anyInt(), anyInt());
        doReturn(List.of()).when(noteRepository).findPublicLibraryListItemProjectionsByIdIn(any());
        UserEntity viewer = new UserEntity();
        viewer.setId(UUID.randomUUID());
        viewer.setRole(UserRole.USER);
        viewer.setStatus(UserStatus.ACTIVE);
        viewer.setTokenVersion(0);
        doAnswer(invocation -> {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            return Optional.of(viewer);
        }).when(userRepository).findById(viewer.getId());
        String accessToken = jwtService.generateAccessToken(viewer);
        RestClient client = RestClient.create("http://localhost:" + port);
        try (ExecutorService callers = Executors.newFixedThreadPool(CALLERS)) {
            long hitsBefore = cache.stats().hitCount();
            List<CompletableFuture<ResponseEntity<String>>> responses = java.util.stream.IntStream.range(0, CALLERS)
                    .mapToObj(index -> CompletableFuture.supplyAsync(() -> {
                        var request = client.get().uri("/api/notes/public/discovery-sections");
                        if (index == 0) {
                            request.header("Authorization", "Bearer " + accessToken);
                        }
                        return request.retrieve().toEntity(String.class);
                    }, callers))
                    .toList();
            try {
                assertThat(rankingEntered.await(10, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                while (cache.stats().hitCount() - hitsBefore < CALLERS - 1 && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                assertThat(cache.stats().hitCount() - hitsBefore).isGreaterThanOrEqualTo(CALLERS - 1);
                assertThat(dataSource.getHikariPoolMXBean().getActiveConnections())
                        .as("Only the ranking worker may hold a connection while HTTP requests wait")
                        .isEqualTo(1);
            } finally {
                releaseRanking.countDown();
            }
            List<ResponseEntity<String>> completed = responses.stream().map(CompletableFuture::join).toList();
            assertThat(completed).allSatisfy(response -> assertThat(response.getStatusCode().value()).isEqualTo(200));
            List<JsonNode> bodies = completed.stream().map(response -> {
                try {
                    return objectMapper.readTree(response.getBody());
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).toList();
            assertThat(bodies).hasSize(CALLERS).allSatisfy(body -> {
                assertThat(body.path("featured").size()).isZero();
                assertThat(body.path("popular").size()).isZero();
                assertThat(body.path("recent").size()).isZero();
            });
            assertThat(bodies).containsOnly(bodies.getFirst());
            verify(noteRepository, times(1)).findPublicLibraryFeaturedAndPopularIds(any(), any(), anyInt());
            verify(userRepository, times(1)).findById(viewer.getId());
        } finally {
            cache.invalidateAll();
        }
    }
}
