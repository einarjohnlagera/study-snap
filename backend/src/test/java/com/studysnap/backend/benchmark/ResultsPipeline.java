package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Preregistration sections 1, 4, 7, 8 and 9: offline evidence-to-verdict and reserve-trigger pipeline. */
final class ResultsPipeline {
    static final class InvalidInput extends RuntimeException {
        InvalidInput(String message) { super(message); }
        InvalidInput(String message, Throwable cause) { super(message, cause); }
    }
    record ArmFiles(Path receipt, Path delivered, Path recheckReceipt, Path recheckDirectory) {}
    record ReserveInput(ArmFiles baseline, ArmFiles candidate, ScoreEvidence.FilesA classA,
                        ScoreEvidence.FilesB classB) {}
    record Input(Path repoRoot, Path freeze, Path parameters, Path mapping, Map<String, Path> manifests,
                 ArmFiles baselineMini, ArmFiles baseline41, ArmFiles candidate,
                 ScoreEvidence.FilesA classA, ScoreEvidence.FilesB classB,
                 Map<String, ReserveInput> reserves, Path compatibilityReports, Instant headCommitTime,
                 Path ownerDispositions, Path report, Path triggerRecord, boolean synthetic) {}
    record Result(Map<String, RouteVerdictCalculator.RouteReport> routes, Path report, Path triggerRecord,
                  Map<String, Set<String>> triggers) {}
    private record FrozenCase(String workflow, String subgroup, int requiredQuestions,
                              String manifestRowSha256) {}
    private record ArmEvidence(Stage1Receipt receipt, Map<String, Stage1Receipt.CaseRow> rows,
                               Map<String, DeliveredOutputs.Line> outputs, RecheckReceipt rechecks,
                               Path recheckDirectory) {}
    private static final String SUCCESS_OUTCOME = "SUCCESS";
    private static final String BASELINE_MINI_ARM = "BASELINE_MINI";
    private static final String BASELINE_41_ARM = "BASELINE_41";
    private static final String WORKFLOW_FIELD = "workflow";
    private static final String HEAD_TIME_ERROR = "Cannot read HEAD commit time: ";
    private static final String CLASS_A_PREFIX = "classA";
    private static final String CASE_ID_FIELD = "caseId";
    private static final String FIXTURE_ID_FIELD = "fixtureId";
    private static final String SUBGROUP_FIELD = "subgroup";
    private static final String REQUESTED_COUNT_FIELD = "requestedQuestionCount";
    private static final String QUALITY_KIND = "quality";
    private static final String FREEZE_SHA_FIELD = "freezeSha256";
    private static final String PARAMETER_SHA_FIELD = "parameterSha256";
    private static final String ROUTES_FIELD = "routes";
    private static final String RESERVE_RESULTS_FIELD = "reserveResults";
    private static final String MISSING_RECHECK_DIRECTORY = "Missing recheck output directory: ";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private ResultsPipeline() {}

