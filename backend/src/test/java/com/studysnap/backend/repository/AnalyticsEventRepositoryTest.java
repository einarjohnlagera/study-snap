package com.studysnap.backend.repository;

import com.studysnap.backend.entity.AnalyticsEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AnalyticsEventRepositoryTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 6, 22, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final String ONBOARDING_STEP_PROFILE = "profile";
    private static final String ONBOARDING_STEP_COURSE_PROGRAM = "course-program";

    @Autowired
    private AnalyticsEventRepository analyticsEventRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("drop table if exists analytics_events");
        jdbcTemplate.execute("create alias if not exists jsonb_extract_path_text for \"com.studysnap.backend.testutil.H2JsonFunctions.jsonbExtractPathText\"");
        jdbcTemplate.execute("drop table if exists users");
        jdbcTemplate.execute("""
                create table users (
                    id uuid primary key,
                    onboarding_completed_at timestamp with time zone null
                )
                """);
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
    void onboardingStepUserCounts_countDistinctUsersInsteadOfEvents() {
        UUID repeatedViewer = UUID.randomUUID();
        UUID secondViewer = UUID.randomUUID();
        insertOnboardingStepEvent(repeatedViewer, ONBOARDING_STEP_PROFILE, NOW.minusHours(4));
        insertOnboardingStepEvent(repeatedViewer, ONBOARDING_STEP_PROFILE, NOW.minusHours(3));
        insertOnboardingStepEvent(repeatedViewer, ONBOARDING_STEP_PROFILE, NOW.minusHours(2));
        insertOnboardingStepEvent(secondViewer, ONBOARDING_STEP_PROFILE, NOW.minusHours(1));
        insertOnboardingStepEvent(secondViewer, ONBOARDING_STEP_COURSE_PROGRAM, NOW);

        List<OnboardingStepUserCountProjection> counts = analyticsEventRepository.findOnboardingStepUserCounts();

        assertThat(counts)
                .extracting(
                        OnboardingStepUserCountProjection::getStepName,
                        OnboardingStepUserCountProjection::getUserCount
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(ONBOARDING_STEP_PROFILE, 2L),
                        org.assertj.core.groups.Tuple.tuple(ONBOARDING_STEP_COURSE_PROGRAM, 1L)
                );
    }

    @Test
    void onboardingCompletion_countsAllUsersAndCompletedUsersFromUsersTable() {
        insertUser(null);
        insertUser(NOW.minusDays(2));
        insertUser(NOW.minusDays(1));

        OnboardingCompletionProjection completion = analyticsEventRepository.findOnboardingCompletion();

        assertThat(completion.getTotalSignups()).isEqualTo(3);
        assertThat(completion.getOnboardingCompletedUsers()).isEqualTo(2);
    }

    @Test
    void retentionQueries_countStrictAndWideReturnsWithTheirOwnMaturitySets() {
        OffsetDateTime firstPack = NOW.minusDays(40);
        UUID returnedUser = UUID.randomUUID();
        UUID earlyOnlyUser = UUID.randomUUID();
        UUID lateOnlyUser = UUID.randomUUID();
        UUID afterWideWindowUser = UUID.randomUUID();
        UUID dayOneBoundaryUser = UUID.randomUUID();
        UUID dayThirtyBoundaryUser = UUID.randomUUID();
        UUID recentUser = UUID.randomUUID();

        insertEvent(returnedUser, AnalyticsEventType.STUDY_PACK_GENERATED, firstPack);
        insertEvent(returnedUser, AnalyticsEventType.LOGIN, firstPack.plusDays(8));

        insertEvent(earlyOnlyUser, AnalyticsEventType.STUDY_PACK_GENERATED, firstPack);
        insertEvent(earlyOnlyUser, AnalyticsEventType.LOGIN, firstPack.plusDays(3));

        insertEvent(lateOnlyUser, AnalyticsEventType.STUDY_PACK_GENERATED, firstPack);
        insertEvent(lateOnlyUser, AnalyticsEventType.LOGIN, firstPack.plusDays(20));

        insertEvent(afterWideWindowUser, AnalyticsEventType.STUDY_PACK_GENERATED, firstPack);
        insertEvent(afterWideWindowUser, AnalyticsEventType.LOGIN, firstPack.plusDays(31));

        insertEvent(dayOneBoundaryUser, AnalyticsEventType.STUDY_PACK_GENERATED, firstPack);
        insertEvent(dayOneBoundaryUser, AnalyticsEventType.LOGIN, firstPack.plusDays(1));

        insertEvent(dayThirtyBoundaryUser, AnalyticsEventType.STUDY_PACK_GENERATED, firstPack);
        insertEvent(dayThirtyBoundaryUser, AnalyticsEventType.LOGIN, firstPack.plusDays(30));

        insertEvent(recentUser, AnalyticsEventType.STUDY_PACK_GENERATED, NOW.minusDays(20));
        insertEvent(recentUser, AnalyticsEventType.LOGIN, NOW.minusDays(1));

        assertThat(analyticsEventRepository.countEligibleActivatedUsersForWeek2Retention(NOW)).isEqualTo(7);
        assertThat(analyticsEventRepository.countReturnedWeek2Users(NOW)).isEqualTo(1);
        assertThat(analyticsEventRepository.countEligibleActivatedUsersForWideRetention(NOW)).isEqualTo(6);
        assertThat(analyticsEventRepository.countReturnedAfterDay7Users(NOW)).isEqualTo(4);
        assertThat(analyticsEventRepository.countReturnedDays2To30Users(NOW)).isEqualTo(4);
        assertThat(analyticsEventRepository.countReturnedAfterDay1Users(NOW)).isEqualTo(5);

        List<WeeklyRetentionCohortProjection> cohorts = analyticsEventRepository.findWeeklyRetentionCohorts(NOW);

        assertThat(cohorts).hasSize(2);
        WeeklyRetentionCohortProjection matureCohort = cohorts.stream()
                .filter(cohort -> cohort.getWeekStart().equals(LocalDate.parse("2026-05-10")))
                .findFirst()
                .orElseThrow();
        assertThat(matureCohort.getCohortSize()).isEqualTo(6);
        assertThat(matureCohort.getReturnedCount()).isEqualTo(1);
        assertThat(matureCohort.getReturnedAfterDay7Count()).isEqualTo(4);
        WeeklyRetentionCohortProjection twentyDayCohort = cohorts.stream()
                .filter(cohort -> cohort.getWeekStart().equals(LocalDate.parse("2026-05-31")))
                .findFirst()
                .orElseThrow();
        assertThat(twentyDayCohort.getCohortSize()).isEqualTo(1);
        assertThat(twentyDayCohort.getReturnedAfterDay7Count()).isEqualTo(1);
    }

    @Test
    void countDistinctUsersWithEventAfterEvent_respectsOrderingAndDedupesUsers() {
        UUID convertedUser = UUID.randomUUID();
        UUID checkoutBeforeClickUser = UUID.randomUUID();
        UUID paidBeforeCheckoutUser = UUID.randomUUID();
        UUID duplicateCheckoutUser = UUID.randomUUID();

        insertEvent(convertedUser, AnalyticsEventType.UPGRADE_CLICKED, NOW.minusDays(5));
        insertEvent(convertedUser, AnalyticsEventType.CHECKOUT_INITIATED, NOW.minusDays(4));
        insertEvent(convertedUser, AnalyticsEventType.SUBSCRIPTION_STARTED, NOW.minusDays(3));

        insertEvent(checkoutBeforeClickUser, AnalyticsEventType.CHECKOUT_INITIATED, NOW.minusDays(5));
        insertEvent(checkoutBeforeClickUser, AnalyticsEventType.UPGRADE_CLICKED, NOW.minusDays(4));
        insertEvent(checkoutBeforeClickUser, AnalyticsEventType.SUBSCRIPTION_STARTED, NOW.minusDays(3));

        insertEvent(paidBeforeCheckoutUser, AnalyticsEventType.CHECKOUT_INITIATED, NOW.minusDays(4));
        insertEvent(paidBeforeCheckoutUser, AnalyticsEventType.SUBSCRIPTION_STARTED, NOW.minusDays(5));

        insertEvent(duplicateCheckoutUser, AnalyticsEventType.UPGRADE_CLICKED, NOW.minusDays(5));
        insertEvent(duplicateCheckoutUser, AnalyticsEventType.CHECKOUT_INITIATED, NOW.minusDays(4));
        insertEvent(duplicateCheckoutUser, AnalyticsEventType.CHECKOUT_INITIATED, NOW.minusDays(3));

        assertThat(analyticsEventRepository.countDistinctUsersWithEventAfterEvent(
                AnalyticsEventType.UPGRADE_CLICKED,
                AnalyticsEventType.CHECKOUT_INITIATED
        )).isEqualTo(2);
        assertThat(analyticsEventRepository.countDistinctUsersWithEventAfterEvent(
                AnalyticsEventType.CHECKOUT_INITIATED,
                AnalyticsEventType.SUBSCRIPTION_STARTED
        )).isEqualTo(2);
    }

    @Test
    void windowedConversionQueries_excludeEarlierEventsBeforeWindow() {
        OffsetDateTime since = NOW.minusDays(30);
        UUID recentConvertedUser = UUID.randomUUID();
        UUID oldClickRecentCheckoutUser = UUID.randomUUID();
        UUID oldOnlyClickUser = UUID.randomUUID();

        insertEvent(recentConvertedUser, AnalyticsEventType.UPGRADE_CLICKED, since.plusDays(1));
        insertEvent(recentConvertedUser, AnalyticsEventType.CHECKOUT_INITIATED, since.plusDays(2));

        insertEvent(oldClickRecentCheckoutUser, AnalyticsEventType.UPGRADE_CLICKED, since.minusDays(1));
        insertEvent(oldClickRecentCheckoutUser, AnalyticsEventType.CHECKOUT_INITIATED, since.plusDays(2));

        insertEvent(oldOnlyClickUser, AnalyticsEventType.UPGRADE_CLICKED, since.minusDays(2));

        assertThat(analyticsEventRepository.countDistinctUsersByEventTypeSince(
                AnalyticsEventType.UPGRADE_CLICKED,
                since
        )).isEqualTo(1);
        assertThat(analyticsEventRepository.countDistinctUsersWithEventAfterEventSince(
                AnalyticsEventType.UPGRADE_CLICKED,
                AnalyticsEventType.CHECKOUT_INITIATED,
                since
        )).isEqualTo(1);
    }

    @Test
    void windowedValueLoop_usesFirstGeneratedPackInsideWindow() {
        OffsetDateTime since = NOW.minusDays(30);
        UUID windowUser = UUID.randomUUID();
        UUID oldPackUser = UUID.randomUUID();

        insertEvent(windowUser, AnalyticsEventType.STUDY_PACK_GENERATED, since.plusDays(2));
        insertEvent(windowUser, AnalyticsEventType.CHALLENGE_QUIZ_STARTED, since.plusDays(4));

        insertEvent(oldPackUser, AnalyticsEventType.STUDY_PACK_GENERATED, since.minusDays(2));
        insertEvent(oldPackUser, AnalyticsEventType.CHALLENGE_QUIZ_STARTED, since.plusDays(1));

        assertThat(analyticsEventRepository.countUsersStartedQuizWithin7DaysOfFirstGeneratedPackSince(since))
                .isEqualTo(1);
    }

    @Test
    void organicLandingQueries_groupPageViewsByWeekSurfaceAndReferrerSource() {
        OffsetDateTime olderWeek = OffsetDateTime.of(2026, 6, 8, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime recentWeek = OffsetDateTime.of(2026, 6, 15, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime since = olderWeek.minusDays(1);

        insertEvent(AnalyticsEventType.LANDING_PAGE_VIEWED, olderWeek, "google");
        insertEvent(AnalyticsEventType.LANDING_PAGE_VIEWED, olderWeek.plusHours(1), "google");
        insertEvent(AnalyticsEventType.EXAM_HUB_VIEWED, recentWeek, "other-search");
        insertEvent(AnalyticsEventType.EXAM_HUB_VIEWED, recentWeek.plusHours(1), "google");
        insertEvent(AnalyticsEventType.PUBLISHED_PLANS_VIEWED, recentWeek.plusHours(2), "social");
        insertEvent(AnalyticsEventType.EXAM_HUB_CTA_CLICKED, recentWeek.plusHours(3), "google");

        List<OrganicLandingProjection> landings = analyticsEventRepository.findOrganicLandingsSince(since);
        ExamHubOrganicClickThroughProjection clickThrough = analyticsEventRepository
                .findExamHubOrganicClickThroughSince(since);

        assertThat(landings)
                .extracting(
                        OrganicLandingProjection::getWeekStart,
                        OrganicLandingProjection::getEventType,
                        OrganicLandingProjection::getReferrerSource,
                        OrganicLandingProjection::getTotalCount
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.parse("2026-06-07"), "LANDING_PAGE_VIEWED", "google", 2L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.parse("2026-06-14"), "EXAM_HUB_VIEWED", "other-search", 1L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.parse("2026-06-14"), "EXAM_HUB_VIEWED", "google", 1L),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.parse("2026-06-14"), "PUBLISHED_PLANS_VIEWED", "social", 1L)
                );
        assertThat(clickThrough.getGoogleExamHubViews()).isEqualTo(1L);
        assertThat(clickThrough.getExamHubCtaClicks()).isEqualTo(1L);
    }

    private void insertEvent(UUID userId, AnalyticsEventType eventType, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                        insert into analytics_events (id, user_id, event_type, metadata_json, created_at)
                        values (?, ?, ?, '{}', ?)
                        """,
                UUID.randomUUID(),
                userId,
                eventType.name(),
                createdAt
        );
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

    private void insertOnboardingStepEvent(UUID userId, String stepName, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                        insert into analytics_events (id, user_id, event_type, metadata_json, created_at)
                        values (?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                userId,
                AnalyticsEventType.ONBOARDING_V2_STEP_VIEWED.name(),
                "{\"step_name\":\"" + stepName + "\"}",
                createdAt
        );
    }

    private void insertUser(OffsetDateTime onboardingCompletedAt) {
        jdbcTemplate.update(
                "insert into users (id, onboarding_completed_at) values (?, ?)",
                UUID.randomUUID(),
                onboardingCompletedAt
        );
    }
}
