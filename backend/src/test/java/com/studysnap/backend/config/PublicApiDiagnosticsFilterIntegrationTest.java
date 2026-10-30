package com.studysnap.backend.config;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
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
        @Bean
        @Primary
        PublicApiDiagnosticsAggregator fixedDiagnostics(StudySnapProperties properties, PublicApiDiagnosticsLog log) {
            return new PublicApiDiagnosticsAggregator(properties.getPublicApiDiagnostics(), log,
                    Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC));
        }
    }
}
