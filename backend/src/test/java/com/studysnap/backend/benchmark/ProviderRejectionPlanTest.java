package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderRejectionPlanTest {
    private static final String ID = "rejected-case";
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String ARM = "LUNA";
    private static final String MODEL = "gpt-6-luna";
    private static final String HASH = "a".repeat(64);
    private static final String HEAD = "b".repeat(40);
    private static final String HTTP = "HTTP_ERROR";
    @TempDir Path temp;

    private static FreezeRecord freeze(Set<String> classes) {
        return new FreezeRecord(HEAD, HASH, HASH, Map.of(), Map.of(),
                Map.of(ARM, new FreezeRecord.Arm(MODEL, List.of(WORKFLOW))),
                Map.of(WORKFLOW, MODEL), LocalDate.of(2026, 11, 17), classes);
    }

    private static Stage1Receipt.CaseRow row(CaseArmOutcome.State state, String failure, int status) {
        return new Stage1Receipt.CaseRow(ID, WORKFLOW, HASH, state, false, false, failure,
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(new Stage1Receipt.Signal("HTTP_ERROR", 1, 1, status)), List.of(HASH));
    }

    private static DeliveredOutputs.Line output(Stage1Receipt.CaseRow row) {
        return new DeliveredOutputs.Line(ID, WORKFLOW, ARM, HASH, 0,
                row.state(), row.failureClass(), null);
    }

    private RecheckPlan.Decision decision(Stage1Receipt.CaseRow row, Set<String> frozen) throws Exception {
        Path file = temp.resolve("output-" + frozen.hashCode() + "-" + row.failureClass()
                + "-" + row.llmCalls().getFirst().httpStatus() + ".jsonl");
        CaseArmOutcome outcome = CaseArmOutcome.classify(ID, List.of(), false,
                row.state() == CaseArmOutcome.State.INCONCLUSIVE, row.failureClass());
        DeliveredOutputs.Written written = DeliveredOutputs.write(file, ARM, 0, List.of(outcome),
                Map.of(ID, WORKFLOW), Map.of(ID, HASH), Map.of());
        Stage1Receipt.CaseRow linked = new Stage1Receipt.CaseRow(ID, WORKFLOW, HASH, row.state(),
                row.firstAttemptClean(), row.finalSuccess(), row.failureClass(), BigDecimal.ZERO,
                BigDecimal.ZERO, row.llmCalls(), row.attempt1BodyHashes(), written.lineSha256().get(ID));
        Stage1Receipt receipt = new Stage1Receipt(HEAD, HASH, ARM, MODEL, StageCapLedger.Stage.STAGE_1,
                Instant.now(), Instant.now(), 0, false, HASH, List.of(linked), Map.of(), written.fileSha256());
        return RecheckPlan.build(receipt, file, freeze(frozen), Map.of(ID, HASH));
    }

    @Test void exactRejectionStatusesNeedTheFrozenToken() throws Exception {
        for (int status : List.of(400, 422)) {
            Stage1Receipt.CaseRow rejected = row(CaseArmOutcome.State.FAILED, HTTP, status);
            assertThat(RecheckPlan.isRejectionCase(rejected)).isTrue();
            assertThat(RecheckPlan.isEligible(rejected, output(rejected),
                    freeze(Set.of(RecheckPlan.PROVIDER_REJECTION)), HASH)).isTrue();
            assertThat(decision(rejected, Set.of()).refused().getFirst().reason())
                    .isEqualTo("Failure class is never a T1 recheck trigger: HTTP_ERROR");
        }
    }

    @Test void deliveredAndOtherClassesKeepLegacyEligibility() throws Exception {
        Stage1Receipt.CaseRow delivered = row(CaseArmOutcome.State.COMPLETE, HTTP, 400);
        assertThat(RecheckPlan.isRejectionCase(delivered)).isFalse();
        assertThat(RecheckPlan.isEligible(delivered, output(delivered),
                freeze(Set.of(RecheckPlan.PROVIDER_REJECTION)), HASH)).isFalse();
        for (String failure : List.of("TIMEOUT", "INPUT_DEFECT", "ARM_MISMATCH", "INCONCLUSIVE_FAKE",
                "SCHEMA_OR_PARSE", "OTHER")) {
            Stage1Receipt.CaseRow other = row(CaseArmOutcome.State.FAILED, failure, 400);
            assertThat(RecheckPlan.isRejectionCase(other)).isFalse();
            assertThat(RecheckPlan.isEligible(other, output(other),
                    freeze(Set.of(RecheckPlan.PROVIDER_REJECTION)), HASH)).isFalse();
        }
        Stage1Receipt.CaseRow frozenParse = row(CaseArmOutcome.State.FAILED, "SCHEMA_OR_PARSE", 400);
        assertThat(RecheckPlan.isEligible(frozenParse, output(frozenParse),
                freeze(Set.of("SCHEMA_OR_PARSE")), HASH)).isTrue();
        assertThat(decision(frozenParse, Set.of("SCHEMA_OR_PARSE")).eligible()).hasSize(1);
        Stage1Receipt.CaseRow nullClass = row(CaseArmOutcome.State.FAILED, null, 400);
        assertThat(RecheckPlan.isRejectionCase(nullClass)).isFalse();
        assertThat(RecheckPlan.isEligible(nullClass, output(nullClass),
                freeze(Set.of(RecheckPlan.PROVIDER_REJECTION)), HASH)).isFalse();
    }

    @Test void onlyExactStatusesAndMatchingEvidenceQualify() {
        for (int status : List.of(399, 401, 423, 500)) {
            Stage1Receipt.CaseRow other = row(CaseArmOutcome.State.FAILED, HTTP, status);
            assertThat(RecheckPlan.isRejectionCase(other)).isFalse();
        }
        Stage1Receipt.CaseRow rejected = row(CaseArmOutcome.State.FAILED, HTTP, 400);
        FreezeRecord enabled = freeze(Set.of(RecheckPlan.PROVIDER_REJECTION));
        assertThat(RecheckPlan.isEligible(rejected, output(rejected), enabled, "c".repeat(64))).isFalse();
        DeliveredOutputs.Line wrongState = new DeliveredOutputs.Line(ID, WORKFLOW, ARM, HASH, 0,
                CaseArmOutcome.State.COMPLETE, HTTP, null);
        assertThat(RecheckPlan.isEligible(rejected, wrongState, enabled, HASH)).isFalse();
    }
}
