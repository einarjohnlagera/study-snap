package com.studysnap.backend.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationRecoveryMigrationTest {
    @Test
    void migrationSeedsOnlyNonTerminalPoolClockFromDeployTime() throws IOException {
        String migration = new ClassPathResource(
                "db/migration/V118__generation_recovery_clocks.sql"
        ).getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("SET generation_status_at = now()")
                .contains("generation_status IN ('PENDING', 'GENERATING')")
                .doesNotContain("SET generation_status_at = created_at")
                .doesNotContain("SET generation_enqueued_at");
    }
}
