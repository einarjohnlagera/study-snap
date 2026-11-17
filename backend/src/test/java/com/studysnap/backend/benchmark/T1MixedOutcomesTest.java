package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic frozen case records. No runner, scorer, model, or paid call. */
class T1MixedOutcomesTest {
    private static final String INTERACTIVE = "INTERACTIVE_QUIZ";
    private static final String CRITIQUE = "CRITIQUE";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String ASK = "ASK_COMPANION";
    private static final String HTTP_ERROR = "HTTP_ERROR";
    private static final String PARSE = "PARSE";
    private static final String ISOLATED = "ISOLATED_FINAL_FAILURE";
    private static final String MIXED = "RECHECK_MIXED";
    private static final String UNRESOLVED = "RECHECK_UNRESOLVED";
    private static final String HARNESS = "HARNESS_REGRESSION";
    private static final String FRB_RESOURCE =
            "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json";
    private static final String SAME_REJECTION = "SAME_REJECTION";
    private static final String SUCCESS = "SUCCESS";
    private static final String DIFFERENT_REJECTION = "DIFFERENT_REJECTION";
    private static final String TIMEOUT = "TIMEOUT";
    private static final String OTHER = "OTHER";
    private static final String QUALITY = "QUALITY";
    private static final String SUCCESS_COMPONENT = "SUCCESS_COMPONENT";
    private static final String SHA_SUFFIX = ".sha256";
    private static final String ID_SEPARATOR = "-";
    private static final String ZERO_SUFFIX = "-0";
    private static final String ONE_SUFFIX = "-1";
    private static final String TWO_SUFFIX = "-2";
    private static final String CANDIDATE_PREFIX = "candidate:";
    private static final String CAUSES_KEY = "causes";
    private static final String OUTCOMES_KEY = "recheckOutcomesByCaseId";
    private static final String REJECTION_EVIDENCE_KEY = "rejectionEvidenceByCaseId";
    private static final String[] OUTCOMES = {
            SAME_REJECTION, SUCCESS, DIFFERENT_REJECTION, TIMEOUT, OTHER
    };
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    private FrozenGateParameters parameters(boolean frb) throws Exception {
        Path source = frb ? Path.of(FRB_RESOURCE) : GateTestFixture.SOURCE;
        Path copy = temp.resolve(frb ? "frb.json" : "legacy.json");
        Files.copy(source, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(source.resolveSibling(source.getFileName() + SHA_SUFFIX),
                copy.resolveSibling(copy.getFileName() + SHA_SUFFIX),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return FrozenGateParameters.load(copy);
    }

    private static RouteVerdictCalculator.ArmResult clean() {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, null, List.of(), false, false, 0, 0);
    }

    private static RouteVerdictCalculator.ArmResult rejection(boolean finalFailed,
            List<RouteVerdictCalculator.Recheck> checks) {
        return new RouteVerdictCalculator.ArmResult(
                finalFailed ? RouteVerdictCalculator.Evidence.UNSCORABLE : RouteVerdictCalculator.Evidence.SCORED,
                finalFailed ? Set.of() : Set.of(QUALITY), false,
                finalFailed ? HTTP_ERROR : null, checks, true, finalFailed, 0, 0);
    }

    private static RouteVerdictCalculator.Recheck check(String outcome) {
        return switch (outcome) {
            case SAME_REJECTION -> new RouteVerdictCalculator.Recheck(
                    RouteVerdictCalculator.Evidence.SCORED, Set.of(), false, HTTP_ERROR, 0, 0, true);
            case SUCCESS -> new RouteVerdictCalculator.Recheck(
                    RouteVerdictCalculator.Evidence.SCORED, Set.of(SUCCESS_COMPONENT), false, null, 0, 0);
            case DIFFERENT_REJECTION -> new RouteVerdictCalculator.Recheck(
                    RouteVerdictCalculator.Evidence.SCORED, Set.of(), false, PARSE, 0, 0);
            case TIMEOUT -> new RouteVerdictCalculator.Recheck(
                    RouteVerdictCalculator.Evidence.SCORED, Set.of(), false, TIMEOUT, 0, 0, true);
            case OTHER -> new RouteVerdictCalculator.Recheck(
                    RouteVerdictCalculator.Evidence.MISSING, Set.of(), false, HTTP_ERROR, 0, 0, true);
            default -> throw new AssertionError(outcome);
        };
    }

    private static Map<String, Set<String>> frozen(FrozenGateParameters p, String route) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String workflow : p.routes.get(route).workflows()) {
            Set<String> ids = new LinkedHashSet<>();
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) ids.add(workflow + ID_SEPARATOR + i);
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
                String id = workflow + ID_SEPARATOR + i;
                result.add(new RouteVerdictCalculator.CaseRecord(id, workflow, route,
                        groups.isEmpty() ? null : groups.get(i), id, id, clean(), clean(), width));
            }
        }
        return result;
    }

    private static void set(List<RouteVerdictCalculator.CaseRecord> records, String id,
                            RouteVerdictCalculator.ArmResult baseline,
                            RouteVerdictCalculator.ArmResult candidate) {
        for (int i = 0; i < records.size(); i++) if (records.get(i).id().equals(id)) {
            var old = records.get(i);
            records.set(i, new RouteVerdictCalculator.CaseRecord(id, old.workflow(), old.route(),
                    old.subgroup(), id, id, baseline, candidate, old.requestedQuestions()));
            return;
        }
        throw new AssertionError(id);
    }

    private static RouteVerdictCalculator.RouteReport run(FrozenGateParameters p, String route,
            List<RouteVerdictCalculator.CaseRecord> records, Map<String, Boolean> suites) {
        Map<String, Boolean> all = new HashMap<>();
        for (String workflow : p.routes.get(route).workflows()) all.put(workflow, true);
        all.putAll(suites);
        Map<String, Boolean> decisions = new HashMap<>();
        if (p.frbEnabled) for (String workflow : p.routes.get(route).workflows())
            if (p.frbClassBWorkflows.contains(workflow)) decisions.put(workflow, false);
        return RouteVerdictCalculator.calculate(p, route, frozen(p, route), records,
                Map.of(), all, List.of(), decisions, Map.of());
    }

    private static RouteVerdictCalculator.WorkflowReport workflow(RouteVerdictCalculator.RouteReport report) {
        return report.workflows().get(NOTE);
    }

    @Test void allTwentyFiveOrderedPairsHaveExactlyOneDisposition() throws Exception {
        FrozenGateParameters p = parameters(true);
        int counted = 0;
        for (String first : OUTCOMES) for (String second : OUTCOMES) {
            List<RouteVerdictCalculator.CaseRecord> records = cases(p, INTERACTIVE);
            set(records, NOTE + ZERO_SUFFIX, clean(), rejection(true, List.of(check(first), check(second))));
            var report = run(p, INTERACTIVE, records, Map.of());
            var note = workflow(report);
            boolean same = first.equals(SAME_REJECTION) && second.equals(SAME_REJECTION);
            boolean success = first.equals(SUCCESS) && second.equals(SUCCESS);
            String cause = first.equals(OTHER) || second.equals(OTHER) ? UNRESOLVED : MIXED;
            if (same) {
                assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.FAIL);
                assertThat(note.failedTiers()).contains(RouteVerdictCalculator.Tier.T1);
                assertThat(note.candidateOnlyT1()).isEqualTo(1);
                assertThat(note.frb().unresolvedReasonsByCaseId()).isNull();
            } else if (success) {
                assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
                assertThat(note.candidateComponents()).containsKey(ISOLATED);
                assertThat(note.failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T1);
            } else {
                assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
                assertThat(note.causes()).containsExactly(cause);
                assertThat(note.candidateOnlyT1()).isZero();
                assertThat(note.candidateComponents()).doesNotContainKey(ISOLATED);
            }
            assertThat(note.recheckOutcomesByCaseId()).containsEntry(
                    NOTE + ZERO_SUFFIX, CANDIDATE_PREFIX + first + "+" + second);
            counted++;
        }
        assertThat(counted).isEqualTo(25);
    }

    @Test void missingAndWrongCountRechecksStayUnresolved() throws Exception {
        FrozenGateParameters p = parameters(true);
        for (List<RouteVerdictCalculator.Recheck> checks : List.of(
                List.<RouteVerdictCalculator.Recheck>of(), List.of(check(SUCCESS)),
                List.of(check(SUCCESS), check(SUCCESS), check(SUCCESS)))) {
            var records = cases(p, INTERACTIVE);
            set(records, NOTE + ZERO_SUFFIX, clean(), rejection(true, checks));
            var note = workflow(run(p, INTERACTIVE, records, Map.of()));
            assertThat(note.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
            assertThat(note.causes()).containsExactly(UNRESOLVED);
            assertThat(note.failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T1);
        }
    }

    @Test void missingOriginalEvidenceCannotBeRecoveredBySuccessfulRechecks() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, INTERACTIVE);
        var unavailable = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.MISSING,
                Set.of(), false, HTTP_ERROR, List.of(check(SUCCESS), check(SUCCESS)), true, true, 0, 0);
        set(records, NOTE + ZERO_SUFFIX, clean(), unavailable);
        var note = workflow(run(p, INTERACTIVE, records, Map.of()));
        assertThat(note.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(note.causes()).containsExactly(UNRESOLVED);
        assertThat(note.candidateComponents()).doesNotContainKey(ISOLATED);
    }

    @Test void recheckRejectionFlagDefaultsFalseAndFailsClosed() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, INTERACTIVE);
        var oldSignature = new RouteVerdictCalculator.Recheck(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, HTTP_ERROR, 0, 0);
        assertThat(oldSignature.providerRejected()).isFalse();
        set(records, NOTE + ZERO_SUFFIX, clean(), rejection(true, List.of(oldSignature, oldSignature)));
        var note = workflow(run(p, INTERACTIVE, records, Map.of()));
        assertThat(note.causes()).containsExactly(MIXED);
        assertThat(note.failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T1);
        assertThat(note.recheckOutcomesByCaseId()).containsEntry(NOTE + ZERO_SUFFIX,
                "candidate:DIFFERENT_REJECTION+DIFFERENT_REJECTION");
    }

    @Test void rejectedThenSucceededAndFailedOriginalsNeedRechecks() throws Exception {
        FrozenGateParameters p = parameters(true);
        for (boolean failed : List.of(false, true)) {
            var records = cases(p, INTERACTIVE);
            set(records, NOTE + ZERO_SUFFIX, clean(), rejection(failed, List.of()));
            var report = run(p, INTERACTIVE, records, Map.of());
            var note = workflow(report);
            assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
            assertThat(note.failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T1);
            assertThat(note.causes()).containsExactly(UNRESOLVED);
            assertThat(note.rejectionEvidenceByCaseId()).containsEntry(NOTE + ZERO_SUFFIX,
                    CANDIDATE_PREFIX + (failed ? "REJECTED_FINAL_FAILURE" : "REJECTED_THEN_RETRY_SUCCEEDED"));
            assertThat(note.frb().workflowGate()).isEqualTo(Tri.INCONCLUSIVE);
        }
    }

    @Test void retriedSuccessfulOriginalKeepsOwnComponentsAndCountsOnTransient() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, INTERACTIVE);
        var original = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(QUALITY), true, null, List.of(check(SUCCESS), check(SUCCESS)), true, false, 2, 0);
        set(records, NOTE + ZERO_SUFFIX, clean(), original);
        var note = workflow(run(p, INTERACTIVE, records, Map.of()));
        assertThat(note.candidateComponents()).containsKeys(QUALITY, ISOLATED);
        assertThat(note.candidateComponents()).doesNotContainKey(SUCCESS_COMPONENT);
        assertThat(note.frb().pValue()).isEqualTo(ExactPairedTest.test(List.of(2)));
        assertThat(note.candidateOnlyT2()).isEqualTo(1);
    }

    @Test void confirmedAndMixedPairsApplyToRetriedSuccessfulOriginal() throws Exception {
        FrozenGateParameters p = parameters(true);
        for (List<String> pair : List.of(List.of(SAME_REJECTION, SAME_REJECTION),
                List.of(SAME_REJECTION, SUCCESS), List.of(SUCCESS, OTHER))) {
            var records = cases(p, INTERACTIVE);
            set(records, NOTE + ZERO_SUFFIX, clean(), rejection(false, List.of(check(pair.get(0)), check(pair.get(1)))));
            var note = workflow(run(p, INTERACTIVE, records, Map.of()));
            if (pair.get(1).equals(SAME_REJECTION))
                assertThat(note.failedTiers()).contains(RouteVerdictCalculator.Tier.T1);
            else assertThat(note.causes()).containsExactly(pair.get(1).equals(OTHER) ? UNRESOLVED : MIXED);
        }
    }

    @Test void nonRejectionKeepsFirstSuccessRecovery() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, INTERACTIVE);
        var parse = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.UNSCORABLE,
                Set.of(), false, PARSE, List.of(check(SUCCESS), check(DIFFERENT_REJECTION)),
                false, false, 0, 0);
        set(records, NOTE + ZERO_SUFFIX, clean(), parse);
        var note = workflow(run(p, INTERACTIVE, records, Map.of()));
        assertThat(note.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(note.candidateComponents()).containsKey(ISOLATED);
        assertThat(note.causes()).isNull();
    }

    @Test void baselineMixedDropsPairButConfirmedBaselineIsNotCharged() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, INTERACTIVE);
        set(records, NOTE + ZERO_SUFFIX, rejection(true, List.of(check(SUCCESS), check(SAME_REJECTION))), clean());
        var mixed = workflow(run(p, INTERACTIVE, records, Map.of()));
        assertThat(mixed.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(mixed.causes()).containsExactly(MIXED);
        assertThat(mixed.missingPairs()).isEqualTo(1);
        set(records, NOTE + ZERO_SUFFIX, rejection(true,
                List.of(check(SAME_REJECTION), check(SAME_REJECTION))), clean());
        var confirmed = workflow(run(p, INTERACTIVE, records, Map.of()));
        assertThat(confirmed.failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T1);
        assertThat(confirmed.baselineOnlyT1()).isEqualTo(1);
    }

    @Test void baselineRetriedAndSucceededRemainsIncludedAndReported() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, INTERACTIVE);
        set(records, NOTE + ZERO_SUFFIX, rejection(false, List.of()), clean());
        var note = workflow(run(p, INTERACTIVE, records, Map.of()));
        assertThat(note.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(note.missingPairs()).isZero();
        assertThat(note.rejectionEvidenceByCaseId()).containsEntry(NOTE + ZERO_SUFFIX,
                "baseline:REJECTED_THEN_RETRY_SUCCEEDED");
    }

    @Test void bothArmsKeepRejectionEvidenceInOneOrderedEntry() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, INTERACTIVE);
        set(records, NOTE + ZERO_SUFFIX, rejection(false, List.of()), rejection(true, List.of()));
        var note = workflow(run(p, INTERACTIVE, records, Map.of()));
        assertThat(note.rejectionEvidenceByCaseId()).containsEntry(NOTE + ZERO_SUFFIX,
                "baseline:REJECTED_THEN_RETRY_SUCCEEDED;candidate:REJECTED_FINAL_FAILURE");
        assertThat(note.causes()).containsExactly(UNRESOLVED);
    }

    @Test void presentArmKeepsRejectionEvidenceWhenCounterpartIsMissing() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, INTERACTIVE);
        set(records, NOTE + ZERO_SUFFIX, null, rejection(false, List.of()));
        var note = workflow(run(p, INTERACTIVE, records, Map.of()));
        assertThat(note.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(note.rejectionEvidenceByCaseId()).containsEntry(NOTE + ZERO_SUFFIX,
                "candidate:REJECTED_THEN_RETRY_SUCCEEDED");
    }

    @Test void frbT2RejectsMixedArmAndReportsSpecificReason() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, CRITIQUE);
        set(records, ASK + ZERO_SUFFIX, clean(), rejection(true,
                List.of(check(SUCCESS), check(SAME_REJECTION))));
        var critical = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), true, null, List.of(), false, false, 1, 0);
        set(records, ASK + ONE_SUFFIX, clean(), critical);
        List<RouteVerdictCalculator.ReserveCase> reserve = new ArrayList<>();
        for (int i = 0; i < p.reserveSize; i++) reserve.add(new RouteVerdictCalculator.ReserveCase(
                "reserve-" + i, RouteVerdictCalculator.Evidence.SCORED,
                RouteVerdictCalculator.Evidence.SCORED, false, false));
        Map<String, Boolean> suites = new HashMap<>();
        for (String workflow : p.routes.get(CRITIQUE).workflows()) suites.put(workflow, true);
        Map<String, Boolean> decisions = new HashMap<>();
        for (String workflow : p.routes.get(CRITIQUE).workflows())
            if (p.frbClassBWorkflows.contains(workflow)) decisions.put(workflow, workflow.equals(ASK));
        var disposition = new RouteVerdictCalculator.OwnerDisposition(ASK, ASK + ONE_SUFFIX,
                RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION);
        var report = RouteVerdictCalculator.calculate(p, CRITIQUE, frozen(p, CRITIQUE), records,
                Map.of(ASK, reserve), suites, List.of(disposition), decisions, Map.of());
        var ask = report.workflows().get(ASK);
        assertThat(ask.frb().unresolvedReasonsByCaseId()).containsEntry(ASK + ZERO_SUFFIX, CANDIDATE_PREFIX + MIXED);
        assertThat(ask.frb().unresolvedCases()).isEqualTo(1);
        assertThat(ask.frb().workflowGate()).isEqualTo(Tri.INCONCLUSIVE);
        set(records, ASK + ZERO_SUFFIX, rejection(true,
                List.of(check(SUCCESS), check(SAME_REJECTION))), clean());
        var baselineMixed = RouteVerdictCalculator.calculate(p, CRITIQUE, frozen(p, CRITIQUE), records,
                Map.of(ASK, reserve), suites, List.of(disposition), decisions, Map.of());
        assertThat(baselineMixed.workflows().get(ASK).frb().unresolvedReasonsByCaseId())
                .containsEntry(ASK + ZERO_SUFFIX, "baseline:" + MIXED);
        set(records, ASK + ZERO_SUFFIX, clean(), rejection(false, List.of()));
        var retriedWithoutRechecks = RouteVerdictCalculator.calculate(p, CRITIQUE,
                frozen(p, CRITIQUE), records, Map.of(ASK, reserve), suites,
                List.of(disposition), decisions, Map.of());
        assertThat(retriedWithoutRechecks.workflows().get(ASK).frb().unresolvedReasonsByCaseId())
                .containsEntry(ASK + ZERO_SUFFIX, CANDIDATE_PREFIX + UNRESOLVED);
    }

    @Test void failingSuiteIsHarnessRegressionButOtherTierCanStillFail() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, INTERACTIVE);
        var suites = Map.<String, Boolean>of(NOTE, false);
        var only = workflow(run(p, INTERACTIVE, records, suites));
        assertThat(only.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(only.failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T1);
        assertThat(only.inconclusiveTiers()).contains(RouteVerdictCalculator.Tier.T1);
        assertThat(only.causes()).containsExactly(HARNESS);
        for (int i = 0; i < 12; i++) {
            var bad = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                    Set.of(QUALITY), false, null, List.of(), false, false, 0, 0);
            set(records, NOTE + ID_SEPARATOR + i, clean(), bad);
        }
        var t3 = run(p, INTERACTIVE, records, suites);
        assertThat(t3.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.FAIL);
        assertThat(t3.failedTiers()).contains(RouteVerdictCalculator.Tier.T3);
        assertThat(workflow(t3).causes()).containsExactly(HARNESS);
    }

    @Test void missingAndPassingSuiteKeepOldDisposition() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, INTERACTIVE);
        assertThat(workflow(run(p, INTERACTIVE, records, Map.of())).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        Map<String, Boolean> suites = new HashMap<>();
        for (String workflow : p.routes.get(INTERACTIVE).workflows()) suites.put(workflow, true);
        suites.remove(NOTE);
        Map<String, Boolean> decisions = new HashMap<>();
        for (String workflow : p.routes.get(INTERACTIVE).workflows())
            if (p.frbClassBWorkflows.contains(workflow)) decisions.put(workflow, false);
        var missing = RouteVerdictCalculator.calculate(p, INTERACTIVE, frozen(p, INTERACTIVE),
                records, Map.of(), suites, List.of(), decisions, Map.of());
        assertThat(workflow(missing).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(workflow(missing).causes()).isNull();
    }

    @Test void legacySemanticsAndJsonShapeStayUnchanged() throws Exception {
        FrozenGateParameters p = parameters(false);
        var records = cases(p, INTERACTIVE);
        set(records, NOTE + ZERO_SUFFIX, clean(), rejection(false, List.of()));
        var rejected = workflow(run(p, INTERACTIVE, records, Map.of()));
        assertThat(rejected.failedTiers()).contains(RouteVerdictCalculator.Tier.T1);
        assertThat(rejected.causes()).isNull();
        assertThat(rejected.recheckOutcomesByCaseId()).isNull();
        assertThat(rejected.rejectionEvidenceByCaseId()).isNull();
        JsonNode json = JSON.valueToTree(rejected);
        assertThat(json.has(CAUSES_KEY)).isFalse();
        assertThat(json.has(OUTCOMES_KEY)).isFalse();
        assertThat(json.has(REJECTION_EVIDENCE_KEY)).isFalse();
        var roundTrip = JSON.treeToValue(JSON.valueToTree(run(p, INTERACTIVE, records, Map.of())),
                RouteVerdictCalculator.RouteReport.class);
        assertThat(roundTrip.workflows().get(NOTE).causes()).isNull();
        set(records, NOTE + ZERO_SUFFIX, clean(), clean());
        assertThat(workflow(run(p, INTERACTIVE, records, Map.of(NOTE, false))).failedTiers())
                .contains(RouteVerdictCalculator.Tier.T1);
    }

    @Test void causesAndOutcomesHaveDeterministicOrderWithoutDuplicates() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, INTERACTIVE);
        set(records, NOTE + TWO_SUFFIX, clean(), rejection(true, List.of(check(SUCCESS), check(OTHER))));
        set(records, NOTE + ONE_SUFFIX, clean(), rejection(true, List.of(check(SUCCESS), check(SAME_REJECTION))));
        set(records, NOTE + ZERO_SUFFIX, clean(), rejection(true, List.of(check(OTHER), check(SUCCESS))));
        var note = workflow(run(p, INTERACTIVE, records, Map.of(NOTE, false)));
        assertThat(note.causes()).containsExactly(HARNESS, MIXED, UNRESOLVED);
        assertThat(new ArrayList<>(note.recheckOutcomesByCaseId().keySet()))
                .containsExactly(NOTE + ZERO_SUFFIX, NOTE + ONE_SUFFIX, NOTE + TWO_SUFFIX);
        assertThat(note.rejectionEvidenceByCaseId()).hasSize(3);
        assertThat(JSON.valueToTree(note).has(CAUSES_KEY)).isTrue();
    }

    @Test void cleanFrbReportOmitsAdditiveFields() throws Exception {
        FrozenGateParameters p = parameters(true);
        var note = workflow(run(p, INTERACTIVE, cases(p, INTERACTIVE), Map.of()));
        assertThat(note.causes()).isNull();
        assertThat(note.recheckOutcomesByCaseId()).isNull();
        assertThat(note.rejectionEvidenceByCaseId()).isNull();
        JsonNode json = JSON.valueToTree(note);
        assertThat(json.has(CAUSES_KEY)).isFalse();
        assertThat(json.has(OUTCOMES_KEY)).isFalse();
        assertThat(json.has(REJECTION_EVIDENCE_KEY)).isFalse();
    }

    @Test void deadlineRuleCurrentlyAllowsFallbackForTheseInconclusiveCauses() throws Exception {
        FrozenGateParameters p = parameters(true);
        var records = cases(p, INTERACTIVE);
        set(records, NOTE + ZERO_SUFFIX, clean(), rejection(true, List.of()));
        var report = run(p, INTERACTIVE, records, Map.of(NOTE, false));
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(DeadlineRule.classify(report)).isEqualTo(DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE);
    }
}
