package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
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

/** Owner-disposition mutation checks for preregistration sections 4, 8 and 9, Revision 2.2. */
class OwnerDispositionRuleTest {
    private static final String ASK = "ASK_COMPANION";
    private static final String CRITIQUE = GateTestFixture.CRITIQUE;
    private static final String RESERVE_PREFIX = "reserve-";
    private static final String ID_SEPARATOR = "-";
    @TempDir Path temp;

    private FrozenGateParameters parameters() throws Exception {
        byte[] bytes = Files.readAllBytes(GateTestFixture.SOURCE);
        Path file = temp.resolve("gates.json");
        Files.write(file, bytes);
        Files.writeString(temp.resolve("gates.json.sha256"),
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
        for (String workflow : p.routes.get(route).workflows())
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) {
                String id = workflow + ID_SEPARATOR + i;
                result.add(new RouteVerdictCalculator.CaseRecord(id, workflow, route, null,
                        id, id, emptyArm(), emptyArm()));
            }
        return result;
    }
    private static RouteVerdictCalculator.CaseRecord withArms(RouteVerdictCalculator.CaseRecord old,
                                                               RouteVerdictCalculator.ArmResult baseline,
                                                               RouteVerdictCalculator.ArmResult candidate) {
        return new RouteVerdictCalculator.CaseRecord(old.id(), old.workflow(), old.route(), old.subgroup(),
                old.id(), old.id(), baseline, candidate);
    }
    private static List<RouteVerdictCalculator.ReserveCase> reserve(FrozenGateParameters p, int candidateOnly) {
        List<RouteVerdictCalculator.ReserveCase> result = new ArrayList<>();
        for (int i = 0; i < p.reserveSize; i++)
            result.add(new RouteVerdictCalculator.ReserveCase(RESERVE_PREFIX + i,
                    RouteVerdictCalculator.Evidence.SCORED, RouteVerdictCalculator.Evidence.SCORED,
                    false, i < candidateOnly));
        return result;
    }

    private static RouteVerdictCalculator.OwnerDisposition disposition(String caseId,
            RouteVerdictCalculator.DispositionValue value) {
        return new RouteVerdictCalculator.OwnerDisposition(ASK, caseId, value,
                RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION);
    }

    private RouteVerdictCalculator.RouteReport withDisposition(FrozenGateParameters p,
            List<RouteVerdictCalculator.CaseRecord> records,
            Map<String, List<RouteVerdictCalculator.ReserveCase>> reserve,
            List<RouteVerdictCalculator.OwnerDisposition> dispositions) {
        return RouteVerdictCalculator.calculate(p, CRITIQUE, frozen(p, CRITIQUE), records, reserve,
                Map.of(ASK, true, "INTERVIEW_CRITIQUE", true), dispositions);
    }

