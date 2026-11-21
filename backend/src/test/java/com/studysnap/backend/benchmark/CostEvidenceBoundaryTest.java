package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Boundary controls for the cost-evidence producer and its eligibility hook (added by the 5m audit). */
class CostEvidenceBoundaryTest {
    private static final StageCapLedger.Stage ONE = StageCapLedger.Stage.STAGE_1;
    private static final String CELL = "synthetic-cell";
    private static final BigDecimal CAP = new BigDecimal("1");
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal LOW = new BigDecimal("0.4");
    private static final String SCHEMA_ONE = "{\"schema\":1,\"caps\":{\"STAGE_1\":\"1\"}}";
    @TempDir Path temp;

    private static Map<StageCapLedger.Stage, BigDecimal> caps() { return Map.of(ONE, CAP); }
    private Path newLedger(String name) {
        Path file = temp.resolve(name + ".jsonl");
        try (StageCapLedger ignored = StageCapLedger.open(file, caps(), true)) { return file; }
    }
    private ApprovedCaps approved() throws Exception {
        Path path = temp.resolve("approved-" + java.util.UUID.randomUUID() + ".json");
        Files.writeString(path, SCHEMA_ONE);
        return ApprovedCaps.load(path);
    }
    private CostEvidence produce(Path file) throws Exception { return CostEvidenceProducer.produce(file, approved()); }

    @Test void nullClassificationInputNeverRemovesTheCostReason() throws Exception {
        Path file = newLedger("null-input");
        var item = new ProductionSwitchEligibility.CostLedgerInput(file, approved());
        var decision = ProductionSwitchEligibility.check("INTERACTIVE_QUIZ", null, null, null, null,
                null, null, Set.of(), List.of(item));
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED,
                ProductionSwitchEligibility.COST_FLAGS_DIFFER_FROM_LEDGER);
    }

    @Test void approvedCapsRejectWrongSchemaNumericCapsAndBadDigests() throws Exception {
        String[] bad = {"{\"schema\":2,\"caps\":{\"STAGE_1\":\"1\"}}", "{\"schema\":1,\"caps\":{\"STAGE_1\":1}}",
                "{\"schema\":\"1\",\"caps\":{\"STAGE_1\":\"1\"}}", "{\"caps\":{\"STAGE_1\":\"1\"}}"};
        for (String json : bad) {
            Path path = temp.resolve("bad-" + java.util.UUID.randomUUID() + ".json");
            Files.writeString(path, json);
            assertThatThrownBy(() -> ApprovedCaps.load(path)).isInstanceOf(InvalidCostEvidence.class);
        }
        assertThatThrownBy(() -> new ApprovedCaps(caps(), "not-a-digest")).isInstanceOf(InvalidCostEvidence.class);
        assertThatThrownBy(() -> new ApprovedCaps(caps(), "A".repeat(64))).isInstanceOf(InvalidCostEvidence.class);
        assertThatThrownBy(() -> new ApprovedCaps(caps(), null)).isInstanceOf(InvalidCostEvidence.class);
        assertThat(new ApprovedCaps(caps(), "a".repeat(64)).caps()).isEqualTo(caps());
    }

    @Test void transferredReservationsAreTrackedThroughTheScan() throws Exception {
        Path partial = newLedger("transfer-partial");
        try (StageCapLedger opened = StageCapLedger.open(partial, caps(), false)) {
            StageCapLedger.Reservation parent = opened.reserve(ONE, CELL, HALF);
            opened.reserveFrom(parent, ONE, CELL, LOW).settle(LOW, LOW);
        }
        assertThat(produce(partial).flags()).contains(CostEvidenceProducer.IN_FLIGHT + ONE);
        Path full = newLedger("transfer-full");
        try (StageCapLedger opened = StageCapLedger.open(full, caps(), false)) {
            StageCapLedger.Reservation parent = opened.reserve(ONE, CELL, HALF);
            opened.reserveFrom(parent, ONE, CELL, HALF).settle(HALF, HALF);
        }
        assertThat(produce(full).flags()).doesNotContain(CostEvidenceProducer.IN_FLIGHT + ONE);
        Path childLive = newLedger("transfer-child-live");
        try (StageCapLedger opened = StageCapLedger.open(childLive, caps(), false)) {
            StageCapLedger.Reservation parent = opened.reserve(ONE, CELL, HALF);
            opened.reserveFrom(parent, ONE, CELL, LOW);
            parent.settle(LOW, LOW);
        }
        assertThat(produce(childLive).flags()).contains(CostEvidenceProducer.IN_FLIGHT + ONE);
    }

    @Test void stopReasonMappingUsesPrefixForUnmeteredAndExactMatchForProviderBound() throws Exception {
        Path unmetered = newLedger("stop-unmetered-text");
        try (StageCapLedger opened = StageCapLedger.open(unmetered, caps(), false)) {
            opened.stop("UNMETERED response: STAGE_1");
        }
        assertThat(produce(unmetered).flags()).contains(CostEvidenceProducer.STOP_RECORDED + "UNMETERED")
                .doesNotContain(CostEvidenceProducer.STOP_RECORDED + "OTHER");
        Path near = newLedger("stop-near-provider-bound");
        try (StageCapLedger opened = StageCapLedger.open(near, caps(), false)) {
            opened.stop("PROVIDER_BOUND_EXCEEDED_EXTRA");
        }
        assertThat(produce(near).flags()).contains(CostEvidenceProducer.STOP_RECORDED + "OTHER")
                .doesNotContain(CostEvidenceProducer.STOP_RECORDED + "PROVIDER_BOUND_EXCEEDED");
    }

    @Test void aSymlinkIntoTheRepositoryIsRefusedForLedgerAndApproval() throws Exception {
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        Path link = temp.resolve("link");
        Files.createSymbolicLink(link, repo);
        Path ledger = link.resolve("never-created").resolve("ledger.jsonl");
        assertThatThrownBy(() -> CostEvidenceProducer.produce(ledger, approved()))
                .isInstanceOf(InvalidCostEvidence.class);
        assertThatThrownBy(() -> ApprovedCaps.load(link.resolve("never-created").resolve("caps.json")))
                .isInstanceOf(InvalidCostEvidence.class);
        assertThat(Files.exists(repo.resolve("never-created"))).isFalse();
    }

    @Test void aPathUnderBackendTargetIsRefusedEvenThoughTheSharedHelperAllowsIt() throws Exception {
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        Path target = repo.resolve("backend").resolve("target").resolve("never-created-5m-audit");
        assertThatThrownBy(() -> CostEvidenceProducer.produce(target.resolve("ledger.jsonl"), approved()))
                .isInstanceOf(InvalidCostEvidence.class).hasMessageContaining("inside repository");
        assertThatThrownBy(() -> ApprovedCaps.load(target.resolve("caps.json")))
                .isInstanceOf(InvalidCostEvidence.class).hasMessageContaining("inside repository");
        Path link = temp.resolve("target-link");
        Files.createSymbolicLink(link, repo.resolve("backend").resolve("target"));
        assertThatThrownBy(() -> CostEvidenceProducer.produce(link.resolve("x").resolve("ledger.jsonl"), approved()))
                .isInstanceOf(InvalidCostEvidence.class).hasMessageContaining("inside repository");
        assertThat(Files.exists(target)).isFalse();
    }
}
