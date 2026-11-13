package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sections 4, 8 and 9 route-gate mutation-killing cases. */
class RouteVerdictCalculatorTest {
    private static final String ID_SEPARATOR = "-";
    private static final String RESERVE_PREFIX = "reserve-";
    private static final String QUALITY_COMPONENT = "QUALITY";
    private static final String COST_COMPONENT = "COST";
    private static final String PARSE_FAILURE = "PARSE";
    private static final String FIRST_COMPONENT = "FIRST";
    private static final String SECOND_COMPONENT = "SECOND";
    private static final String DIRECT_SUBGROUP = "DIRECT";
    private static final String STUDY_TIP_WORKFLOW = "STUDY_TIP";
    private static final String LONG_POOL_SUBGROUP = "LONG_EXAM_POOL";
    private static final String BOARD_POOL_SUBGROUP = "BOARD_EXAM_POOL";
    private static final String UNFROZEN_ID = "unfrozen";
    private static final String CRITIQUE = GateTestFixture.CRITIQUE;
    private static final String INTERACTIVE = GateTestFixture.INTERACTIVE;
    private static final String ASK = "ASK_COMPANION";
    private static final String LONG = "LONG_EXAM";
    @TempDir Path temp;

    private FrozenGateParameters parameters() throws IOException, NoSuchAlgorithmException {
        return parameters(null);
    }
    private FrozenGateParameters parameters(Integer critiqueMargin) throws IOException, NoSuchAlgorithmException {
        ObjectNode tree = (ObjectNode) new ObjectMapper().readTree(Files.readString(GateTestFixture.SOURCE));
        if (critiqueMargin != null) ((ObjectNode) tree.path("routes").path(CRITIQUE)).put("margin", critiqueMargin);
        byte[] bytes = new ObjectMapper().writeValueAsBytes(tree);
        Path file = temp.resolve(GateTestFixture.FILE_NAME);
        Files.write(file, bytes);
        Files.writeString(temp.resolve(GateTestFixture.FILE_NAME + GateTestFixture.SHA_SUFFIX),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        return FrozenGateParameters.load(file);
    }

    private static RouteVerdictCalculator.ArmResult arm(Set<String> components, boolean critical) {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                components, critical, null, List.of(), false, false);
    }
    private static RouteVerdictCalculator.ArmResult emptyArm() { return arm(Set.of(), false); }

