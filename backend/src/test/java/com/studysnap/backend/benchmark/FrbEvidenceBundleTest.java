package com.studysnap.backend.benchmark;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrbEvidenceBundleTest {
    private static final String TIP = "STUDY_TIP";
    private static final String DIGEST = "0".repeat(64);
    private static final AdjudicatedFacts CLEAN = new AdjudicatedFacts(1, 0, 0, 0, 0, 0, 0, 0, null);
    private static final AdjudicatedFacts CRITICAL = new AdjudicatedFacts(1, 1, 0, 0, 0, 0, 0, 0, null);

    private static AdjudicatedLoaded part(String caseId, String baseline, boolean exclude) {
        return new AdjudicatedLoaded(exclude ? Map.of() : Map.of(
                caseId + "\u0000" + baseline, CLEAN, caseId + "\u0000LUNA", CRITICAL),
                exclude ? Set.of(caseId) : Set.of(), Map.of(caseId, 1));
    }

    private static ReserveDecisionRecord record(boolean drawn, String workflow) {
        return new ReserveDecisionRecord(1, List.of(new ReserveDecisionRecord.WorkflowDecision(
                workflow, 1, drawn, false, DIGEST)), DIGEST, DIGEST, "2026-11-16T00:02:00Z");
    }

    private static FrbEvidenceBundle assemble(List<AdjudicatedLoaded> classA, AdjudicatedLoaded primary,
            List<FrbEvidenceBundle.ReservePart> reserves, Set<String> frozenA, Set<String> frozenB,
            ReserveDecisionRecord record) {
        return FrbEvidenceBundle.assemble(classA, primary, reserves, frozenA, frozenB, record, Set.of(TIP));
    }

    @Test void assemblesTwoClassAExportsPrimaryAndReserveWithWidthsAndHashes() {
        var a1 = part("a1", "BASELINE_MINI", false);
        var a2 = part("a2", "BASELINE_41", true);
        var b = part("b", "BASELINE_MINI", false);
        var reserve = new FrbEvidenceBundle.ReservePart(TIP, part("r", "BASELINE_MINI", false), Set.of("r"));
        var bundle = assemble(List.of(a1, a2), b, List.of(reserve),
                Set.of("a1", "a2"), Set.of("b"), record(true, TIP));
        assertThat(bundle.requestedQuestionsByCaseId()).containsEntry("a1", 1).containsEntry("a2", 1)
                .containsEntry("b", 1).containsEntry("r", 1);
        assertThat(bundle.reserveDrawnByWorkflow()).containsEntry(TIP, true);
        assertThat(bundle.workflowsAwaitingReserve()).isEmpty();
        assertThat(bundle.toLoaded(Map.of("digest", "value")).get("b", "LUNA"))
                .isEqualTo(new ScoreEvidence.Facts(true, false, 1, null));
        assertThat(bundle.toLoaded(Map.of()).excluded()).containsExactly("a2");
    }

    @Test void reportsMissingDrawnReserveAndRejectsUndrawnReserve() {
        var a = part("a", "BASELINE_MINI", false);
        var b = part("b", "BASELINE_MINI", false);
        var reserve = new FrbEvidenceBundle.ReservePart(TIP, part("r", "BASELINE_MINI", false), Set.of("r"));
        var pending = assemble(List.of(a), b, List.of(), Set.of("a"), Set.of("b"), record(true, TIP));
        assertThat(pending.workflowsAwaitingReserve()).containsExactly(TIP);
        assertThatThrownBy(() -> assemble(List.of(a), b, List.of(reserve), Set.of("a"), Set.of("b"),
                record(false, TIP))).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> assemble(List.of(a), b, List.of(reserve), Set.of("a"), Set.of("b"),
                record(true, "ASK_COMPANION"))).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> assemble(List.of(a), b, List.of(), Set.of("a"), Set.of("b"),
                record(false, "ASK_COMPANION"))).isInstanceOf(InvalidEvidence.class);
    }

    @Test void rejectsDuplicatesFrozenUnionAndClassBWidth() {
        var a = part("a", "BASELINE_MINI", false);
        var b = part("b", "BASELINE_MINI", false);
        assertThatThrownBy(() -> assemble(List.of(a, a), b, List.of(), Set.of("a"), Set.of("b"),
                record(false, TIP))).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> assemble(List.of(a), part("a", "BASELINE_MINI", false),
                List.of(), Set.of("a"), Set.of("a"), record(false, TIP))).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> assemble(List.of(a), b, List.of(), Set.of("a", "extra"), Set.of("b"),
                record(false, TIP))).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> assemble(List.of(a), b, List.of(), Set.of("a"), Set.of("b", "extra"),
                record(false, TIP))).isInstanceOf(InvalidEvidence.class);
        var wide = new AdjudicatedLoaded(b.byCaseArm(), Set.of(), Map.of("b", 2));
        assertThatThrownBy(() -> assemble(List.of(a), wide, List.of(), Set.of("a"), Set.of("b"),
                record(false, TIP))).isInstanceOf(InvalidEvidence.class);
    }
}
