package com.studysnap.backend.config;

import com.studysnap.backend.dto.PublicNoteListResponse;
import com.studysnap.backend.service.NoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

/** Full application filter chain, including Spring Security and downstream error responses. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PublicApiDiagnosticsFilterIntegrationTest.DeniedEndpoint.class,
        PublicApiDiagnosticsFilterIntegrationTest.FixedClockConfig.class})
class PublicApiDiagnosticsFilterIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private PublicApiDiagnosticsAggregator aggregator;
    @Autowired private FixedClockConfig clockConfig;
    @Autowired private CapturingLog log;
    @MockitoBean private NoteService noteService;

    @Test
    void catalogWalkHeaderLeavesRealListingResponseIdenticalAndAppearsInNextMinute() throws Exception {
        doReturn(new PublicNoteListResponse(List.of(), 0, 0, 1, 0L, false)).when(noteService)
                .listPublic(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                        any(Boolean.class), any());
        long before = aggregator.currentBucket().walks.get(0);
        MockHttpServletResponse baseline = mvc.perform(get("/notes/public")
                .servletPath("/notes/public").param("page", "0").param("pageSize", "1")
                .param("sort", "recent").contentType("application/json")
                .header("X-Request-Id", "catalog-walk-parity"))
                .andReturn().getResponse();
        MockHttpServletResponse withHeader = mvc.perform(get("/notes/public")
                .servletPath("/notes/public").param("page", "0").param("pageSize", "1")
                .param("sort", "recent").contentType("application/json")
                .header("X-Request-Id", "catalog-walk-parity")
                .header("X-NoteLib-Catalog-Walk", "sitemap"))
                .andReturn().getResponse();

        assertThat(baseline.getStatus()).isEqualTo(200);
        assertThat(withHeader.getStatus()).isEqualTo(baseline.getStatus());
        assertThat(withHeader.getContentAsByteArray()).isEqualTo(baseline.getContentAsByteArray());
        assertThat(withHeader.getHeaderNames()).isEqualTo(baseline.getHeaderNames());
        for (String name : baseline.getHeaderNames()) {
            assertThat(withHeader.getHeaders(name)).as(name).isEqualTo(baseline.getHeaders(name));
        }
        assertThat(aggregator.currentBucket().walks.get(0) - before).isEqualTo(1);
        clockConfig.clock.advanceMinutes(1);
        aggregator.flushStragglers();
        for (int i = 0; i < 200 && log.aggregates.isEmpty(); i++) Thread.sleep(10);
        assertThat(log.aggregates).anySatisfy(line -> assertThat(line)
                .contains("walk=[sitemap:1,exam-hub:0,subject:0,other:0,none:"));
    }

    @Test
    void seesAnonymousAuthenticatedFacetDeniedAndPreflightResponses() throws Exception {
        PublicApiDiagnosticsAggregator.Bucket bucket = aggregator.currentBucket();
        long total = bucket.total.sum();
        long anonymous = bucket.anonymous.sum();
        long authenticated = bucket.authenticated.sum();
        long options = bucket.options.sum();
        long forbidden = bucket.statuses.get(403);
        long unauthorized = bucket.statuses.get(401);
        long facets = bucket.groups.get(2);
        long listing = bucket.groups.get(0);

        int listingStatus = mvc.perform(get("/notes/public?search=secret+value").servletPath("/notes/public"))
                .andReturn().getResponse().getStatus();
        mvc.perform(get("/notes/public").servletPath("/notes/public").header("Authorization", "Bearer invalid-token"));
        mvc.perform(get("/subjects?scope=mine").servletPath("/subjects"));
        int securityStatus = mvc.perform(head("/tags").servletPath("/tags")).andReturn().getResponse().getStatus();
        mvc.perform(get("/public/diagnostic-forbidden").servletPath("/public/diagnostic-forbidden"));
        mvc.perform(options("/notes/public").servletPath("/notes/public"));

        assertThat(securityStatus).isEqualTo(401);
        assertThat(bucket.total.sum() - total).isEqualTo(5);
        assertThat(bucket.anonymous.sum() - anonymous).isEqualTo(4);
        assertThat(bucket.authenticated.sum() - authenticated).isEqualTo(1);
        assertThat(bucket.options.sum() - options).isEqualTo(1);
        assertThat(bucket.statuses.get(listingStatus)).isGreaterThan(0);
        assertThat(bucket.statuses.get(401) - unauthorized).isEqualTo(2);
        assertThat(bucket.statuses.get(403) - forbidden).isEqualTo(1);
        assertThat(bucket.groups.get(0) - listing).isEqualTo(1);
        assertThat(bucket.groups.get(2) - facets).isEqualTo(2);
    }

    @RestController
    static class DeniedEndpoint {
        @GetMapping("/public/diagnostic-forbidden")
        void forbidden(HttpServletResponse response) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
        }
    }

    @TestConfiguration
    static class FixedClockConfig {
        final MutableClock clock = new MutableClock();

        @Bean
        @Primary
        CapturingLog capturingDiagnosticsLog() { return new CapturingLog(); }

        @Bean
        @Primary
        PublicApiDiagnosticsAggregator fixedDiagnostics(StudySnapProperties properties, PublicApiDiagnosticsLog log) {
            return new PublicApiDiagnosticsAggregator(properties.getPublicApiDiagnostics(), log,
                    clock);
        }
    }

    static class CapturingLog extends PublicApiDiagnosticsLog {
        final List<String> aggregates = new CopyOnWriteArrayList<>();
        @Override public void aggregate(String line) { aggregates.add(line); }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-10-29T12:00:00Z"));
        void advanceMinutes(long minutes) { time.updateAndGet(now -> now.plusSeconds(minutes * 60)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time.get(); }
    }
}
