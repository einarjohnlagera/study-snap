package com.studysnap.backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expired-invitation re-arm, exercised through the repository's REAL {@code @Query} SQL.
 *
 * <p>⚠️ The service-level test can only assert which arguments were passed. The defect being
 * guarded here lives in the statement itself — an invitation first sent as SUPPORTER and re-sent as
 * LEARNER used to reactivate the OLD direction, because {@code insertPendingIfAbsent} no-ops
 * against the live-row unique index and the re-arm updated only {@code expires_at}. Acceptance
 * would then build the opposite relationship to the one the inviter asked for.
 */
class LinkedLearnerInvitationReArmTest {
    private static final UUID INVITER = UUID.randomUUID();
    private static final String EMAIL = "invited@example.com";

    private JdbcTemplate jdbcTemplate;
    private NamedParameterJdbcTemplate namedJdbcTemplate;
    private UUID invitationId;
    private OffsetDateTime firstInvitedAt;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build());
        namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        jdbcTemplate.execute("""
                create table linked_learner_invitations (
                    id uuid primary key,
                    inviter_user_id uuid not null,
                    invited_email varchar(320) not null,
                    inviter_role varchar(16) not null,
                    status varchar(16) not null,
                    created_at timestamp with time zone not null,
                    expires_at timestamp with time zone not null,
                    accepted_at timestamp with time zone,
                    revoked_at timestamp with time zone
                )""");
        invitationId = UUID.randomUUID();
        firstInvitedAt = OffsetDateTime.now().minusDays(90);
    }

    private void seed(String role, OffsetDateTime expiresAt) {
        jdbcTemplate.update("""
                insert into linked_learner_invitations
                    (id, inviter_user_id, invited_email, inviter_role, status, created_at, expires_at)
                values (?, ?, ?, ?, 'PENDING', ?, ?)""",
                invitationId, INVITER, EMAIL, role, firstInvitedAt, expiresAt);
    }

    /** Run the repository's actual re-arm statement, so this test binds to production SQL. */
    private int reArm(String requestedRole, OffsetDateTime newExpiry, OffsetDateTime now) {
        Method method = Arrays.stream(LinkedLearnerInvitationRepository.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("reArmExpired"))
                .findFirst()
                .orElseThrow();
        String sql = method.getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        return namedJdbcTemplate.update(sql, Map.of(
                "inviterUserId", INVITER,
                "invitedEmail", EMAIL,
                "inviterRole", requestedRole,
                "expiresAt", newExpiry,
                "now", now));
    }

    private String column(String name) {
        return jdbcTemplate.queryForObject(
                "select " + name + " from linked_learner_invitations where id = ?", String.class, invitationId);
    }

    @Test
    void reArmingAnExpiredInvitationAppliesTheNEWLYREQUESTEDRole() {
        seed("SUPPORTER", OffsetDateTime.now().minusDays(1));
        OffsetDateTime now = OffsetDateTime.now();

        int affected = reArm("LEARNER", now.plusDays(30), now);

        assertThat(affected).isEqualTo(1);
        // ⚠️ The direction the inviter just asked for, not the one that lapsed.
        assertThat(column("inviter_role")).isEqualTo("LEARNER");
    }

    @Test
    void reArmingRefreshesExpiryButNeverWhenTheAddressWasFirstInvited() {
        seed("SUPPORTER", OffsetDateTime.now().minusDays(1));
        OffsetDateTime now = OffsetDateTime.now();

        reArm("SUPPORTER", now.plusDays(30), now);

        OffsetDateTime createdAt = jdbcTemplate.queryForObject(
                "select created_at from linked_learner_invitations where id = ?",
                OffsetDateTime.class, invitationId);
        OffsetDateTime expiresAt = jdbcTemplate.queryForObject(
                "select expires_at from linked_learner_invitations where id = ?",
                OffsetDateTime.class, invitationId);

        assertThat(createdAt.toInstant()).isEqualTo(firstInvitedAt.toInstant());
        assertThat(expiresAt).isAfter(now.plusDays(29));
    }

    @Test
    void anUnexpiredInvitationIsNotReArmedSoRepeatingTheEndpointStaysIdempotent() {
        OffsetDateTime liveUntil = OffsetDateTime.now().plusDays(10);
        seed("SUPPORTER", liveUntil);
        OffsetDateTime now = OffsetDateTime.now();

        int affected = reArm("LEARNER", now.plusDays(30), now);

        // ⚠️ Zero rows: a live invitation keeps its record AND its direction. Re-posting the
        // endpoint re-sends mail (rate-limited) and changes no stored row.
        assertThat(affected).isZero();
        assertThat(column("inviter_role")).isEqualTo("SUPPORTER");
        OffsetDateTime expiresAt = jdbcTemplate.queryForObject(
                "select expires_at from linked_learner_invitations where id = ?",
                OffsetDateTime.class, invitationId);
        assertThat(expiresAt.toInstant()).isEqualTo(liveUntil.toInstant());
    }

}
