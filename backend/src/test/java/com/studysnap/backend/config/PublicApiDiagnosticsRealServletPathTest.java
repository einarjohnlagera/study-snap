package com.studysnap.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter matches on {@code request.getServletPath()}, which MockMvc only has if a test sets it by hand. Under
 * the real Tomcat and the production context path {@code /api} it must yield {@code /notes/public/...}, or the
 * diagnostic would silently record nothing. This drives a real HTTP request to prove it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicApiDiagnosticsRealServletPathTest {
    @LocalServerPort private int port;
    @Autowired private PublicApiDiagnosticsAggregator aggregator;

    @Test
    void aRealRequestUnderTheApiContextPathIsRecordedAsAPublicFacetRequest() {
        PublicApiDiagnosticsAggregator.Bucket before = aggregator.currentBucket();
        long facetsBefore = before.groups.get(2);
        long totalBefore = before.total.sum();

        RestClient.create("http://localhost:" + port).get().uri("/api/notes/public/learner-levels")
                .exchange((request, response) -> response.getStatusCode().value());

        // A minute boundary may have moved the request into a fresh bucket; either way it must be counted.
        PublicApiDiagnosticsAggregator.Bucket after = aggregator.currentBucket();
        long recorded = after == before
                ? after.total.sum() - totalBefore
                : (before.total.sum() - totalBefore) + after.total.sum();
        assertThat(recorded).as("the request reached the diagnostics filter with a matching servlet path")
                .isGreaterThanOrEqualTo(1);
        long facets = after == before ? after.groups.get(2) - facetsBefore
                : (before.groups.get(2) - facetsBefore) + after.groups.get(2);
        assertThat(facets).as("learner-levels is grouped as a facet request").isGreaterThanOrEqualTo(1);
    }
}
