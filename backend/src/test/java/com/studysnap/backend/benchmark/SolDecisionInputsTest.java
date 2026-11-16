package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Preregistration section 9 refusal of missing owner-authored Sol decisions. */
class SolDecisionInputsTest {
    @TempDir Path temp;
    private Path decision(String economics, String investigation, String sameInputs) throws Exception {
        Path file = temp.resolve("sol-decision.json");
        Files.writeString(file, "{\"route\":\"INTERACTIVE_QUIZ\",\"lunaReportSha256\":\""
                + "a".repeat(64) + "\",\"solReportSha256\":\"" + "b".repeat(64)
                + "\",\"economicsAndOtherGatesPass\":" + economics
                + ",\"investigationComplete\":" + investigation
                + ",\"samePrimaryInputs\":" + sameInputs + "}");
        return file;
    }

    @Test void allThreeOwnerFieldsAreRequiredWithoutDefaults() throws Exception {
        Path file = decision("true", "false", "true");
        assertThat(SolDecisionInputs.load(file).investigationComplete()).isFalse();
        decision("null", "true", "true");
        assertThatThrownBy(() -> SolDecisionInputs.load(file))
                .isInstanceOf(SolDecisionInputs.InvalidDecision.class);
        decision("true", "null", "true");
        assertThatThrownBy(() -> SolDecisionInputs.load(file))
                .isInstanceOf(SolDecisionInputs.InvalidDecision.class);
        decision("true", "true", "null");
        assertThatThrownBy(() -> SolDecisionInputs.load(file))
                .isInstanceOf(SolDecisionInputs.InvalidDecision.class);
    }
}
