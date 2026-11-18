package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real case and file fixtures for Option II wiring, separate from the classification matrix. */
class FrbOptionTwoWiringTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String ROUTE = "CRITIQUE";
    private static final String ASK = "ASK_COMPANION";
    private static final String INTERVIEW = "INTERVIEW_CRITIQUE";
    private static final String INTERACTIVE = "INTERACTIVE_QUIZ";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String CASE = ASK + "-0";
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

    private static RouteVerdictCalculator.RouteReport calculate(FrozenGateParameters p,
            List<RouteVerdictCalculator.CaseRecord> cases,
            List<RouteVerdictCalculator.OwnerDisposition> dispositions,
            Map<String, Integer> widths) {
        boolean askDrawn = cases.stream().anyMatch(row -> row.workflow().equals(ASK)
                && row.baseline() != null && row.candidate() != null
                && !row.baseline().critical() && row.candidate().critical());
        Map<String, Boolean> decisions = Map.of(ASK, askDrawn, INTERVIEW, false);
        Map<String, List<RouteVerdictCalculator.ReserveCase>> reserves = askDrawn
                ? Map.of(ASK, ReceiptGapDrawnReserveTest.reserve(0, 0)) : Map.of();
        return RouteVerdictCalculator.calculate(p, ROUTE, ReceiptGapDrawnReserveTest.frozen(p),
                cases, reserves, Map.of(ASK, true, INTERVIEW, true), dispositions,
                decisions, widths);
    }

    private static RouteVerdictCalculator.OwnerDisposition disposition(
            RouteVerdictCalculator.DispositionValue value,
            RouteVerdictCalculator.DispositionReason reason) {
        return new RouteVerdictCalculator.OwnerDisposition(ASK, CASE, value, reason);
    }

    @Test void vetoIsDistinctFromStatisticalT2AndCannotLaunchSol() throws Exception {
        FrozenGateParameters p = parameters();
        var cases = ReceiptGapDrawnReserveTest.cases(p);
        ReceiptGapDrawnReserveTest.set(cases, CASE, ReceiptGapDrawnReserveTest.scored(0),
                ReceiptGapDrawnReserveTest.scored(1));
        var acknowledged = calculate(p, cases, List.of(disposition(
                RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION)), Map.of());
        var vetoed = calculate(p, cases, List.of(disposition(
                RouteVerdictCalculator.DispositionValue.DEFECT_BLOCKS_ROUTE,
                RouteVerdictCalculator.DispositionReason.BLOCKING_CORRECTNESS)), Map.of());
        var undisposed = calculate(p, cases, List.of(), Map.of());
        assertThat(vetoed.verdict()).isEqualTo(acknowledged.verdict());
        assertThat(vetoed.failedTiers()).isEqualTo(acknowledged.failedTiers());
        assertThat(vetoed.workflows().get(ASK).candidateOnlyT2())
                .isEqualTo(acknowledged.workflows().get(ASK).candidateOnlyT2());
        assertThat(undisposed.workflows().get(ASK).candidateOnlyT2())
                .isEqualTo(acknowledged.workflows().get(ASK).candidateOnlyT2());
        assertThat(vetoed.workflows().get(ASK).ownerVetoCaseIds()).containsExactly(CASE);
        assertThat(vetoed.workflows().get(ASK).causes()).contains("OWNER_VETO");

        var lunaT3 = new RouteVerdictCalculator.RouteReport(RouteVerdictCalculator.Verdict.FAIL,
                Set.of(RouteVerdictCalculator.Tier.T3), Set.of(), 0, false, p.sha256,
                acknowledged.workflows(), acknowledged.frb());
        var lunaT3Veto = new RouteVerdictCalculator.RouteReport(RouteVerdictCalculator.Verdict.FAIL,
                Set.of(RouteVerdictCalculator.Tier.T3), Set.of(), 0, false, p.sha256,
                vetoed.workflows(), vetoed.frb());
        assertThat(SolRule.justified(p, comparison(lunaT3, acknowledged))).isTrue();
        assertThat(SolRule.justified(p, comparison(lunaT3, vetoed))).isFalse();
        assertThat(SolRule.justified(p, comparison(lunaT3Veto, acknowledged))).isFalse();
    }

    private static SolRule.Comparison comparison(RouteVerdictCalculator.RouteReport luna,
                                                   RouteVerdictCalculator.RouteReport sol) {
        return new SolRule.Comparison(luna, sol, -1000, Map.of(), Map.of(), Set.of(), Set.of(),
                true, true, true);
    }

    private static List<RouteVerdictCalculator.CaseRecord> classACases(FrozenGateParameters p) {
        List<RouteVerdictCalculator.CaseRecord> rows = new ArrayList<>();
        for (String workflow : p.routes.get(INTERACTIVE).workflows()) {
            List<String> groups = new ArrayList<>();
            p.workflows.get(workflow).subgroups().forEach((group, count) -> {
                for (int i = 0; i < count; i++) groups.add(group);
            });
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) {
                String id = workflow + "-" + i;
                rows.add(new RouteVerdictCalculator.CaseRecord(id, workflow, INTERACTIVE,
                        groups.isEmpty() ? null : groups.get(i), id, id,
                        ReceiptGapDrawnReserveTest.scored(0), ReceiptGapDrawnReserveTest.scored(0), 1));
            }
        }
        return rows;
    }

    private static RouteVerdictCalculator.RouteReport classAReport(FrozenGateParameters p,
            List<RouteVerdictCalculator.CaseRecord> cases, Map<String, Integer> widths) {
        return classAReport(p, cases, widths, false);
    }

    private static RouteVerdictCalculator.RouteReport classAReport(FrozenGateParameters p,
            List<RouteVerdictCalculator.CaseRecord> cases, Map<String, Integer> widths,
            boolean suiteNotExecuted) {
        Map<String, Set<String>> frozen = new java.util.LinkedHashMap<>();
        Map<String, Boolean> suites = new java.util.LinkedHashMap<>();
        Map<String, Boolean> decisions = new java.util.LinkedHashMap<>();
        for (String workflow : p.routes.get(INTERACTIVE).workflows()) {
            Set<String> ids = new java.util.TreeSet<>();
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++)
                ids.add(workflow + "-" + i);
            frozen.put(workflow, ids);
            suites.put(workflow, suiteNotExecuted && workflow.equals(NOTE) ? null : true);
            if (p.frbClassBWorkflows.contains(workflow)) decisions.put(workflow, false);
        }
        return RouteVerdictCalculator.calculate(p, INTERACTIVE, frozen, cases, Map.of(),
                suites, List.of(), decisions, widths);
    }

    private FrbPipelineTest.Fixture pipelineFixture() throws Exception {
        return pipelineFixture(false, false);
    }

    private FrbPipelineTest.Fixture pipelineFixture(boolean drawn, boolean unresolvedReserve) throws Exception {
        FrbPipelineTest fixtureFactory = new FrbPipelineTest();
        Field field = FrbPipelineTest.class.getDeclaredField("temp");
        field.setAccessible(true);
        field.set(fixtureFactory, temp);
        return fixtureFactory.fixture(drawn, 0, 1, false, unresolvedReserve);
    }

    @Test void postLockPairingValidationIsFrbOnly() throws Exception {
        FrozenGateParameters p = parameters();
        var cases = ReceiptGapDrawnReserveTest.cases(p);
        ReceiptGapDrawnReserveTest.set(cases, CASE, ReceiptGapDrawnReserveTest.scored(0),
                ReceiptGapDrawnReserveTest.scored(1));
        var humanClear = List.of(disposition(
                RouteVerdictCalculator.DispositionValue.NOT_A_DEFECT_ON_REVIEW,
                RouteVerdictCalculator.DispositionReason.REVIEWED_NOT_DEFECT));
        var wrongPair = List.of(disposition(
                RouteVerdictCalculator.DispositionValue.DEFECT_BLOCKS_ROUTE,
                RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION));
        assertThatThrownBy(() -> calculate(p, cases, humanClear, Map.of()))
                .isInstanceOf(RouteVerdictCalculator.InvalidOwnerDisposition.class);
        assertThatThrownBy(() -> calculate(p, cases, wrongPair, Map.of()))
                .isInstanceOf(RouteVerdictCalculator.InvalidOwnerDisposition.class);
        var nonCritical = ReceiptGapDrawnReserveTest.cases(p);
        var valid = List.of(disposition(RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION));
        assertThatThrownBy(() -> calculate(p, nonCritical, valid, Map.of()))
                .isInstanceOf(RouteVerdictCalculator.InvalidOwnerDisposition.class);
    }

    @Test void classAReasonsAndAbsentCaseAreSeparated() throws Exception {
        FrozenGateParameters p = parameters();
        String id = NOTE + "-0";
        var missingEvidence = classACases(p);
        ReceiptGapDrawnReserveTest.set(missingEvidence, id, ReceiptGapDrawnReserveTest.scored(0),
                ReceiptGapDrawnReserveTest.evidence(RouteVerdictCalculator.Evidence.MISSING));
        var existing = classAReport(p, missingEvidence, Map.of());
        assertThat(existing.workflows().get(NOTE).unresolvedCaseIdsByCause())
                .containsKey("MISSING").doesNotContainKey("MISSING_RUN_DATA");
        assertThat(existing.workflows().get(NOTE).unresolvedCaseIdsByCause().get("MISSING"))
                .contains(id);
        var absent = new ArrayList<>(classACases(p));
        absent.removeIf(row -> row.id().equals(id));
        var noRecord = classAReport(p, absent, Map.of(id, 1));
        assertThat(noRecord.workflows().get(NOTE).unresolvedCaseIdsByCause().get("MISSING_RUN_DATA"))
                .contains(id);
        assertThat(noRecord.workflows().get(NOTE).causes()).contains("MISSING_RUN_DATA");
    }

    @Test void unexecutedSuiteCauseReachesClassifierWithoutChangingPinnedLegacyShape() throws Exception {
        FrozenGateParameters p = parameters();
        var report = classAReport(p, classACases(p), Map.of(), true);
        var note = report.workflows().get(NOTE);
        assertThat(note.causes()).isNull();
        assertThat(note.unresolvedCaseIdsByCause()).containsKey("SUITE_NOT_EXECUTED");
        Map<String, String> expansion = new java.util.LinkedHashMap<>();
        for (String workflow : p.routes.get(INTERACTIVE).workflows())
            if (!p.frbClassBWorkflows.contains(workflow)) expansion.put(workflow, "NOT_TRIGGERED");
        var input = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of(), expansion, Map.of());
        var classification = DeadlineRule.classifyFrb(report, input);
        assertThat(classification.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(classification.causes()).contains("SUITE_NOT_EXECUTED");
    }

    @Test void dispositionFileRationaleIsRequiredOnlyForFrb() throws Exception {
        Path file = temp.resolve("dispositions.json");
        var blank = new RouteVerdictCalculator.OwnerDisposition(INTERVIEW, CASE,
                RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION, " ");
        JSON.writeValue(file.toFile(), List.of(blank));
        assertThatThrownBy(() -> ResultsPipeline.dispositions(file, true))
                .isInstanceOf(RouteVerdictCalculator.InvalidOwnerDisposition.class);
        assertThat(ResultsPipeline.dispositions(file, false)).hasSize(1);
        var absent = new RouteVerdictCalculator.OwnerDisposition(INTERVIEW, CASE,
                RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION);
        JSON.writeValue(file.toFile(), List.of(absent));
        assertThatThrownBy(() -> ResultsPipeline.dispositions(file, true))
                .isInstanceOf(RouteVerdictCalculator.InvalidOwnerDisposition.class);
    }

    @Test void pipelineWritesTopLevelPerRouteClassificationAndRefusesUnknownRoute() throws Exception {
        FrbPipelineTest.Fixture fixture = pipelineFixture();
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), fixture.frb());
        JsonNode report = JSON.readTree(result.report().toFile());
        JsonNode classification = report.path("classification").path("INTERACTIVE_QUIZ");
        assertThat(classification.path("classification").asText()).isEqualTo("RETURN_TO_OWNER");
        assertThat(classification.path("causes").toString()).contains("LEDGER_NOT_SUPPLIED");
        assertThat(classification.path("causes").toString()).contains("REVIEW_RECORD_MISSING");
        assertThat(classification.path("flags").toString()).contains("COST_REVIEW_UNDETERMINED");
        assertThat(report.path("routes").path("INTERACTIVE_QUIZ").has("classification")).isFalse();
        assertThat(report.path("classification").isObject()).isTrue();
        var inputs = new ResultsPipeline.FrbInput(fixture.frb().bundle(),
                fixture.frb().reserveRecordFile(), fixture.frb().verification(),
                fixture.frb().provenance(), Map.of("UNKNOWN_ROUTE", new FrbClassificationInput(
                        Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false,
                        Set.of(), Map.of(), Map.of())));
        assertThatThrownBy(() -> ResultsPipeline.runFrb(fixture.run(), inputs))
                .isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void pendingAdjudicationInBundleReturnsToOwner() throws Exception {
        FrbPipelineTest.Fixture fixture = pipelineFixture();
        AdjudicatedLoaded loaded = fixture.frb().bundle().evidence();
        Map<String, AdjudicatedFacts> facts = new java.util.LinkedHashMap<>(loaded.byCaseArm());
        String identity = facts.keySet().iterator().next();
        AdjudicatedFacts old = facts.get(identity);
        facts.put(identity, new AdjudicatedFacts(old.scoredQuestions(), old.criticalQuestions(),
                old.qualityFailQuestions(), old.unresolvedQuestions(), 1, old.doubleMisses(),
                old.scorerFailureCriticals(), old.humanCleared(), old.subgroup()));
        FrbEvidenceBundle original = fixture.frb().bundle();
        FrbEvidenceBundle pending = new FrbEvidenceBundle(new AdjudicatedLoaded(facts,
                loaded.excludedCaseIds(), loaded.requestedQuestionsByCaseId()),
                original.requestedQuestionsByCaseId(), original.reserveDrawnByWorkflow(),
                original.workflowsAwaitingReserve());
        var frb = new ResultsPipeline.FrbInput(pending, fixture.frb().reserveRecordFile(),
                fixture.frb().verification(), fixture.frb().provenance());
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), frb);
        JsonNode classification = JSON.readTree(result.report().toFile())
                .path("classification").path("INTERACTIVE_QUIZ");
        assertThat(classification.path("classification").asText()).isEqualTo("RETURN_TO_OWNER");
        assertThat(classification.path("causes").toString()).contains("PENDING_REVIEW_ITEMS");
    }

    @Test void reserveOnlyUnresolvedIsAttributedBeforeClassification() throws Exception {
        FrbPipelineTest.Fixture fixture = pipelineFixture(true, true);
        var supplied = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of(), Map.of(NOTE, "NOT_TRIGGERED"), Map.of("safety", "0:head"));
        var frb = new ResultsPipeline.FrbInput(fixture.frb().bundle(),
                fixture.frb().reserveRecordFile(), fixture.frb().verification(),
                fixture.frb().provenance(), Map.of(INTERACTIVE, supplied));
        ResultsPipeline.Result result = ResultsPipeline.runFrb(fixture.run(), frb);
        JsonNode route = JSON.readTree(result.report().toFile());
        assertThat(route.path("routes").path(INTERACTIVE).path("workflows").path("STUDY_TIP")
                .path("causes").toString()).contains("RESERVE_EVIDENCE_UNRESOLVED");
        assertThat(route.path("classification").path(INTERACTIVE).path("causes").toString())
                .contains("RESERVE_EVIDENCE_UNRESOLVED");
        assertThat(route.path("classification").path(INTERACTIVE).path("classification").asText())
                .isEqualTo("RETURN_TO_OWNER");
        assertThat(route.path("ledgerHeadHashes").path(INTERACTIVE).path("safety").asText())
                .isEqualTo("0:head");
    }

    @Test void solRecommendationIsSeparateFromLunaClassification() throws Exception {
        FrozenGateParameters p = parameters();
        var frb = new RouteVerdictCalculator.FrbRouteReport(Tri.PASS_COMPLETE, null,
                p.declaredK(ROUTE), "1/100", "1/100", Map.of("one", 0), Map.of("one", 0));
        var luna = new RouteVerdictCalculator.RouteReport(RouteVerdictCalculator.Verdict.FAIL,
                Set.of(RouteVerdictCalculator.Tier.T3), Set.of(), 1000, false,
                p.sha256, Map.of(), frb);
        var sol = new RouteVerdictCalculator.RouteReport(RouteVerdictCalculator.Verdict.PASS,
                Set.of(), Set.of(), 0, false, p.sha256, Map.of(), frb);
        Path lunaFile = temp.resolve("luna.json");
        Path solFile = temp.resolve("sol.json");
        Path ownerFile = temp.resolve("owner.json");
        Path output = temp.resolve("decision.json");
        JSON.writeValue(lunaFile.toFile(), comparisonReport(p, "LUNA", luna, true));
        JSON.writeValue(solFile.toFile(), comparisonReport(p, "SOL", sol, false));
        JSON.writeValue(ownerFile.toFile(), Map.of("route", ROUTE,
                "lunaReportSha256", FreezeRecord.sha256(lunaFile),
                "solReportSha256", FreezeRecord.sha256(solFile),
                "economicsAndOtherGatesPass", true, "investigationComplete", true,
                "samePrimaryInputs", true));
        assertThat(ResultsPipeline.compareSol(lunaFile, solFile, ownerFile,
                temp.resolve("parameters.json"), output)).isTrue();
        assertThat(JSON.readTree(output.toFile()).path("solRecommendation").asText())
                .isEqualTo("JUSTIFIED");
        assertThat(JSON.readTree(lunaFile.toFile()).path("classification").path(ROUTE).asText())
                .isEqualTo("RETURN_TO_OWNER");
    }

    @Test void solRecommendationIsNotJustifiedWhenLunaPasses() throws Exception {
        FrozenGateParameters p = parameters();
        var frb = new RouteVerdictCalculator.FrbRouteReport(Tri.PASS_COMPLETE, null,
                p.declaredK(ROUTE), "1/100", "1/100", Map.of("one", 0), Map.of("one", 0));
        var luna = new RouteVerdictCalculator.RouteReport(RouteVerdictCalculator.Verdict.PASS,
                Set.of(), Set.of(), 0, false, p.sha256, Map.of(), frb);
        var sol = new RouteVerdictCalculator.RouteReport(RouteVerdictCalculator.Verdict.PASS,
                Set.of(), Set.of(), 0, false, p.sha256, Map.of(), frb);
        Path lunaFile = temp.resolve("luna-pass.json");
        Path solFile = temp.resolve("sol-pass.json");
        Path ownerFile = temp.resolve("owner-pass.json");
        Path output = temp.resolve("decision-pass.json");
        JSON.writeValue(lunaFile.toFile(), comparisonReport(p, "LUNA", luna, true));
        JSON.writeValue(solFile.toFile(), comparisonReport(p, "SOL", sol, false));
        JSON.writeValue(ownerFile.toFile(), Map.of("route", ROUTE,
                "lunaReportSha256", FreezeRecord.sha256(lunaFile),
                "solReportSha256", FreezeRecord.sha256(solFile),
                "economicsAndOtherGatesPass", true, "investigationComplete", true,
                "samePrimaryInputs", true));
        assertThat(ResultsPipeline.compareSol(lunaFile, solFile, ownerFile,
                temp.resolve("parameters.json"), output)).isFalse();
        assertThat(JSON.readTree(output.toFile()).path("solRecommendation").asText())
                .isEqualTo("NOT_JUSTIFIED");
    }

    @Test void legacyPipelineReportHasNoFrbClassificationObject() throws Exception {
        ResultsPipelineTest fixtureFactory = new ResultsPipelineTest();
        Field field = ResultsPipelineTest.class.getDeclaredField("temp");
        field.setAccessible(true);
        field.set(fixtureFactory, temp);
        var fixtureMethod = ResultsPipelineTest.class.getDeclaredMethod("fixture",
                boolean.class, boolean.class, boolean.class);
        fixtureMethod.setAccessible(true);
        Object fixture = fixtureMethod.invoke(fixtureFactory, false, false, false);
        var inputMethod = fixture.getClass().getDeclaredMethod("input");
        inputMethod.setAccessible(true);
        ResultsPipeline.Input input = (ResultsPipeline.Input) inputMethod.invoke(fixture);
        ResultsPipeline.Result result = ResultsPipeline.run(input);
        assertThat(JSON.readTree(result.report().toFile()).has("classification")).isFalse();
    }

    private static ObjectNode comparisonReport(FrozenGateParameters p, String arm,
            RouteVerdictCalculator.RouteReport route, boolean luna) {
        ObjectNode root = JSON.createObjectNode();
        root.put("parameterSha256", p.sha256);
        root.put("freezeSha256", "freeze");
        root.put("caseSetSha256", "cases");
        root.put("candidateArm", arm);
        root.putObject("receiptSha256").put("BASELINE_MINI", "mini").put("BASELINE_41", "paid");
        root.putObject("routes").set(ROUTE, JSON.valueToTree(route));
        if (luna) root.putObject("classification").put(ROUTE, "RETURN_TO_OWNER");
        return root;
    }
}
