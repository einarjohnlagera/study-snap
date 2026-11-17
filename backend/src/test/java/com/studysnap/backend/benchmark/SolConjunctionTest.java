package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SolConjunctionTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String RESOURCE = "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json";
    @TempDir Path temp;

    private FrozenGateParameters parameters() throws Exception {
        Path source = Path.of(RESOURCE);
        Path copy = temp.resolve("gates.json");
        Files.copy(source, copy);
        Files.copy(source.resolveSibling(source.getFileName() + ".sha256"),
                copy.resolveSibling(copy.getFileName() + ".sha256"));
        return FrozenGateParameters.load(copy);
    }

    private static RouteVerdictCalculator.ArmResult arm(int critical) {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), critical > 0, null, List.of(), false, false, critical, 0);
    }

    private static Map<String, Set<String>> frozen(FrozenGateParameters p) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String workflow : p.routes.get(ROUTE).workflows()) {
            Set<String> ids = new LinkedHashSet<>();
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) ids.add(workflow + "-" + i);
            result.put(workflow, ids);
        }
        return result;
    }

    private static List<RouteVerdictCalculator.CaseRecord> cases(FrozenGateParameters p, boolean workflowGate) {
        List<RouteVerdictCalculator.CaseRecord> result = new ArrayList<>();
        for (String workflow : p.routes.get(ROUTE).workflows()) {
            List<String> groups = new ArrayList<>();
            p.workflows.get(workflow).subgroups().forEach((name, count) -> {
                for (int i = 0; i < count; i++) groups.add(name);
            });
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) {
                String id = workflow + "-" + i;
                int critical = workflowGate ? workflow.equals(NOTE) && i < 8 ? 1 : 0
                        : i == 0 && !workflow.equals("STUDY_TIP") ? 1 : 0;
                result.add(new RouteVerdictCalculator.CaseRecord(id, workflow, ROUTE,
                        groups.isEmpty() ? null : groups.get(i), id, id, arm(0), arm(critical),
                        p.frbClassBWorkflows.contains(workflow) ? 1 : 4));
            }
        }
        return result;
    }

    private static RouteVerdictCalculator.RouteReport report(FrozenGateParameters p, boolean workflowGate,
                                                              boolean failing) {
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, workflowGate);
        if (!failing) records = cases(p, false).stream().map(row ->
                new RouteVerdictCalculator.CaseRecord(row.id(), row.workflow(), row.route(), row.subgroup(),
                        row.baselineCaseId(), row.candidateCaseId(), arm(0), arm(0), row.requestedQuestions())).toList();
        Map<String, Boolean> suites = new LinkedHashMap<>();
        Map<String, Boolean> decisions = new LinkedHashMap<>();
        for (String workflow : p.routes.get(ROUTE).workflows()) {
            suites.put(workflow, true);
            if (p.frbClassBWorkflows.contains(workflow)) decisions.put(workflow, false);
        }
        List<RouteVerdictCalculator.OwnerDisposition> dispositions = new ArrayList<>();
        for (var row : records) if (row.candidate().criticalQuestions() > 0)
            dispositions.add(new RouteVerdictCalculator.OwnerDisposition(row.workflow(), row.id(),
                    RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                    RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION));
        return RouteVerdictCalculator.calculate(p, ROUTE, frozen(p), records, Map.of(), suites,
                dispositions, decisions, Map.of());
    }

    private static Map<String, Integer> counts(FrozenGateParameters p, int ones) {
        Map<String, Integer> result = new LinkedHashMap<>();
        int index = 0;
        for (String id : frozen(p).values().stream().flatMap(Set::stream).sorted().toList())
            result.put(id, index++ < ones ? 1 : 0);
        return result;
    }

    private static SolRule.Comparison comparison(RouteVerdictCalculator.RouteReport luna,
            RouteVerdictCalculator.RouteReport sol, int d, Map<String, Integer> lunaLow,
            Map<String, Integer> solHigh) {
        return new SolRule.Comparison(luna, sol, d, Map.of(), Map.of(), Set.of(), Set.of(),
                true, true, true, lunaLow, solHigh);
    }

    @Test void pooledOnlyBlindSpotBeforeFix() throws Exception {
        FrozenGateParameters p = parameters();
        var luna = report(p, false, true);
        var sol = report(p, false, false);
        assertThat(luna.failedTiers()).contains(RouteVerdictCalculator.Tier.T2);
        assertThat(luna.workflows().values()).allSatisfy(w ->
                assertThat(w.failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T2));
        assertThat(SolRule.justified(p, comparison(luna, sol, 0, null, null))).isFalse();
    }

    @Test void workflowGateBeforeFixRequiresReserveMap() throws Exception {
        FrozenGateParameters p = parameters();
        var luna = report(p, true, true);
        var sol = report(p, false, false);
        assertThat(luna.workflows().get(NOTE).failedTiers()).contains(RouteVerdictCalculator.Tier.T2);
        assertThat(SolRule.justified(p, new SolRule.Comparison(luna, sol, 0, Map.of(), Map.of(),
                Set.of(), Set.of(), true, true, true))).isFalse();
    }

    @Test void pooledAndWorkflowFailuresUseTheSameRouteConjunction() throws Exception {
        FrozenGateParameters p = parameters();
        var sol = report(p, false, false);
        var lunaLow = counts(p, 20);
        var solHigh = counts(p, 14);
        for (boolean workflowGate : List.of(false, true)) {
            var luna = report(p, workflowGate, true);
            assertThat(SolRule.justified(p, comparison(luna, sol, 0, lunaLow, solHigh))).isTrue();
            assertThat(SolRule.justified(p, comparison(luna, sol, 0, counts(p, 30), counts(p, 25))))
                    .isFalse();
        }
    }

    @Test void exactPAndThirtyPercentEachBindAloneAndBoundariesPass() throws Exception {
        FrozenGateParameters p = parameters();
        var luna = report(p, false, true);
        var sol = report(p, false, false);
        Map<String, Integer> concentrated = counts(p, 0);
        concentrated.put(concentrated.keySet().iterator().next(), 10);
        assertThat(SolRule.justified(p, comparison(luna, sol, 0, concentrated, counts(p, 0))))
                .isFalse();
        assertThat(SolRule.justified(p, comparison(luna, sol, 0, counts(p, 30), counts(p, 25))))
                .isFalse();
        assertThat(SolRule.justified(p, comparison(luna, sol, 0, counts(p, 20), counts(p, 14))))
                .isTrue();
        assertThat(SolT2Improvement.evaluate(List.of(0, 0, 0), List.of(1, 1, 1)).threeFewerOk())
                .isTrue();
        // With integer paired counts, a total gain of two cannot itself reach p <= .05.
        assertThat(SolT2Improvement.evaluate(List.of(0, 0, 1), List.of(1, 1, 1)).threeFewerOk())
                .isFalse();
    }

    @Test void missingDifferentAndUnevaluableMapsFailClosed() throws Exception {
        FrozenGateParameters p = parameters();
        var luna = report(p, false, true);
        var sol = report(p, false, false);
        Map<String, Integer> good = counts(p, 20);
        assertThat(SolRule.justified(p, comparison(luna, sol, 0, null, counts(p, 14)))).isFalse();
        assertThat(SolRule.justified(p, comparison(luna, sol, 0, good, null))).isFalse();
        assertThat(SolRule.justified(p, comparison(luna, sol, 0, Map.of(), Map.of()))).isFalse();
        Map<String, Integer> different = new LinkedHashMap<>(counts(p, 14));
        different.remove(different.keySet().iterator().next());
        assertThat(SolRule.justified(p, comparison(luna, sol, 0, good, different))).isFalse();
        Map<String, Integer> extraKey = new LinkedHashMap<>(counts(p, 0));
        extraKey.put("extra-case", 0);
        assertThat(SolRule.justified(p, comparison(luna, sol, 0, counts(p, 20), extraKey))).isFalse();
        Map<String, Integer> invalid = new LinkedHashMap<>(counts(p, 14));
        invalid.put(invalid.keySet().iterator().next(), -1);
        assertThat(SolRule.justified(p, comparison(luna, sol, 0, good, invalid))).isFalse();
    }

    @Test void leastFavourableBoundsCanRejectAnApparentPointImprovement() throws Exception {
        FrozenGateParameters p = parameters();
        var luna = report(p, false, true);
        var sol = report(p, false, false);
        assertThat(SolRule.justified(p, comparison(luna, sol, 0, counts(p, 5), counts(p, 0)))).isTrue();
        assertThat(SolRule.justified(p, comparison(luna, sol, 0, counts(p, 4), counts(p, 1)))).isFalse();
    }

    @Test void t3AndT1OnlyFailuresDoNotRequireConjunction() throws Exception {
        FrozenGateParameters p = parameters();
        var actual = report(p, false, true);
        var sol = report(p, false, false);
        for (var tier : List.of(RouteVerdictCalculator.Tier.T3, RouteVerdictCalculator.Tier.T1)) {
            var luna = new RouteVerdictCalculator.RouteReport(RouteVerdictCalculator.Verdict.FAIL,
                    Set.of(tier), Set.of(), actual.d(), false, p.sha256, actual.workflows(), actual.frb());
            int d = tier == RouteVerdictCalculator.Tier.T3 ? -p.solT3Improvement : 0;
            SolRule.Comparison input = tier == RouteVerdictCalculator.Tier.T1
                    ? new SolRule.Comparison(luna, sol, d, Map.of(), Map.of(), Set.of(NOTE), Set.of(),
                            true, true, true, null, null)
                    : comparison(luna, sol, d, null, null);
            assertThat(SolRule.justified(p, input)).isTrue();
            if (tier == RouteVerdictCalculator.Tier.T3)
                assertThat(SolRule.justified(p, comparison(luna, sol, d + 1, null, null))).isFalse();
        }
    }
}
