package com.studysnap.backend.service.jobs;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The production schedule of every cron job, pinned.
 *
 * <p>⚠️ WHY THIS EXISTS. v0.98.0 made three hardcoded crons configurable, and the pre-signoff cold
 * agent verified their defaults by hand against {@code origin/main} — because nothing in the build
 * could. A future edit that silently moved a production schedule would have shipped green. Hand
 * verification does not survive the session that performed it.
 *
 * <p>⚠️ AND IT CATCHES THE GAP THAT ACTUALLY SHIPPED. The same audit found the claim "every scheduled
 * job is disabled in tests" was made one commit before it was true: three jobs had been fixed, and
 * five others were already placeholder-driven but simply never listed — including
 * {@code account.purge-cron}, which DELETES DATA and was armed during every test run. That was found
 * by a human enumerating annotations. {@link #everyCronJobIsDisabledInTheTestProfile} enumerates them
 * mechanically instead, so a NEW job added without a test-profile entry fails immediately.
 */
class ScheduledJobCronContractTest {
    /**
     * ⚠️ THE WHOLE APPLICATION TREE, NOT {@code service/jobs}. A cold agent falsified the narrower
     * scan at the v0.99.0 signoff: a {@code @Scheduled(cron = "0 0 4 * * *")} placed one package up,
     * as a BARE LITERAL, passed both tests — the form check never saw it and the disablement check
     * never saw it, so it would have run on the wall clock through the entire suite with no way to
     * turn it off. Every cron job living under {@code service/jobs} was true by file placement, not
     * by construction.
     */
    private static final String BASE_PACKAGE_PATH = "com/studysnap/backend/";
    private static final String JOB_CLASSES = "classpath*:" + BASE_PACKAGE_PATH + "**/*.class";
    private static final Path TEST_PROFILE = Path.of("src/test/resources/application.yaml");
    private static final Path PRODUCTION_PROFILE = Path.of("src/main/resources/application.yaml");

    /**
     * Every cron key and its PRODUCTION default. Changing a schedule means changing this map too —
     * deliberately, so the change is a decision rather than a side effect.
     */
    private static final Map<String, String> EXPECTED_DEFAULTS = new TreeMap<>(Map.ofEntries(
            Map.entry("studysnap.account.purge-cron", "0 30 3 * * *"),
            Map.entry("studysnap.billing.expiry-email-cron", "0 0 3 * * *"),
            Map.entry("studysnap.billing.subscription-expiry-cron", "0 30 2 * * *"),
            Map.entry("studysnap.billing.usage-reset-cron", "0 15 1 * * *"),
            Map.entry("studysnap.generation.bulk-result-cleanup-cron", "0 45 * * * *"),
            Map.entry("studysnap.generation.recovery-cron", "0 */10 * * * *"),
            Map.entry("studysnap.linked-learners.request-expiry-cron", "0 45 2 * * *"),
            Map.entry("studysnap.retention.daily-cron", "0 45 2 * * *"),
            Map.entry("studysnap.retention.knowledge-impact-digest-monthly-cron", "0 0 9 1 * *"),
            Map.entry("studysnap.retention.weekly-cron", "0 0 18 * * SUN")
    ));

    /**
     * Every cron key and the timezone its dispatch is anchored to — {@code ""} meaning none declared, so
     * the job runs in the JVM default (UTC in production: nothing sets TZ in the Dockerfile,
     * application.yaml or docker-compose.yml, and eclipse-temurin defaults to UTC).
     *
     * ⚠️ Recorded as a v0.99.0 Known limitation and closed here. The cron contract pinned the EXPRESSION
     * but not the ZONE, so changing {@code zone} would have moved all three retention dispatches with a
     * green build — the same shadowing class as the yaml gap, at lower stakes.
     *
     * ⚠️ The empty entries are the load-bearing half, not filler. An UNZONED schedule reads as local time
     * and is not: {@code billing.usage-reset-cron} at {@code 0 15 1 * * *} runs 01:15 UTC, which is
     * 09:15 in Manila, not 01:15. Pinning the blanks makes adding a zone — or dropping one — a decision.
     */
    private static final Map<String, String> EXPECTED_ZONES = new TreeMap<>(Map.ofEntries(
            Map.entry("studysnap.account.purge-cron", ""),
            Map.entry("studysnap.billing.expiry-email-cron", ""),
            Map.entry("studysnap.billing.subscription-expiry-cron", ""),
            Map.entry("studysnap.billing.usage-reset-cron", ""),
            Map.entry("studysnap.generation.bulk-result-cleanup-cron", ""),
            Map.entry("studysnap.generation.recovery-cron", ""),
            Map.entry("studysnap.linked-learners.request-expiry-cron", ""),
            Map.entry("studysnap.retention.daily-cron", "Asia/Manila"),
            Map.entry("studysnap.retention.knowledge-impact-digest-monthly-cron", ""),
            Map.entry("studysnap.retention.weekly-cron", "Asia/Manila")
    ));

    private static final String DISABLED = "-";
    /** A {@code name: value} YAML entry, capturing its indent so the parent path can be tracked. */
    private static final Pattern YAML_ENTRY = Pattern.compile("^( *)([A-Za-z0-9_-]+):(.*)$");
    /** {@code ${key:default}} — the only form that is both overridable and startup-safe. */
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([^:}]+):(.+)}$");

    @Test
    void everyCronJobDeclaresItsProductionScheduleAsAnOverridableDefault() throws Exception {
        Map<String, String> found = discoverCronExpressions();

        assertThat(found)
                .as("a new cron job must be added to EXPECTED_DEFAULTS, so its schedule is a decision")
                .containsOnlyKeys(EXPECTED_DEFAULTS.keySet().toArray(new String[0]));
        assertThat(found).isEqualTo(EXPECTED_DEFAULTS);
    }

    @Test
    void everyCronJobPinsTheTimezoneItsScheduleIsInterpretedIn() throws Exception {
        Map<String, String> found = discoverCronZones();

        assertThat(found)
                .as("a new cron job must be added to EXPECTED_ZONES, so its dispatch timezone is a decision")
                .containsOnlyKeys(EXPECTED_ZONES.keySet().toArray(new String[0]));
        assertThat(found)
                .as("a changed zone moves when a job fires without changing its cron expression, so it "
                        + "must be an explicit edit here rather than a silent one")
                .isEqualTo(EXPECTED_ZONES);
    }

    /**
     * ⚠️ The assertion that would have caught v0.98.0's gap. A cron job with no test-profile entry
     * runs on the WALL CLOCK during the suite — which is how GenerationRecoveryJob, firing every ten
     * minutes, once landed its queries inside another test's captured query list and produced a
     * failure reproducible only by timing.
     */
    @Test
    void everyCronJobIsDisabledInTheTestProfile() throws Exception {
        Map<String, String> profile = cronKeysDeclaredIn(TEST_PROFILE);

        for (String key : discoverCronExpressions().keySet()) {
            assertThat(profile)
                    .as("%s must be disabled in the test profile — an unlisted cron job runs on the "
                            + "wall clock during the suite", key)
                    .containsEntry(key, DISABLED);
        }
    }

    /**
     * ⚠️ THE ANNOTATION DEFAULT IS NOT THE PRODUCTION SCHEDULE WHERE {@code application.yaml}
     * DECLARES THE KEY — a present property WINS over the {@code ${key:default}} fallback, so for 8
     * of the 10 jobs the annotation default is dead text. A cold agent falsified the original test
     * at the v0.99.0 signoff by moving {@code studysnap.retention.daily-cron} in the production yaml
     * and leaving the annotation alone: the suite stayed green while the real dispatch time moved,
     * which is verbatim the failure this class exists to prevent.
     *
     * <p>Both declarations are now pinned to the SAME expected value, so drift in either fails, and
     * so does a disagreement between them.
     */
    @Test
    void everyCronKeyDeclaredInProductionYamlPinsTheSameScheduleAsItsAnnotation() throws Exception {
        Map<String, String> production = cronKeysDeclaredIn(PRODUCTION_PROFILE);

        assertThat(EXPECTED_DEFAULTS.keySet())
                .as("a cron key in application.yaml that no @Scheduled job reads is dead config")
                .containsAll(production.keySet());
        production.forEach((key, cron) -> assertThat(cron)
                .as("%s is declared in application.yaml, so THAT value is the production schedule — "
                        + "not the annotation default it shadows", key)
                .isEqualTo(EXPECTED_DEFAULTS.get(key)));
    }

    /**
     * Dotted cron keys and their effective values from a YAML profile, tracking indentation so a key
     * is matched under its real parent.
     *
     * <p>⚠️ IT MUST IGNORE COMMENTS, and that is not hypothetical: the previous unanchored
     * {@code contains(leaf + ": \"-\"")} check was satisfied by a COMMENTED-OUT line, so a cold
     * agent armed {@code account.purge-cron} — the job that DELETES DATA — at 01:00 for the whole
     * suite and the test stayed green. Leaf-only matching also made {@code retention.daily-cron}
     * interchangeable with any other {@code daily-cron}.
     */
    private Map<String, String> cronKeysDeclaredIn(Path yaml) throws IOException {
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
            if (!name.endsWith("-cron")) {
                continue;
            }
            String key = String.join(".", path.values()) + "." + name;
            found.put(key, unquote(value));
        }
        return found;
    }

    /** Unwraps {@code ${ENV:default}} to the default, and strips surrounding quotes. */
    private String unquote(String value) {
        String bare = value.replaceAll("^[\"']|[\"']$", "");
        Matcher placeholder = PLACEHOLDER.matcher(bare);
        return placeholder.matches() ? placeholder.group(2) : bare;
    }

    /** Maps each job's cron placeholder key to its declared default. */
    private Map<String, String> discoverCronExpressions() throws IOException, ClassNotFoundException {
        Map<String, String> found = new TreeMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (Resource resource : resolver.getResources(JOB_CLASSES)) {
            String className = className(resource);
            if (className.contains("$")) {
                continue;
            }
            for (Method method : Class.forName(className).getDeclaredMethods()) {
                Scheduled scheduled = method.getAnnotation(Scheduled.class);
                if (scheduled == null || scheduled.cron().isEmpty()) {
                    continue;
                }
                Matcher matcher = PLACEHOLDER.matcher(scheduled.cron());
                assertThat(matcher.matches())
                        .as("%s.%s must declare its cron as ${key:default}. A bare literal cannot be "
                                + "overridden or disabled; a bare ${key} fails startup on a missing key.",
                                className, method.getName())
                        .isTrue();
                found.put(matcher.group(1), matcher.group(2));
            }
        }
        return new LinkedHashMap<>(found);
    }

    /**
     * Mirrors {@link #discoverCronExpressions()} but collects {@code zone} instead of the expression, so a
     * job cannot be pinned in one map and missing from the other.
     */
    private Map<String, String> discoverCronZones() throws IOException, ClassNotFoundException {
        Map<String, String> found = new TreeMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (Resource resource : resolver.getResources(JOB_CLASSES)) {
            String className = className(resource);
            if (className.contains("$")) {
                continue;
            }
            for (Method method : Class.forName(className).getDeclaredMethods()) {
                Scheduled scheduled = method.getAnnotation(Scheduled.class);
                if (scheduled == null || scheduled.cron().isEmpty()) {
                    continue;
                }
                Matcher matcher = PLACEHOLDER.matcher(scheduled.cron());
                if (!matcher.matches()) {
                    continue;
                }
                found.put(matcher.group(1), scheduled.zone());
            }
        }
        return new LinkedHashMap<>(found);
    }

    private String className(Resource resource) throws IOException {
        URI uri = resource.getURI();
        String path = uri.toString();
        int start = path.lastIndexOf(BASE_PACKAGE_PATH);
        return path.substring(start, path.length() - ".class".length()).replace('/', '.');
    }
}
