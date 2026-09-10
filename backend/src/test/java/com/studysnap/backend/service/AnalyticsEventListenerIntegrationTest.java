package com.studysnap.backend.service;

import com.studysnap.backend.controller.AnalyticsController;
import com.studysnap.backend.dto.AnalyticsEventRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsEventListenerIntegrationTest {
    private static final Duration ASYNC_WAIT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration ROLLBACK_SETTLE_TIME = Duration.ofMillis(300);

    @Autowired
    private AnalyticsService analyticsService;
    @Autowired
    private AnalyticsController analyticsController;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;

    @Autowired
    void setTransactionTemplate(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void initSchema() {
        jdbcTemplate.execute("drop table if exists analytics_events");
        jdbcTemplate.execute("drop table if exists users");
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
        jdbcTemplate.execute("create index idx_analytics_events_user_id on analytics_events(user_id)");
    }

    @Test
    void trackEvent_insideTransaction_persistsOnlyAfterCommit() {
        UUID userId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            analyticsService.trackEvent(userId, AnalyticsEventType.LOGIN, userId, Map.of("source", "test"));
            assertEventCount(AnalyticsEventType.LOGIN, 0);
        });

        awaitEventCount(AnalyticsEventType.LOGIN, 1);
    }

    @Test
    void trackEvent_insideRolledBackTransaction_doesNotPersistPhantomEvent() throws InterruptedException {
        UUID userId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            analyticsService.trackEvent(userId, AnalyticsEventType.SIGNUP, userId, Map.of("method", "test"));
            status.setRollbackOnly();
        });

        Thread.sleep(ROLLBACK_SETTLE_TIME.toMillis());
        assertEventCount(AnalyticsEventType.SIGNUP, 0);
    }

    @Test
    void trackEvent_withoutTransaction_persistsThroughFallbackExecution() {
        UUID userId = UUID.randomUUID();

        analyticsService.trackEvent(userId, AnalyticsEventType.LANDING_PAGE_VIEWED, null, Map.of("page", "home"));

        awaitEventCount(AnalyticsEventType.LANDING_PAGE_VIEWED, 1);
    }

    @Test
    void dueConceptsDigestLanding_withoutResolvedPrincipal_requestsAuthenticationRetry() throws Exception {
        mockMvc.perform(post("/analytics/events")
                        // An expired bearer is the production shape: the permitAll endpoint used to
                        // accept it as anonymous with 200, preventing the client from refreshing it.
                        .header("Authorization", "Bearer expired-access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"DUE_CONCEPTS_DIGEST_LANDED","metadata":{}}
                                """))
                .andExpect(status().isUnauthorized());

        assertEventCount(AnalyticsEventType.DUE_CONCEPTS_DIGEST_LANDED, 0);
    }

    @Test
    void dueConceptsDigestLanding_persistsResolvedPrincipalUserId() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(userId, UserRole.USER, true, 0);
        analyticsController.trackEvent(
                principal,
                new AnalyticsEventRequest(AnalyticsEventType.DUE_CONCEPTS_DIGEST_LANDED, null, Map.of())
        );

        awaitEventCount(AnalyticsEventType.DUE_CONCEPTS_DIGEST_LANDED, 1);
        UUID persistedUserId = jdbcTemplate.queryForObject(
                "select user_id from analytics_events where event_type = ?",
                UUID.class,
                AnalyticsEventType.DUE_CONCEPTS_DIGEST_LANDED.name()
        );
        assertThat(persistedUserId).isEqualTo(userId);
    }

    @Test
    void anonymousAnalyticsEvents_remainAccepted() throws Exception {
        mockMvc.perform(post("/analytics/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"LANDING_PAGE_VIEWED","metadata":{}}
                                """))
                .andExpect(status().isOk());

        awaitEventCount(AnalyticsEventType.LANDING_PAGE_VIEWED, 1);
    }

    @Test
    void signupLifecycleEvents_persistAfterSignupTransactionCommits() {
        UUID userId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            analyticsService.trackEvent(userId, AnalyticsEventType.SIGNUP, userId, Map.of("method", "email_password"));
            analyticsService.trackEvent(
                    userId,
                    AnalyticsEventType.SIGNUP_COMPLETED,
                    userId,
                    Map.of("method", "email_password")
            );
            analyticsService.trackEvent(
                    userId,
                    AnalyticsEventType.EMAIL_VERIFICATION_SENT,
                    userId,
                    Map.of("source", "signup")
            );
            assertThat(countAllEvents()).isZero();
        });

        awaitEventCount(AnalyticsEventType.SIGNUP, 1);
        awaitEventCount(AnalyticsEventType.SIGNUP_COMPLETED, 1);
        awaitEventCount(AnalyticsEventType.EMAIL_VERIFICATION_SENT, 1);
        assertThat(countAllEvents()).isEqualTo(3);
    }

    @Test
    void analyticsEvents_allowsUserIdWithoutMatchingUserRow() {
        jdbcTemplate.execute("drop table analytics_events");
        jdbcTemplate.execute("create table users (id uuid primary key)");
        jdbcTemplate.execute("""
                create table analytics_events (
                    id uuid primary key,
                    user_id uuid null,
                    event_type varchar(64) not null,
                    entity_id uuid null,
                    metadata_json json not null,
                    created_at timestamp with time zone not null,
                    constraint analytics_events_user_id_fkey foreign key (user_id) references users(id) on delete set null
                )
                """);
        jdbcTemplate.execute("""
                alter table analytics_events
                    drop constraint if exists analytics_events_user_id_fkey
                """);

        jdbcTemplate.update(
                """
                        insert into analytics_events (id, user_id, event_type, metadata_json, created_at)
                        values (?, ?, ?, '{}', current_timestamp)
                        """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                AnalyticsEventType.LOGIN.name()
        );

        assertEventCount(AnalyticsEventType.LOGIN, 1);
    }

    private void awaitEventCount(AnalyticsEventType eventType, int expectedCount) {
        long deadline = System.nanoTime() + ASYNC_WAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (countEvents(eventType) == expectedCount) {
                return;
            }
            sleepBriefly();
        }
        assertEventCount(eventType, expectedCount);
    }

    private void assertEventCount(AnalyticsEventType eventType, int expectedCount) {
        assertThat(countEvents(eventType)).isEqualTo(expectedCount);
    }

    private int countEvents(AnalyticsEventType eventType) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from analytics_events where event_type = ?",
                Integer.class,
                eventType.name()
        );
        return count == null ? 0 : count;
    }

    private int countAllEvents() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from analytics_events", Integer.class);
        return count == null ? 0 : count;
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for analytics event", ex);
        }
    }
}
