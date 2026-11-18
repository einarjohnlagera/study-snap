package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic post-unlock FRB records only; no scorer, model, or pipeline. */
class RouteVerdictCalculatorFrbTest {
    private static final String INTERACTIVE = "INTERACTIVE_QUIZ";
    private static final String PAID = "PAID_INTERACTIVE";
    private static final String CRITIQUE = "CRITIQUE";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String PLAN = "PLAN_ADAPTIVE";
    private static final String TEACHER = "TEACHER_QUIZ";
    private static final String BOARD = "BOARD_EXAM";
    private static final String LONG = "LONG_EXAM";
    private static final String TIP = "STUDY_TIP";
    private static final String INTERVIEW = "INTERVIEW_GENERATION";
    private static final String COMPANION = "COLLECTION_COMPANION";
    private static final String ASK = "ASK_COMPANION";
    private static final String CRITIQUE_WORKFLOW = "INTERVIEW_CRITIQUE";
    private static final String SHIPPED_HASH = "c94ea33fbf79300c4b98a1f30e240c9f7bee2587efdcb573ecfe8dc85782b390";
    private static final String RESOURCE = "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json";
    private static final String RESERVE_PREFIX = "reserve-";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    private FrozenGateParameters copyRaw(String source) throws Exception {
        Path original = Path.of(source);
        Path copy = temp.resolve("gates.json");
        Files.copy(original, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(original.resolveSibling(original.getFileName() + ".sha256"),
                copy.resolveSibling(copy.getFileName() + ".sha256"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return FrozenGateParameters.load(copy);
    }

    private FrozenGateParameters frb() throws Exception { return copyRaw(RESOURCE); }

    private FrozenGateParameters variant(java.util.function.Consumer<ObjectNode> change) throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(Files.readAllBytes(Path.of(RESOURCE)));
        change.accept(root);
        byte[] bytes = JSON.writeValueAsBytes(root);
        Path copy = temp.resolve("variant.json");
        Files.write(copy, bytes);
        Files.writeString(copy.resolveSibling(copy.getFileName() + ".sha256"),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        return FrozenGateParameters.load(copy);
    }

    private static RouteVerdictCalculator.ArmResult arm(int critical, int unresolved) {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), critical > 0, null, List.of(), false, false, critical, unresolved);
    }
    private static RouteVerdictCalculator.ArmResult bad(RouteVerdictCalculator.Evidence evidence) {
        return new RouteVerdictCalculator.ArmResult(evidence, Set.of(), false,
                null, List.of(), false, false, 0, 0);
    }
    private static RouteVerdictCalculator.ArmResult zero() { return arm(0, 0); }
    private static RouteVerdictCalculator.ArmResult inconsistentArm() {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, null, List.of(), false, false, 1, 0);
    }

