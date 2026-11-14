package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** Preregistration section 7 and Addendum 3 executed-surefire regression checks. */
class CompatibilitySuiteEvidenceTest {
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String SUITE = "QuickReviewAdaptivePracticeServiceTest";
    private static final Map<String, String> FROZEN = Map.of(SUITE, WORKFLOW);
    private static final Instant HEAD = Instant.parse("2026-11-14T00:00:00Z");
    @TempDir Path temp;

    private Path report(int tests, int failures, int errors, int skipped) throws Exception {
        Path file = temp.resolve("TEST-com.studysnap.backend.service." + SUITE + ".xml");
        Files.writeString(file, "<testsuite tests=\"" + tests + "\" failures=\"" + failures
                + "\" errors=\"" + errors + "\" skipped=\"" + skipped + "\"/>");
        Files.setLastModifiedTime(file, FileTime.from(HEAD.plusSeconds(1)));
        return file;
    }

    @Test void absentStaleAndZeroExecutedAreNeverTrue() throws Exception {
        assertThat(CompatibilitySuiteEvidence.read(temp, FROZEN, HEAD).get(WORKFLOW).passed()).isNull();
        Path file = report(1, 0, 0, 0);
        assertThat(CompatibilitySuiteEvidence.read(temp, FROZEN, HEAD).get(WORKFLOW).passed()).isTrue();
        Files.setLastModifiedTime(file, FileTime.from(HEAD.minusSeconds(1)));
        assertThat(CompatibilitySuiteEvidence.read(temp, FROZEN, HEAD).get(WORKFLOW).passed()).isNull();
        report(0, 0, 0, 0);
        assertThat(CompatibilitySuiteEvidence.read(temp, FROZEN, HEAD).get(WORKFLOW).passed()).isNull();
        report(1, 0, 0, 1);
        assertThat(CompatibilitySuiteEvidence.read(temp, FROZEN, HEAD).get(WORKFLOW).passed()).isNull();
    }

    @Test void failedOrErroredReportIsExplicitFalse() throws Exception {
        report(2, 1, 0, 0);
        assertThat(CompatibilitySuiteEvidence.read(temp, FROZEN, HEAD).get(WORKFLOW).passed()).isFalse();
        report(2, 0, 1, 0);
        assertThat(CompatibilitySuiteEvidence.read(temp, FROZEN, HEAD).get(WORKFLOW).passed()).isFalse();
    }
}
