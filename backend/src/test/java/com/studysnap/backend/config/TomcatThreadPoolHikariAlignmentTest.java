package com.studysnap.backend.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the structural relationship between the Tomcat worker-thread pool and the Hikari connection
 * pool, not just the two numbers.
 *
 * <p>⚠️ WHY THIS EXISTS. {@code server.tomcat.threads.max} EXCEEDING
 * {@code spring.datasource.hikari.maximum-pool-size} is what let ~21 concurrent requests alone
 * exhaust the connection pool under {@code spring.jpa.open-in-view: true} (a connection is held for
 * the whole request, not just its transaction), independent of whatever else was holding connections
 * across the 2026-09-04/05/10/17 outages. `threads.max` was lowered from 25 to 20 in `v0.153.0` Leg B
 * to close this specific structural exposure.
 *
 * <p>⚠️ A LITERAL PAIR OF NUMBERS WOULD ROT. {@link DataSourcePoolContractTest} already pins Hikari's
 * {@code maximum-pool-size} at 20 for its own reasons (the deploy-overlap ceiling). If a future editor
 * raises that pool size without re-reading this file, a bare {@code assertThat(25).isEqualTo(25)}-style
 * pin would stay green while the exposure this test exists to prevent came back. Asserting the
 * INEQUALITY, re-read from both files every run, is what keeps the two settings honest against each
 * other rather than against a remembered number.
 *
 * <p>⚠️ THIS PINS THE DECLARED DEFAULT, NOT THE EFFECTIVE PRODUCTION VALUE, AND THAT GAP IS LIVE
 * TODAY. Render sets {@code SERVER_TOMCAT_THREADS_MAX=25} explicitly (confirmed on the dashboard,
 * 2026-09-17), which overrides the default this test reads and stays green against. This test can
 * therefore be fully passing while the exposure it describes is still open in production — the same
 * shadowing risk {@link DataSourcePoolContractTest#theProdProfileOverlayDoesNotSilentlyShadowThePinnedPoolSettings()}
 * documents for {@code application-prod.yaml}, except here the shadowing source is a dashboard
 * environment variable this test suite has no way to read. Closing the actual exposure requires the
 * owner to change or remove that Render variable; this test only guarantees the code-side default is
 * correct once nothing overrides it.
 *
 * @see DataSourcePoolContractTest the sibling pin for the Hikari side of this relationship
 */
class TomcatThreadPoolHikariAlignmentTest {
    private static final Path PRODUCTION_PROFILE = Path.of("src/main/resources/application.yaml");
    private static final Pattern YAML_ENTRY = Pattern.compile("^( *)([A-Za-z0-9_-]+):(.*)$");
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([^:}]+):(.*)}$");

    private static final String TOMCAT_THREADS_MAX = "server.tomcat.threads.max";
    private static final String HIKARI_MAX_POOL_SIZE = "spring.datasource.hikari.maximum-pool-size";

    @Test
    void tomcatThreadsMaxStaysAtOrBelowTheHikariConnectionPoolSize() throws IOException {
        Map<String, String> declared = scalarKeysDeclaredIn(PRODUCTION_PROFILE);

        int tomcatThreadsMax = Integer.parseInt(declared.get(TOMCAT_THREADS_MAX));
        int hikariMaxPoolSize = Integer.parseInt(declared.get(HIKARI_MAX_POOL_SIZE));

        assertThat(tomcatThreadsMax)
                .as("server.tomcat.threads.max (%d) must not exceed "
                        + "spring.datasource.hikari.maximum-pool-size (%d) — under open-in-view, "
                        + "concurrent requests alone (independent of query speed) can exhaust the pool "
                        + "the moment Tomcat admits more concurrent requests than Hikari can serve",
                        tomcatThreadsMax, hikariMaxPoolSize)
                .isLessThanOrEqualTo(hikariMaxPoolSize);
    }

    @Test
    void bothPinnedValuesStayOverridableFromTheEnvironment() throws IOException {
        Map<String, String> raw = rawKeysDeclaredIn(PRODUCTION_PROFILE);

        for (String key : new String[] {TOMCAT_THREADS_MAX, HIKARI_MAX_POOL_SIZE}) {
            assertThat(PLACEHOLDER.matcher(raw.get(key)).matches())
                    .as("%s must be declared as ${ENV:default}. A bare literal cannot be tuned per "
                            + "environment without a code change and a redeploy.", key)
                    .isTrue();
        }
    }

    /** Dotted scalar keys and their EFFECTIVE values, unwrapping {@code ${ENV:default}} to the default. */
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