    private static Map<String, Set<String>> frozen(FrozenGateParameters p, String route) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String workflow : p.routes.get(route).workflows()) {
            Set<String> ids = new HashSet<>();
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
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) {
                String id = workflow + ID_SEPARATOR + i;
                String subgroup = groups.isEmpty() ? null : groups.get(i);
                result.add(new RouteVerdictCalculator.CaseRecord(id, workflow, route, subgroup,
                        id, id, emptyArm(), emptyArm()));
            }
        }
        return result;
    }
    private static RouteVerdictCalculator.CaseRecord withArms(RouteVerdictCalculator.CaseRecord old,
                                                               RouteVerdictCalculator.ArmResult baseline,
                                                               RouteVerdictCalculator.ArmResult candidate) {
        return new RouteVerdictCalculator.CaseRecord(old.id(), old.workflow(), old.route(), old.subgroup(),
                old.id(), old.id(), baseline, candidate);
    }
    private static RouteVerdictCalculator.RouteReport calculate(FrozenGateParameters p, String route,
            List<RouteVerdictCalculator.CaseRecord> cases, Map<String, List<RouteVerdictCalculator.ReserveCase>> reserve) {
        Map<String, Boolean> suites = new HashMap<>();
        for (String name : p.routes.get(route).workflows()) suites.put(name, true);
        return RouteVerdictCalculator.calculate(p, route, frozen(p, route), cases, reserve, suites);
    }
    private static List<RouteVerdictCalculator.ReserveCase> reserve(FrozenGateParameters p, int candidateOnly) {
        List<RouteVerdictCalculator.ReserveCase> result = new ArrayList<>();
        for (int i = 0; i < p.reserveSize; i++)
            result.add(new RouteVerdictCalculator.ReserveCase(RESERVE_PREFIX + i,
                    RouteVerdictCalculator.Evidence.SCORED, RouteVerdictCalculator.Evidence.SCORED,
                    false, i < candidateOnly));
        return result;
    }
    private static Map<String, Boolean> matchedReserve(FrozenGateParameters p, int candidateOnly) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (int i = 0; i < p.reserveSize; i++) result.put(RESERVE_PREFIX + i, i < candidateOnly);
        return result;
    }

    @Test void strictMarginBoundaryAndChangedParameterKillThresholdLiteral() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        for (int i = 0; i < p.routes.get(CRITIQUE).margin(); i++)
            records.set(i, withArms(records.get(i), emptyArm(), arm(Set.of(QUALITY_COMPONENT), false)));
        assertThat(calculate(p, CRITIQUE, records, Map.of()).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(calculate(parameters(0), CRITIQUE, records, Map.of()).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.FAIL);
        int next = p.routes.get(CRITIQUE).margin();
        records.set(next, withArms(records.get(next), emptyArm(), arm(Set.of(QUALITY_COMPONENT), false)));
        assertThat(calculate(p, CRITIQUE, records, Map.of()).failedTiers())
                .contains(RouteVerdictCalculator.Tier.T3);
    }

    @Test void marginExceededOnPartialEvidenceIsInconclusiveUnlessMissingPairsCannotCureIt() throws Exception {
        FrozenGateParameters p = parameters();
        int overMargin = p.routes.get(CRITIQUE).margin() + 1;
        RouteVerdictCalculator.ArmResult missing = new RouteVerdictCalculator.ArmResult(
                RouteVerdictCalculator.Evidence.MISSING, Set.of(), false, null, List.of(), false, false);
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        for (int i = 0; i < overMargin; i++)
            records.set(i, withArms(records.get(i), emptyArm(), arm(Set.of(QUALITY_COMPONENT), false)));
        assertThat(calculate(p, CRITIQUE, records, Map.of()).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.FAIL);
        records.set(overMargin, withArms(records.get(overMargin), emptyArm(), missing));
        RouteVerdictCalculator.RouteReport oneMissing = calculate(p, CRITIQUE, records, Map.of());
        assertThat(oneMissing.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(oneMissing.failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T3);
        records.set(overMargin + 1, withArms(records.get(overMargin + 1), emptyArm(), arm(Set.of(QUALITY_COMPONENT), false)));
        assertThat(calculate(p, CRITIQUE, records, Map.of()).failedTiers()).contains(RouteVerdictCalculator.Tier.T3);
    }

    @Test void t3SignCaseUnionAndSharedDebt() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        records.set(0, withArms(records.get(0), emptyArm(), arm(Set.of(QUALITY_COMPONENT, COST_COMPONENT), false)));
        records.set(1, withArms(records.get(1), arm(Set.of(QUALITY_COMPONENT), true), emptyArm()));
        records.set(2, withArms(records.get(2), arm(Set.of(QUALITY_COMPONENT), true), arm(Set.of(COST_COMPONENT), true)));
        RouteVerdictCalculator.RouteReport report = calculate(p, CRITIQUE, records, Map.of());
        assertThat(report.d()).isZero();
        assertThat(report.workflows().get(ASK).candidateOnlyT3()).isEqualTo(1);
        assertThat(report.workflows().get(ASK).baselineOnlyT3()).isEqualTo(1);
        assertThat(report.workflows().get(ASK).sharedT3()).isEqualTo(1);
        assertThat(report.workflows().get(ASK).baselineOnlyT2()).isEqualTo(1);
        assertThat(report.workflows().get(ASK).sharedT2()).isEqualTo(1);
        assertThat(report.workflows().get(ASK).candidateComponents()).containsEntry(COST_COMPONENT, 2);
        assertThat(report.parameterSha256()).isEqualTo(p.sha256);
        assertThat(report.workflows().get(ASK).parameterSha256()).isEqualTo(p.sha256);
    }

    @Test void t2ReserveTwoThreeFourAndPooledT3CannotClearIt() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        records.set(0, withArms(records.get(0), emptyArm(), arm(Set.of(), true)));
        for (int i = 1; i < 8; i++)
            records.set(i, withArms(records.get(i), arm(Set.of(QUALITY_COMPONENT), false), emptyArm()));
        for (int count : List.of(2, 3, 4)) {
            RouteVerdictCalculator.RouteReport report = calculate(p, CRITIQUE, records,
                    Map.of(ASK, reserve(p, count)));
            assertThat(report.workflows().get(ASK).reserveCandidateOnly()).isEqualTo(count);
            assertThat(report.failedTiers().contains(RouteVerdictCalculator.Tier.T2)).isEqualTo(count >= 3);
            if (count >= 3) assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.FAIL);
        }
    }

    @Test void missingUnmeteredAndUnscorableNeverPass() throws Exception {
        FrozenGateParameters p = parameters();
        for (RouteVerdictCalculator.Evidence evidence : List.of(RouteVerdictCalculator.Evidence.MISSING,
                RouteVerdictCalculator.Evidence.UNMETERED, RouteVerdictCalculator.Evidence.UNSCORABLE)) {
            List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
            RouteVerdictCalculator.ArmResult bad = new RouteVerdictCalculator.ArmResult(evidence,
                    Set.of(), false, null, List.of(), false, false);
            records.set(0, withArms(records.get(0), emptyArm(), bad));
            assertThat(calculate(p, CRITIQUE, records, Map.of()).verdict())
                    .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        }
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        records.remove(0);
        assertThat(calculate(p, CRITIQUE, records, Map.of()).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    private static RouteVerdictCalculator.Recheck recheck(String failure, String component) {
        return new RouteVerdictCalculator.Recheck(RouteVerdictCalculator.Evidence.SCORED,
                component == null ? Set.of() : Set.of(component), false, failure);
    }
    private static RouteVerdictCalculator.ArmResult failedArm(List<RouteVerdictCalculator.Recheck> rechecks) {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, PARSE_FAILURE, rechecks, false, false);
    }

    @Test void t1BothSameClassDifferentClassAndOneSuccess() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        records.set(0, withArms(records.get(0), emptyArm(),
                failedArm(List.of(recheck(PARSE_FAILURE, null), recheck(PARSE_FAILURE, null)))));
        assertThat(calculate(p, CRITIQUE, records, Map.of()).failedTiers())
                .contains(RouteVerdictCalculator.Tier.T1);
        records.set(0, withArms(records.get(0), emptyArm(),
                failedArm(List.of(recheck(PARSE_FAILURE, null), recheck("SCHEMA", null)))));
        assertThat(calculate(p, CRITIQUE, records, Map.of()).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        records.set(0, withArms(records.get(0), emptyArm(),
                failedArm(List.of(recheck(null, FIRST_COMPONENT), recheck(PARSE_FAILURE, null)))));
        RouteVerdictCalculator.RouteReport recovered = calculate(p, CRITIQUE, records, Map.of());
        assertThat(recovered.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(recovered.workflows().get(ASK).candidateComponents())
                .containsEntry("ISOLATED_FINAL_FAILURE", 1).containsEntry(FIRST_COMPONENT, 1);
        RouteVerdictCalculator.ArmResult sharedFailure = failedArm(List.of(
                recheck(PARSE_FAILURE, null), recheck(PARSE_FAILURE, null)));
        records.set(0, withArms(records.get(0), sharedFailure, sharedFailure));
        RouteVerdictCalculator.RouteReport shared = calculate(p, CRITIQUE, records, Map.of());
        assertThat(shared.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(shared.failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T1);
        assertThat(shared.workflows().get(ASK).sharedT1()).isEqualTo(1);
        RouteVerdictCalculator.ArmResult baselineRecovered = failedArm(List.of(
                recheck(null, null), recheck(null, null)));
        records.set(0, withArms(records.get(0), baselineRecovered, sharedFailure));
        RouteVerdictCalculator.RouteReport baselineOriginalFailed = calculate(p, CRITIQUE, records, Map.of());
        assertThat(baselineOriginalFailed.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(baselineOriginalFailed.failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T1);
    }

    @Test void firstSuccessfulRecheckSuppliesScoredComponents() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        records.set(0, withArms(records.get(0), emptyArm(),
                failedArm(List.of(recheck(null, FIRST_COMPONENT), recheck(null, SECOND_COMPONENT)))));
        assertThat(calculate(p, CRITIQUE, records, Map.of()).workflows().get(ASK).candidateComponents())
                .containsKey(FIRST_COMPONENT).doesNotContainKey(SECOND_COMPONENT);
    }

    @Test void malformedCaseSetThrowsAndIncompletePairDoesNot() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        records.add(records.get(0));
        assertThatThrownBy(() -> calculate(p, CRITIQUE, records, Map.of()))
                .isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
        records.remove(records.size() - 1);
        RouteVerdictCalculator.CaseRecord old = records.get(0);
        records.set(0, new RouteVerdictCalculator.CaseRecord(old.id(), old.workflow(), old.route(),
                old.subgroup(), old.id(), "other", old.baseline(), old.candidate()));
        assertThatThrownBy(() -> calculate(p, CRITIQUE, records, Map.of()))
                .isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
        records.set(0, withArms(old, old.baseline(), null));
        assertThat(calculate(p, CRITIQUE, records, Map.of()).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void reserveWithoutTriggerIsReportedAndLongSubgroupsRemainSeparate() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, INTERACTIVE);
        RouteVerdictCalculator.CaseRecord firstLong = records.stream()
                .filter(item -> LONG.equals(item.workflow()) && DIRECT_SUBGROUP.equals(item.subgroup()))
                .findFirst().orElseThrow();
        int index = records.indexOf(firstLong);
        records.set(index, withArms(firstLong, emptyArm(), arm(Set.of(QUALITY_COMPONENT), true)));
        RouteVerdictCalculator.RouteReport report = calculate(p, INTERACTIVE, records,
                Map.of(LONG, reserve(p, 2), STUDY_TIP_WORKFLOW, reserve(p, 0)));
        assertThat(report.workflows().get(STUDY_TIP_WORKFLOW).protocolDeviation()).isTrue();
        Map<String, RouteVerdictCalculator.SubgroupReport> groups = report.workflows().get(LONG).subgroups();
        assertThat(groups).containsOnlyKeys(DIRECT_SUBGROUP, LONG_POOL_SUBGROUP, BOARD_POOL_SUBGROUP);
        assertThat(groups.get(DIRECT_SUBGROUP).candidateOnlyT3()).isEqualTo(1);
        assertThat(groups.get(LONG_POOL_SUBGROUP).candidateOnlyT3()).isZero();
        assertThat(groups.get(BOARD_POOL_SUBGROUP).candidateOnlyT3()).isZero();
    }

    @Test void missingReserveAndUnresolvedReserveAreInconclusive() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        records.set(0, withArms(records.get(0), emptyArm(), arm(Set.of(), true)));
        assertThat(calculate(p, CRITIQUE, records, Map.of()).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        List<RouteVerdictCalculator.ReserveCase> incomplete = reserve(p, 3);
        RouteVerdictCalculator.ReserveCase first = incomplete.get(0);
        incomplete.set(0, new RouteVerdictCalculator.ReserveCase(first.id(), first.baselineEvidence(),
                RouteVerdictCalculator.Evidence.UNSCORABLE, false, true));
        assertThat(calculate(p, CRITIQUE, records, Map.of(ASK, incomplete)).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void providerRejectionUnusableFractionAndFailedSuiteBlock() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        RouteVerdictCalculator.ArmResult rejected = new RouteVerdictCalculator.ArmResult(
                RouteVerdictCalculator.Evidence.SCORED, Set.of(), false, null, List.of(), true, false);
        records.set(0, withArms(records.get(0), emptyArm(), rejected));
        assertThat(calculate(p, CRITIQUE, records, Map.of()).failedTiers())
                .contains(RouteVerdictCalculator.Tier.T1);
        records = cases(p, CRITIQUE);
        RouteVerdictCalculator.ArmResult unusable = new RouteVerdictCalculator.ArmResult(
                RouteVerdictCalculator.Evidence.SCORED, Set.of(), false, null, List.of(), false, true);
        for (int i = 0; i < 11; i++) records.set(i, withArms(records.get(i), emptyArm(), unusable));
        assertThat(calculate(p, CRITIQUE, records, Map.of()).workflows().get(ASK).failedTiers())
                .contains(RouteVerdictCalculator.Tier.T1);
        records = cases(p, CRITIQUE);
        Map<String, Boolean> suites = Map.of(ASK, false, "INTERVIEW_CRITIQUE", true);
        assertThat(RouteVerdictCalculator.calculate(p, CRITIQUE, frozen(p, CRITIQUE), records,
                Map.of(), suites).failedTiers()).contains(RouteVerdictCalculator.Tier.T1);
        RouteVerdictCalculator.RouteReport missingSuite = RouteVerdictCalculator.calculate(
                p, CRITIQUE, frozen(p, CRITIQUE), records, Map.of(), Map.of());
        assertThat(missingSuite.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(missingSuite.inconclusiveTiers()).contains(RouteVerdictCalculator.Tier.T1);
    }

    @Test void unknownWorkflowRouteAndFrozenCaseIdThrow() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        RouteVerdictCalculator.CaseRecord old = records.get(0);
        records.set(0, new RouteVerdictCalculator.CaseRecord(old.id(), "UNKNOWN", old.route(),
                old.subgroup(), old.id(), old.id(), old.baseline(), old.candidate()));
        assertThatThrownBy(() -> calculate(p, CRITIQUE, records, Map.of()))
                .isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
        records.set(0, new RouteVerdictCalculator.CaseRecord(old.id(), old.workflow(), INTERACTIVE,
                old.subgroup(), old.id(), old.id(), old.baseline(), old.candidate()));
        assertThatThrownBy(() -> calculate(p, CRITIQUE, records, Map.of()))
                .isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
        records.set(0, new RouteVerdictCalculator.CaseRecord(UNFROZEN_ID, old.workflow(), old.route(),
                old.subgroup(), UNFROZEN_ID, UNFROZEN_ID, old.baseline(), old.candidate()));
        assertThatThrownBy(() -> calculate(p, CRITIQUE, records, Map.of()))
                .isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
    }

    @Test void solT2AndT1RequirePairedImprovementAndNoNewFailure() throws Exception {
        FrozenGateParameters p = parameters();
        RouteVerdictCalculator.RouteReport pass = calculate(p, CRITIQUE, cases(p, CRITIQUE), Map.of());
        RouteVerdictCalculator.WorkflowReport oldAsk = pass.workflows().get(ASK);
        RouteVerdictCalculator.WorkflowReport failedAsk = new RouteVerdictCalculator.WorkflowReport(
                RouteVerdictCalculator.Verdict.FAIL, Set.of(RouteVerdictCalculator.Tier.T2), Set.of(),
                oldAsk.paired(), oldAsk.d(), oldAsk.candidateOnlyT3(), oldAsk.baselineOnlyT3(),
                oldAsk.sharedT3(), 1, oldAsk.baselineOnlyT2(), oldAsk.sharedT2(),
                oldAsk.candidateOnlyT1(), oldAsk.baselineOnlyT1(), oldAsk.sharedT1(),
                3, false, false,
                oldAsk.candidateComponents(), oldAsk.baselineComponents(), oldAsk.subgroups(), p.sha256, oldAsk.missingPairs(),
                Set.of());
        Map<String, RouteVerdictCalculator.WorkflowReport> workflows = new HashMap<>(pass.workflows());
        workflows.put(ASK, failedAsk);
        RouteVerdictCalculator.RouteReport t2Fail = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.FAIL, Set.of(RouteVerdictCalculator.Tier.T2), Set.of(), 0,
                false, p.sha256, workflows);
        assertThat(SolRule.justified(p, new SolRule.Comparison(t2Fail, pass, 0,
                Map.of(ASK, matchedReserve(p, 3)), Map.of(ASK, matchedReserve(p, 1)),
                Set.of(), Set.of(), true, true, true))).isTrue();
        assertThat(SolRule.justified(p, new SolRule.Comparison(t2Fail, pass, 0,
                Map.of(ASK, matchedReserve(p, 3)), Map.of(ASK, matchedReserve(p, 2)),
                Set.of(), Set.of(), true, true, true))).isFalse();
        RouteVerdictCalculator.RouteReport t1Fail = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.FAIL, Set.of(RouteVerdictCalculator.Tier.T1), Set.of(), 0,
                false, p.sha256, pass.workflows());
        assertThat(SolRule.justified(p, new SolRule.Comparison(t1Fail, pass, 0,
                Map.of(), Map.of(), Set.of(PARSE_FAILURE), Set.of(), true, true, true))).isTrue();
        assertThat(SolRule.justified(p, new SolRule.Comparison(t1Fail, pass, 0,
                Map.of(), Map.of(), Set.of(PARSE_FAILURE), Set.of(PARSE_FAILURE), true, true, true))).isFalse();
    }

    @Test void solPairedThresholdAndInconclusiveLunaAndDeadlineSafety() throws Exception {
        FrozenGateParameters p = parameters();
        RouteVerdictCalculator.RouteReport pass = calculate(p, CRITIQUE, cases(p, CRITIQUE), Map.of());
        RouteVerdictCalculator.RouteReport t3Fail = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.FAIL, Set.of(RouteVerdictCalculator.Tier.T3), Set.of(), 5,
                false, p.sha256, pass.workflows());
        RouteVerdictCalculator.RouteReport inconclusive = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.INCONCLUSIVE, Set.of(), Set.of(RouteVerdictCalculator.Tier.T1), 0,
                false, p.sha256, pass.workflows());
        SolRule.Comparison enough = new SolRule.Comparison(t3Fail, pass, -3, Map.of(), Map.of(),
                Set.of(), Set.of(), true, true, true);
        assertThat(SolRule.justified(p, enough)).isTrue();
        assertThat(SolRule.justified(p, new SolRule.Comparison(t3Fail, pass, -2, Map.of(), Map.of(),
                Set.of(), Set.of(), true, true, true))).isFalse();
        assertThat(SolRule.justified(p, new SolRule.Comparison(inconclusive, pass, -10, Map.of(), Map.of(),
                Set.of(), Set.of(), true, true, true))).isFalse();
        assertThat(SolRule.justified(p, new SolRule.Comparison(t3Fail, pass, -3, Map.of(), Map.of(),
                Set.of(), Set.of(), true, false, true))).isFalse();
        assertThat(DeadlineRule.classify(t3Fail))
                .isEqualTo(DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE);
        assertThat(DeadlineRule.classify(inconclusive))
                .isEqualTo(DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE);
        for (RouteVerdictCalculator.Tier tier : List.of(RouteVerdictCalculator.Tier.T1,
                RouteVerdictCalculator.Tier.T2)) {
            RouteVerdictCalculator.RouteReport critical = new RouteVerdictCalculator.RouteReport(
                    RouteVerdictCalculator.Verdict.FAIL, Set.of(tier), Set.of(), 0,
                    false, p.sha256, pass.workflows());
            assertThat(DeadlineRule.classify(critical))
                    .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        }
        RouteVerdictCalculator.RouteReport incident = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.PASS, Set.of(), Set.of(), 0,
                true, p.sha256, pass.workflows());
        assertThat(DeadlineRule.classify(incident))
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
    }
}