    private static Map<String, Set<String>> frozen(FrozenGateParameters p, String route) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String workflow : p.routes.get(route).workflows()) {
            Set<String> ids = new LinkedHashSet<>();
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) ids.add(workflow + "-" + i);
            result.put(workflow, ids);
        }
        return result;
    }

    private static List<RouteVerdictCalculator.CaseRecord> cases(FrozenGateParameters p, String route) {
        List<RouteVerdictCalculator.CaseRecord> result = new ArrayList<>();
        for (String workflow : p.routes.get(route).workflows()) {
            List<String> groups = new ArrayList<>();
            p.workflows.get(workflow).subgroups().forEach((name, count) -> {
                for (int i = 0; i < count; i++) groups.add(name);
            });
            int width = p.frbClassBWorkflows.contains(workflow) ? 1 : 4;
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) {
                String id = workflow + "-" + i;
                result.add(new RouteVerdictCalculator.CaseRecord(id, workflow, route,
                        groups.isEmpty() ? null : groups.get(i), id, id, zero(), zero(), width));
            }
        }
        return result;
    }

    private static void set(List<RouteVerdictCalculator.CaseRecord> records, String workflow, int ordinal,
                            RouteVerdictCalculator.ArmResult baseline,
                            RouteVerdictCalculator.ArmResult candidate) {
        int index = index(records, workflow, ordinal);
        var old = records.get(index);
        records.set(index, new RouteVerdictCalculator.CaseRecord(old.id(), old.workflow(), old.route(),
                old.subgroup(), old.baselineCaseId(), old.candidateCaseId(), baseline, candidate,
                old.requestedQuestions()));
    }
    private static int index(List<RouteVerdictCalculator.CaseRecord> records, String workflow, int ordinal) {
        for (int i = 0; i < records.size(); i++)
            if (records.get(i).id().equals(workflow + "-" + ordinal)) return i;
        throw new AssertionError(workflow + ordinal);
    }
    private static List<RouteVerdictCalculator.ReserveCase> reserve(int candidateOnly, int incomplete) {
        List<RouteVerdictCalculator.ReserveCase> result = new ArrayList<>();
        for (int i = 0; i < 30; i++) result.add(new RouteVerdictCalculator.ReserveCase(
                RESERVE_PREFIX + i, RouteVerdictCalculator.Evidence.SCORED,
                i < incomplete ? RouteVerdictCalculator.Evidence.MISSING : RouteVerdictCalculator.Evidence.SCORED,
                false, i >= incomplete && i < candidateOnly + incomplete));
        return result;
    }
    private static Map<String, Boolean> decisions(FrozenGateParameters p, String route) {
        Map<String, Boolean> result = new HashMap<>();
        for (String workflow : p.routes.get(route).workflows())
            if (p.frbClassBWorkflows.contains(workflow)) result.put(workflow, false);
        return result;
    }
    private static Map<String, Boolean> suites(FrozenGateParameters p, String route) {
        Map<String, Boolean> result = new HashMap<>();
        for (String workflow : p.routes.get(route).workflows()) result.put(workflow, true);
        return result;
    }
    private static List<RouteVerdictCalculator.OwnerDisposition> acknowledge(
            List<RouteVerdictCalculator.CaseRecord> records, Map<String, List<RouteVerdictCalculator.ReserveCase>> reserves) {
        List<RouteVerdictCalculator.OwnerDisposition> result = new ArrayList<>();
        for (var item : records) if (item.baseline() != null && item.candidate() != null
                && item.baseline().criticalQuestions() == 0 && item.candidate().criticalQuestions() > 0)
            result.add(disposition(item.workflow(), item.id(),
                    RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED));
        reserves.forEach((workflow, items) -> {
            for (var item : items) if (item.baselineEvidence() == RouteVerdictCalculator.Evidence.SCORED
                    && item.candidateEvidence() == RouteVerdictCalculator.Evidence.SCORED
                    && item.candidateCritical() && !item.baselineCritical())
                result.add(disposition(workflow, item.id(),
                        RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED));
        });
        return result;
    }
    private static RouteVerdictCalculator.OwnerDisposition disposition(String workflow, String id,
            RouteVerdictCalculator.DispositionValue value) {
        return new RouteVerdictCalculator.OwnerDisposition(workflow, id, value,
                value == RouteVerdictCalculator.DispositionValue.DEFECT_BLOCKS_ROUTE
                        ? RouteVerdictCalculator.DispositionReason.BLOCKING_CORRECTNESS
                        : RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION);
    }
    private static RouteVerdictCalculator.RouteReport run(FrozenGateParameters p, String route,
            List<RouteVerdictCalculator.CaseRecord> records,
            Map<String, List<RouteVerdictCalculator.ReserveCase>> reserves,
            Map<String, Boolean> decisions, Map<String, Integer> widths,
            List<RouteVerdictCalculator.OwnerDisposition> dispositions) {
        return RouteVerdictCalculator.calculate(p, route, frozen(p, route), records, reserves,
                suites(p, route), dispositions, decisions, widths);
    }
    private static RouteVerdictCalculator.RouteReport run(FrozenGateParameters p, String route,
            List<RouteVerdictCalculator.CaseRecord> records,
            Map<String, List<RouteVerdictCalculator.ReserveCase>> reserves,
            Map<String, Boolean> decisions) {
        return run(p, route, records, reserves, decisions, Map.of(), acknowledge(records, reserves));
    }

    @Test void rawShippedAndFrbFilesSelectOnlyTheDeclaredGateAndLegacyJsonOmitsFrb() throws Exception {
        Path shipped = GateTestFixture.SOURCE;
        assertThat(Files.readString(shipped.resolveSibling(shipped.getFileName() + ".sha256")).trim())
                .isEqualTo(SHIPPED_HASH);
        FrozenGateParameters old = copyRaw(shipped.toString());
        assertThat(old.sha256).isEqualTo(SHIPPED_HASH);
        assertThat(old.frbEnabled).isFalse();
        assertThat(old.frbClassBWorkflows).isEmpty();
        var report = RouteVerdictCalculator.calculate(old, CRITIQUE, frozen(old, CRITIQUE),
                cases(old, CRITIQUE), Map.of(), suites(old, CRITIQUE), List.of());
        assertThat(report.frb()).isNull();
        assertThat(report.workflows().get(ASK).frb()).isNull();
        JsonNode tree = JSON.valueToTree(report);
        assertThat(tree.has("frb")).isFalse();
        assertThat(tree.path("workflows").path(ASK).has("frb")).isFalse();
        assertThat(JSON.treeToValue(tree, RouteVerdictCalculator.RouteReport.class).frb()).isNull();
        FrozenGateParameters p = frb();
        assertThat(p.frbEnabled).isTrue();
        assertThat(p.frbRouteAlpha).isEqualTo(Rational.of(4, 100));
        assertThat(p.frbFamilyBudget).isEqualTo(Rational.of(4, 100));
        assertThat(p.frbClassBWorkflows).containsExactlyInAnyOrder(TIP, COMPANION, ASK, CRITIQUE_WORKFLOW);
        assertThat(p.declaredK(INTERACTIVE)).isEqualTo(6);
        assertThat(p.declaredK(PAID)).isEqualTo(2);
        assertThat(p.declaredK(CRITIQUE)).isEqualTo(2);
    }

    @Test void malformedFrbParameterBlocksAreRejected() throws Exception {
        List<java.util.function.Consumer<ObjectNode>> mutants = List.of(
                root -> ((ObjectNode) root.path("frb")).remove("routeAlpha"),
                root -> ((ObjectNode) root.path("frb")).put("unexpected", 1),
                root -> ((ObjectNode) root.path("frb").path("routeAlpha")).put("numerator", 0),
                root -> ((ObjectNode) root.path("frb").path("familyBudget")).put("denominator", 0),
                root -> ((ObjectNode) root.path("frb").path("routeAlpha")).put("numerator", 101),
                root -> ((ObjectNode) root.path("frb").path("routeAlpha")).put("extra", 1),
                root -> ((com.fasterxml.jackson.databind.node.ArrayNode) root.path("frb")
                        .path("classBWorkflows")).add("UNDECLARED"),
                root -> ((ObjectNode) root.path("frb")).put("reserveSize", 29),
                root -> { root.put("reserveSize", 29); ((ObjectNode) root.path("frb")).put("reserveSize", 29); },
                root -> root.put("frb", "wrong type"));
        for (var mutant : mutants)
            assertThatThrownBy(() -> variant(mutant))
                    .isInstanceOf(FrozenGateParameters.InvalidParameters.class);
    }

    @Test void allThreeRoutesPassWithZeroDifferencesAndReportDeclaredK() throws Exception {
        FrozenGateParameters p = frb();
        for (String route : List.of(INTERACTIVE, PAID, CRITIQUE)) {
            var report = run(p, route, cases(p, route), Map.of(), decisions(p, route));
            assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
            assertThat(report.frb().routeGate()).isEqualTo(Tri.PASS_COMPLETE);
            assertThat(report.frb().declaredK()).isEqualTo(p.declaredK(route));
            assertThat(report.frb().alpha()).isEqualTo("4/100");
            assertThat(report.frb().familyBudget()).isEqualTo("4/100");
            assertThat(report.workflows().values()).allSatisfy(w ->
                    assertThat(w.frb().workflowGate()).isEqualTo(Tri.PASS_COMPLETE));
            assertThat(JSON.treeToValue(JSON.valueToTree(report), RouteVerdictCalculator.RouteReport.class).frb())
                    .isEqualTo(report.frb());
        }
    }

    @Test void pooledOnlyFailureAndPooledAlphaBracketUseDeclaredWorkflows() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, INTERACTIVE);
        for (String workflow : List.of(NOTE, PLAN, TEACHER, BOARD, LONG))
            set(records, workflow, 0, zero(), arm(1, 0));
        var report = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE));
        assertThat(report.frb().routeGate()).isEqualTo(Tri.FAIL);
        assertThat(report.failedTiers()).contains(RouteVerdictCalculator.Tier.T2);
        assertThat(report.workflows().values()).allSatisfy(w ->
                assertThat(w.frb().workflowGate()).isEqualTo(Tri.PASS_COMPLETE));
        set(records, LONG, 0, zero(), zero());
        assertThat(run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE)).frb().routeGate())
                .isEqualTo(Tri.PASS_COMPLETE);
    }

    @Test void classAWorkflowUsesFamilyBudgetOverSixAndCountsAboveOne() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, INTERACTIVE);
        for (int i = 0; i < 8; i++) set(records, NOTE, i, zero(), arm(1, 0));
        var eight = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE));
        assertThat(eight.workflows().get(NOTE).frb().workflowGate()).isEqualTo(Tri.FAIL);
        assertThat(eight.workflows().get(NOTE).failedTiers()).contains(RouteVerdictCalculator.Tier.T2);
        set(records, NOTE, 7, zero(), zero());
        var seven = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE));
        assertThat(seven.workflows().get(NOTE).frb().workflowGate()).isEqualTo(Tri.PASS_COMPLETE);
        var counts = cases(p, INTERACTIVE);
        set(counts, NOTE, 0, arm(0, 0), arm(3, 0));
        set(counts, NOTE, 1, arm(2, 0), zero());
        var counted = run(p, INTERACTIVE, counts, Map.of(), decisions(p, INTERACTIVE));
        assertThat(counted.workflows().get(NOTE).frb().pValue())
                .isEqualTo(ExactPairedTest.test(List.of(3, -2)));
    }

    @Test void paidAndCritiqueWorkflowThresholdsBracketSixAndFive() throws Exception {
        FrozenGateParameters p = frb();
        var paid = cases(p, PAID);
        for (int i = 0; i < 6; i++) set(paid, INTERVIEW, i, zero(), arm(1, 0));
        assertThat(run(p, PAID, paid, Map.of(), decisions(p, PAID))
                .workflows().get(INTERVIEW).frb().workflowGate()).isEqualTo(Tri.FAIL);
        set(paid, INTERVIEW, 5, zero(), zero());
        assertThat(run(p, PAID, paid, Map.of(), decisions(p, PAID))
                .workflows().get(INTERVIEW).frb().workflowGate()).isEqualTo(Tri.PASS_COMPLETE);
        var critique = cases(p, CRITIQUE);
        for (int i = 0; i < 6; i++) set(critique, ASK, i, zero(), arm(1, 0));
        var draw = decisions(p, CRITIQUE);
        draw.put(ASK, true);
        var reserves = Map.of(ASK, reserve(0, 0));
        assertThat(run(p, CRITIQUE, critique, reserves, draw)
                .workflows().get(ASK).frb().workflowGate()).isEqualTo(Tri.FAIL);
        set(critique, ASK, 5, zero(), zero());
        assertThat(run(p, CRITIQUE, critique, reserves, draw)
                .workflows().get(ASK).frb().workflowGate()).isEqualTo(Tri.PASS_COMPLETE);
    }

    @Test void classBPrimaryAndReserveOnlyFailuresAreSeparate() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, CRITIQUE);
        var draw = decisions(p, CRITIQUE);
        draw.put(ASK, true);
        var cleanReserve = Map.of(ASK, reserve(0, 0));
        for (int i = 0; i < 6; i++) set(records, ASK, i, zero(), arm(1, 0));
        var primary = run(p, CRITIQUE, records, cleanReserve, draw);
        assertThat(primary.workflows().get(ASK).frb().workflowGate()).isEqualTo(Tri.FAIL);
        assertThat(primary.workflows().get(ASK).frb().reserveGate()).isEqualTo(Tri.FAIL);
        for (int i = 1; i < 6; i++) set(records, ASK, i, zero(), zero());
        var reserveOnly = Map.of(ASK, reserve(12, 0));
        var retest = run(p, CRITIQUE, records, reserveOnly, draw);
        assertThat(retest.frb().routeGate()).isEqualTo(Tri.PASS_COMPLETE);
        assertThat(retest.workflows().get(ASK).frb().reserveGate()).isEqualTo(Tri.FAIL);
        assertThat(retest.workflows().get(ASK).frb().workflowGate()).isEqualTo(Tri.PASS_COMPLETE);
        assertThat(retest.workflows().get(ASK).frb().pValue().p()).isEqualTo(0.5);
    }

    @Test void originalRouteT2DecideStillShortCircuitsAfterPrimaryFailure() {
        List<Integer> known = new ArrayList<>(java.util.Collections.nCopies(6, 1));
        List<ClassBReserve.PairCritical> pairs = new ArrayList<>();
        for (int i = 0; i < 6; i++) pairs.add(new ClassBReserve.PairCritical(1, 0));
        List<Integer> reserve = new ArrayList<>(java.util.Collections.nCopies(30, 0));
        reserve.set(0, null);
        var workflow = new RouteT2.WorkflowInput(ASK, true, known, pairs, List.of(), true, reserve);
        var input = new RouteT2.RouteInput(1, Rational.of(4, 100), Rational.of(4, 100), List.of(workflow));
        assertThat(RouteT2.decide(input)).isEqualTo(Tri.FAIL);
    }

    @Test void declaredKDoesNotShrinkWhenAWorkflowHasNoRecords() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, INTERACTIVE);
        records.removeIf(item -> item.workflow().equals(TIP));
        for (int i = 0; i < 7; i++) set(records, NOTE, i, zero(), arm(1, 0));
        var report = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE));
        assertThat(report.frb().declaredK()).isEqualTo(6);
        assertThat(report.workflows().get(NOTE).frb().workflowGate()).isEqualTo(Tri.PASS_COMPLETE);
        assertThat(report.workflows().get(TIP).frb().workflowGate()).isEqualTo(Tri.INCONCLUSIVE);
    }

    @Test void symmetricBaselineWorsePairTriggersReserveAndNeverSetsLegacyFlags() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, CRITIQUE);
        set(records, ASK, 0, arm(1, 0), zero());
        var draw = decisions(p, CRITIQUE);
        draw.put(ASK, true);
        var report = run(p, CRITIQUE, records, Map.of(ASK, reserve(0, 0)), draw);
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(report.workflows().get(ASK).frb().reserveDrawn()).isTrue();
        assertThat(report.workflows().get(ASK).protocolDeviation()).isFalse();
        assertThat(report.workflows().get(ASK).isolatedCriticalIncident()).isFalse();
    }

    @Test void scoredButUnresolvedQuestionsAndMissingCaseAreNotZeroDifferences() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, INTERACTIVE);
        set(records, NOTE, 0, arm(0, 1), zero());
        var first = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE));
        assertThat(first.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(first.frb().routeGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(first.workflows().get(NOTE).frb().workflowGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(first.workflows().get(NOTE).frb().unresolvedCases()).isEqualTo(1);
        assertThat(first.workflows().get(NOTE).frb().pValue())
                .isEqualTo(ExactPairedTest.test(List.of(-1)));
        assertThat(first.inconclusiveTiers()).contains(RouteVerdictCalculator.Tier.T2);
        assertThat(first.workflows().get(NOTE).inconclusiveTiers()).contains(RouteVerdictCalculator.Tier.T2);
        set(records, NOTE, 0, zero(), arm(0, 1));
        var candidateUnresolved = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE));
        assertThat(candidateUnresolved.workflows().get(NOTE).frb().workflowGate())
                .isEqualTo(Tri.INCONCLUSIVE);
        assertThat(candidateUnresolved.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        int missingIndex = index(records, NOTE, 0);
        records.remove(missingIndex);
        var missing = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE),
                Map.of(NOTE + "-0", 4), acknowledge(records, Map.of()));
        assertThat(missing.frb().routeGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(missing.workflows().get(NOTE).frb().unresolvedCases()).isEqualTo(1);
        assertThat(missing.workflows().get(NOTE).frb().pValue())
                .isEqualTo(ExactPairedTest.test(List.of(-4)));
    }

    @Test void missingCaseUsesRequestedWidthInTheConservativeGate() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, INTERACTIVE);
        for (int i = 0; i < 10; i++) set(records, NOTE, i, zero(), arm(1, 0));
        records.remove(index(records, NOTE, 10));
        var report = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE),
                Map.of(NOTE + "-10", 4), acknowledge(records, Map.of()));
        assertThat(report.workflows().get(NOTE).frb().workflowGate()).isEqualTo(Tri.INCONCLUSIVE);
        List<Integer> lowest = new ArrayList<>(java.util.Collections.nCopies(10, 1));
        lowest.add(-4);
        assertThat(report.workflows().get(NOTE).frb().pValue()).isEqualTo(ExactPairedTest.test(lowest));
    }

    @Test void definiteLowestCompletionRejectsAndPooledInconclusiveCannotPass() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, INTERACTIVE);
        for (int i = 0; i < 18; i++) set(records, NOTE, i, zero(), arm(1, 0));
        set(records, PLAN, 0, arm(0, 1), zero());
        var report = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE));
        assertThat(report.frb().routeGate()).isEqualTo(Tri.FAIL);
        assertThat(report.failedTiers()).contains(RouteVerdictCalculator.Tier.T2);
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.FAIL);
        var pooled = cases(p, INTERACTIVE);
        set(pooled, NOTE, 0, arm(0, 1), zero());
        assertThat(run(p, INTERACTIVE, pooled, Map.of(), decisions(p, INTERACTIVE)).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void confirmedT1IsUnresolvedAndRecoveredArmUsesFirstSuccessfulCounts() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, INTERACTIVE);
        var repeated = new RouteVerdictCalculator.Recheck(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, "PARSE", 0, 0);
        var t1 = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.UNSCORABLE,
                Set.of(), false, "PARSE", List.of(repeated, repeated), false, false, 0, 0);
        set(records, NOTE, 0, arm(2, 0), t1);
        var failed = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE));
        assertThat(failed.workflows().get(NOTE).failedTiers()).contains(RouteVerdictCalculator.Tier.T1);
        assertThat(failed.workflows().get(NOTE).frb().workflowGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(failed.workflows().get(NOTE).frb().pValue())
                .isEqualTo(ExactPairedTest.test(List.of(-2)));
        assertThat(failed.frb().routeGate()).isEqualTo(Tri.INCONCLUSIVE);
        var first = new RouteVerdictCalculator.Recheck(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), true, null, 3, 0);
        var second = new RouteVerdictCalculator.Recheck(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, null, 0, 0);
        var recovered = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, "PARSE", List.of(first, second), false, false, 0, 0);
        set(records, NOTE, 0, zero(), recovered);
        var report = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE));
        assertThat(report.workflows().get(NOTE).frb().pValue())
                .isEqualTo(ExactPairedTest.test(List.of(3)));
        assertThat(report.workflows().get(NOTE).candidateOnlyT2()).isEqualTo(1);
    }

    @Test void missingAndConflictingWidthsAreNamedCaseErrors() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, INTERACTIVE);
        var noReserves = Map.<String, List<RouteVerdictCalculator.ReserveCase>>of();
        var recorded = decisions(p, INTERACTIVE);
        var noDispositions = List.<RouteVerdictCalculator.OwnerDisposition>of();
        int at = index(records, NOTE, 0);
        var old = records.get(at);
        records.set(at, new RouteVerdictCalculator.CaseRecord(old.id(), old.workflow(), old.route(),
                old.subgroup(), old.id(), old.id(), old.baseline(), old.candidate(), 0));
        assertThatThrownBy(() -> run(p, INTERACTIVE, records, noReserves, recorded))
                .isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
        var suppliedThree = Map.of(old.id(), 3);
        assertThat(run(p, INTERACTIVE, records, noReserves, recorded,
                suppliedThree, noDispositions).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        records.set(at, old);
        assertThatThrownBy(() -> run(p, INTERACTIVE, records, noReserves, recorded,
                suppliedThree, noDispositions))
                .isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
    }

    @Test void reserveIncompleteIsInconclusiveEvenWhenLowestCompletionRejects() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, CRITIQUE);
        set(records, ASK, 0, zero(), arm(1, 0));
        var draw = decisions(p, CRITIQUE);
        draw.put(ASK, true);
        var incomplete = Map.of(ASK, reserve(0, 1));
        var first = run(p, CRITIQUE, records, incomplete, draw);
        assertThat(first.workflows().get(ASK).frb().reserveGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(first.workflows().get(ASK).frb().workflowGate()).isEqualTo(Tri.PASS_COMPLETE);
        assertThat(first.workflows().get(ASK).frb().unresolvedCases()).isEqualTo(1);
        assertThat(first.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        var strong = Map.of(ASK, reserve(20, 1));
        var second = run(p, CRITIQUE, records, strong, draw);
        assertThat(second.workflows().get(ASK).frb().reserveGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(second.workflows().get(ASK).failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T2);
        assertThat(second.workflows().get(ASK).inconclusiveTiers()).contains(RouteVerdictCalculator.Tier.T2);
        assertThat(second.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void reserveDecisionAndSizeProtocolErrorsPropagate() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, CRITIQUE);
        var noReserves = Map.<String, List<RouteVerdictCalculator.ReserveCase>>of();
        var none = decisions(p, CRITIQUE);
        none.remove(ASK);
        assertThatThrownBy(() -> run(p, CRITIQUE, records, noReserves, none))
                .isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
        set(records, ASK, 0, zero(), arm(1, 0));
        assertThatThrownBy(() -> run(p, CRITIQUE, records, noReserves, none))
                .isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
        set(records, ASK, 0, zero(), zero());
        var falseTrigger = decisions(p, CRITIQUE);
        falseTrigger.put(ASK, true);
        var cleanReserve = Map.of(ASK, reserve(0, 0));
        assertThatThrownBy(() -> run(p, CRITIQUE, records, cleanReserve, falseTrigger))
                .isInstanceOf(InvalidReserve.class);
        set(records, ASK, 0, zero(), arm(1, 0));
        var missedTrigger = decisions(p, CRITIQUE);
        assertThatThrownBy(() -> run(p, CRITIQUE, records, noReserves, missedTrigger))
                .isInstanceOf(InvalidReserve.class);
        var drawn = decisions(p, CRITIQUE);
        drawn.put(ASK, true);
        var shortReserve = reserve(0, 0);
        shortReserve.remove(0);
        var shortReserveMap = Map.of(ASK, shortReserve);
        assertThatThrownBy(() -> run(p, CRITIQUE, records, shortReserveMap, drawn))
                .isInstanceOf(InvalidReserve.class);
        var longReserve = reserve(0, 0);
        longReserve.add(new RouteVerdictCalculator.ReserveCase("extra",
                RouteVerdictCalculator.Evidence.SCORED, RouteVerdictCalculator.Evidence.SCORED,
                false, false));
        var longReserveMap = Map.of(ASK, longReserve);
        assertThatThrownBy(() -> run(p, CRITIQUE, records, longReserveMap, drawn))
                .isInstanceOf(InvalidReserve.class);
    }

    @Test void classAReserveOrDecisionIsNamedReserveErrorAndLegacyBlockIsSkipped() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, INTERACTIVE);
        var decisions = decisions(p, INTERACTIVE);
        var noReserves = Map.<String, List<RouteVerdictCalculator.ReserveCase>>of();
        decisions.put(NOTE, false);
        assertThatThrownBy(() -> run(p, INTERACTIVE, records, noReserves, decisions))
                .isInstanceOf(InvalidReserve.class);
        decisions.remove(NOTE);
        var badReserve = Map.of(NOTE, reserve(0, 0));
        assertThatThrownBy(() -> run(p, INTERACTIVE, records, badReserve, decisions))
                .isInstanceOf(InvalidReserve.class);
        set(records, NOTE, 0, zero(), arm(1, 0));
        var clean = run(p, INTERACTIVE, records, Map.of(), decisions);
        assertThat(clean.workflows().get(NOTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(clean.workflows().get(NOTE).inconclusiveTiers()).doesNotContain(RouteVerdictCalculator.Tier.T2);
        assertThat(clean.workflows().get(NOTE).isolatedCriticalIncident()).isFalse();
        assertThat(clean.workflows().get(NOTE).protocolDeviation()).isFalse();
    }

    @Test void ownerDispositionAndUndisposedReserveCasesStillApply() throws Exception {
        FrozenGateParameters p = frb();
        var records = cases(p, INTERACTIVE);
        set(records, NOTE, 0, zero(), arm(1, 0));
        var blocked = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE), Map.of(),
                List.of(disposition(NOTE, NOTE + "-0",
                        RouteVerdictCalculator.DispositionValue.DEFECT_BLOCKS_ROUTE)));
        assertThat(blocked.workflows().get(NOTE).failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T2);
        assertThat(blocked.workflows().get(NOTE).ownerVetoCaseIds()).contains(NOTE + "-0");
        var undisposed = run(p, INTERACTIVE, records, Map.of(), decisions(p, INTERACTIVE),
                Map.of(), List.of());
        assertThat(undisposed.workflows().get(NOTE).undisposedCriticalCaseIds()).contains(NOTE + "-0");
        assertThat(undisposed.workflows().get(NOTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        var critique = cases(p, CRITIQUE);
        set(critique, ASK, 0, zero(), arm(1, 0));
        var draw = decisions(p, CRITIQUE);
        draw.put(ASK, true);
        var reserve = Map.of(ASK, reserve(1, 0));
        var report = run(p, CRITIQUE, critique, reserve, draw, Map.of(),
                List.of(disposition(ASK, ASK + "-0",
                        RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED)));
        assertThat(report.workflows().get(ASK).undisposedCriticalCaseIds()).contains("reserve-0");
        assertThat(report.workflows().get(ASK).reserveCandidateOnly()).isEqualTo(1);
        assertThat(report.workflows().get(ASK).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void countValidationPreventsPairCriticalInvalidInput() throws Exception {
        FrozenGateParameters p = frb();
        assertThatThrownBy(() -> arm(-1, 0)).isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
        assertThatThrownBy(() -> inconsistentArm()).isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
        var records = cases(p, CRITIQUE);
        var noReserves = Map.<String, List<RouteVerdictCalculator.ReserveCase>>of();
        var recorded = decisions(p, CRITIQUE);
        set(records, ASK, 0, zero(), arm(2, 0));
        assertThatThrownBy(() -> run(p, CRITIQUE, records, noReserves, recorded))
                .isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
        set(records, ASK, 0, zero(), arm(1, 1));
        assertThatThrownBy(() -> run(p, CRITIQUE, records, noReserves, recorded))
                .isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
        var old = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), true, null, List.of(), false, false);
        assertThat(old.criticalQuestions()).isEqualTo(1);
        assertThat(old.unresolvedQuestions()).isZero();
        var oldRecheck = new RouteVerdictCalculator.Recheck(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), true, null);
        assertThat(oldRecheck.criticalQuestions()).isEqualTo(1);
    }

    @Test void t1AndT3ContributionsMatchLegacyWhenT2DoesNotDecide() throws Exception {
        FrozenGateParameters p = frb();
        FrozenGateParameters old = copyRaw(GateTestFixture.SOURCE.toString());
        var records = cases(p, CRITIQUE);
        var repeated = new RouteVerdictCalculator.Recheck(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, "PARSE", 0, 0);
        var bad = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.UNSCORABLE,
                Set.of(), false, "PARSE", List.of(repeated, repeated), false, false, 0, 0);
        set(records, ASK, 0, zero(), bad);
        for (int i = 1; i < 6; i++) set(records, ASK, i, zero(),
                new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                        Set.of("QUALITY"), false, null, List.of(), false, false));
        var frbReport = run(p, CRITIQUE, records, Map.of(), decisions(p, CRITIQUE));
        var oldReport = RouteVerdictCalculator.calculate(old, CRITIQUE, frozen(old, CRITIQUE), records,
                Map.of(), suites(old, CRITIQUE), List.of());
        assertThat(frbReport.workflows().get(ASK).candidateOnlyT3())
                .isEqualTo(oldReport.workflows().get(ASK).candidateOnlyT3());
        assertThat(frbReport.workflows().get(ASK).failedTiers()).contains(RouteVerdictCalculator.Tier.T1);
        assertThat(oldReport.workflows().get(ASK).failedTiers()).contains(RouteVerdictCalculator.Tier.T1);
        assertThat(frbReport.d()).isEqualTo(oldReport.d());
        assertThat(frbReport.failedTiers()).contains(RouteVerdictCalculator.Tier.T3);
        assertThat(oldReport.failedTiers()).contains(RouteVerdictCalculator.Tier.T3);
    }
}
