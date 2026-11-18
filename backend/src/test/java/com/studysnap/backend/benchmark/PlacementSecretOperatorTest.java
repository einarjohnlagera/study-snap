package com.studysnap.backend.benchmark;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Run once per export id with an owner-selected private path; skipped in ordinary suites. */
class PlacementSecretOperatorTest {
    @Test void createOnePrivateSecretAndPrintOnlyCommitment() {
        assumeTrue(Boolean.getBoolean("placement.secret.create"));
        String file = System.getProperty("placement.secret.file");
        String exportId = System.getProperty("placement.export.id");
        assumeTrue(file != null && exportId != null);
        String commitment = PlacementSecret.create(Path.of(file));
        String line = PlacementSecret.operatorLine(exportId, commitment);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            out.println(line);
        }
        assertThat(captured.toString(StandardCharsets.UTF_8)).isEqualTo(exportId + " " + commitment + "\n");
        System.out.print(captured.toString(StandardCharsets.UTF_8));
    }
}
