package com.studysnap.backend.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalRelationshipGrantHealMigrationTest {
    /**
     * ⚠️ Pins the one thing about V129 that would be a real defect: cutting grants on a CONSENT PAUSE.
     *
     * <p>A v0.89.1 birth-year correction returns an ACCEPTED relationship to PENDING, and v0.93.0 made
     * the grant row survive that pause BY DESIGN — {@code *SharedByMe} reflects the row, so it reports
     * the learner's own standing act of sharing and what resumes on re-acceptance. A migration that
     * swept PENDING would turn a learner's own toggle OFF without them touching it.
     *
     * <p>NOTE ON REACH: {@code src/test/resources/application.yaml} disables Flyway, so this asserts on
     * SQL TEXT and pins intent, not correctness. The executable counterpart is
     * {@code NativeQueryPostgresIntegrationTest.theTerminalGrantHealNeverTouchesAConsentPause}.
     */
    @Test
    void theHealCoversTerminalStatusesOnlyAndNeverThePause() throws IOException {
        String migration = new ClassPathResource(
                "db/migration/V129__revoke_grants_on_terminal_relationships.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration).contains("r.status IN ('REVOKED', 'EXPIRED')");
        // ⚠️ The defect this exists to prevent: a pause is PENDING, and sweeping it is the one
        // outcome that would be actively wrong rather than merely incomplete.
        assertThat(migration).doesNotContain("'PENDING'");
        // Idempotent, so a re-run is a no-op rather than re-stamping revoked_at.
        assertThat(migration).contains("g.revoked_at IS NULL");
        // ⚠️ It heals grants; it must never delete or alter a relationship.
        assertThat(migration).doesNotContain("DELETE");
        assertThat(migration).doesNotContain("UPDATE linked_learner_relationships");
    }
}
