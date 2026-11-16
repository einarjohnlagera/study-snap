package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrbCalculatorAlignmentTest {
    private static final String CRITIQUE = "CRITIQUE";
    private static final String ASK = "ASK_COMPANION";
    private static final String INTERVIEW = "INTERVIEW_CRITIQUE";
    private static final String LUNA = "LUNA";
    private static final String PAID = "BASELINE_41";
    private static final String HUMAN = "HUMAN_OWNER";
    private static final String RESOURCE =
            "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json";
    @TempDir Path temp;

    private record Evidence(AdjudicatedLoaded loaded, ReserveDecisionRecord.WorkflowDecision interviewDecision) {}

    private static ObjectNode score(String id, String workflow, boolean critical) {
        ObjectNode row = IntegritySupport.JSON.createObjectNode();
        row.put("itemId", id).put("workflow", workflow).put("round", 1)
                .put("scorerId", HUMAN).put("scorerKind", "HUMAN")
                .put("taskFulfilled", true).put("grounded", true).put("actionable", true)
                .put("noCriticalMisleading", !critical).put("critical", critical)
                .put("borderline", false).put("adjudication", false);
        if (critical) row.put("reasonCode", "CRITICAL_MISLEADING");
        else row.putNull("reasonCode");
        return row;
    }

    private Evidence evidence(boolean missing, boolean discordant) throws Exception {
        var export = BlindReviewFixtures.classBAll(temp, false);
        Path key = temp.resolve("arm-key.jsonl");
        Path seal = ArmKeyGateFixtures.seal(temp, key);
        Path finding = ArmKeyGateFixtures.findings(temp);
        Path lock = ArmKeyGateFixtures.lock(temp, seal, finding, LockRecord.Kind.PRIMARY);
        var opened = ArmKeyAccess.open(key, seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, finding)), Set.of(LockRecord.Kind.PRIMARY),
                temp.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED, "FRB alignment");
        List<String> rows = new ArrayList<>();
        for (var item : export.manifest()) {
            var keyed = export.armKey().stream().filter(row -> row.itemId().equals(item.itemId()))
                    .findFirst().orElseThrow();
            if (missing && keyed.workflow().name().equals(INTERVIEW)
                    && keyed.arm() == ClassBScoreAggregator.Arm.CANDIDATE) continue;
            rows.add(score(item.itemId(), item.workflow().name(), discordant
                    && keyed.workflow().name().equals(INTERVIEW)
                    && keyed.arm() == ClassBScoreAggregator.Arm.BASELINE).toString());
        }
        Path scores = temp.resolve("scores.jsonl");
        Files.write(scores, rows);
        var validated = ClassBScoreFiles.validate(scores, export.manifest(),
                Map.of(HUMAN, ClassBScoreFiles.ScorerKind.HUMAN));
        var decisions = ReserveDecisionRecord.compute(temp.resolve("packets"), validated,
                ClassAScoreSchemaV2Test.MAPPING, Set.of(ASK, INTERVIEW));
        var interviewDecision = decisions.stream().filter(row -> row.workflow().equals(INTERVIEW))
                .findFirst().orElseThrow();
        var loaded = ClassBAdjudicatedConverter.classB(opened,
                ClassBAdjudicatedConverter.ExportKind.PRIMARY, validated,
                ClassAScoreSchemaV2Test.MAPPING, PairMap.fromPackets(temp.resolve("packets")),
                Set.of(ASK, INTERVIEW), Set.of("ask", "interview"),
                new ClassBAdjudicatedConverter.ArmLabels(LUNA, ignored -> PAID));
        return new Evidence(loaded, interviewDecision);
    }

    private FrozenGateParameters parameters() throws Exception {
        Path original = Path.of(RESOURCE);
        Path copy = temp.resolve("parameters.json");
        Files.copy(original, copy);
        Files.copy(original.resolveSibling(original.getFileName() + ".sha256"),
                copy.resolveSibling(copy.getFileName() + ".sha256"));
        return FrozenGateParameters.load(copy);
    }

    private record RouteInput(Map<String, Set<String>> frozen,
            List<RouteVerdictCalculator.CaseRecord> cases) {}

    private static RouteVerdictCalculator.ArmResult arm(AdjudicatedFacts facts,
            RouteVerdictCalculator.Evidence evidence) {
        return new RouteVerdictCalculator.ArmResult(evidence, Set.of(), facts.critical(),
                null, List.of(), false, false, facts.criticalQuestions(), facts.unresolvedQuestions());
    }

    private static RouteInput cases(FrozenGateParameters p, AdjudicatedLoaded loaded,
            boolean unmeteredCandidate) {
        Map<String, Set<String>> frozen = new LinkedHashMap<>();
        List<RouteVerdictCalculator.CaseRecord> cases = new ArrayList<>();
        for (String workflow : p.routes.get(CRITIQUE).workflows()) {
            Set<String> ids = new java.util.LinkedHashSet<>();
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) {
                String id = i == 0 ? workflow.equals(ASK) ? "ask" : "interview" : workflow + "-" + i;
                ids.add(id);
                var zero = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                        Set.of(), false, null, List.of(), false, false);
                RouteVerdictCalculator.ArmResult baseline = zero, candidate = zero;
                if (i == 0) {
                    baseline = arm(loaded.get(id, PAID), RouteVerdictCalculator.Evidence.SCORED);
                    candidate = arm(loaded.get(id, LUNA),
                            unmeteredCandidate && workflow.equals(INTERVIEW)
                                    ? RouteVerdictCalculator.Evidence.UNMETERED
                                    : loaded.get(id, LUNA).unresolvedQuestions() > 0
                                        ? RouteVerdictCalculator.Evidence.UNSCORABLE
                                        : RouteVerdictCalculator.Evidence.SCORED);
                }
                cases.add(new RouteVerdictCalculator.CaseRecord(id, workflow, CRITIQUE,
                        null, id, id, baseline, candidate, 1));
            }
            frozen.put(workflow, ids);
        }
        return new RouteInput(frozen, cases);
    }

    private static Map<String, List<RouteVerdictCalculator.ReserveCase>> reserve() {
        List<RouteVerdictCalculator.ReserveCase> rows = new ArrayList<>();
        for (int i = 0; i < 30; i++) rows.add(new RouteVerdictCalculator.ReserveCase(
                "reserve-" + i, RouteVerdictCalculator.Evidence.SCORED,
                RouteVerdictCalculator.Evidence.SCORED, false, false));
        return Map.of(INTERVIEW, rows);
    }

    private static RouteVerdictCalculator.RouteReport calculate(FrozenGateParameters p, RouteInput input,
            Map<String, List<RouteVerdictCalculator.ReserveCase>> reserve, boolean drawn) {
        return RouteVerdictCalculator.calculate(p, CRITIQUE, input.frozen(), input.cases(), reserve,
                Map.of(ASK, true, INTERVIEW, true), List.of(),
                Map.of(ASK, false, INTERVIEW, drawn), Map.of());
    }

    @Test void honestUnresolvedHumanFindingIsInconclusiveWithoutInvalidReserve() throws Exception {
        FrozenGateParameters p = parameters();
        Evidence evidence = evidence(true, false);
        assertThat(evidence.interviewDecision().primaryUnresolved()).isTrue();
        assertThat(evidence.interviewDecision().reserveDrawn()).isFalse();
        assertThat(evidence.loaded().get("interview", LUNA).unresolvedQuestions()).isEqualTo(1);
        var input = cases(p, evidence.loaded(), false);
        var report = calculate(p, input, Map.of(), evidence.interviewDecision().reserveDrawn());
        assertThat(report.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(report.workflows().get(INTERVIEW).verdict())
                .isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
    }

    @Test void resolvedDiscordanceRequiresTheRecordedReserve() throws Exception {
        FrozenGateParameters p = parameters();
        Evidence evidence = evidence(false, true);
        assertThat(evidence.interviewDecision().primaryUnresolved()).isFalse();
        assertThat(evidence.interviewDecision().reserveDrawn()).isTrue();
        var input = cases(p, evidence.loaded(), false);
        var report = calculate(p, input, reserve(), evidence.interviewDecision().reserveDrawn());
        assertThat(report.workflows().get(INTERVIEW).frb().reserveDrawn()).isTrue();
        assertThatThrownBy(() -> calculate(p, input, Map.of(), false))
                .isInstanceOf(InvalidReserve.class);
    }

    @Test void reportReceiptGapScenarioWithoutPinningItsOutcome() throws Exception {
        FrozenGateParameters p = parameters();
        Evidence evidence = evidence(false, true);
        assertThat(evidence.interviewDecision().reserveDrawn()).isTrue();
        var input = cases(p, evidence.loaded(), true);
        try {
            var report = calculate(p, input, reserve(), true);
            System.out.println("FRB_RECEIPT_GAP_SCENARIO=REPORT:" + report.verdict());
        } catch (RuntimeException exception) {
            System.out.println("FRB_RECEIPT_GAP_SCENARIO=THROW:" + exception.getClass().getSimpleName()
                    + ":" + exception.getMessage());
        }
    }
}
