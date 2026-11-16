package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** Freeze-owned T1 eligibility mutants for preregistration sections 1 and 8. */
class RecheckPlanTest {
    private static final String ARM = "BASELINE_MINI";
    private static final String MODEL = "gpt-4.1-mini";
    private static final String HASH = "a".repeat(64);
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String ASK = "ASK_COMPANION_UNAVAILABLE";
    private static final String SHORT = "SHORT_DELIVERY";
    private static final String DUPLICATE = "DUPLICATE_DELIVERY";
    @TempDir Path temp;

    private FreezeRecord freeze(Set<String> eligible) {
        return new FreezeRecord("b".repeat(40), HASH, HASH, Map.of(), Map.of(),
                Map.of(ARM, new FreezeRecord.Arm(MODEL, List.of(WORKFLOW))),
                Map.of(WORKFLOW, MODEL), LocalDate.of(2026, 11, 14), eligible);
    }

    @Test void frozenSetAndHttpStatusControlEligibilityWhileForbiddenClassesStayRefused() throws Exception {
        Map<String, String> failures = new LinkedHashMap<>();
        failures.put("short", SHORT);
        failures.put("duplicate", DUPLICATE);
        failures.put("parse", "SCHEMA_OR_PARSE");
        failures.put("ask-200", ASK);
        failures.put("ask-500", ASK);
        failures.put("http", "HTTP_ERROR");
        failures.put("timeout", "TIMEOUT");
        failures.put("input", "INPUT_DEFECT");
        failures.put("arm", "ARM_MISMATCH");
        failures.put("inconclusive", "INCONCLUSIVE_SIGNAL_MISMATCH");
        failures.put("inconclusive-class", "INCONCLUSIVE_FAKE");
        failures.put("unlisted", "UNLISTED_STRUCTURAL");
        List<CaseArmOutcome> outcomes = new ArrayList<>();
        List<Stage1Receipt.CaseRow> rows = new ArrayList<>();
        Map<String, String> workflows = new LinkedHashMap<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        failures.forEach((id, failure) -> {
            boolean inconclusive = id.equals("inconclusive");
            outcomes.add(CaseArmOutcome.classify(id, List.of(), false, inconclusive, failure));
            workflows.put(id, WORKFLOW);
            hashes.put(id, HASH);
        });
        Path file = temp.resolve("delivered-outputs.jsonl");
        DeliveredOutputs.Written written = DeliveredOutputs.write(file, ARM, 0, outcomes,
                workflows, hashes, Map.of());
        failures.forEach((id, failure) -> {
            int status = id.equals("ask-200") ? 200 : id.equals("ask-500") ? 500 : 0;
            rows.add(new Stage1Receipt.CaseRow(id, WORKFLOW, HASH,
                    id.equals("inconclusive") ? CaseArmOutcome.State.INCONCLUSIVE : CaseArmOutcome.State.FAILED,
                    false, false, failure, BigDecimal.ZERO, BigDecimal.ZERO,
                    List.of(new Stage1Receipt.Signal("SUCCESS", 1, 1, status)), List.of(HASH),
                    written.lineSha256().get(id)));
        });
        Stage1Receipt receipt = new Stage1Receipt("b".repeat(40), HASH, ARM, MODEL,
                StageCapLedger.Stage.STAGE_1, Instant.now(), Instant.now(), 1, false, HASH,
                rows, Map.of(), written.fileSha256());
        Set<String> allowed = Set.of(SHORT, DUPLICATE, "SCHEMA_OR_PARSE", ASK,
                "HTTP_ERROR", "TIMEOUT", "INPUT_DEFECT", "ARM_MISMATCH",
                "INCONCLUSIVE_SIGNAL_MISMATCH", "INCONCLUSIVE_FAKE");
        RecheckPlan.Decision decision = RecheckPlan.build(receipt, file, freeze(allowed), hashes);
        assertThat(decision.eligible()).extracting(RecheckPlan.Eligible::caseId)
                .containsExactly("short", "duplicate", "parse", "ask-200");
        assertThat(decision.refused()).extracting(RecheckPlan.Refused::caseId)
                .containsExactly("ask-500", "http", "timeout", "input", "arm", "inconclusive",
                        "inconclusive-class", "unlisted");
        assertThat(decision.refused().getFirst().reason()).contains("HTTP 2xx");
        RecheckPlan.Decision changedFreeze = RecheckPlan.build(receipt, file, freeze(Set.of(DUPLICATE)), hashes);
        assertThat(changedFreeze.eligible()).extracting(RecheckPlan.Eligible::caseId)
                .containsExactly("duplicate");
        Map<String, String> changedManifest = new LinkedHashMap<>(hashes);
        changedManifest.put("duplicate", "c".repeat(64));
        assertThat(RecheckPlan.build(receipt, file, freeze(allowed), changedManifest).eligible())
                .extracting(RecheckPlan.Eligible::caseId).doesNotContain("duplicate");
    }
}