    static Result run(Input input) {
        try {
            requireInputs(input);
            FreezeRecord freeze = FreezeRecord.load(input.freeze());
            FrozenGateParameters parameters = FrozenGateParameters.load(input.parameters());
            EvidenceMapping mapping = EvidenceMapping.load(input.mapping());
            if (!parameters.sha256.equals(freeze.parametersSha256())
                    || !mapping.sha256.equals(freeze.evidenceMappingSha256()))
                throw new InvalidInput("Freeze parameter or mapping SHA-256 differs: " + input.freeze());
            if (!freeze.costForecastUsdPerDeliveredCase().keySet().equals(parameters.workflows.keySet()))
                throw new InvalidInput("Missing or extra workflow cost ceiling: " + input.freeze());
            if (!freeze.compatibilitySuites().values().containsAll(parameters.workflows.keySet()))
                throw new InvalidInput("Missing workflow compatibility suite: " + input.freeze());
            Map<String, FrozenCase> frozen = loadCases(input.manifests(), freeze);
            Map<String, FrozenCase> frozenReserve = loadReserveCases(input.manifests(), freeze);
            String caseSetHash = caseSetHash(frozen);
            Stage1Receipt candidate = Stage1Receipt.read(input.candidate().receipt());
            FreezeRecord.Arm candidateArm = freeze.arms().get(candidate.arm());
            if (candidateArm == null) throw new InvalidInput("Candidate arm absent from freeze: " + candidate.arm());
            if (input.synthetic())
                FreezeGuard.verify(input.freeze(), input.repoRoot(), candidate.arm(), candidate.model(),
                        input.manifests(), candidateArm.workflows(), freeze.gitCommit());
            else FreezeGuard.verify(input.freeze(), input.repoRoot(), candidate.arm(), candidate.model(),
                    input.manifests(), candidateArm.workflows());
            String freezeHash = FreezeRecord.sha256(input.freeze());
            ArmEvidence mini = loadArm(input.baselineMini(), freezeHash, caseSetHash, freeze.gitCommit());
            ArmEvidence paid = loadArm(input.baseline41(), freezeHash, caseSetHash, freeze.gitCommit());
            ArmEvidence target = loadArm(input.candidate(), freezeHash, caseSetHash, freeze.gitCommit());
            for (ArmEvidence arm : List.of(mini, paid, target)) if (arm.rechecks() != null
                    && !freeze.recheckEligibleFailureClasses().equals(
                            arm.rechecks().recheckEligibleFailureClasses()))
                throw new InvalidInput("Recheck eligible classes differ from freeze: " + arm.receipt().arm());
            if (!mini.receipt().arm().equals(BASELINE_MINI_ARM) || !paid.receipt().arm().equals(BASELINE_41_ARM)
                    || !freeze.arms().get(candidate.arm()).model().equals(candidate.model()))
                throw new InvalidInput("Receipt arm or model differs from freeze");
            ScoreEvidence.Loaded scores = ScoreEvidence.load(input.classA(), input.classB(), mapping);
            Instant headTime = input.synthetic() ? input.headCommitTime() : committedHeadTime(input.repoRoot());
            Map<String, CompatibilitySuiteEvidence.State> suites = CompatibilitySuiteEvidence.read(
                    input.compatibilityReports(), freeze.compatibilitySuites(), headTime);
            List<RouteVerdictCalculator.OwnerDisposition> dispositions = dispositions(input.ownerDispositions());
            Map<String, List<RouteVerdictCalculator.CaseRecord>> records = new LinkedHashMap<>();
            Map<String, Set<String>> ids = new LinkedHashMap<>();
            Map<String, Object> costs = new LinkedHashMap<>();
            Map<String, String> excludedEvidence = new LinkedHashMap<>();
            for (var item : frozen.entrySet()) {
                String id = item.getKey();
                FrozenCase fixture = item.getValue();
                String workflow = fixture.workflow();
                String route = parameters.workflows.get(workflow).route();
                ArmEvidence baseline = freeze.arms().get(BASELINE_41_ARM).workflows().contains(workflow)
                        ? paid : mini;
                Stage1Receipt.CaseRow baselineRow = baseline.rows().get(id);
                Stage1Receipt.CaseRow candidateRow = target.rows().get(id);
                requireRow(fixture, baselineRow, baseline.receipt().arm());
                requireRow(fixture, candidateRow, target.receipt().arm());
                RouteVerdictCalculator.ArmResult b = arm(id, fixture, baseline, scores, mapping,
                        freeze.costForecastUsdPerDeliveredCase().get(workflow));
                RouteVerdictCalculator.ArmResult c = arm(id, fixture, target, scores, mapping,
                        freeze.costForecastUsdPerDeliveredCase().get(workflow));
                boolean recheckedFailure = hasEligibleFailureWithRechecks(id, baseline, freeze)
                        || hasEligibleFailureWithRechecks(id, target, freeze);
                if (scores.excluded().contains(id) && !recheckedFailure) {
                    b = missing(); c = missing();
                    excludedEvidence.put(id, "MISSING");
                } else if (scores.excluded().contains(id) && recheckedFailure && baselineRow != null
                        && baselineRow.finalSuccess() && b.evidence() == RouteVerdictCalculator.Evidence.UNSCORABLE
                        && structurallyComplete(baseline.outputs().get(id), fixture)) {
                    b = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                            b.t3Components(), false, null, List.of(), false, false);
                }
                if (candidateRow != null && baselineRow != null && c.providerRejected()) {
                    boolean baselineSuccess = baselineRow.state() == CaseArmOutcome.State.COMPLETE
                            && baselineRow.finalSuccess() && baselineRow.llmCalls().stream()
                                .anyMatch(call -> SUCCESS_OUTCOME.equals(call.outcome()));
                    c = new RouteVerdictCalculator.ArmResult(c.evidence(), c.t3Components(), c.critical(),
                            c.finalFailureClass(), c.rechecks(), baselineSuccess, c.unusable());
                }
                records.computeIfAbsent(route, ignored -> new ArrayList<>()).add(
                        new RouteVerdictCalculator.CaseRecord(id, workflow, route, fixture.subgroup(),
                                baselineRow == null ? null : id, candidateRow == null ? null : id, b, c));
                ids.computeIfAbsent(workflow, ignored -> new LinkedHashSet<>()).add(id);
                BigDecimal ceiling = freeze.costForecastUsdPerDeliveredCase().get(workflow)
                        .multiply(mapping.costTolerance);
                costs.put(id, Map.of(WORKFLOW_FIELD, workflow, "ceilingUsd", ceiling,
                        "baselineConservativeUsd", baselineRow == null ? BigDecimal.ZERO : baselineRow.conservativeUsd(),
                        "baselineBilledUsd", baselineRow == null ? BigDecimal.ZERO : baselineRow.billedUsd(),
                        "candidateConservativeUsd", candidateRow == null ? BigDecimal.ZERO : candidateRow.conservativeUsd(),
                        "candidateBilledUsd", candidateRow == null ? BigDecimal.ZERO : candidateRow.billedUsd()));
            }
            Map<String, Map<String, List<RouteVerdictCalculator.ReserveCase>>> reserveByRoute =
                    loadReserves(input.reserves(), freeze, parameters, mapping, frozenReserve,
                            frozen, freezeHash, caseSetHash);
            Map<String, RouteVerdictCalculator.RouteReport> routeReports = new LinkedHashMap<>();
            Map<String, Object> deadline = new LinkedHashMap<>();
            for (var route : parameters.routes.entrySet()) {
                Map<String, Set<String>> routeIds = new LinkedHashMap<>();
                Map<String, Boolean> routeSuites = new LinkedHashMap<>();
                for (String workflow : route.getValue().workflows()) {
                    routeIds.put(workflow, ids.getOrDefault(workflow, Set.of()));
                    CompatibilitySuiteEvidence.State state = suites.get(workflow);
                    routeSuites.put(workflow, state == null ? null : state.passed());
                }
                // HashMap accepts a null suite value; Map.copyOf does not.
                RouteVerdictCalculator.RouteReport verdict = RouteVerdictCalculator.calculate(parameters,
                        route.getKey(), routeIds, records.getOrDefault(route.getKey(), List.of()),
                        reserveByRoute.getOrDefault(route.getKey(), Map.of()), routeSuites,
                        dispositions.stream().filter(d -> route.getValue().workflows().contains(d.workflow())).toList());
                routeReports.put(route.getKey(), verdict);
                DeadlineRule.Classification classification = DeadlineRule.classify(verdict);
                deadline.put(route.getKey(), classification == DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE
                        ? "DEADLINE FALLBACK, NOT BENCHMARK PASS" : classification.name());
            }
            Path trigger = input.triggerRecord();
            Path scorerRegistry = trigger.resolveSibling(trigger.getFileName() + ".scorers.json");
            DeliveredOutputs.guard(scorerRegistry);
            JSON.writeValue(scorerRegistry.toFile(), input.classB().scorers());
            Map<String, Path> linked = triggerPaths(input, scorerRegistry);
            Map<String, Path> recheckLinks = triggerRecheckPaths(input);
            Map<String, Set<String>> triggers = T2TriggerRecord.recompute(linked, recheckLinks, freezeHash);
            T2TriggerRecord.create(linked, recheckLinks, triggers, freezeHash, trigger);
            writeReport(input, freezeHash, parameters, mapping, mini, paid, target, scores, suites,
                    costs, dispositions, reserveByRoute, routeReports, deadline, excludedEvidence);
            return new Result(Map.copyOf(routeReports), input.report(), trigger, Map.copyOf(triggers));
        } catch (Exception exception) {
            if (exception instanceof InvalidInput invalid) throw invalid;
            throw new InvalidInput("Results pipeline refused: " + exception.getMessage(), exception);
        }
    }

    private static void requireInputs(Input input) {
        if (input == null || input.repoRoot() == null || input.freeze() == null || input.parameters() == null
                || input.mapping() == null || input.manifests() == null || input.baselineMini() == null
                || input.baseline41() == null || input.candidate() == null || input.classA() == null
                || input.classB() == null || input.reserves() == null || input.compatibilityReports() == null
                || input.headCommitTime() == null || input.report() == null || input.triggerRecord() == null)
            throw new InvalidInput("Missing required pipeline input");
        DeliveredOutputs.guard(input.freeze());
        input.manifests().values().forEach(DeliveredOutputs::guard);
        for (ArmFiles arm : List.of(input.baselineMini(), input.baseline41(), input.candidate())) {
            DeliveredOutputs.guard(arm.receipt());
            DeliveredOutputs.guard(arm.delivered());
            if (arm.recheckReceipt() != null && arm.recheckDirectory() == null)
                throw new InvalidInput(MISSING_RECHECK_DIRECTORY + arm.recheckReceipt());
            if (arm.recheckReceipt() != null) DeliveredOutputs.guard(arm.recheckReceipt());
            if (arm.recheckDirectory() != null) DeliveredOutputs.guard(arm.recheckDirectory());
        }
        DeliveredOutputs.guard(input.report());
        DeliveredOutputs.guard(input.triggerRecord());
    }

    private static Instant committedHeadTime(Path repoRoot) {
        try {
            Process process = new ProcessBuilder("git", "show", "-s", "--format=%cI", "HEAD")
                    .directory(repoRoot.toFile()).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.waitFor() != 0) throw new InvalidInput(HEAD_TIME_ERROR + repoRoot);
            return java.time.OffsetDateTime.parse(output).toInstant();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new InvalidInput(HEAD_TIME_ERROR + repoRoot, exception);
        }
    }

    private static Map<String, FrozenCase> loadCases(Map<String, Path> manifests, FreezeRecord freeze) throws IOException {
        Map<String, FrozenCase> result = new LinkedHashMap<>();
        for (String key : List.of("classA.primary", "classB.primary")) {
            Path file = manifests.get(key);
            if (file == null || !FreezeRecord.sha256(file).equals(freeze.manifests().get(file.toAbsolutePath().toString())))
                throw new InvalidInput("Unpinned primary manifest: " + file);
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                var json = JSON.readTree(line);
                String id = json.path(key.startsWith(CLASS_A_PREFIX) ? CASE_ID_FIELD : FIXTURE_ID_FIELD).asText();
                String workflow = Stage1RunPlan.workflowName(json.path(WORKFLOW_FIELD).asText());
                String subgroup = json.path(SUBGROUP_FIELD).isTextual() ? json.path(SUBGROUP_FIELD).asText() : null;
                int required = key.startsWith(CLASS_A_PREFIX) ? json.path(REQUESTED_COUNT_FIELD).asInt() : 1;
                if (id.isBlank() || required < 1 || result.putIfAbsent(id, new FrozenCase(workflow, subgroup,
                        required,
                        FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)))) != null)
                    throw new InvalidInput("Invalid or duplicate frozen case in " + file);
            }
        }
        return result;
    }

    private static String caseSetHash(Map<String, FrozenCase> cases) {
        StringBuilder text = new StringBuilder();
        cases.forEach((id, row) -> text.append(id).append(':').append(row.manifestRowSha256()).append('\n'));
        return FreezeRecord.sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, FrozenCase> loadReserveCases(Map<String, Path> manifests,
                                                            FreezeRecord freeze) throws IOException {
        Map<String, FrozenCase> result = new LinkedHashMap<>();
        for (String key : List.of("classA.reserve", "classB.reserve")) {
            Path file = manifests.get(key);
            if (file == null || !FreezeRecord.sha256(file).equals(freeze.manifests().get(file.toAbsolutePath().toString())))
                throw new InvalidInput("Unpinned reserve manifest: " + file);
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                JsonNode json = JSON.readTree(line);
                String id = json.path(key.startsWith(CLASS_A_PREFIX) ? CASE_ID_FIELD : FIXTURE_ID_FIELD).asText();
                String workflow = Stage1RunPlan.workflowName(json.path(WORKFLOW_FIELD).asText());
                int required = key.startsWith(CLASS_A_PREFIX) ? json.path(REQUESTED_COUNT_FIELD).asInt() : 1;
                String subgroup = json.path(SUBGROUP_FIELD).isTextual() ? json.path(SUBGROUP_FIELD).asText() : null;
                if (id.isBlank() || required < 1 || result.putIfAbsent(id, new FrozenCase(workflow,
                        subgroup, required,
                        FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)))) != null)
                    throw new InvalidInput("Invalid or duplicate reserve manifest row: " + file);
            }
        }
        return result;
    }

    private static ArmEvidence loadArm(ArmFiles files, String freezeHash, String caseSetHash, String head) {
        Stage1Receipt receipt = Stage1Receipt.read(files.receipt());
        if (!freezeHash.equals(receipt.freezeSha256()) || !caseSetHash.equals(receipt.caseSetSha256())
                || !head.equals(receipt.head()))
            throw new InvalidInput("Receipt freeze, case set or HEAD differs: " + files.receipt());
        Map<String, Stage1Receipt.CaseRow> rows = new HashMap<>();
        for (Stage1Receipt.CaseRow row : receipt.cases())
            if (rows.putIfAbsent(row.id(), row) != null) throw new InvalidInput("Duplicate receipt row: " + files.receipt());
        Map<String, DeliveredOutputs.Line> outputs = new HashMap<>();
        for (DeliveredOutputs.Line line : DeliveredOutputs.read(files.delivered(), receipt))
            outputs.put(line.caseId(), line);
        RecheckReceipt rechecks = files.recheckReceipt() == null ? null : RecheckReceipt.read(files.recheckReceipt());
        if (rechecks != null && (!rechecks.head().equals(head) || !rechecks.freezeSha256().equals(freezeHash)
                || !caseSetHash.equals(rechecks.caseSetSha256())
                || !rechecks.arm().equals(receipt.arm()) || !rechecks.model().equals(receipt.model())))
            throw new InvalidInput("Recheck receipt identity differs: " + files.recheckReceipt());
        return new ArmEvidence(receipt, Map.copyOf(rows), Map.copyOf(outputs), rechecks, files.recheckDirectory());
    }

    private static void requireRow(FrozenCase fixture, Stage1Receipt.CaseRow row, String arm) {
        if (row != null && (!fixture.workflow().equals(row.workflow())
                || !fixture.manifestRowSha256().equals(row.manifestRowSha256())))
            throw new InvalidInput("Receipt row differs from frozen manifest: " + arm + "/" + row.id());
    }

    private static RouteVerdictCalculator.ArmResult missing() {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.MISSING,
                Set.of(), false, null, List.of(), false, false);
    }

    private static RouteVerdictCalculator.ArmResult arm(String id, FrozenCase fixture, ArmEvidence source,
            ScoreEvidence.Loaded scores, EvidenceMapping mapping, BigDecimal forecast) {
        Stage1Receipt.CaseRow row = source.rows().get(id);
        if (row == null) return missing();
        boolean unmetered = row.llmCalls().stream().anyMatch(call -> "UNMETERED".equals(call.outcome()));
        Set<String> components = new LinkedHashSet<>();
        if (row.finalSuccess() && !row.firstAttemptClean() && row.llmCalls().stream()
                .anyMatch(call -> "SCHEMA_OR_PARSE".equals(call.outcome()) && call.attempt() == 1))
            components.add(mapping.component("recoveredParse"));
        if (row.llmCalls().stream().anyMatch(call -> "TIMEOUT".equals(call.outcome())))
            components.add(mapping.component("timeout"));
        if ("LONG_EXAM".equals(fixture.workflow()) && recoveredFallback(row))
            components.add(mapping.component("recoveredFallback"));
        BigDecimal ceiling = forecast.multiply(mapping.costTolerance);
        if (row.finalSuccess() && mapping.cost(row).compareTo(ceiling) > 0)
            components.add(mapping.component("cost"));
        boolean rejected = row.llmCalls().stream().anyMatch(call -> call.httpStatus() == 400 || call.httpStatus() == 422);
        boolean successCall = row.llmCalls().stream().anyMatch(call -> SUCCESS_OUTCOME.equals(call.outcome()));
        ScoreEvidence.Facts facts = scores.get(id, source.receipt().arm());
        DeliveredOutputs.Line output = source.outputs().get(id);
        RouteVerdictCalculator.Evidence evidence = unmetered ? RouteVerdictCalculator.Evidence.UNMETERED
                : row.state() == CaseArmOutcome.State.INCONCLUSIVE || output == null
                    ? RouteVerdictCalculator.Evidence.MISSING
                : row.state() == CaseArmOutcome.State.COMPLETE && row.finalSuccess()
                    && validOutput(output, fixture, facts) ? RouteVerdictCalculator.Evidence.SCORED
                : RouteVerdictCalculator.Evidence.UNSCORABLE;
        List<RouteVerdictCalculator.Recheck> rechecks = rechecks(id, row, fixture, source, scores, mapping);
        if (row.state() == CaseArmOutcome.State.FAILED && row.failureClass() != null
                && evidence == RouteVerdictCalculator.Evidence.MISSING)
            evidence = RouteVerdictCalculator.Evidence.UNSCORABLE;
        if (evidence == RouteVerdictCalculator.Evidence.UNSCORABLE && facts != null
                && rechecks.stream().anyMatch(recheck -> recheck.evidence() == RouteVerdictCalculator.Evidence.SCORED
                        && recheck.finalFailureClass() == null))
            evidence = RouteVerdictCalculator.Evidence.SCORED;
        if (facts != null && evidence == RouteVerdictCalculator.Evidence.SCORED) {
            if (facts.quality()) components.add(mapping.component(QUALITY_KIND));
        }
        if (!mapping.structuralComponents.containsValue(mapping.component("isolatedFinalFailure"))
                || !mapping.structuralComponents.values().containsAll(components))
            throw new InvalidInput("Structural component absent from mapping: " + components);
        return new RouteVerdictCalculator.ArmResult(evidence, components,
                facts != null && facts.critical(), row.failureClass(), rechecks,
                rejected, !successCall);
    }

    static boolean recoveredFallback(Stage1Receipt.CaseRow row) {
        boolean batch = row.llmCalls().stream().anyMatch(call -> call.groupId() != null
                && call.groupId().startsWith(row.id() + "-batch-"));
        boolean sequential = row.llmCalls().stream().anyMatch(call -> call.groupId() != null
                && call.groupId().startsWith(row.id() + "-seq-"));
        return row.finalSuccess() && "LONG_EXAM".equals(row.workflow()) && batch && sequential;
    }

    private static boolean hasEligibleFailureWithRechecks(String id, ArmEvidence source,
                                                           FreezeRecord freeze) {
        Stage1Receipt.CaseRow row = source.rows().get(id);
        return row != null && row.state() == CaseArmOutcome.State.FAILED
                && freeze.recheckEligibleFailureClasses().contains(row.failureClass())
                && source.rechecks() != null && source.rechecks().cases().stream()
                    .anyMatch(item -> item.caseId().equals(row.id()));
    }

    private static boolean validOutput(DeliveredOutputs.Line output, FrozenCase fixture, ScoreEvidence.Facts facts) {
        return facts != null && structurallyComplete(output, fixture)
                && (output.content().isArray() ? facts.scoredQuestions() == output.content().size()
                        : facts.scoredQuestions() == 1);
    }

    private static boolean structurallyComplete(DeliveredOutputs.Line output, FrozenCase fixture) {
        return output != null && output.state() == CaseArmOutcome.State.COMPLETE && output.content() != null
                && (fixture.requiredQuestions() == 1 && !output.content().isArray()
                    || output.content().isArray() && output.content().size() >= fixture.requiredQuestions());
    }

    private static List<RouteVerdictCalculator.Recheck> rechecks(String id, Stage1Receipt.CaseRow original,
            FrozenCase fixture, ArmEvidence source, ScoreEvidence.Loaded scores, EvidenceMapping mapping) {
        if (source.rechecks() == null) return List.of();
        RecheckReceipt.Case item = source.rechecks().cases().stream()
                .filter(value -> id.equals(value.caseId())).findFirst().orElse(null);
        if (item == null) return List.of();
        if (!java.util.Objects.equals(original.failureClass(), item.originalFailureClass()))
            throw new InvalidInput("Recheck original failure differs: " + id);
        List<RouteVerdictCalculator.Recheck> result = new ArrayList<>();
        for (RecheckReceipt.Repeat repeat : item.repeats()) {
            RouteVerdictCalculator.Evidence evidence = repeat.evidence();
            if (repeat.protocolDeviation() != null) evidence = RouteVerdictCalculator.Evidence.MISSING;
            ScoreEvidence.Facts facts = scores.get(id, source.receipt().arm());
            if (source.recheckDirectory() == null) throw new InvalidInput(MISSING_RECHECK_DIRECTORY + id);
            Path path = source.recheckDirectory().resolve(id).resolve("recheck-" + repeat.recheckIndex())
                    .resolve("delivered-outputs.jsonl");
            DeliveredOutputs.Line line = DeliveredOutputs.readRecheck(path, source.rechecks(), item, repeat);
            if (!java.util.Objects.equals(line.failureClass(), repeat.finalFailureClass())
                    || repeat.finalFailureClass() == null && line.state() != CaseArmOutcome.State.COMPLETE
                    || repeat.finalFailureClass() != null && line.state() == CaseArmOutcome.State.COMPLETE)
                throw new InvalidInput("Recheck outcome differs from delivered evidence: " + path);
            if (repeat.finalFailureClass() == null && evidence == RouteVerdictCalculator.Evidence.SCORED) {
                if (!validOutput(line, fixture, facts)) evidence = RouteVerdictCalculator.Evidence.UNSCORABLE;
            }
            result.add(new RouteVerdictCalculator.Recheck(evidence,
                    facts != null && facts.quality() ? Set.of(mapping.component(QUALITY_KIND)) : Set.of(),
                    facts != null && facts.critical(), repeat.finalFailureClass()));
        }
        return List.copyOf(result);
    }

    private static List<RouteVerdictCalculator.OwnerDisposition> dispositions(Path file) throws IOException {
        if (file == null || !Files.exists(file)) return List.of();
        DeliveredOutputs.guard(file);
        return JSON.readValue(file.toFile(), new TypeReference<>() {});
    }

    private static Map<String, Map<String, List<RouteVerdictCalculator.ReserveCase>>> loadReserves(
            Map<String, ReserveInput> inputs, FreezeRecord freeze, FrozenGateParameters parameters,
            EvidenceMapping mapping, Map<String, FrozenCase> frozenReserve,
            Map<String, FrozenCase> frozen, String freezeHash, String primaryCaseSetHash) {
        Map<String, Map<String, List<RouteVerdictCalculator.ReserveCase>>> result = new HashMap<>();
        if (inputs.isEmpty()) return result;
        for (var entry : inputs.entrySet()) {
            String workflow = entry.getKey();
            if (!parameters.workflows.containsKey(workflow)) throw new InvalidInput("Unknown reserve workflow: " + workflow);
            ReserveInput input = entry.getValue();
            Stage1Receipt b = Stage1Receipt.read(input.baseline().receipt());
            Stage1Receipt c = Stage1Receipt.read(input.candidate().receipt());
            if (!freezeHash.equals(b.freezeSha256()) || !freezeHash.equals(c.freezeSha256())
                    || !primaryCaseSetHash.equals(b.caseSetSha256())
                    || !primaryCaseSetHash.equals(c.caseSetSha256()))
                throw new InvalidInput("Reserve receipt freeze or case set differs: " + workflow);
            ArmEvidence baseline = loadArm(input.baseline(), freezeHash, b.caseSetSha256(), freeze.gitCommit());
            ArmEvidence candidate = loadArm(input.candidate(), freezeHash, c.caseSetSha256(), freeze.gitCommit());
            ScoreEvidence.Loaded scores = ScoreEvidence.load(input.classA(), input.classB(), mapping);
            List<RouteVerdictCalculator.ReserveCase> rows = new ArrayList<>();
            for (Stage1Receipt.CaseRow row : c.cases()) {
                FrozenCase fixture = frozenReserve.get(row.id());
                if (fixture == null || !workflow.equals(row.workflow()) || frozen.containsKey(row.id()))
                    throw new InvalidInput("Reserve case overlaps primary or workflow differs: " + row.id());
                requireRow(fixture, row, c.arm());
                requireRow(fixture, baseline.rows().get(row.id()), b.arm());
                RouteVerdictCalculator.ArmResult left = arm(row.id(), fixture, baseline, scores, mapping,
                        freeze.costForecastUsdPerDeliveredCase().get(workflow));
                RouteVerdictCalculator.ArmResult right = arm(row.id(), fixture, candidate, scores, mapping,
                        freeze.costForecastUsdPerDeliveredCase().get(workflow));
                rows.add(new RouteVerdictCalculator.ReserveCase(row.id(), left.evidence(), right.evidence(),
                        left.critical(), right.critical()));
            }
            result.computeIfAbsent(parameters.workflows.get(workflow).route(), ignored -> new HashMap<>())
                    .put(workflow, List.copyOf(rows));
        }
        return result;
    }

    private static Map<String, Path> triggerPaths(Input input, Path scorerRegistry) {
        Map<String, Path> paths = new HashMap<>();
        paths.put(T2TriggerRecord.CANDIDATE, input.candidate().receipt());
        paths.put(T2TriggerRecord.CANDIDATE_OUTPUT, input.candidate().delivered());
        paths.put(T2TriggerRecord.MINI, input.baselineMini().receipt());
        paths.put(T2TriggerRecord.MINI_OUTPUT, input.baselineMini().delivered());
        paths.put(T2TriggerRecord.FOUR_ONE, input.baseline41().receipt());
        paths.put(T2TriggerRecord.FOUR_ONE_OUTPUT, input.baseline41().delivered());
        paths.put(T2TriggerRecord.MAPPING, input.mapping());
        paths.put(T2TriggerRecord.PARAMETERS, input.parameters());
        paths.put(T2TriggerRecord.A_KEY, input.classA().key());
        paths.put(T2TriggerRecord.A_PRIMARY, input.classA().primary());
        paths.put(T2TriggerRecord.A_REVIEWS, input.classA().reviews());
        paths.put(T2TriggerRecord.A_ADJUDICATIONS, input.classA().adjudications());
        paths.put(T2TriggerRecord.A_EXCLUSIONS, input.classA().exclusions());
        paths.put(T2TriggerRecord.B_KEY, input.classB().key());
        paths.put(T2TriggerRecord.B_SCORES, input.classB().scores());
        paths.put(T2TriggerRecord.SCORER_REGISTRY, scorerRegistry);
        return paths;
    }

    private static Map<String, Path> triggerRecheckPaths(Input input) {
        Map<String, Path> paths = new HashMap<>();
        for (ArmFiles arm : List.of(input.baselineMini(), input.baseline41(), input.candidate())) {
            if (arm.recheckReceipt() == null) continue;
            RecheckReceipt receipt = RecheckReceipt.read(arm.recheckReceipt());
            paths.put(receipt.arm() + "/receipt", arm.recheckReceipt());
            for (RecheckReceipt.Case item : receipt.cases()) {
                for (RecheckReceipt.Repeat repeat : item.repeats()) {
                    Path output = arm.recheckDirectory().resolve(item.caseId())
                            .resolve("recheck-" + repeat.recheckIndex()).resolve("delivered-outputs.jsonl");
                    paths.put(receipt.arm() + "/" + item.caseId() + "/" + repeat.recheckIndex(), output);
                }
            }
        }
        return paths;
    }

    private static void writeReport(Input input, String freezeHash, FrozenGateParameters parameters,
            EvidenceMapping mapping, ArmEvidence mini, ArmEvidence paid, ArmEvidence candidate,
            ScoreEvidence.Loaded scores, Map<String, CompatibilitySuiteEvidence.State> suites,
            Map<String, Object> costs, List<RouteVerdictCalculator.OwnerDisposition> dispositions,
            Map<String, Map<String, List<RouteVerdictCalculator.ReserveCase>>> reserves,
            Map<String, RouteVerdictCalculator.RouteReport> routes, Map<String, Object> deadline,
            Map<String, String> excludedEvidence) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(FREEZE_SHA_FIELD, freezeHash);
        report.put(PARAMETER_SHA_FIELD, parameters.sha256);
        report.put("mappingSha256", mapping.sha256);
        report.put("caseSetSha256", candidate.receipt().caseSetSha256());
        report.put("candidateArm", candidate.receipt().arm());
        report.put("candidateModel", candidate.receipt().model());
        report.put("receiptSha256", Map.of(BASELINE_MINI_ARM, FreezeRecord.sha256(input.baselineMini().receipt()),
                BASELINE_41_ARM, FreezeRecord.sha256(input.baseline41().receipt()),
                candidate.receipt().arm(), FreezeRecord.sha256(input.candidate().receipt())));
        report.put("deliveredOutputSha256", Map.of(BASELINE_MINI_ARM,
                FreezeRecord.sha256(input.baselineMini().delivered()), BASELINE_41_ARM,
                FreezeRecord.sha256(input.baseline41().delivered()), candidate.receipt().arm(),
                FreezeRecord.sha256(input.candidate().delivered())));
        Map<String, String> recheckHashes = new LinkedHashMap<>();
        for (ArmFiles files : List.of(input.baselineMini(), input.baseline41(), input.candidate()))
            if (files.recheckReceipt() != null)
                recheckHashes.put(Stage1Receipt.read(files.receipt()).arm(), FreezeRecord.sha256(files.recheckReceipt()));
        report.put("recheckReceiptSha256", recheckHashes);
        report.put("scoreFileSha256", scores.hashes());
        Map<String, Object> reserveHashes = new LinkedHashMap<>();
        for (var item : input.reserves().entrySet()) {
            ReserveInput reserve = item.getValue();
            reserveHashes.put(item.getKey(), Map.of(
                    "baselineReceipt", FreezeRecord.sha256(reserve.baseline().receipt()),
                    "candidateReceipt", FreezeRecord.sha256(reserve.candidate().receipt()),
                    "baselineDeliveredOutputs", FreezeRecord.sha256(reserve.baseline().delivered()),
                    "candidateDeliveredOutputs", FreezeRecord.sha256(reserve.candidate().delivered()),
                    "classAKey", FreezeRecord.sha256(reserve.classA().key()),
                    "classAPrimary", FreezeRecord.sha256(reserve.classA().primary()),
                    "classAReviews", FreezeRecord.sha256(reserve.classA().reviews()),
                    "classAAdjudications", FreezeRecord.sha256(reserve.classA().adjudications()),
                    "classBKey", FreezeRecord.sha256(reserve.classB().key()),
                    "classBScores", FreezeRecord.sha256(reserve.classB().scores())));
        }
        report.put("reserveFileSha256", reserveHashes);
        Map<String, String> suiteStates = new LinkedHashMap<>();
        suites.forEach((workflow, state) -> suiteStates.put(workflow,
                state.passed() == null ? "NOT_EXECUTED" : state.passed() ? "EXECUTED_PASS" : "EXECUTED_FAIL"));
        report.put("compatibilitySuitesRegressionEvidenceOnly", suiteStates);
        report.put("costPerDeliveredCase", costs);
        report.put("ownerDispositionCount", dispositions.size());
        report.put("excludedFixtureIdsInconclusive", scores.excluded());
        report.put("excludedFixtureEvidence", excludedEvidence);
        report.put(ROUTES_FIELD, routes);
        report.put(RESERVE_RESULTS_FIELD, reserves);
        report.put("deadlineClassification", deadline);
        Map<String, Integer> margins = new LinkedHashMap<>();
        parameters.routes.forEach((name, route) -> margins.put(name, route.margin()));
        report.put("routeMargins", margins);
        JSON.writeValue(input.report().toFile(), report);
    }

    static boolean compareSol(Path lunaReport, Path solReport, Path ownerFile,
                              Path parametersFile, Path output) {
        try {
            DeliveredOutputs.guard(lunaReport);
            DeliveredOutputs.guard(solReport);
            DeliveredOutputs.guard(output);
            SolDecisionInputs owner = SolDecisionInputs.load(ownerFile);
            if (!FreezeRecord.sha256(lunaReport).equals(owner.lunaReportSha256())
                    || !FreezeRecord.sha256(solReport).equals(owner.solReportSha256()))
                throw new InvalidInput("Owner Sol report links differ: " + ownerFile);
            FrozenGateParameters parameters = FrozenGateParameters.load(parametersFile);
            JsonNode lunaJson = JSON.readTree(lunaReport.toFile());
            JsonNode solJson = JSON.readTree(solReport.toFile());
            if (!lunaJson.path(PARAMETER_SHA_FIELD).asText().equals(parameters.sha256)
                    || !solJson.path(PARAMETER_SHA_FIELD).asText().equals(parameters.sha256)
                    || !lunaJson.path(FREEZE_SHA_FIELD).asText().equals(solJson.path(FREEZE_SHA_FIELD).asText())
                    || !lunaJson.path("caseSetSha256").asText().equals(solJson.path("caseSetSha256").asText())
                    || !lunaJson.path("receiptSha256").path(BASELINE_MINI_ARM).asText()
                        .equals(solJson.path("receiptSha256").path(BASELINE_MINI_ARM).asText())
                    || !lunaJson.path("receiptSha256").path(BASELINE_41_ARM).asText()
                        .equals(solJson.path("receiptSha256").path(BASELINE_41_ARM).asText())
                    || !"LUNA".equals(lunaJson.path("candidateArm").asText())
                    || !solJson.path("candidateArm").asText().startsWith("SOL"))
                throw new InvalidInput("Sol comparison freeze or parameter differs");
            JsonNode lunaRoute = lunaJson.path(ROUTES_FIELD).path(owner.route());
            JsonNode solRoute = solJson.path(ROUTES_FIELD).path(owner.route());
            if (lunaRoute.isMissingNode() || solRoute.isMissingNode())
                throw new InvalidInput("Sol route missing from report: " + owner.route());
            RouteVerdictCalculator.RouteReport luna = JSON.treeToValue(lunaRoute,
                    RouteVerdictCalculator.RouteReport.class);
            RouteVerdictCalculator.RouteReport sol = JSON.treeToValue(solRoute,
                    RouteVerdictCalculator.RouteReport.class);
            int pairedD = sol.d() - luna.d();
            SolRule.Comparison comparison = new SolRule.Comparison(luna, sol, pairedD,
                    reserveFlags(lunaJson, owner.route()), reserveFlags(solJson, owner.route()),
                    t1Workflows(luna), t1Workflows(sol), owner.economicsAndOtherGatesPass(),
                    owner.investigationComplete(), owner.samePrimaryInputs());
            boolean justified = SolRule.justified(parameters, comparison);
            JSON.writeValue(output.toFile(), Map.of("route", owner.route(), "solJustified", justified,
                    "solVersusLunaD", pairedD, PARAMETER_SHA_FIELD, parameters.sha256,
                    "lunaReportSha256", owner.lunaReportSha256(), "solReportSha256", owner.solReportSha256(),
                    "ownerDecisionInputSha256", FreezeRecord.sha256(ownerFile),
                    "economicsAndOtherGatesPass", owner.economicsAndOtherGatesPass(),
                    "investigationComplete", owner.investigationComplete(),
                    "samePrimaryInputs", owner.samePrimaryInputs()));
            return justified;
        } catch (Exception exception) {
            if (exception instanceof InvalidInput invalid) throw invalid;
            throw new InvalidInput("Sol comparison refused: " + exception.getMessage(), exception);
        }
    }

    private static Map<String, Map<String, Boolean>> reserveFlags(JsonNode report, String route) {
        Map<String, Map<String, Boolean>> workflows = new HashMap<>();
        JsonNode rows = report.path(RESERVE_RESULTS_FIELD).path(route);
        rows.properties().forEach(item -> {
            Map<String, Boolean> cases = new HashMap<>();
            item.getValue().forEach(value -> cases.put(value.path("id").asText(),
                    value.path("candidateCritical").asBoolean() && !value.path("baselineCritical").asBoolean()));
            workflows.put(item.getKey(), Map.copyOf(cases));
        });
        return Map.copyOf(workflows);
    }

    private static Set<String> t1Workflows(RouteVerdictCalculator.RouteReport report) {
        Set<String> names = new HashSet<>();
        report.workflows().forEach((name, row) -> {
            if (row.candidateOnlyT1() > 0) names.add(name);
        });
        return Set.copyOf(names);
    }
}
