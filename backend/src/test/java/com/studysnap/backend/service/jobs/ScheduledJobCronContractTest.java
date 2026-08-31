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
    private static final String JOB_CLASSES =
            "classpath*:com/studysnap/backend/service/jobs/**/*.class";
    private static final Path TEST_PROFILE = Path.of("src/test/resources/application.yaml");

    /**
     * Every cron key and its PRODUCTION default. Changing a schedule means changing this map too —
     * deliberately, so the change is a decision rather than a side effect.
     */
    private static final Map<String, String> EXPECTED_DEFAULTS = new TreeMap<>(Map.of(
            "studysnap.account.purge-cron", "0 30 3 * * *",
            "studysnap.billing.expiry-email-cron", "0 0 3 * * *",
            "studysnap.billing.subscription-expiry-cron", "0 30 2 * * *",
            "studysnap.billing.usage-reset-cron", "0 15 1 * * *",
            "studysnap.generation.bulk-result-cleanup-cron", "0 45 * * * *",
            "studysnap.generation.recovery-cron", "0 */10 * * * *",
            "studysnap.linked-learners.request-expiry-cron", "0 45 2 * * *",
            "studysnap.retention.daily-cron", "0 45 2 * * *",
            "studysnap.retention.knowledge-impact-digest-monthly-cron", "0 0 9 1 * *",
            "studysnap.retention.weekly-cron", "0 0 18 * * SUN"
    ));

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

    /**
     * ⚠️ The assertion that would have caught v0.98.0's gap. A cron job with no test-profile entry
     * runs on the WALL CLOCK during the suite — which is how GenerationRecoveryJob, firing every ten
     * minutes, once landed its queries inside another test's captured query list and produced a
     * failure reproducible only by timing.
     */
    @Test
    void everyCronJobIsDisabledInTheTestProfile() throws Exception {
        String profile = Files.readString(TEST_PROFILE, StandardCharsets.UTF_8);

        for (String key : discoverCronExpressions().keySet()) {
            String leaf = key.substring(key.lastIndexOf('.') + 1);
            assertThat(profile)
                    .as("%s must be disabled in the test profile — an unlisted cron job runs on the "
                            + "wall clock during the suite", key)
                    .contains(leaf + ": \"-\"");
        }
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

    private String className(Resource resource) throws IOException {
        URI uri = resource.getURI();
        String path = uri.toString();
        int start = path.lastIndexOf("com/studysnap/backend/service/jobs/");
        return path.substring(start, path.length() - ".class".length()).replace('/', '.');
    }
}
