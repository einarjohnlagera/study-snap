package com.studysnap.backend.service;

import com.studysnap.backend.dto.AdminOrganicLandingsResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AdminDashboardOrganicLandingsIntegrationTest {
    private static final OffsetDateTime SINCE = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private AdminDashboardService adminDashboardService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("drop table if exists analytics_events");
        jdbcTemplate.execute("create alias if not exists jsonb_extract_path_text for \"com.studysnap.backend.testutil.H2JsonFunctions.jsonbExtractPathText\"");
        jdbcTemplate.execute("""
                create table analytics_events (
                    id uuid primary key,
                    user_id uuid null,
                    event_type varchar(64) not null,
                    entity_id uuid null,
                    metadata_json json not null,
                    created_at timestamp with time zone not null
                )
                """);
    }

    @Test
    void getOrganicLandings_aggregatesWeeksSourcesAndExamHubClickThrough() {
        OffsetDateTime olderWeek = OffsetDateTime.of(2026, 5, 4, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime recentWeek = OffsetDateTime.of(2026, 5, 11, 9, 0, 0, 0, ZoneOffset.UTC);
        insertEvent(AnalyticsEventType.LANDING_PAGE_VIEWED, olderWeek, "google");
        insertEvent(AnalyticsEventType.LANDING_PAGE_VIEWED, olderWeek.plusMinutes(1), "google");
        insertEvent(AnalyticsEventType.EXAM_HUB_VIEWED, recentWeek, "google");
        insertEvent(AnalyticsEventType.EXAM_HUB_VIEWED, recentWeek.plusMinutes(1), "other-search");
        insertEvent(AnalyticsEventType.PUBLISHED_PLANS_VIEWED, recentWeek.plusMinutes(2), "social");
        insertEvent(AnalyticsEventType.EXAM_HUB_CTA_CLICKED, recentWeek.plusMinutes(3), "direct");

        AdminOrganicLandingsResponse response = adminDashboardService.getOrganicLandingsSince(SINCE);

        assertThat(response.landings())
                .extracting(
                        AdminOrganicLandingsResponse.Landing::weekStart,
                        AdminOrganicLandingsResponse.Landing::eventType,
                        AdminOrganicLandingsResponse.Landing::referrerSource,
                        AdminOrganicLandingsResponse.Landing::count
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.parse("2026-05-03"), "LANDING_PAGE_VIEWED", "google", 2L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.parse("2026-05-10"), "EXAM_HUB_VIEWED", "google", 1L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.parse("2026-05-10"), "EXAM_HUB_VIEWED", "other-search", 1L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.parse("2026-05-10"), "PUBLISHED_PLANS_VIEWED", "social", 1L)
                );
        assertThat(response.googleExamHubViews()).isEqualTo(1L);
        assertThat(response.examHubCtaClicks()).isEqualTo(1L);
        assertThat(response.examHubOrganicClickThroughRatio()).isEqualByComparingTo("1.0000");
    }

    @Test
    void getOrganicLandings_returnsNullRatioWhenThereAreNoGoogleExamHubViews() {
        insertEvent(
                AnalyticsEventType.EXAM_HUB_CTA_CLICKED,
                OffsetDateTime.of(2026, 5, 11, 9, 0, 0, 0, ZoneOffset.UTC),
                "direct"
        );

        AdminOrganicLandingsResponse response = adminDashboardService.getOrganicLandingsSince(SINCE);

        assertThat(response.googleExamHubViews()).isZero();
        assertThat(response.examHubCtaClicks()).isEqualTo(1L);
        assertThat(response.examHubOrganicClickThroughRatio()).isNull();
    }

    private void insertEvent(AnalyticsEventType eventType, OffsetDateTime createdAt, String referrerSource) {
        jdbcTemplate.update(
                """
                        insert into analytics_events (id, event_type, metadata_json, created_at)
                        values (?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                eventType.name(),
                "{\"referrerSource\":\"" + referrerSource + "\"}",
                createdAt
        );
    }
}
