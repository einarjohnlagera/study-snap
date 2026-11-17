package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins the current deadline classification pending the targeted 5f ruling. */
class DeadlineReceiptGapCharacterizationTest {
    private static final String ROUTE = "CRITIQUE";
    private static final String ASK = "ASK_COMPANION";
    private static final String INTERVIEW = "INTERVIEW_CRITIQUE";
    private static final String CASE_ID = INTERVIEW + "-0";
    private static final String CANDIDATE_ONLY_ID = INTERVIEW + "-1";
    private static final String RESOURCE =
            "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json";
    @TempDir Path temp;

    private FrozenGateParameters parameters() throws Exception {
        Path source = Path.of(RESOURCE);
        Path copy = temp.resolve("parameters.json");
        Files.copy(source, copy);
        Files.copy(source.resolveSibling(source.getFileName() + ".sha256"),
                copy.resolveSibling(copy.getFileName() + ".sha256"));
        return FrozenGateParameters.load(copy);
    }

    @Test void drawnUnmeteredConflictCurrentlyFallsBackDespiteItsCause() throws Exception {
        FrozenGateParameters p = parameters();
        var records = ReceiptGapDrawnReserveTest.cases(p);
        ReceiptGapDrawnReserveTest.set(records, CASE_ID, ReceiptGapDrawnReserveTest.scored(0),
                ReceiptGapDrawnReserveTest.evidence(RouteVerdictCalculator.Evidence.UNMETERED));
        ReceiptGapDrawnReserveTest.set(records, CANDIDATE_ONLY_ID,
                ReceiptGapDrawnReserveTest.scored(0), ReceiptGapDrawnReserveTest.scored(1));
        var disposition = new RouteVerdictCalculator.OwnerDisposition(INTERVIEW, CANDIDATE_ONLY_ID,
                RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION);
        var report = RouteVerdictCalculator.calculate(p, ROUTE, ReceiptGapDrawnReserveTest.frozen(p),
                records, Map.of(INTERVIEW, ReceiptGapDrawnReserveTest.reserve(0, 0)),
                Map.of(ASK, true, INTERVIEW, true), List.of(disposition),
                ReceiptGapDrawnReserveTest.decisions(true), Map.of());
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(report.workflows().get(INTERVIEW).frb().reserveCause())
                .isEqualTo("DRAWN_RESERVE_WITH_UNRESOLVED_PRIMARY");
        assertThat(report.workflows().get(INTERVIEW).frb().unresolvedReasonsByCaseId())
                .containsEntry(CASE_ID, "candidate:UNMETERED");
        assertThat(report.undisposedCriticalCaseIds()).isEmpty();
        assertThat(report.failedTiers()).isEmpty();
        assertThat(DeadlineRule.classify(report))
                .isEqualTo(DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE);
    }

    @Test void humanUnresolvedWithoutReserveAlsoCurrentlyFallsBack() throws Exception {
        FrozenGateParameters p = parameters();
        var records = ReceiptGapDrawnReserveTest.cases(p);
        var human = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, null, List.of(), false, false, 0, 1);
        ReceiptGapDrawnReserveTest.set(records, CASE_ID, ReceiptGapDrawnReserveTest.scored(0), human);
        var report = RouteVerdictCalculator.calculate(p, ROUTE, ReceiptGapDrawnReserveTest.frozen(p),
                records, Map.of(), Map.of(ASK, true, INTERVIEW, true), List.of(),
                ReceiptGapDrawnReserveTest.decisions(false), Map.of());
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(report.workflows().get(INTERVIEW).frb().reserveCause()).isNull();
        assertThat(report.undisposedCriticalCaseIds()).isEmpty();
        assertThat(DeadlineRule.classify(report))
                .isEqualTo(DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE);
    }
}
