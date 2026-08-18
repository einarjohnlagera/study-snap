package com.studysnap.backend.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationRecoveryMigrationTest {
    @Test
    void migrationSeedsBothGenerationClocksFromDeployTime() throws IOException {
        // NOTE ON THIS TEST'S REACH: src/test/resources/application.yaml sets flyway.enabled=false,
        // so no migration in this repo is executed by the suite — this asserts on SQL TEXT only and
        // would pass on syntactically invalid SQL. It pins intent, not correctness. V118 was
        // validated by execution against a real PostgreSQL during the v0.86.0 pressure test.
        String migration = new ClassPathResource(
                "db/migration/V118__generation_recovery_clocks.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("SET generation_status_at = now()")
                .contains("generation_status IN ('PENDING', 'GENERATING')")
                // Deploy time, never the reused row's created_at: a pool re-queued seconds ago
                // carries an ancient created_at and would be swept on the first tick.
                .doesNotContain("SET generation_status_at = created_at")
                // Notes are seeded on the SAME argument as pools. The deploy that installs the
                // sweeper is itself the event that strands in-flight generation, and a note left
                // GENERATING with a NULL clock can never satisfy `generation_enqueued_at < cutoff`
                // — unrecoverable forever, warning every ten minutes.
                .contains("SET generation_enqueued_at = now()")
                .contains("WHERE status = 'GENERATING'");
    }
}
