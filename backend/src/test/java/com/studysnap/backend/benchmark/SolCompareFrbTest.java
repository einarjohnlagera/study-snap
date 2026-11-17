package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SolCompareFrbTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String RESOURCE = "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    @TempDir Path temp;

    private FrozenGateParameters parameters() throws Exception {
        Path source = Path.of(RESOURCE);
        Path copy = temp.resolve("gates.json");
        Files.copy(source, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(source.resolveSibling(source.getFileName() + ".sha256"),
                copy.resolveSibling(copy.getFileName() + ".sha256"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return FrozenGateParameters.load(copy);
    }

    private FrozenGateParameters legacyParameters() throws Exception {
        Path source = Path.of("src/test/java/com/studysnap/backend/benchmark/frozen-route-gates.json");
        Path copy = temp.resolve("gates.json");
        Files.copy(source, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(source.resolveSibling(source.getFileName() + ".sha256"),
                copy.resolveSibling(copy.getFileName() + ".sha256"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return FrozenGateParameters.load(copy);
    }

    private static RouteVerdictCalculator.RouteReport report(FrozenGateParameters p) {
        Map<String, Set<String>> frozen = new LinkedHashMap<>();
        Map<String, Integer> widths = new LinkedHashMap<>();
        Map<String, Boolean> suites = new LinkedHashMap<>();
        Map<String, Boolean> decisions = new LinkedHashMap<>();
        for (String workflow : p.routes.get(ROUTE).workflows()) {
            Set<String> ids = new LinkedHashSet<>();
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) {
                String id = workflow + "-" + i;
                ids.add(id);
                widths.put(id, p.frbClassBWorkflows.contains(workflow) ? 1 : 4);
            }
            frozen.put(workflow, ids);
            suites.put(workflow, true);
            if (p.frbClassBWorkflows.contains(workflow)) decisions.put(workflow, false);
        }
        return RouteVerdictCalculator.calculate(p, ROUTE, frozen, List.of(), Map.of(), suites,
                List.of(), decisions, widths);
    }

    private static RouteVerdictCalculator.RouteReport scoredReport(FrozenGateParameters p, int criticalWorkflows) {
        return scoredReport(p, criticalWorkflows, false);
    }

    private static RouteVerdictCalculator.RouteReport scoredReport(FrozenGateParameters p, int criticalWorkflows,
                                                                    boolean missingCandidate) {
        Map<String, Set<String>> frozen = new LinkedHashMap<>();
        Map<String, Boolean> suites = new LinkedHashMap<>();
        Map<String, Boolean> decisions = new LinkedHashMap<>();
        List<RouteVerdictCalculator.CaseRecord> cases = new ArrayList<>();
        List<RouteVerdictCalculator.OwnerDisposition> dispositions = new ArrayList<>();
        int marked = 0;
        for (String workflow : p.routes.get(ROUTE).workflows()) {
            Set<String> ids = new LinkedHashSet<>();
            List<String> groups = new ArrayList<>();
            p.workflows.get(workflow).subgroups().forEach((name, count) -> {
                for (int i = 0; i < count; i++) groups.add(name);
            });
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) {
                String id = workflow + "-" + i;
                ids.add(id);
                int critical = i == 0 && marked < criticalWorkflows ? 1 : 0;
                if (i == 0) marked++;
                var baseline = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                        Set.of(), false, null, List.of(), false, false, 0, 0);
                var candidate = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                        Set.of(), critical > 0, null, List.of(), false, false, critical, 0);
                cases.add(new RouteVerdictCalculator.CaseRecord(id, workflow, ROUTE,
                        groups.isEmpty() ? null : groups.get(i), id, id, baseline,
                        missingCandidate && workflow.equals(NOTE) && i == 1 ? null : candidate,
                        p.frbClassBWorkflows.contains(workflow) ? 1 : 4));
                if (critical > 0)
                    dispositions.add(new RouteVerdictCalculator.OwnerDisposition(workflow, id,
                            RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                            RouteVerdictCalculator.DispositionReason.ACKNOWLEDGED_FOR_MITIGATION));
            }
            frozen.put(workflow, ids);
            suites.put(workflow, true);
            if (p.frbClassBWorkflows.contains(workflow)) decisions.put(workflow, false);
        }
        return RouteVerdictCalculator.calculate(p, ROUTE, frozen, cases, Map.of(), suites,
                dispositions, decisions, Map.of());
    }

    private ObjectNode wrapper(FrozenGateParameters p, String arm,
            RouteVerdictCalculator.RouteReport report) {
        ObjectNode root = JSON.createObjectNode();
        root.put("parameterSha256", p.sha256);
        root.put("freezeSha256", "freeze");
        root.put("caseSetSha256", "cases");
        root.put("candidateArm", arm);
        root.putObject("receiptSha256").put("BASELINE_MINI", "mini").put("BASELINE_41", "full");
        root.putObject("routes").set(ROUTE, JSON.valueToTree(report));
        return root;
    }

    private boolean compare(FrozenGateParameters p, String suffix, ObjectNode luna, ObjectNode sol)
            throws Exception {
        Path lunaFile = temp.resolve("luna-" + suffix + ".json");
        Path solFile = temp.resolve("sol-" + suffix + ".json");
        Path ownerFile = temp.resolve("owner-" + suffix + ".json");
        JSON.writeValue(lunaFile.toFile(), luna);
        JSON.writeValue(solFile.toFile(), sol);
        JSON.writeValue(ownerFile.toFile(), Map.of("route", ROUTE,
                "lunaReportSha256", FreezeRecord.sha256(lunaFile),
                "solReportSha256", FreezeRecord.sha256(solFile),
                "economicsAndOtherGatesPass", true, "investigationComplete", true,
                "samePrimaryInputs", true));
        return ResultsPipeline.compareSol(lunaFile, solFile, ownerFile, temp.resolve("gates.json"),
                temp.resolve("decision-" + suffix + ".json"));
    }

    @Test void realFrbRouteRoundTripsThroughPipelineMapperShape() throws Exception {
        RouteVerdictCalculator.RouteReport source = report(parameters());
        ObjectNode wrapper = JSON.createObjectNode();
        wrapper.putObject("routes").set(ROUTE, JSON.valueToTree(source));
        Path file = temp.resolve("report.json");
        JSON.writeValue(file.toFile(), wrapper);
        var route = JSON.readTree(file.toFile()).path("routes").path(ROUTE);
        var restored = JSON.treeToValue(route, RouteVerdictCalculator.RouteReport.class);
        assertThat(restored).isEqualTo(source);
    }

    @Test void realReportsWriteConjunctionAuditForSelectedAndRefusedSol() throws Exception {
        FrozenGateParameters p = parameters();
        var lunaReport = scoredReport(p, 5);
        var selected = scoredReport(p, 0);
        var refused = scoredReport(p, 1);
        assertThat(lunaReport.failedTiers()).contains(RouteVerdictCalculator.Tier.T2);
        assertThat(lunaReport.workflows().values()).allSatisfy(w ->
                assertThat(w.failedTiers()).doesNotContain(RouteVerdictCalculator.Tier.T2));
        assertThat(selected.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(refused.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(compare(p, "selected", wrapper(p, "LUNA", lunaReport), wrapper(p, "SOL", selected)))
                .isTrue();
        var selectedJson = JSON.readTree(temp.resolve("decision-selected.json").toFile());
        assertThat(selectedJson.path("solJustified").asBoolean()).isTrue();
        assertThat(selectedJson.path("solT2ConjunctionRequired").asBoolean()).isTrue();
        assertThat(selectedJson.path("pValueOk").asBoolean()).isTrue();
        assertThat(selectedJson.path("thirtyPercentOk").asBoolean()).isTrue();
        assertThat(selectedJson.path("threeFewerOk").asBoolean()).isTrue();
        assertThat(selectedJson.path("lunaCriticalTotal").asInt()).isEqualTo(5);
        assertThat(selectedJson.path("solCriticalTotal").asInt()).isZero();
        assertThat(selectedJson.path("selectionNote").asText()).contains("recommendation", "not an approval",
                "regression to the mean");
        assertThat(compare(p, "refused", wrapper(p, "LUNA", lunaReport), wrapper(p, "SOL", refused)))
                .isFalse();
        var refusedJson = JSON.readTree(temp.resolve("decision-refused.json").toFile());
        assertThat(refusedJson.path("solJustified").asBoolean()).isFalse();
        assertThat(refusedJson.path("pValueOk").asBoolean()).isFalse();
        assertThat(refusedJson.path("lunaCriticalTotal").asInt()).isEqualTo(5);
        assertThat(refusedJson.path("solCriticalTotal").asInt()).isEqualTo(1);
        assertThat(lunaReport.frb().candidateCriticalLowByCaseId().keySet())
                .containsExactlyElementsOf(lunaReport.frb().candidateCriticalLowByCaseId().keySet().stream().sorted().toList());
        assertThat(lunaReport.frb().candidateCriticalLowByCaseId()).containsEntry(NOTE + "-0", 1);
        assertThat(lunaReport.frb().candidateCriticalHighByCaseId()).containsEntry(NOTE + "-0", 1);
    }

    @Test void compareRequiresBothMapsAndMatchingCaseIds() throws Exception {
        FrozenGateParameters p = parameters();
        var luna = wrapper(p, "LUNA", scoredReport(p, 5));
        var sol = wrapper(p, "SOL", scoredReport(p, 0));
        var missing = luna.deepCopy();
        ((ObjectNode) missing.path("routes").path(ROUTE).path("frb"))
                .remove("candidateCriticalLowByCaseId");
        assertThatThrownBy(() -> compare(p, "missing", missing, sol))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("Luna report");
        var different = sol.deepCopy();
        ObjectNode frb = (ObjectNode) different.path("routes").path(ROUTE).path("frb");
        ObjectNode low = (ObjectNode) frb.path("candidateCriticalLowByCaseId");
        ObjectNode high = (ObjectNode) frb.path("candidateCriticalHighByCaseId");
        low.remove(NOTE + "-0");
        high.remove(NOTE + "-0");
        low.put("other", 0);
        high.put("other", 0);
        assertThatThrownBy(() -> compare(p, "different", luna, different))
                .isInstanceOf(ResultsPipeline.InvalidInput.class).hasMessageContaining("case IDs differ");
    }

    @Test void unusableCandidatesUseZeroAndFullWidthAndLegacyAddsNothing() throws Exception {
        FrozenGateParameters p = parameters();
        var missing = report(p);
        assertThat(missing.frb().candidateCriticalLowByCaseId()).containsEntry(NOTE + "-0", 0);
        assertThat(missing.frb().candidateCriticalHighByCaseId()).containsEntry(NOTE + "-0", 4);
        assertThat(missing.frb().candidateCriticalHighByCaseId()).containsEntry("STUDY_TIP-0", 1);
        assertThat(missing.frb().candidateCriticalLowByCaseId().keySet()).containsExactlyElementsOf(
                missing.frb().candidateCriticalLowByCaseId().keySet().stream().sorted().toList());
        FrozenGateParameters legacy = legacyParameters();
        var old = report(legacy);
        assertThat(old.frb()).isNull();
        assertThat(JSON.valueToTree(old).has("frb")).isFalse();
        assertThat(compare(legacy, "legacy", wrapper(legacy, "LUNA", old),
                wrapper(legacy, "SOL", old))).isFalse();
        var decision = JSON.readTree(temp.resolve("decision-legacy.json").toFile());
        assertThat(decision.has("solT2ConjunctionRequired")).isFalse();
        assertThat(decision.has("selectionNote")).isFalse();
        assertThat(decision.has("lunaCriticalTotal")).isFalse();
    }

    @Test void duplicateFrozenCaseAcrossWorkflowsIsRejected() throws Exception {
        FrozenGateParameters p = parameters();
        Map<String, Set<String>> frozen = new LinkedHashMap<>();
        Map<String, Integer> widths = new LinkedHashMap<>();
        Map<String, Boolean> decisions = new LinkedHashMap<>();
        for (String workflow : p.routes.get(ROUTE).workflows()) {
            Set<String> ids = new LinkedHashSet<>();
            for (int i = 0; i < p.workflows.get(workflow).primarySize(); i++) {
                String id = workflow + "-" + i;
                ids.add(id);
                widths.put(id, p.frbClassBWorkflows.contains(workflow) ? 1 : 4);
            }
            frozen.put(workflow, ids);
            if (p.frbClassBWorkflows.contains(workflow)) decisions.put(workflow, false);
        }
        var names = p.routes.get(ROUTE).workflows();
        String first = names.get(0);
        String second = names.get(1);
        String duplicate = frozen.get(first).iterator().next();
        String replaced = frozen.get(second).iterator().next();
        frozen.get(second).remove(replaced);
        frozen.get(second).add(duplicate);
        assertThatThrownBy(() -> RouteVerdictCalculator.calculate(p, ROUTE, frozen, List.of(), Map.of(),
                Map.of(), List.of(), decisions, widths))
                .isInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class)
                .hasMessageContaining("Duplicate frozen case ID");
    }

    @Test void compareUsesLunaLowAndSolHighEvenWhenAnArmIsUnusable() throws Exception {
        FrozenGateParameters p = parameters();
        var luna = scoredReport(p, 5, true);
        var sol = scoredReport(p, 0, true);
        assertThat(luna.failedTiers()).contains(RouteVerdictCalculator.Tier.T2);
        assertThat(luna.frb().candidateCriticalLowByCaseId()).containsEntry(NOTE + "-1", 0);
        assertThat(luna.frb().candidateCriticalHighByCaseId()).containsEntry(NOTE + "-1", 4);
        assertThat(compare(p, "bounds", wrapper(p, "LUNA", luna), wrapper(p, "SOL", sol))).isFalse();
        var decision = JSON.readTree(temp.resolve("decision-bounds.json").toFile());
        assertThat(decision.path("lunaCriticalTotal").asInt()).isEqualTo(5);
        assertThat(decision.path("solCriticalTotal").asInt()).isEqualTo(4);
    }

    @Test void routeMapBuilderRejectsDuplicateCaseIdItself() throws Exception {
        FrozenGateParameters p = parameters();
        var method = RouteVerdictCalculator.class.getDeclaredMethod("frbWorkflowInput",
                FrozenGateParameters.class, String.class, Set.class, List.class, List.class,
                Map.class, Map.class, Map.class, Map.class, Map.class);
        method.setAccessible(true);
        String id = NOTE + "-0";
        Map<String, Integer> low = new LinkedHashMap<>();
        Map<String, Integer> high = new LinkedHashMap<>();
        Map<String, Integer> widths = Map.of(id, 4);
        Map<String, Map<String, String>> reasons = new LinkedHashMap<>();
        Object[] args = {p, NOTE, Set.of(id), List.of(), null, Map.of(), widths, reasons, low, high};
        method.invoke(null, args);
        assertThatThrownBy(() -> method.invoke(null, args))
                .hasCauseInstanceOf(RouteVerdictCalculator.InvalidCaseSet.class);
    }
}
