package com.studysnap.backend.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The health-group contract, pinned.
 *
 * <p>⚠️ WHY THIS EXISTS. {@code DataSourceHealthIndicator} needs a connection from the SAME pool the
 * application is busy with, so on 2026-09-04 and again on 2026-09-05 a pool saturated by our own
 * unbounded query left the instance unable to answer its own health check — and the platform restarted
 * a process whose only problem was that it was busy. A liveness group that excludes {@code db} is what
 * separates "this process is dead" from "this process is busy".
 *
 * <p>⚠️ READS THE PRODUCTION PROFILE AS TEXT, for the reason {@link DataSourcePoolContractTest}
 * records: {@code src/test/resources/application.yaml} shadows {@code src/main/resources} on the test
 * classpath, so a running context here is backed by H2 and would pin the test profile instead.
 *
 * <p>⚠️⚠️ WHAT THIS CANNOT PROVE, STATED SO NOBODY READS IT AS MORE THAN IT IS: the groups only take
 * effect if the PLATFORM probes {@code /api/actuator/health/liveness}. Render's {@code healthCheckPath}
 * is owner-configured and lives outside this repository, so a green test here does NOT mean the restart
 * behaviour changed. That repoint is tracked as an outstanding obligation.
 */
class HealthGroupContractTest {
    private static final Path PRODUCTION_PROFILE = Path.of("src/main/resources/application.yaml");

    private String productionProfile() throws IOException {
        return Files.readString(PRODUCTION_PROFILE, StandardCharsets.UTF_8);
    }

    @Test
    void livenessExcludesTheDatabaseSoASaturatedPoolCannotReadAsADeadProcess() throws IOException {
        String yaml = productionProfile();

        assertThat(yaml)
                .as("a liveness group must exist, or /actuator/health/liveness does not resolve")
                .contains("liveness:");
        // ⚠️ THE DISCRIMINATING ASSERTION. A liveness group that happened to include `db` would satisfy
        // "a group exists" while reproducing the exact failure: the probe starving on the pool it is
        // meant to be reporting on.
        String liveness = yaml.substring(yaml.indexOf("liveness:"));
        liveness = liveness.substring(0, liveness.indexOf("readiness:"));
        assertThat(liveness)
                .as("liveness must NOT depend on the database pool -- that is the whole point")
                .doesNotContain("db");
        assertThat(liveness).contains("livenessState");
    }

    @Test
    void databaseHealthStaysObservableThroughReadiness() throws IOException {
        // The trade this configuration accepts is that a genuinely-dead database no longer fails
        // liveness. It must therefore remain visible SOMEWHERE, or the config trades a self-inflicted
        // restart loop for an invisible outage.
        String yaml = productionProfile();
        String readiness = yaml.substring(yaml.indexOf("readiness:"));

        assertThat(readiness.substring(0, Math.min(readiness.length(), 200)))
                .as("database health must remain observable via readiness")
                .contains("db");
    }
}
