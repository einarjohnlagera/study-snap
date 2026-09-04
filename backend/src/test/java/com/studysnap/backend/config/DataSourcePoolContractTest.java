package com.studysnap.backend.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The production connection-pool configuration, pinned.
 *
 * <p>⚠️ WHY THIS EXISTS. Before v0.112.0 there was NO HikariCP configuration anywhere — not in
 * {@code application.yaml}, not in {@code application-prod.yaml}. Production therefore ran on Hikari's
 * DEFAULT {@code maximumPoolSize} of 10 and DEFAULT {@code connectionTimeout} of 30,001 ms, and on
 * 2026-09-04 the pool was exhausted while {@code DataSourceHealthIndicator} — which needs a pool
 * connection to answer {@code /actuator/health} — queued behind that same 30 s timeout, failed at
 * 30,002 ms, and Render replaced the instance. **Nobody chose 10.** Nothing in the build could have
 * told anyone that, because there was no value to disagree with.
 *
 * <p>⚠️ IT READS THE PRODUCTION PROFILE AS TEXT, AND THAT IS DELIBERATE.
 * {@code src/test/resources/application.yaml} sits ahead of {@code src/main/resources} on the test
 * classpath and SHADOWS it entirely, so a running context in this suite is backed by H2 and never sees
 * these keys at all. A test asserting the live {@code DataSource} would pin the test profile and prove
 * nothing about production — the same reasoning {@code ScheduledJobCronContractTest} records for
 * {@code PRODUCTION_PROFILE}.
 *
 * @see <a href="file:../../../../../../../docs/claude-findings/2026-09-04-prod-outage-hikari-pool-exhaustion.md">the outage finding</a>
 */
