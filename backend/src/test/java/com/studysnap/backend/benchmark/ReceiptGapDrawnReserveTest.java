package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Receipt-side gaps after the blind reserve decision; no scorer or pipeline involved. */
class ReceiptGapDrawnReserveTest {
    private static final String ROUTE = "CRITIQUE";
    private static final String ASK = "ASK_COMPANION";
    private static final String INTERVIEW = "INTERVIEW_CRITIQUE";
    private static final String CASE_ID = INTERVIEW + "-0";
    private static final String CAUSE = "DRAWN_RESERVE_WITH_UNRESOLVED_PRIMARY";
    private static final String FAILURE = "SCHEMA_OR_PARSE";
    private static final String RESOURCE =
            "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    private FrozenGateParameters parameters() throws Exception {
        Path source = Path.of(RESOURCE);
        Path copy = temp.resolve("parameters.json");
        Files.copy(source, copy);
        Files.copy(source.resolveSibling(source.getFileName() + ".sha256"),
                copy.resolveSibling(copy.getFileName() + ".sha256"));
        return FrozenGateParameters.load(copy);
    }

    static RouteVerdictCalculator.ArmResult scored(int critical) {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), critical > 0, null, List.of(), false, false, critical, 0);
    }

    static RouteVerdictCalculator.ArmResult evidence(RouteVerdictCalculator.Evidence value) {
        return new RouteVerdictCalculator.ArmResult(value, Set.of(), false,
                null, List.of(), false, false);
    }

    private static RouteVerdictCalculator.ArmResult rechecking(int checks, boolean complete) {
        List<RouteVerdictCalculator.Recheck> rechecks = new ArrayList<>();
        for (int i = 0; i < checks; i++) rechecks.add(new RouteVerdictCalculator.Recheck(
                RouteVerdictCalculator.Evidence.SCORED, Set.of(), false,
                complete ? FAILURE : null));
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.UNSCORABLE,
                Set.of(), false, FAILURE, rechecks, false, false);
    }

    static Map<String, Set<String>> frozen(FrozenGateParameters p) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String workflow : p.routes.get(ROUTE).workflows()) {
            Set<String> ids = new LinkedHashSet<>();
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) ids.add(workflow + "-" + i);
            result.put(workflow, ids);
        }
        return result;
    }

    static List<RouteVerdictCalculator.CaseRecord> cases(FrozenGateParameters p) {
        List<RouteVerdictCalculator.CaseRecord> result = new ArrayList<>();
        for (String workflow : p.routes.get(ROUTE).workflows()) {
            List<String> groups = new ArrayList<>();
            p.workflows.get(workflow).subgroups().forEach((group, count) -> {
                for (int i = 0; i < count; i++) groups.add(group);
            });
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) {
                String id = workflow + "-" + i;
                result.add(new RouteVerdictCalculator.CaseRecord(id, workflow, ROUTE,
                        groups.isEmpty() ? null : groups.get(i), id, id, scored(0), scored(0), 1));
            }
        }
        return result;
    }

    static void set(List<RouteVerdictCalculator.CaseRecord> cases, String id,
            RouteVerdictCalculator.ArmResult baseline, RouteVerdictCalculator.ArmResult candidate) {
        for (int i = 0; i < cases.size(); i++) if (cases.get(i).id().equals(id)) {
            var old = cases.get(i);
            cases.set(i, new RouteVerdictCalculator.CaseRecord(id, old.workflow(), old.route(),
                    old.subgroup(), id, id, baseline, candidate, old.requestedQuestions()));
            return;
        }
        throw new AssertionError(id);
    }

    static List<RouteVerdictCalculator.ReserveCase> reserve(int candidateOnly, int gaps) {
        List<RouteVerdictCalculator.ReserveCase> rows = new ArrayList<>();
        for (int i = 0; i < 30; i++) rows.add(new RouteVerdictCalculator.ReserveCase("reserve-" + i,
                RouteVerdictCalculator.Evidence.SCORED,
                i < gaps ? RouteVerdictCalculator.Evidence.MISSING : RouteVerdictCalculator.Evidence.SCORED,
                false, i >= gaps && i < gaps + candidateOnly));
        return rows;
    }

    static Map<String, Boolean> decisions(boolean drawn) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put(ASK, false);
        result.put(INTERVIEW, drawn);
        return result;
    }

    private static RouteVerdictCalculator.RouteReport run(FrozenGateParameters p,
            List<RouteVerdictCalculator.CaseRecord> cases, Map<String, Boolean> decisions,
            List<RouteVerdictCalculator.ReserveCase> reserve) {
        Map<String, List<RouteVerdictCalculator.ReserveCase>> reserves = reserve == null
                ? Map.of() : Map.of(INTERVIEW, reserve);
        return RouteVerdictCalculator.calculate(p, ROUTE, frozen(p), cases, reserves,
                Map.of(ASK, true, INTERVIEW, true), List.of(), decisions, Map.of());
    }

    private static void assertConflict(RouteVerdictCalculator.RouteReport report,
            Map<String, String> expectedReasons, RouteVerdictCalculator.Verdict expectedVerdict) {
        var workflow = report.workflows().get(INTERVIEW);
        assertThat(workflow.verdict()).isEqualTo(expectedVerdict);
        assertThat(workflow.frb().reserveDrawn()).isTrue();
        assertThat(workflow.frb().reserveGate()).isNull();
        assertThat(workflow.frb().reserveCause()).isEqualTo(CAUSE);
        assertThat(workflow.frb().unresolvedReasonsByCaseId()).containsExactlyInAnyOrderEntriesOf(expectedReasons);
        assertThat(workflow.frb().unresolvedCases()).isEqualTo(expectedReasons.size());
        assertThat(report.verdict()).isEqualTo(expectedVerdict);
        if (expectedVerdict == RouteVerdictCalculator.Verdict.INCONCLUSIVE) {
            assertThat(workflow.inconclusiveTiers()).contains(RouteVerdictCalculator.Tier.T2);
            assertThat(report.inconclusiveTiers()).contains(RouteVerdictCalculator.Tier.T2);
        } else {
            assertThat(workflow.failedTiers()).contains(RouteVerdictCalculator.Tier.T2);
            assertThat(report.failedTiers()).contains(RouteVerdictCalculator.Tier.T2);
        }
    }

    private void checkReason(RouteVerdictCalculator.ArmResult baseline,
            RouteVerdictCalculator.ArmResult candidate, String reason) throws Exception {
        FrozenGateParameters p = parameters();
        var records = cases(p);
        set(records, CASE_ID, baseline, candidate);
        // The human finding at export was discordant; receipt-side resolution now has a gap.
        set(records, INTERVIEW + "-1", scored(1), scored(0));
        Map<String, Boolean> recorded = decisions(true);
        Map<String, Boolean> before = Map.copyOf(recorded);
        var report = run(p, records, recorded, reserve(0, 0));
        assertConflict(report, Map.of(CASE_ID, reason), RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(recorded).isEqualTo(before);
    }

    @Test void unmeteredReason() throws Exception {
        checkReason(scored(0), evidence(RouteVerdictCalculator.Evidence.UNMETERED),
                "candidate:UNMETERED");
    }

    @Test void missingReason() throws Exception {
        checkReason(scored(0), null, "candidate:MISSING");
    }

    @Test void unscorableReason() throws Exception {
        checkReason(scored(0), evidence(RouteVerdictCalculator.Evidence.UNSCORABLE),
                "candidate:UNSCORABLE");
    }

    @Test void incompleteRecheckReason() throws Exception {
        checkReason(scored(0), rechecking(1, false), "candidate:INCOMPLETE_RECHECK");
    }

    @Test void baselineConfirmedT1IsBothIncompleteAndReportedAsConfirmed() throws Exception {
        checkReason(rechecking(2, true), scored(0), "baseline:CONFIRMED_T1");
    }

    @Test void candidateConfirmedT1WithFailedBaselineOriginalDoesNotFailLegacyT1() throws Exception {
        // A recovered baseline was originally UNSCORABLE, so legacy T1 does not reject.
        checkReason(rechecking(2, false), rechecking(2, true), "candidate:CONFIRMED_T1");
    }

    @Test void twoArmReasonsAndSortedCaseIdsAreRetained() throws Exception {
        FrozenGateParameters p = parameters();
        var records = cases(p);
        set(records, CASE_ID, evidence(RouteVerdictCalculator.Evidence.UNMETERED),
                rechecking(2, true));
        set(records, INTERVIEW + "-2", scored(0), evidence(RouteVerdictCalculator.Evidence.MISSING));
        set(records, INTERVIEW + "-1", scored(1), scored(0));
        var report = run(p, records, decisions(true), reserve(0, 0));
        var reasons = report.workflows().get(INTERVIEW).frb().unresolvedReasonsByCaseId();
        assertThat(reasons).containsEntry(CASE_ID, "baseline:UNMETERED,candidate:CONFIRMED_T1")
                .containsEntry(INTERVIEW + "-2", "candidate:MISSING");
        assertThat(new ArrayList<>(reasons.keySet())).containsExactly(CASE_ID, INTERVIEW + "-2");
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void rejectingReserveIsNotEvaluatedUnderPrimaryGap() throws Exception {
        FrozenGateParameters p = parameters();
        var records = cases(p);
        set(records, CASE_ID, scored(0), evidence(RouteVerdictCalculator.Evidence.UNMETERED));
        set(records, INTERVIEW + "-1", scored(1), scored(0));
        var report = run(p, records, decisions(true), reserve(20, 0));
        assertConflict(report, Map.of(CASE_ID, "candidate:UNMETERED"),
                RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void onlyDiscordantPairIsReceiptGappedAndTriggerIsNotRecomputed() throws Exception {
        FrozenGateParameters p = parameters();
        var records = cases(p);
        set(records, CASE_ID, scored(0), evidence(RouteVerdictCalculator.Evidence.UNMETERED));
        var report = run(p, records, decisions(true), reserve(0, 0));
        assertConflict(report, Map.of(CASE_ID, "candidate:UNMETERED"),
                RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void primaryFailureIsPreservedWithoutEvaluatingReserve() throws Exception {
        FrozenGateParameters p = parameters();
        var records = cases(p);
        set(records, CASE_ID, scored(0), evidence(RouteVerdictCalculator.Evidence.UNMETERED));
        for (int i = 1; i <= 9; i++) set(records, INTERVIEW + "-" + i, scored(0), scored(1));
        var report = run(p, records, decisions(true), reserve(0, 0));
        assertConflict(report, Map.of(CASE_ID, "candidate:UNMETERED"),
                RouteVerdictCalculator.Verdict.FAIL);
    }

    @Test void ownReserveGapsAreNotCountedWhenReserveIsUnevaluated() throws Exception {
        FrozenGateParameters p = parameters();
        var records = cases(p);
        set(records, CASE_ID, scored(0), evidence(RouteVerdictCalculator.Evidence.UNMETERED));
        var report = run(p, records, decisions(true), reserve(0, 2));
        assertConflict(report, Map.of(CASE_ID, "candidate:UNMETERED"),
                RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void ordinaryDrawnReserveHasNoCause() throws Exception {
        FrozenGateParameters p = parameters();
        var records = cases(p);
        set(records, CASE_ID, scored(1), scored(0));
        var report = run(p, records, decisions(true), reserve(0, 0));
        assertThat(report.workflows().get(INTERVIEW).frb().reserveCause()).isNull();
        assertThat(report.workflows().get(INTERVIEW).frb().unresolvedReasonsByCaseId()).isNull();
        assertThat(report.workflows().get(INTERVIEW).frb().reserveGate()).isEqualTo(Tri.PASS_COMPLETE);
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
    }

    @Test void humanUnresolvedAndRecordedFalseRemainUnchanged() throws Exception {
        FrozenGateParameters p = parameters();
        var records = cases(p);
        var human = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, null, List.of(), false, false, 0, 1);
        set(records, CASE_ID, scored(0), human);
        var report = run(p, records, decisions(false), null);
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(report.workflows().get(INTERVIEW).frb().reserveDrawn()).isFalse();
        assertThat(report.workflows().get(INTERVIEW).frb().reserveCause()).isNull();
        assertThat(report.workflows().get(INTERVIEW).frb().unresolvedReasonsByCaseId()).isNull();
        assertThat(report.workflows().get(INTERVIEW).frb().unresolvedCases()).isEqualTo(1);
    }

    @Test void drawnWorkflowWithoutReserveStillThrowsPendingRuling() throws Exception {
        FrozenGateParameters p = parameters();
        var records = cases(p);
        set(records, CASE_ID, scored(1), scored(0));
        var decisions = decisions(true);
        assertThatThrownBy(() -> run(p, records, decisions, null)).isInstanceOf(InvalidReserve.class);
    }

    @Test void frbWithoutConflictOmitsNewJsonFieldsOnRoundTrip() throws Exception {
        FrozenGateParameters p = parameters();
        var records = cases(p);
        set(records, CASE_ID, scored(1), scored(0));
        var report = run(p, records, decisions(true), reserve(0, 0));
        JsonNode tree = JSON.valueToTree(report);
        JsonNode frb = tree.path("workflows").path(INTERVIEW).path("frb");
        assertThat(frb.has("reserveCause")).isFalse();
        assertThat(frb.has("unresolvedReasonsByCaseId")).isFalse();
        var roundTrip = JSON.treeToValue(tree, RouteVerdictCalculator.RouteReport.class);
        assertThat(roundTrip.workflows().get(INTERVIEW).frb().reserveCause()).isNull();
        assertThat(roundTrip.workflows().get(INTERVIEW).frb().unresolvedReasonsByCaseId()).isNull();
    }

    @Test void routeT2DrawnGapIgnoresReserveDataAndNeverPasses() {
        var known = List.of(-1);
        var pairs = List.of(new ClassBReserve.PairCritical(0, 1));
        var input = direct(known, pairs, true, Collections.nCopies(30, 1), List.of());
        var decision = RouteT2.decideDetailed(input);
        var workflow = decision.workflows().get(INTERVIEW);
        assertThat(workflow.workflowGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(workflow.reserveGate()).isNull();
        assertThat(workflow.cause()).isEqualTo(CAUSE);
        assertThat(RouteT2.decide(input)).isEqualTo(Tri.INCONCLUSIVE);
    }

    @Test void routeT2DrawnGapIgnoresNullAndWrongSizeReserveLists() {
        var known = List.of(0);
        var pairs = List.of(new ClassBReserve.PairCritical(0, 0));
        for (List<Integer> reserve : List.<List<Integer>>of(List.of(), List.of(1))) {
            var input = direct(known, pairs, true, reserve, List.of());
            assertThat(RouteT2.decideDetailed(input).workflows().get(INTERVIEW).workflowGate())
                    .isEqualTo(Tri.INCONCLUSIVE);
        }
        var input = direct(known, pairs, true, null, List.of(new Tri.Unresolved(-1, 1)));
        assertThat(RouteT2.decideDetailed(input).workflows().get(INTERVIEW).reserveGate()).isNull();
    }

    @Test void routeT2RecordedFalseRejectsUnexpectedReserveAndOldConstructorStillDecides() {
        var known = List.of(0);
        var pairs = List.of(new ClassBReserve.PairCritical(0, 0));
        var valid = direct(known, pairs, false, null, List.of());
        assertThat(RouteT2.decide(valid)).isEqualTo(Tri.INCONCLUSIVE);
        var unexpected = direct(known, pairs, false, List.of(0), List.of());
        assertThatThrownBy(() -> RouteT2.decide(unexpected)).isInstanceOf(InvalidReserve.class);
        assertThat(new RouteT2.WorkflowDecision(Tri.INCONCLUSIVE, null).cause()).isNull();
    }

    private static RouteT2.RouteInput direct(List<Integer> known,
            List<ClassBReserve.PairCritical> pairs, boolean drawn,
            List<Integer> reserve, List<Tri.Unresolved> reserveGaps) {
        var workflow = new RouteT2.WorkflowInput(INTERVIEW, true, known, pairs,
                List.of(new Tri.Unresolved(-1, 1)), drawn, reserve, reserveGaps);
        return new RouteT2.RouteInput(1, Rational.of(4, 100), Rational.of(4, 100),
                List.of(workflow));
    }
}
