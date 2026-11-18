package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R8 B field and classification checks using real calculator inputs, not a synthetic report. */
class RouteVerdictCalculatorReserveGapRuleTest {
    private static final String ROUTE = "CRITIQUE";
    private static final String WORKFLOW = "INTERVIEW_CRITIQUE";
    private static final String ASK = "ASK_COMPANION";
    private static final String PRIMARY_ID = WORKFLOW + "-0";
    private static final String CAUSE = "RESERVE_EVIDENCE_UNRESOLVED";
    private static final String SHA_SUFFIX = ".sha256";
    private static final String FRB_SOURCE =
            "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json";
    private static final RouteVerdictCalculator.Tier T2 = RouteVerdictCalculator.Tier.T2;
    @TempDir Path temp;

    @Test void gappedReserveIsInconclusiveInEveryFrbFieldAndReturnsToOwner() throws Exception {
        FrozenGateParameters parameters = parameters(FRB_SOURCE);
        var records = ReceiptGapDrawnReserveTest.cases(parameters);
        ReceiptGapDrawnReserveTest.set(records, PRIMARY_ID, ReceiptGapDrawnReserveTest.scored(0),
                ReceiptGapDrawnReserveTest.scored(1));
        var report = calculateFrb(parameters, records, 20);
        var workflow = report.workflows().get(WORKFLOW);
        assertThat(workflow.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(workflow.frb().reserveGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(workflow.frb().workflowGate()).isEqualTo(Tri.PASS_COMPLETE);
        assertThat(workflow.frb().pValue()).isEqualTo(ExactPairedTest.test(List.of(1)));
        assertThat(workflow.inconclusiveTiers()).contains(T2);
        assertThat(workflow.failedTiers()).doesNotContain(T2);
        assertThat(workflow.causes()).containsExactly(CAUSE);
        assertThat(workflow.frb().unresolvedCases()).isEqualTo(1);
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(report.inconclusiveTiers()).contains(T2);
        assertThat(report.failedTiers()).doesNotContain(T2);
        var classification = DeadlineRule.classifyFrb(report, new FrbClassificationInput(
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false,
                Set.of(), Map.of(), Map.of("safety", "0:head")));
        assertThat(classification.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(classification.causes()).contains(CAUSE);
        var luna = new RouteVerdictCalculator.RouteReport(RouteVerdictCalculator.Verdict.FAIL,
                Set.of(T2), Set.of(), 0, false, parameters.sha256, report.workflows(), report.frb());
        var comparison = new SolRule.Comparison(luna, report, -1000, Map.of(), Map.of(),
                Set.of(), Set.of(), true, true, true);
        assertThat(SolRule.justified(parameters, comparison)).isFalse();
    }

    @Test void independentlyFailingPrimaryStillFailsWorkflowAndRoute() throws Exception {
        FrozenGateParameters parameters = parameters(FRB_SOURCE);
        var records = ReceiptGapDrawnReserveTest.cases(parameters);
        for (int i = 0; i < 6; i++) ReceiptGapDrawnReserveTest.set(records, WORKFLOW + "-" + i,
                ReceiptGapDrawnReserveTest.scored(0), ReceiptGapDrawnReserveTest.scored(1));
        var report = calculateFrb(parameters, records, 0);
        var workflow = report.workflows().get(WORKFLOW);
        assertThat(workflow.frb().workflowGate()).isEqualTo(Tri.FAIL);
        assertThat(workflow.frb().reserveGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(workflow.failedTiers()).contains(T2);
        assertThat(workflow.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.FAIL);
        assertThat(report.failedTiers()).contains(T2);
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.FAIL);
    }

    @Test void legacyGappedReserveKeepsItsExistingInconclusiveResult() throws Exception {
        FrozenGateParameters parameters = parameters(GateTestFixture.SOURCE.toString());
        var records = ReceiptGapDrawnReserveTest.cases(parameters);
        ReceiptGapDrawnReserveTest.set(records, PRIMARY_ID, ReceiptGapDrawnReserveTest.scored(0),
                ReceiptGapDrawnReserveTest.scored(1));
        var reserve = ReceiptGapDrawnReserveTest.reserve(20, 1);
        var report = RouteVerdictCalculator.calculate(parameters, ROUTE,
                ReceiptGapDrawnReserveTest.frozen(parameters), records, Map.of(WORKFLOW, reserve),
                Map.of(ASK, true, WORKFLOW, true));
        assertThat(report.frb()).isNull();
        assertThat(report.workflows().get(WORKFLOW).frb()).isNull();
        assertThat(report.workflows().get(WORKFLOW).inconclusiveTiers()).contains(T2);
        assertThat(report.workflows().get(WORKFLOW).failedTiers()).doesNotContain(T2);
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    private FrozenGateParameters parameters(String source) throws Exception {
        Path original = Path.of(source);
        Path copy = temp.resolve(original.getFileName());
        Files.copy(original, copy);
        Files.copy(original.resolveSibling(original.getFileName() + SHA_SUFFIX),
                copy.resolveSibling(copy.getFileName() + SHA_SUFFIX));
        return FrozenGateParameters.load(copy);
    }

    private static RouteVerdictCalculator.RouteReport calculateFrb(FrozenGateParameters parameters,
            List<RouteVerdictCalculator.CaseRecord> records, int reserveCriticals) {
        List<RouteVerdictCalculator.OwnerDisposition> dispositions = new ArrayList<>();
        for (var record : records) if (record.workflow().equals(WORKFLOW)
                && record.candidate().criticalQuestions() > record.baseline().criticalQuestions())
            dispositions.add(acknowledged(record.id()));
        for (int i = 1; i <= reserveCriticals; i++) dispositions.add(acknowledged("reserve-" + i));
        return RouteVerdictCalculator.calculate(parameters, ROUTE,
                ReceiptGapDrawnReserveTest.frozen(parameters), records,
                Map.of(WORKFLOW, ReceiptGapDrawnReserveTest.reserve(reserveCriticals, 1)),
                Map.of(ASK, true, WORKFLOW, true), dispositions,
                ReceiptGapDrawnReserveTest.decisions(true), Map.of());
    }

    private static RouteVerdictCalculator.OwnerDisposition acknowledged(String id) {
        return new RouteVerdictCalculator.OwnerDisposition(WORKFLOW, id,
                RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION);
    }
}
