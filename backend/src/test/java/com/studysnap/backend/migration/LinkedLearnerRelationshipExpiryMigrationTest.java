package com.studysnap.backend.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LinkedLearnerRelationshipExpiryMigrationTest {
    /**
     * ⚠️ Pins the ONE thing about V128 that was a real defect, found by the pre-signoff pressure
     * test: the backfill must not date an inherited row from {@code created_at} alone.
     *
     * <p>New rows are protected because acceptance clears {@code expires_at} and the consent pause
     * leaves it clear, so {@code markExpiredIfPending}'s not-null guard makes a paused relationship
     * structurally unexpirable. That argument does not reach rows the migration inherits: a
     * relationship paused BEFORE V128 is PENDING with a NULL deadline only because the column did
     * not exist. {@code created_at + 30 days} hands it a deadline already in the past and the first
     * sweep terminates a connection that had been ACCEPTED.
     *
     * <p>NOTE ON REACH: {@code src/test/resources/application.yaml} disables Flyway, so this
     * asserts on SQL TEXT and pins intent, not correctness. The executable counterpart is
     * {@code NativeQueryPostgresIntegrationTest.aPreMigrationConsentPausedRowIsNeverExpirableAtAnyFutureInstant},
     * which runs this statement against real rows.
     */
    @Test
    void theMigrationNeverBackfillsADeadlineOntoAnInheritedRow() throws IOException {
        String migration = new ClassPathResource(
                "db/migration/V128__linked_learner_relationship_expiry.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        // ⚠️ NO backfill at all. Both earlier attempts set expires_at and both were wrong:
        // created_at + 30d expires an inherited pause immediately; greatest(created_at, now()) + 30d
        // merely delays it, because setting the column at all erases the NULL that IS the protection.
        assertThat(migration)
                .doesNotContain("SET expires_at = created_at + interval")
                .doesNotContain("SET expires_at = greatest")
                .doesNotContain("UPDATE linked_learner_relationships");
        // ⚠️ The invitation table keeps its own three-value vocabulary; expiry there is expires_at.
        assertThat(migration).doesNotContain("ck_linked_learner_invitation_status");
    }
}
