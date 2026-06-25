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

    @Autowired
    private AnalyticsEventRepository analyticsEventRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("drop table if exists analytics_events");
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
    void retentionQueries_countEligibleWeek2ReturnsAndWeeklyCohorts() {
        OffsetDateTime firstPack = OffsetDateTime.of(2026, 5, 26, 9, 0, 0, 0, ZoneOffset.UTC);
        UUID returnedUser = UUID.randomUUID();
        UUID earlyOnlyUser = UUID.randomUUID();
        UUID lateOnlyUser = UUID.randomUUID();
        UUID recentUser = UUID.randomUUID();

        insertEvent(returnedUser, AnalyticsEventType.STUDY_PACK_GENERATED, firstPack);
        insertEvent(returnedUser, AnalyticsEventType.LOGIN, firstPack.plusDays(8));

        insertEvent(earlyOnlyUser, AnalyticsEventType.STUDY_PACK_GENERATED, firstPack.plusDays(1));
        insertEvent(earlyOnlyUser, AnalyticsEventType.LOGIN, firstPack.plusDays(7));

        insertEvent(lateOnlyUser, AnalyticsEventType.STUDY_PACK_GENERATED, firstPack.plusDays(2));
        insertEvent(lateOnlyUser, AnalyticsEventType.LOGIN, firstPack.plusDays(17));

        insertEvent(recentUser, AnalyticsEventType.STUDY_PACK_GENERATED, NOW.minusDays(7));
        insertEvent(recentUser, AnalyticsEventType.LOGIN, NOW.minusDays(1));

        assertThat(analyticsEventRepository.countEligibleActivatedUsersForWeek2Retention(NOW)).isEqualTo(3);
        assertThat(analyticsEventRepository.countReturnedWeek2Users(NOW)).isEqualTo(1);

        List<WeeklyRetentionCohortProjection> cohorts = analyticsEventRepository.findWeeklyRetentionCohorts(NOW);

        assertThat(cohorts).hasSize(1);
        assertThat(cohorts.getFirst().getWeekStart()).isEqualTo(LocalDate.parse("2026-05-24"));
        assertThat(cohorts.getFirst().getCohortSize()).isEqualTo(3);
        assertThat(cohorts.getFirst().getReturnedCount()).isEqualTo(1);
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
}