    @Test void undisposedPrimaryCriticalBlocksWorkflowRouteDeadlineAndSol() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        String id = records.get(0).id();
        records.set(0, withArms(records.get(0), emptyArm(), arm(Set.of(), true)));
        RouteVerdictCalculator.RouteReport report = withDisposition(p, records, Map.of(ASK, reserve(p, 0)), List.of());
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(report.workflows().get(ASK).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(report.workflows().get(ASK).inconclusiveTiers()).contains(RouteVerdictCalculator.Tier.T2);
        assertThat(report.undisposedCriticalCaseIds().get(ASK)).containsExactly(id);
        assertThat(DeadlineRule.classify(report)).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        RouteVerdictCalculator.RouteReport noLegacyIncidentFlag = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.INCONCLUSIVE, Set.of(), Set.of(RouteVerdictCalculator.Tier.T2), 0,
                false, p.sha256, report.workflows());
        assertThat(DeadlineRule.classify(noLegacyIncidentFlag))
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        RouteVerdictCalculator.RouteReport passed = withDisposition(p, records, Map.of(ASK, reserve(p, 0)),
                List.of(disposition(id, RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED)));
        assertThat(passed.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        RouteVerdictCalculator.RouteReport failed = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.FAIL, Set.of(RouteVerdictCalculator.Tier.T3), Set.of(), 5,
                false, p.sha256, passed.workflows());
        RouteVerdictCalculator.RouteReport apparentPass = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.PASS, Set.of(), Set.of(), 0,
                false, p.sha256, report.workflows());
        assertThat(SolRule.justified(p, new SolRule.Comparison(failed, apparentPass, -3, Map.of(), Map.of(),
                Set.of(), Set.of(), true, true, true))).isFalse();
        RouteVerdictCalculator.RouteReport undisposedLuna = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.FAIL, Set.of(RouteVerdictCalculator.Tier.T3), Set.of(), 5,
                false, p.sha256, report.workflows());
        assertThat(SolRule.justified(p, new SolRule.Comparison(undisposedLuna, passed, -3, Map.of(), Map.of(),
                Set.of(), Set.of(), true, true, true))).isFalse();
    }

    @Test void undisposedReserveCriticalBlocksPassAndBlockingDispositionFails() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        String primaryId = records.get(0).id();
        records.set(0, withArms(records.get(0), emptyArm(), arm(Set.of(), true)));
        List<RouteVerdictCalculator.OwnerDisposition> primary = List.of(
                disposition(primaryId, RouteVerdictCalculator.DispositionValue.NOT_A_DEFECT_ON_REVIEW));
        RouteVerdictCalculator.RouteReport unresolved = withDisposition(p, records, Map.of(ASK, reserve(p, 1)), primary);
        assertThat(unresolved.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(unresolved.undisposedCriticalCaseIds().get(ASK)).containsExactly(RESERVE_PREFIX + 0);
        List<RouteVerdictCalculator.OwnerDisposition> cleared = new ArrayList<>(primary);
        cleared.add(disposition(RESERVE_PREFIX + 0, RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED));
        assertThat(withDisposition(p, records, Map.of(ASK, reserve(p, 1)), cleared).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        cleared.set(1, disposition(RESERVE_PREFIX + 0, RouteVerdictCalculator.DispositionValue.DEFECT_BLOCKS_ROUTE));
        assertThat(withDisposition(p, records, Map.of(ASK, reserve(p, 1)), cleared).failedTiers())
                .contains(RouteVerdictCalculator.Tier.T2);
    }

    @Test void reserveCriticalWithoutPrimaryTriggerStillNeedsDisposition() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        RouteVerdictCalculator.RouteReport report = withDisposition(p, records,
                Map.of(ASK, reserve(p, 1)), List.of());
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(report.undisposedCriticalCaseIds().get(ASK)).containsExactly(RESERVE_PREFIX + 0);
        assertThat(report.workflows().get(ASK).inconclusiveTiers()).contains(RouteVerdictCalculator.Tier.T2);
        assertThat(DeadlineRule.classify(report)).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
    }

    @Test void nonDefectDispositionCannotClearReserveFailureAndSharedDebtNeedsNone() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        String id = records.get(0).id();
        records.set(0, withArms(records.get(0), emptyArm(), arm(Set.of(), true)));
        List<RouteVerdictCalculator.OwnerDisposition> dispositions = new ArrayList<>();
        dispositions.add(disposition(id, RouteVerdictCalculator.DispositionValue.NOT_A_DEFECT_ON_REVIEW));
        for (int i = 0; i < p.t2FailCount; i++)
            dispositions.add(disposition(RESERVE_PREFIX + i,
                    RouteVerdictCalculator.DispositionValue.NOT_A_DEFECT_ON_REVIEW));
        assertThat(withDisposition(p, records, Map.of(ASK, reserve(p, p.t2FailCount)), dispositions).failedTiers())
                .contains(RouteVerdictCalculator.Tier.T2);
        records.set(0, withArms(records.get(0), arm(Set.of(), true), arm(Set.of(), true)));
        assertThat(withDisposition(p, records, Map.of(), List.of()).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        Map<String, List<RouteVerdictCalculator.ReserveCase>> noReserve = Map.of();
        assertThatThrownBy(() -> withDisposition(p, records, noReserve, dispositions))
                .isInstanceOf(RouteVerdictCalculator.InvalidOwnerDisposition.class);
    }

    @Test void unknownAndDuplicateDispositionsAreProtocolDeviation() throws Exception {
        FrozenGateParameters p = parameters();
        List<RouteVerdictCalculator.CaseRecord> records = cases(p, CRITIQUE);
        RouteVerdictCalculator.OwnerDisposition extra = disposition(records.get(0).id(),
                RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED);
        Map<String, List<RouteVerdictCalculator.ReserveCase>> noReserve = Map.of();
        List<RouteVerdictCalculator.OwnerDisposition> extraOnly = List.of(extra);
        assertThatThrownBy(() -> withDisposition(p, records, noReserve, extraOnly))
                .isInstanceOf(RouteVerdictCalculator.InvalidOwnerDisposition.class);
        records.set(0, withArms(records.get(0), emptyArm(), arm(Set.of(), true)));
        Map<String, List<RouteVerdictCalculator.ReserveCase>> reserve = Map.of(ASK, reserve(p, 0));
        List<RouteVerdictCalculator.OwnerDisposition> duplicates = List.of(extra, extra);
        assertThatThrownBy(() -> withDisposition(p, records, reserve, duplicates))
                .isInstanceOf(RouteVerdictCalculator.InvalidOwnerDisposition.class);
        RouteVerdictCalculator.OwnerDisposition unknown = new RouteVerdictCalculator.OwnerDisposition(
                "UNKNOWN", extra.caseId(), extra.value(), extra.reasonCode());
        List<RouteVerdictCalculator.OwnerDisposition> unknownOnly = List.of(unknown);
        assertThatThrownBy(() -> withDisposition(p, records, reserve, unknownOnly))
                .isInstanceOf(RouteVerdictCalculator.InvalidOwnerDisposition.class);
        RouteVerdictCalculator.OwnerDisposition unknownCase = disposition("unknown-case",
                RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED);
        List<RouteVerdictCalculator.OwnerDisposition> unknownCaseOnly = List.of(unknownCase);
        assertThatThrownBy(() -> withDisposition(p, records, reserve, unknownCaseOnly))
                .isInstanceOf(RouteVerdictCalculator.InvalidOwnerDisposition.class);
    }

}
