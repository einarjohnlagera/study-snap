package com.studysnap.backend.benchmark;

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

import static org.assertj.core.api.Assertions.assertThat;

/** Literal pre-change report values from the seven legacy paths. */
class LegacyGoldenTest {
    private static final String ROUTE = "CRITIQUE";
    private static final String WORKFLOW = "ASK_COMPANION";
    @TempDir Path temp;

    private FrozenGateParameters parameters() throws Exception {
        Path source = GateTestFixture.SOURCE;
        Path copy = temp.resolve("gates.json");
        Files.copy(source, copy);
        Files.copy(source.resolveSibling(source.getFileName() + ".sha256"),
                copy.resolveSibling(copy.getFileName() + ".sha256"));
        return FrozenGateParameters.load(copy);
    }

    private static RouteVerdictCalculator.ArmResult arm(boolean critical, String component) {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                component == null ? Set.of() : Set.of(component), critical, null, List.of(), false, false);
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

    private static List<RouteVerdictCalculator.CaseRecord> cases(FrozenGateParameters p) {
        List<RouteVerdictCalculator.CaseRecord> result = new ArrayList<>();
        frozen(p).forEach((workflow, ids) -> ids.forEach(id -> result.add(
                new RouteVerdictCalculator.CaseRecord(id, workflow, ROUTE, null, id, id,
                        arm(false, null), arm(false, null)))));
        return result;
    }

    private static void change(List<RouteVerdictCalculator.CaseRecord> cases, int index,
                               RouteVerdictCalculator.ArmResult baseline,
                               RouteVerdictCalculator.ArmResult candidate) {
        var old = cases.get(index);
        cases.set(index, new RouteVerdictCalculator.CaseRecord(old.id(), old.workflow(), old.route(),
                old.subgroup(), old.baselineCaseId(), old.candidateCaseId(), baseline, candidate));
    }

    private static List<RouteVerdictCalculator.ReserveCase> reserve(int critical) {
        List<RouteVerdictCalculator.ReserveCase> result = new ArrayList<>();
        for (int i = 0; i < 30; i++) result.add(new RouteVerdictCalculator.ReserveCase("reserve-" + i,
                RouteVerdictCalculator.Evidence.SCORED, RouteVerdictCalculator.Evidence.SCORED,
                false, i < critical));
        return result;
    }

    private static RouteVerdictCalculator.RouteReport calculate(FrozenGateParameters p,
            List<RouteVerdictCalculator.CaseRecord> cases,
            Map<String, List<RouteVerdictCalculator.ReserveCase>> reserve,
            Map<String, Boolean> suites,
            List<RouteVerdictCalculator.OwnerDisposition> dispositions) {
        return RouteVerdictCalculator.calculate(p, ROUTE, frozen(p), cases, reserve, suites, dispositions);
    }

    private static String values(RouteVerdictCalculator.RouteReport r) {
        var w = r.workflows().get(WORKFLOW);
        return List.of(r.verdict(), r.failedTiers(), r.inconclusiveTiers(), r.d(),
                r.isolatedCriticalIncident(), w.verdict(), w.failedTiers(), w.inconclusiveTiers(),
                w.paired(), w.d(), w.candidateOnlyT3(), w.baselineOnlyT3(), w.sharedT3(),
                w.candidateOnlyT2(), w.baselineOnlyT2(), w.sharedT2(), w.candidateOnlyT1(),
                w.baselineOnlyT1(), w.sharedT1(), w.reserveCandidateOnly(),
                w.isolatedCriticalIncident(), w.protocolDeviation(), w.missingPairs(),
                w.undisposedCriticalCaseIds(), w.candidateComponents(), w.baselineComponents(),
                w.subgroups(), r.undisposedCriticalCaseIds()).toString();
    }

    @Test void capturedLegacyValuesStayLiteral() throws Exception {
        var p = parameters();
        var suites = Map.of(WORKFLOW, true, "INTERVIEW_CRITIQUE", true);
        var all = cases(p);
        assertThat(values(calculate(p, all, Map.of(), suites, List.of()))).isEqualTo(
                "[PASS, [], [], 0, false, PASS, [], [], 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, 0, [], {}, {}, {}, {}]");
        change(all, 0, arm(false, null), arm(true, null));
        var disposition = new RouteVerdictCalculator.OwnerDisposition(WORKFLOW, all.get(0).id(),
                RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION);
        assertThat(values(calculate(p, all, Map.of(WORKFLOW, reserve(0)), suites, List.of(disposition))))
                .isEqualTo("[PASS, [], [], 0, true, PASS, [], [], 12, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, true, false, 0, [], {}, {}, {}, {}]");
        assertThat(values(calculate(p, all, Map.of(WORKFLOW, reserve(3)), suites, List.of(disposition))))
                .isEqualTo("[FAIL, [T2], [T2], 0, false, FAIL, [T2], [T2], 12, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 3, false, false, 0, [reserve-0, reserve-1, reserve-2], {}, {}, {}, {ASK_COMPANION=[reserve-0, reserve-1, reserve-2]}]");
        assertThat(values(calculate(p, all, Map.of(WORKFLOW, reserve(0)), suites, List.of())))
                .isEqualTo("[INCONCLUSIVE, [], [T2], 0, true, INCONCLUSIVE, [], [T2], 12, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, true, false, 0, [ASK_COMPANION-0], {}, {}, {}, {ASK_COMPANION=[ASK_COMPANION-0]}]");
        var incomplete = cases(p);
        incomplete.remove(0);
        assertThat(values(calculate(p, incomplete, Map.of(), suites, List.of())))
                .isEqualTo("[INCONCLUSIVE, [], [T1], 0, false, INCONCLUSIVE, [], [T1], 11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, 1, [], {}, {}, {}, {}]");
        var t3 = cases(p);
        for (int i = 0; i < 5; i++) change(t3, i, arm(false, null), arm(false, "QUALITY"));
        assertThat(values(calculate(p, t3, Map.of(), suites, List.of())))
                .isEqualTo("[FAIL, [T3], [], 5, false, PASS, [], [], 12, 5, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, 0, [], {QUALITY=5}, {}, {}, {}]");
        assertThat(values(calculate(p, cases(p), Map.of(), Map.of(WORKFLOW, false, "INTERVIEW_CRITIQUE", true), List.of())))
                .isEqualTo("[FAIL, [T1], [], 0, false, FAIL, [T1], [], 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, 0, [], {}, {}, {}, {}]");
    }
}