class DataSourcePoolContractTest {
    private static final Path PRODUCTION_PROFILE = Path.of("src/main/resources/application.yaml");
    private static final Path PROD_PROFILE_OVERLAY = Path.of("src/main/resources/application-prod.yaml");
    /** A {@code name: value} YAML entry, capturing its indent so the parent path can be tracked. */
    private static final Pattern YAML_ENTRY = Pattern.compile("^( *)([A-Za-z0-9_-]+):(.*)$");
    /** {@code ${key:default}} — the only form that is both overridable and startup-safe. */
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([^:}]+):(.*)}$");

    private static final String MAX_POOL_SIZE = "spring.datasource.hikari.maximum-pool-size";
    private static final String CONNECTION_TIMEOUT = "spring.datasource.hikari.connection-timeout";
    private static final String LEAK_DETECTION = "spring.datasource.hikari.leak-detection-threshold";
    private static final String LLM_READ_TIMEOUT = "studysnap.llm.api.read-timeout-seconds";

    /**
     * ⚠️ THE CEILING IS A DEPLOY-OVERLAP BOUND, NOT A PERFORMANCE LIMIT, AND IT IS THE ONE FACT A
     * FUTURE EDITOR IS MOST LIKELY NOT TO KNOW. Render runs the NEW instance alongside the OLD during
     * a deploy, each opening its own pool, so the real bound is {@code (max_connections - reserved) / 2}.
     * Render Postgres allows >=100 connections on every plan minus ~10 reserved, giving 45 on the
     * SMALLEST plan. Raising the pool past this means reading the live limit from the Render dashboard
     * and confirming with {@code SHOW max_connections;} — not reasoning about it from here.
     */
    private static final int DEPLOY_OVERLAP_CEILING = 45;

    /** Hikari's own default, and the value that let the health check starve. */
    private static final int HIKARI_DEFAULT_CONNECTION_TIMEOUT_MS = 30_000;

    /**
     * Key prefixes the production overlay must not declare. Both decide how long a connection is held —
     * {@code spring.datasource.} how many there are and how long a waiter queues, {@code spring.jpa.}
     * when an acquired one is given back.
     */
    private static final List<String> SHADOWABLE_PREFIXES = List.of("spring.datasource.", "spring.jpa.");

    /** Every pinned key and its PRODUCTION default. Changing one means changing this map too. */
    private static final Map<String, String> EXPECTED_DEFAULTS = new TreeMap<>(Map.of(
            LEAK_DETECTION, "60000",
            MAX_POOL_SIZE, "20",
            CONNECTION_TIMEOUT, "5000",
            LLM_READ_TIMEOUT, "180"
    ));

    @Test
    void poolSettingsAreDeclaredExplicitlyRatherThanInheritedFromHikariDefaults() throws IOException {
        Map<String, String> declared = scalarKeysDeclaredIn(PRODUCTION_PROFILE);

        for (Map.Entry<String, String> expected : EXPECTED_DEFAULTS.entrySet()) {
            assertThat(declared)
                    .as("%s must be declared in application.yaml — an ABSENT key silently takes a "
                            + "framework default nobody chose, which is what took production down on "
                            + "2026-09-04", expected.getKey())
                    .containsEntry(expected.getKey(), expected.getValue());
        }
    }

    /**
     * ⚠️ A BARE LITERAL WOULD PASS THE ASSERTION ABOVE AND STILL BE WRONG. Phase 2 dials these from the
     * environment while reading leak-detection output; a value that cannot be overridden forces a code
     * change and a redeploy to answer a question the running instance is already holding the data for.
     */
    @Test
    void everyPinnedPoolSettingStaysOverridableFromTheEnvironment() throws IOException {
        Map<String, String> raw = rawKeysDeclaredIn(PRODUCTION_PROFILE);

        for (String key : EXPECTED_DEFAULTS.keySet()) {
            assertThat(PLACEHOLDER.matcher(raw.get(key)).matches())
                    .as("%s must be declared as ${ENV:default}. A bare literal cannot be tuned without "
                            + "a code change; a bare ${ENV} fails startup on a missing key.", key)
                    .isTrue();
        }
    }

    /**
     * ⚠️ THE ASSERTION THAT ENCODES SOMETHING UNGUESSABLE. Every other value here can be re-derived by
     * reading the finding; this one depends on how Render deploys, and getting it wrong exhausts
     * PostgreSQL during a deploy rather than under load — a failure that looks nothing like the one
     * this release fixed.
     */
    @Test
    void poolSizeStaysUnderTheDeployOverlapCeiling() throws IOException {
        int poolSize = Integer.parseInt(scalarKeysDeclaredIn(PRODUCTION_PROFILE).get(MAX_POOL_SIZE));

        assertThat(poolSize)
                .as("Render runs two instances during a deploy, each with its own pool, so the bound is "
                        + "(max_connections - reserved) / 2 = %d on the smallest plan. Raising past this "
                        + "requires reading the real limit from Render, not a bigger number here.",
                        DEPLOY_OVERLAP_CEILING)
                .isLessThanOrEqualTo(DEPLOY_OVERLAP_CEILING);
    }

    /**
     * ⚠️ PINS THE DIRECTION OF A TRADE, NOT A NUMBER. Fast-fail is deliberate: waiters return a 500
     * rather than queueing, because that queueing is exactly what pushed the health check past Render's
     * probe threshold and got the instance killed. Restoring a 30 s wait would restore the outage
     * mechanism while every other value here still looked correct.
     */
    @Test
    void connectionAcquisitionFailsFastRatherThanQueueingPastTheHealthProbe() throws IOException {
        int timeoutMs = Integer.parseInt(scalarKeysDeclaredIn(PRODUCTION_PROFILE).get(CONNECTION_TIMEOUT));

        assertThat(timeoutMs)
                .as("a waiter must give up well before Hikari's %d ms default — at that default the "
                        + "health check queued 30,002 ms behind an exhausted pool and Render replaced "
                        + "the instance", HIKARI_DEFAULT_CONNECTION_TIMEOUT_MS)
                .isLessThan(HIKARI_DEFAULT_CONNECTION_TIMEOUT_MS);
    }

    /**
     * ⚠️ THE SINGLE-FILE READ ABOVE IS ONLY SUFFICIENT WHILE NOTHING SHADOWS IT, SO THAT IS ENFORCED
     * RATHER THAN ASSUMED. {@code application-prod.yaml} is an ACTIVE profile — it already overrides
     * {@code spring.config.import} and {@code server.port} — so a {@code spring.datasource.hikari.*}
     * key added there would WIN in production while every assertion in this class still passed
     * against the base file.
     *
     * <p>That is not hypothetical: {@code ScheduledJobCronContractTest} records a cold agent falsifying
     * its predecessor in exactly this shape — the annotation default was pinned, a yaml value shadowed
     * it, the suite stayed green and the real dispatch time moved. If the overlay ever needs its own
     * pool settings, pin them here too rather than deleting this test.
     *
     * <p>⚠️ {@code spring.jpa.} IS GUARDED ALONGSIDE {@code spring.datasource.} SINCE v0.114.0, AND THE
     * REASON IS THE SAME MECHANISM ONE LEVEL OVER. How long a connection is held is decided by
     * {@code spring.jpa.properties.hibernate.connection.handling_mode} and {@code spring.jpa.open-in-view},
     * and v0.112.0 measured both — but that measurement runs against the H2 test profile, so an overlay
     * key would move production's connection lifetime while every assertion in the suite stayed green.
     * {@code ConnectionLifetimeStartupLogger} reports the effective values from the running instance;
     * this stops the overlay changing them silently in the first place.
     */
    @Test
    void theProdProfileOverlayDoesNotSilentlyShadowThePinnedPoolSettings() throws IOException {
        Map<String, String> overlay = rawKeysDeclaredIn(PROD_PROFILE_OVERLAY);

        assertThat(overlay.keySet())
                .as("a pool or JPA key declared in application-prod.yaml OVERRIDES the base profile in "
                        + "production while this test reads only the base file — pin it here before "
                        + "adding it there")
                .noneMatch(key -> SHADOWABLE_PREFIXES.stream().anyMatch(key::startsWith));
    }

    /**
     * Dotted scalar keys and their EFFECTIVE values, unwrapping {@code ${ENV:default}} to the default.
     *
     * <p>⚠️ It must ignore comments and track indentation. {@code ScheduledJobCronContractTest} records
     * that an unanchored substring check was once satisfied by a COMMENTED-OUT line, which armed the
     * job that deletes data for an entire suite run while the test stayed green.
     */
    private Map<String, String> scalarKeysDeclaredIn(Path yaml) throws IOException {
        Map<String, String> effective = new TreeMap<>();
        rawKeysDeclaredIn(yaml).forEach((key, value) -> effective.put(key, unwrap(value)));
        return effective;
    }

    /** As {@link #scalarKeysDeclaredIn}, but leaving {@code ${ENV:default}} wrappers intact. */
    private Map<String, String> rawKeysDeclaredIn(Path yaml) throws IOException {
        Map<String, String> found = new TreeMap<>();
        Map<Integer, String> path = new TreeMap<>();
        for (String raw : Files.readString(yaml, StandardCharsets.UTF_8).split("\n", -1)) {
            String line = raw.stripTrailing();
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
                continue;
            }
            Matcher entry = YAML_ENTRY.matcher(line);
            if (!entry.matches()) {
                continue;
            }
            int indent = entry.group(1).length();
            String name = entry.group(2);
            String value = entry.group(3).strip();
            path.keySet().removeIf(depth -> depth >= indent);
            if (value.isEmpty()) {
                path.put(indent, name);
                continue;
            }
            found.put(String.join(".", path.values()) + "." + name, stripQuotes(value));
        }
        return found;
    }

    private String unwrap(String value) {
        Matcher placeholder = PLACEHOLDER.matcher(value);
        return placeholder.matches() ? placeholder.group(2) : value;
    }

    private String stripQuotes(String value) {
        return value.replaceAll("^[\"']|[\"']$", "");
    }
}
