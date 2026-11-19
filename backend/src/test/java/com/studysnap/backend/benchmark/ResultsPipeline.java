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
    /** Key-free FRB inputs. Receipts and delivered outputs are the only arm files here. */
    record ReserveArms(ArmFiles baseline, ArmFiles candidate) {}
    record FrbRunInput(Path repoRoot, Path freeze, Path parameters, Path mapping,
                       Map<String, Path> manifests, ArmFiles baselineMini, ArmFiles baseline41,
                       ArmFiles candidate, Map<String, ReserveArms> reserves,
                       Path compatibilityReports, Instant headCommitTime,
                       Path ownerDispositions, Path report, boolean synthetic) {}
    record ReserveVerification(Path packetDirectory, Path sealFile, Path scoresFile,
                               Map<String, ClassBScoreFiles.ScorerKind> scorers,
                               Set<String> workflowNames, Path primaryLockFile) {}
    record PrimaryExport(Path accessLog, Path sealFile, Path lockFile, Path findingsDirectory,
                         Path exclusionsFileOrNull, String expectedExclusionsSha256OrNull) {}
    /**
     * @param reservePacketDirectory sealed reserve packets; absent with the other R28 inputs only
     *                               permits a synthetic run to skip the provenance readiness check
     * @param scoresFile locked reserve scores; their human coverage is checked, not scoring quality
     * @param scorers the same registry used at lock time must be supplied by the caller; a different
     *                registry is detected only when score validation fails. No operator bundle
     *                assembler currently populates these inputs.
     */
    record ReserveExport(String workflow, Path reserveKeyAccessLog, Path sealFile,
                         Path lockForPrimaryOpening, Path lockForReserveOpening,
                         Path findingsDirectory, Path reservePacketDirectory, Path scoresFile,
                         Map<String, ClassBScoreFiles.ScorerKind> scorers) {
        ReserveExport(String workflow, Path reserveKeyAccessLog, Path sealFile,
                Path lockForPrimaryOpening, Path lockForReserveOpening, Path findingsDirectory) {
            this(workflow, reserveKeyAccessLog, sealFile, lockForPrimaryOpening,
                    lockForReserveOpening, findingsDirectory, null, null, null);
        }
    }
    record ProvenanceLogs(List<PrimaryExport> primaries, Map<String, ReserveExport> reserves) {}
    record FrbInput(FrbEvidenceBundle bundle, Path reserveRecordFile,
                    ReserveVerification verification, ProvenanceLogs provenance,
                    Map<String, FrbClassificationInput> routeClassifications) {
        FrbInput(FrbEvidenceBundle bundle, Path reserveRecordFile,
                 ReserveVerification verification, ProvenanceLogs provenance) {
            this(bundle, reserveRecordFile, verification, provenance, null);
        }
    }
    record Result(Map<String, RouteVerdictCalculator.RouteReport> routes, Path report, Path triggerRecord,
                  Map<String, Set<String>> triggers,
                  Map<String, List<RouteVerdictCalculator.CaseRecord>> caseRecords) {}
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
    private static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    private static final Set<String> CLASS_B_WORKFLOWS = java.util.Arrays.stream(ClassBCaseManifest.Workflow.values())
            .map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private ResultsPipeline() {}

    static Result run(Input input) {
        try {
            requireInputs(input);
            FreezeRecord freeze = FreezeRecord.load(input.freeze());
            if (freeze.recheckEligibleFailureClasses().contains(RecheckPlan.PROVIDER_REJECTION))
                throw new InvalidInput("PROVIDER_REJECTION requires the FRB gate");
            FrozenGateParameters parameters = FrozenGateParameters.load(input.parameters());
            if (parameters.frbEnabled) throw new InvalidInput("FRB parameters require runFrb");
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
            ScoreEvidence.ClassBArmNames classBNames = classBNames(freeze, candidate.arm());
            ScoreEvidence.Loaded scores = ScoreEvidence.load(input.classA(), input.classB(), mapping, classBNames);
            boolean classBSupplied = classBSupplied(input.classB());
            Instant headTime = input.synthetic() ? input.headCommitTime() : committedHeadTime(input.repoRoot());
            Map<String, CompatibilitySuiteEvidence.State> suites = CompatibilitySuiteEvidence.read(
                    input.compatibilityReports(), freeze.compatibilitySuites(), headTime);
            List<RouteVerdictCalculator.OwnerDisposition> dispositions = dispositions(input.ownerDispositions(), false);
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
                guardClassBPair(id, fixture, baseline, target, scores, input.classB(), classBSupplied);
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
                            frozen, freezeHash, caseSetHash, candidate.arm());
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
            Map<String, List<RouteVerdictCalculator.CaseRecord>> immutableRecords = new LinkedHashMap<>();
            records.forEach((route, cases) -> immutableRecords.put(route, List.copyOf(cases)));
            return new Result(Map.copyOf(routeReports), input.report(), trigger, Map.copyOf(triggers),
                    Map.copyOf(immutableRecords));
        } catch (Exception exception) {
            if (exception instanceof InvalidInput invalid) throw invalid;
            throw new InvalidInput("Results pipeline refused: " + exception.getMessage(), exception);
        }
    }

    /**
     * FRB consumes caller-assembled, post-opening facts and verified provenance. It never opens an
     * arm key. A successful result has no T2 trigger record: {@code triggerRecord == null} and
     * {@code triggers} is empty. Provenance cannot prove that the supplied bundle was computed from
     * the locked files; that requires a file-backed assembler before a frozen evaluation.
     */
    static Result runFrb(FrbRunInput run, FrbInput frb) {
        try {
            requireFrbInputs(run);
            FrozenGateParameters parameters = FrozenGateParameters.load(run.parameters());
            if (!parameters.frbEnabled) throw new InvalidInput("FRB parameters are required");
            FrbProvenance.Verified provenance = FrbProvenance.verify(run, frb);
            FreezeRecord freeze = FreezeRecord.load(run.freeze());
            EvidenceMapping mapping = EvidenceMapping.load(run.mapping());
            if (!parameters.sha256.equals(freeze.parametersSha256())
                    || !mapping.sha256.equals(freeze.evidenceMappingSha256()))
                throw new InvalidInput("FRB freeze parameter or mapping SHA-256 differs");
            if (!freeze.costForecastUsdPerDeliveredCase().keySet().equals(parameters.workflows.keySet())
                    || !freeze.compatibilitySuites().values().containsAll(parameters.workflows.keySet()))
                throw new InvalidInput("FRB freeze lacks workflow ceiling or suite");
            Map<String, FrozenCase> frozen = loadCases(run.manifests(), freeze);
            Map<String, FrozenCase> frozenReserve = loadReserveCases(run.manifests(), freeze);
            String caseSetHash = caseSetHash(frozen);
            Stage1Receipt candidate = Stage1Receipt.read(run.candidate().receipt());
            FreezeRecord.Arm candidateArm = freeze.arms().get(candidate.arm());
            if (candidateArm == null) throw new InvalidInput("Candidate arm absent from FRB freeze");
            if (run.synthetic())
                FreezeGuard.verify(run.freeze(), run.repoRoot(), candidate.arm(), candidate.model(),
                        run.manifests(), candidateArm.workflows(), freeze.gitCommit());
            else FreezeGuard.verify(run.freeze(), run.repoRoot(), candidate.arm(), candidate.model(),
                    run.manifests(), candidateArm.workflows());
            String freezeHash = FreezeRecord.sha256(run.freeze());
            ArmEvidence mini = loadArm(run.baselineMini(), freezeHash, caseSetHash, freeze.gitCommit());
            ArmEvidence paid = loadArm(run.baseline41(), freezeHash, caseSetHash, freeze.gitCommit());
            ArmEvidence target = loadArm(run.candidate(), freezeHash, caseSetHash, freeze.gitCommit());
            for (ArmEvidence arm : List.of(mini, paid, target))
                if (arm.rechecks() != null && !freeze.recheckEligibleFailureClasses().equals(
                        arm.rechecks().recheckEligibleFailureClasses()))
                    throw new InvalidInput("FRB recheck eligible classes differ from freeze: " + arm.receipt().arm());
            if (!mini.receipt().arm().equals(BASELINE_MINI_ARM) || !paid.receipt().arm().equals(BASELINE_41_ARM)
                    || !candidateArm.model().equals(candidate.model()))
                throw new InvalidInput("FRB receipt arm or model differs from freeze");
            AdjudicatedLoaded adjudicated = frb.bundle().evidence();
            ScoreEvidence.Loaded scores = frb.bundle().toLoaded(provenance.hashes());
            Instant headTime = run.synthetic() ? run.headCommitTime() : committedHeadTime(run.repoRoot());
            Map<String, CompatibilitySuiteEvidence.State> suites = CompatibilitySuiteEvidence.read(
                    run.compatibilityReports(), freeze.compatibilitySuites(), headTime);
            List<RouteVerdictCalculator.OwnerDisposition> dispositions = dispositions(run.ownerDispositions(), true);
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
                guardFrbClassBPair(id, fixture, baseline, target, adjudicated);
                checkFrbWidth(id, fixture, baseline.receipt().arm(), adjudicated);
                checkFrbWidth(id, fixture, target.receipt().arm(), adjudicated);
                RouteVerdictCalculator.ArmResult b = arm(id, fixture, baseline, scores, mapping,
                        freeze.costForecastUsdPerDeliveredCase().get(workflow), adjudicated);
                RouteVerdictCalculator.ArmResult c = arm(id, fixture, target, scores, mapping,
                        freeze.costForecastUsdPerDeliveredCase().get(workflow), adjudicated);
                boolean recheckedFailure = hasEligibleFailureWithRechecks(id, baseline, freeze)
                        || hasEligibleFailureWithRechecks(id, target, freeze);
                if (scores.excluded().contains(id) && !recheckedFailure) {
                    b = unresolved(fixture.requiredQuestions());
                    c = unresolved(fixture.requiredQuestions());
                    excludedEvidence.put(id, "MISSING");
                }
                // The legacy recovered-exclusion replacement is intentionally absent here: D4
                // forbids turning an unresolved arm into a scored arm with zero counts.
                if (candidateRow != null && baselineRow != null && c.providerRejected()) {
                    boolean baselineSuccess = baselineRow.state() == CaseArmOutcome.State.COMPLETE
                            && baselineRow.finalSuccess() && baselineRow.llmCalls().stream()
                                .anyMatch(call -> SUCCESS_OUTCOME.equals(call.outcome()));
                    c = new RouteVerdictCalculator.ArmResult(c.evidence(), c.t3Components(), c.critical(),
                            c.finalFailureClass(), c.rechecks(), baselineSuccess, c.unusable(),
                            c.criticalQuestions(), c.unresolvedQuestions());
                }
                records.computeIfAbsent(route, ignored -> new ArrayList<>()).add(
                        new RouteVerdictCalculator.CaseRecord(id, workflow, route, fixture.subgroup(),
                                baselineRow == null ? null : id, candidateRow == null ? null : id,
                                b, c, fixture.requiredQuestions()));
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
                    loadFrbReserves(run.reserves(), freeze, parameters, mapping, frozenReserve,
                            frozen, freezeHash, caseSetHash, candidate.arm(), scores, adjudicated);
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
                RouteVerdictCalculator.RouteReport verdict = RouteVerdictCalculator.calculate(parameters,
                        route.getKey(), routeIds, records.getOrDefault(route.getKey(), List.of()),
                        reserveByRoute.getOrDefault(route.getKey(), Map.of()), routeSuites,
                        dispositions.stream().filter(d -> route.getValue().workflows().contains(d.workflow())).toList(),
                        frb.bundle().reserveDrawnByWorkflow(), frb.bundle().requestedQuestionsByCaseId());
                routeReports.put(route.getKey(), verdict);
            }
            Set<String> pendingRoutes = new HashSet<>();
            for (var fact : adjudicated.byCaseArm().entrySet()) {
                if (fact.getValue().pendingQuestions() <= 0) continue;
                String id = fact.getKey().split("\u0000", 2)[0];
                FrozenCase caseFact = frozen.containsKey(id) ? frozen.get(id) : frozenReserve.get(id);
                if (caseFact != null) pendingRoutes.add(parameters.workflows.get(caseFact.workflow()).route());
            }
            Map<String, FrbClassification> classifications = classifyFrbRoutes(routeReports,
                    frb.routeClassifications(), pendingRoutes);
            classifications.forEach((route, classification) -> deadline.put(route,
                    renderFrbClassification(classification)));
            writeFrbReport(run, frb, provenance, freezeHash, parameters, mapping, mini, paid, target,
                    scores, suites, costs, dispositions, reserveByRoute, routeReports, deadline,
                    classifications, excludedEvidence);
            Map<String, List<RouteVerdictCalculator.CaseRecord>> immutableRecords = new LinkedHashMap<>();
            records.forEach((route, cases) -> immutableRecords.put(route, List.copyOf(cases)));
            return new Result(Map.copyOf(routeReports), run.report(), null, Map.of(), Map.copyOf(immutableRecords));
        } catch (Exception exception) {
            if (exception instanceof InvalidInput invalid) throw invalid;
            throw new InvalidInput("FRB results pipeline refused: " + exception.getMessage(), exception);
        }
    }

    private static void requireFrbInputs(FrbRunInput run) {
        if (run == null || run.repoRoot() == null || run.freeze() == null || run.parameters() == null
                || run.mapping() == null || run.manifests() == null || run.baselineMini() == null
                || run.baseline41() == null || run.candidate() == null || run.reserves() == null
                || run.compatibilityReports() == null || run.headCommitTime() == null || run.report() == null)
            throw new InvalidInput("Missing required FRB run input");
        DeliveredOutputs.guard(run.freeze());
        run.manifests().values().forEach(DeliveredOutputs::guard);
        for (ArmFiles arm : List.of(run.baselineMini(), run.baseline41(), run.candidate()))
            guardArmFiles(arm);
        for (ReserveArms reserve : run.reserves().values()) {
            if (reserve == null) throw new InvalidInput("Missing FRB reserve arms");
            guardArmFiles(reserve.baseline());
            guardArmFiles(reserve.candidate());
        }
        DeliveredOutputs.guard(run.report());
    }

    private static void guardArmFiles(ArmFiles arm) {
        if (arm == null || arm.receipt() == null || arm.delivered() == null)
            throw new InvalidInput("Missing FRB receipt or delivered output");
        DeliveredOutputs.guard(arm.receipt());
        DeliveredOutputs.guard(arm.delivered());
        if (arm.recheckReceipt() != null && arm.recheckDirectory() == null)
            throw new InvalidInput(MISSING_RECHECK_DIRECTORY + arm.recheckReceipt());
        if (arm.recheckReceipt() != null) DeliveredOutputs.guard(arm.recheckReceipt());
        if (arm.recheckDirectory() != null) DeliveredOutputs.guard(arm.recheckDirectory());
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

    static void verifyFrbBundleCases(FrbRunInput run, FrbEvidenceBundle bundle,
                                     Set<String> drawn) throws IOException {
        FreezeRecord freeze = FreezeRecord.load(run.freeze());
        Map<String, FrozenCase> primary = loadCases(run.manifests(), freeze);
        Map<String, FrozenCase> reserve = loadReserveCases(run.manifests(), freeze);
        Map<String, Integer> expected = new LinkedHashMap<>();
        primary.forEach((id, fixture) -> expected.put(id, fixture.requiredQuestions()));
        reserve.forEach((id, fixture) -> {
            if (drawn.contains(fixture.workflow())) expected.put(id, fixture.requiredQuestions());
        });
        if (!bundle.requestedQuestionsByCaseId().equals(expected))
            throw new InvalidInput("FRB bundle cases or widths differ from frozen manifests");
        Set<String> factCases = new HashSet<>();
        for (String identity : bundle.evidence().byCaseArm().keySet()) {
            int split = identity.indexOf('\u0000');
            if (split < 1) throw new InvalidInput("Invalid FRB fact identity: " + identity);
            factCases.add(identity.substring(0, split));
        }
        factCases.addAll(bundle.evidence().excludedCaseIds());
        if (!factCases.equals(expected.keySet())
                || !bundle.evidence().requestedQuestionsByCaseId().equals(expected))
            throw new InvalidInput("FRB facts differ from frozen primary or drawn reserve cases");
        for (var item : reserve.entrySet())
            if (drawn.contains(item.getValue().workflow()) && !factCases.contains(item.getKey()))
                throw new InvalidInput("Drawn reserve lacks bundle facts: " + item.getKey());
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

    private static ScoreEvidence.ClassBArmNames classBNames(FreezeRecord freeze, String candidateArm) {
        FreezeRecord.Arm paid = freeze.arms().get(BASELINE_41_ARM);
        if (paid == null) throw new InvalidInput("Baseline 41 arm absent from freeze");
        return new ScoreEvidence.ClassBArmNames(candidateArm, workflow ->
                paid.workflows().contains(workflow) ? BASELINE_41_ARM : BASELINE_MINI_ARM);
    }

    private static boolean classBSupplied(ScoreEvidence.FilesB files) {
        try { return Files.size(files.key()) > 0 || Files.size(files.scores()) > 0; }
        catch (IOException exception) { throw new InvalidInput("Cannot inspect class-B score files", exception); }
    }

    private static void guardClassBPair(String id, FrozenCase fixture, ArmEvidence baseline,
            ArmEvidence candidate, ScoreEvidence.Loaded scores, ScoreEvidence.FilesB files, boolean supplied) {
        if (!supplied || !CLASS_B_WORKFLOWS.contains(fixture.workflow())) return;
        boolean baselineUsable = usableOutput(id, fixture, baseline);
        boolean candidateUsable = usableOutput(id, fixture, candidate);
        if (!baselineUsable && !candidateUsable) return;
        for (ArmEvidence source : List.of(baseline, candidate)) {
            String arm = source.receipt().arm();
            boolean hasFact = scores.get(id, arm) != null;
            if (baselineUsable && candidateUsable && !hasFact)
                throw new InvalidInput("Missing class-B fact for case " + id + " arm " + arm
                        + " expected key " + id + "\\u0000" + arm + " in " + files.key());
            if (baselineUsable != candidateUsable && hasFact)
                throw new InvalidInput("Unpaired class-B case " + id + " has key for arm " + arm
                        + " expected no key in " + files.key());
        }
    }

    private static boolean usableOutput(String id, FrozenCase fixture, ArmEvidence source) {
        Stage1Receipt.CaseRow row = source.rows().get(id);
        if (row == null) return false;
        if (row.state() == CaseArmOutcome.State.COMPLETE && row.finalSuccess()
                && structurallyComplete(source.outputs().get(id), fixture)) return true;
        if (row.state() != CaseArmOutcome.State.FAILED || source.rechecks() == null
                || source.recheckDirectory() == null) return false;
        RecheckReceipt.Case item = source.rechecks().cases().stream()
                .filter(value -> id.equals(value.caseId())).findFirst().orElse(null);
        if (item == null) return false;
        for (RecheckReceipt.Repeat repeat : item.repeats()) {
            if (repeat.protocolDeviation() != null || repeat.finalFailureClass() != null
                    || repeat.evidence() != RouteVerdictCalculator.Evidence.SCORED
                    || RecheckPlan.isRejectionCase(row) && repeat.providerRejected()) continue;
            Path path = source.recheckDirectory().resolve(id).resolve("recheck-" + repeat.recheckIndex())
                    .resolve("delivered-outputs.jsonl");
            if (structurallyComplete(DeliveredOutputs.readRecheck(path, source.rechecks(), item, repeat), fixture))
                return true;
        }
        return false;
    }

    private static RouteVerdictCalculator.ArmResult missing() {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.MISSING,
                Set.of(), false, null, List.of(), false, false);
    }

    private static RouteVerdictCalculator.ArmResult unresolved(int width) {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.MISSING,
                Set.of(), false, null, List.of(), false, false, 0, width);
    }

    private static void checkFrbWidth(String id, FrozenCase fixture, String arm,
                                      AdjudicatedLoaded adjudicated) {
        AdjudicatedFacts fact = adjudicated.get(id, arm);
        if (fact != null && (fact.scoredQuestions() > fixture.requiredQuestions()
                || (long) fact.criticalQuestions() + fact.unresolvedQuestions() > fixture.requiredQuestions()))
            throw new InvalidInput("FRB over-delivery exceeds requested width: " + id + "/" + arm);
    }

    private static void guardFrbClassBPair(String id, FrozenCase fixture, ArmEvidence baseline,
            ArmEvidence candidate, AdjudicatedLoaded adjudicated) {
        if (!CLASS_B_WORKFLOWS.contains(fixture.workflow())) return;
        boolean baselineUsable = usableOutput(id, fixture, baseline);
        boolean candidateUsable = usableOutput(id, fixture, candidate);
        for (ArmEvidence source : List.of(baseline, candidate)) {
            boolean hasFact = adjudicated.get(id, source.receipt().arm()) != null;
            if (baselineUsable && candidateUsable && !hasFact)
                throw new InvalidInput("Missing FRB class-B fact: " + id + "/" + source.receipt().arm());
            if (baselineUsable != candidateUsable && hasFact)
                throw new InvalidInput("Unpaired FRB class-B fact: " + id + "/" + source.receipt().arm());
        }
    }

    private static RouteVerdictCalculator.ArmResult arm(String id, FrozenCase fixture, ArmEvidence source,
            ScoreEvidence.Loaded scores, EvidenceMapping mapping, BigDecimal forecast) {
        return arm(id, fixture, source, scores, mapping, forecast, null);
    }

    private static RouteVerdictCalculator.ArmResult arm(String id, FrozenCase fixture, ArmEvidence source,
            ScoreEvidence.Loaded scores, EvidenceMapping mapping, BigDecimal forecast,
            AdjudicatedLoaded adjudicated) {
        Stage1Receipt.CaseRow row = source.rows().get(id);
        if (row == null) return adjudicated == null ? missing() : unresolved(fixture.requiredQuestions());
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
        List<RouteVerdictCalculator.Recheck> rechecks = rechecks(id, row, fixture, source, scores, mapping,
                adjudicated);
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
        AdjudicatedFacts counts = adjudicated == null ? null : adjudicated.get(id, source.receipt().arm());
        if (adjudicated == null)
            return new RouteVerdictCalculator.ArmResult(evidence, components,
                    facts != null && facts.critical(), row.failureClass(), rechecks, rejected, !successCall);
        int critical = counts == null ? 0 : counts.criticalQuestions();
        int unresolved = counts == null ? fixture.requiredQuestions() : counts.unresolvedQuestions();
        return new RouteVerdictCalculator.ArmResult(evidence, components, critical > 0,
                row.failureClass(), rechecks, rejected, !successCall, critical, unresolved);
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
                && row.failureClass() != null
                && (freeze.recheckEligibleFailureClasses().contains(row.failureClass())
                    || freeze.recheckEligibleFailureClasses().contains(RecheckPlan.PROVIDER_REJECTION)
                        && RecheckPlan.isRejectionCase(row))
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
        return rechecks(id, original, fixture, source, scores, mapping, null);
    }

    private static List<RouteVerdictCalculator.Recheck> rechecks(String id, Stage1Receipt.CaseRow original,
            FrozenCase fixture, ArmEvidence source, ScoreEvidence.Loaded scores, EvidenceMapping mapping,
            AdjudicatedLoaded adjudicated) {
        if (source.rechecks() == null) return List.of();
        RecheckReceipt.Case item = source.rechecks().cases().stream()
                .filter(value -> id.equals(value.caseId())).findFirst().orElse(null);
        if (item == null) return List.of();
        if (!java.util.Objects.equals(original.failureClass(), item.originalFailureClass()))
            throw new InvalidInput("Recheck original failure differs: " + id);
        List<RouteVerdictCalculator.Recheck> result = new ArrayList<>();
        for (RecheckReceipt.Repeat repeat : item.repeats()) {
            boolean frbRejection = adjudicated != null && RecheckPlan.isRejectionCase(original);
            boolean deliveredAfterRejection = frbRejection && repeat.providerRejected()
                    && repeat.finalFailureClass() == null;
            String equivalence = null;
            if (adjudicated != null) {
                equivalence = frbRejection && repeat.providerRejected()
                        ? RejectionEquivalence.decide(source.receipt(), original, source.rechecks(), repeat)
                                .equivalence().name()
                        : NOT_APPLICABLE;
            }
            RouteVerdictCalculator.Evidence evidence = repeat.evidence();
            if (repeat.protocolDeviation() != null) evidence = RouteVerdictCalculator.Evidence.MISSING;
            if (RecheckPlan.isRejectionCase(original) && repeat.providerRejected()
                    && repeat.finalFailureClass() == null)
                evidence = RouteVerdictCalculator.Evidence.MISSING;
            ScoreEvidence.Facts facts = scores.get(id, source.receipt().arm());
            if (source.recheckDirectory() == null) throw new InvalidInput(MISSING_RECHECK_DIRECTORY + id);
            Path path = source.recheckDirectory().resolve(id).resolve("recheck-" + repeat.recheckIndex())
                    .resolve("delivered-outputs.jsonl");
            DeliveredOutputs.Line line = DeliveredOutputs.readRecheck(path, source.rechecks(), item, repeat);
            if (!java.util.Objects.equals(line.failureClass(), repeat.finalFailureClass())
                    || repeat.finalFailureClass() == null && line.state() != CaseArmOutcome.State.COMPLETE
                    || repeat.finalFailureClass() != null && line.state() == CaseArmOutcome.State.COMPLETE)
                throw new InvalidInput("Recheck outcome differs from delivered evidence: " + path);
            if (!deliveredAfterRejection && repeat.finalFailureClass() == null
                    && evidence == RouteVerdictCalculator.Evidence.SCORED) {
                if (!validOutput(line, fixture, facts)) evidence = RouteVerdictCalculator.Evidence.UNSCORABLE;
            }
            if (adjudicated == null) {
                result.add(new RouteVerdictCalculator.Recheck(evidence,
                        facts != null && facts.quality() ? Set.of(mapping.component(QUALITY_KIND)) : Set.of(),
                        facts != null && facts.critical(), repeat.finalFailureClass(),
                        facts != null && facts.critical() ? 1 : 0, 0, repeat.providerRejected()));
            } else {
                // The export scores the first successful recheck output for a failed original.
                // Every successful recheck therefore carries the same case-and-arm fact.
                AdjudicatedFacts counts = adjudicated.get(id, source.receipt().arm());
                int critical = counts == null ? 0 : counts.criticalQuestions();
                int unresolved = counts == null ? fixture.requiredQuestions() : counts.unresolvedQuestions();
                Set<String> quality = facts != null && facts.quality()
                        ? Set.of(mapping.component(QUALITY_KIND)) : Set.of();
                result.add(new RouteVerdictCalculator.Recheck(evidence,
                        deliveredAfterRejection ? Set.of() : quality,
                        !deliveredAfterRejection && critical > 0, repeat.finalFailureClass(),
                        deliveredAfterRejection ? 0 : critical,
                        deliveredAfterRejection ? 0 : unresolved, repeat.providerRejected(), equivalence,
                        deliveredAfterRejection));
            }
        }
        return List.copyOf(result);
    }

    static List<RouteVerdictCalculator.OwnerDisposition> dispositions(Path file,
                                                                       boolean frb) throws IOException {
        if (file == null || !Files.exists(file)) return List.of();
        DeliveredOutputs.guard(file);
        List<RouteVerdictCalculator.OwnerDisposition> rows = JSON.readValue(file.toFile(), new TypeReference<>() {});
        if (frb) for (var row : rows) {
            if (row == null) throw new RouteVerdictCalculator.InvalidOwnerDisposition(
                    "Null FRB owner disposition");
            if (row.value() == RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED
                    && (row.rationale() == null || row.rationale().isBlank()))
                throw new RouteVerdictCalculator.InvalidOwnerDisposition(
                        "FRB acknowledged disposition requires a rationale");
        }
        return rows;
    }

    /** Validate per-route owner facts independently of synthetic freeze handling. */
    static Map<String, FrbClassification> classifyFrbRoutes(
            Map<String, RouteVerdictCalculator.RouteReport> routes,
            Map<String, FrbClassificationInput> inputs, Set<String> pendingRoutes) {
        if (inputs != null && !routes.keySet().containsAll(inputs.keySet()))
            throw new InvalidInput("FRB classification input names an unknown route");
        Map<String, FrbClassification> classified = new java.util.TreeMap<>();
        routes.forEach((route, report) -> {
            FrbClassification result = DeadlineRule.classifyFrb(report,
                    inputs == null ? null : inputs.get(route));
            if (pendingRoutes != null && pendingRoutes.contains(route)) {
                java.util.SortedSet<String> causes = new java.util.TreeSet<>(result.causes());
                causes.add("PENDING_REVIEW_ITEMS");
                result = new FrbClassification(DeadlineRule.Classification.RETURN_TO_OWNER,
                        java.util.Collections.unmodifiableSortedSet(causes), result.flags(), true);
            }
            classified.put(route, result);
        });
        return java.util.Collections.unmodifiableMap(classified);
    }

    static String renderFrbClassification(FrbClassification result) {
        if (result.classification() == DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE)
            return "DEADLINE FALLBACK, NOT BENCHMARK PASS";
        String value = result.classification().name();
        if (result.classification() == DeadlineRule.Classification.BENCHMARK_PASS) {
            long findings = result.causes().stream()
                    .filter(cause -> cause.startsWith("DISPOSITIONED_CANDIDATE_CRITICAL:")).count();
            if (findings > 0) value += " with " + findings + " dispositioned findings";
            if (result.blocksProductionSwitch())
                value += " (production switch blocked: " + String.join(", ", result.flags()) + ")";
        }
        return value;
    }

    private static Map<String, Map<String, List<RouteVerdictCalculator.ReserveCase>>> loadReserves(
            Map<String, ReserveInput> inputs, FreezeRecord freeze, FrozenGateParameters parameters,
            EvidenceMapping mapping, Map<String, FrozenCase> frozenReserve,
            Map<String, FrozenCase> frozen, String freezeHash, String primaryCaseSetHash, String candidateArm) {
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
            String mappedBaseline = freeze.arms().get(BASELINE_41_ARM).workflows().contains(workflow)
                    ? BASELINE_41_ARM : BASELINE_MINI_ARM;
            if (!b.arm().equals(mappedBaseline) || !c.arm().equals(candidateArm))
                throw new InvalidInput("Reserve receipt arm differs from mapped arms: " + workflow);
            ScoreEvidence.Loaded scores = ScoreEvidence.load(input.classA(), input.classB(), mapping,
                    classBNames(freeze, candidateArm));
            boolean classBSupplied = classBSupplied(input.classB());
            List<RouteVerdictCalculator.ReserveCase> rows = new ArrayList<>();
            for (Stage1Receipt.CaseRow row : c.cases()) {
                FrozenCase fixture = frozenReserve.get(row.id());
                if (fixture == null || !workflow.equals(row.workflow()) || frozen.containsKey(row.id()))
                    throw new InvalidInput("Reserve case overlaps primary or workflow differs: " + row.id());
                requireRow(fixture, row, c.arm());
                requireRow(fixture, baseline.rows().get(row.id()), b.arm());
                guardClassBPair(row.id(), fixture, baseline, candidate, scores, input.classB(), classBSupplied);
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

    private static Map<String, Map<String, List<RouteVerdictCalculator.ReserveCase>>> loadFrbReserves(
            Map<String, ReserveArms> inputs, FreezeRecord freeze, FrozenGateParameters parameters,
            EvidenceMapping mapping, Map<String, FrozenCase> frozenReserve,
            Map<String, FrozenCase> frozen, String freezeHash, String primaryCaseSetHash,
            String candidateArm, ScoreEvidence.Loaded scores, AdjudicatedLoaded adjudicated) {
        Map<String, Map<String, List<RouteVerdictCalculator.ReserveCase>>> result = new HashMap<>();
        for (var entry : inputs.entrySet()) {
            String workflow = entry.getKey();
            if (!CLASS_B_WORKFLOWS.contains(workflow) || !parameters.workflows.containsKey(workflow))
                throw new InvalidInput("FRB reserve input is not a class-B workflow: " + workflow);
            ReserveArms input = entry.getValue();
            Stage1Receipt b = Stage1Receipt.read(input.baseline().receipt());
            Stage1Receipt c = Stage1Receipt.read(input.candidate().receipt());
            if (!freezeHash.equals(b.freezeSha256()) || !freezeHash.equals(c.freezeSha256())
                    || !primaryCaseSetHash.equals(b.caseSetSha256())
                    || !primaryCaseSetHash.equals(c.caseSetSha256()))
                throw new InvalidInput("FRB reserve receipt freeze or case set differs: " + workflow);
            ArmEvidence baseline = loadArm(input.baseline(), freezeHash, b.caseSetSha256(), freeze.gitCommit());
            ArmEvidence candidate = loadArm(input.candidate(), freezeHash, c.caseSetSha256(), freeze.gitCommit());
            String mappedBaseline = freeze.arms().get(BASELINE_41_ARM).workflows().contains(workflow)
                    ? BASELINE_41_ARM : BASELINE_MINI_ARM;
            if (!b.arm().equals(mappedBaseline) || !c.arm().equals(candidateArm))
                throw new InvalidInput("FRB reserve receipt arm differs from mapped arms: " + workflow);
            Set<String> expected = new LinkedHashSet<>();
            frozenReserve.forEach((id, fixture) -> {
                if (workflow.equals(fixture.workflow())) expected.add(id);
            });
            if (!c.cases().stream().map(Stage1Receipt.CaseRow::id)
                    .collect(java.util.stream.Collectors.toSet()).equals(expected)
                    || !baseline.rows().keySet().equals(expected))
                throw new InvalidInput("FRB reserve receipts differ from frozen cases: " + workflow);
            List<RouteVerdictCalculator.ReserveCase> rows = new ArrayList<>();
            for (Stage1Receipt.CaseRow row : c.cases()) {
                FrozenCase fixture = frozenReserve.get(row.id());
                if (fixture == null || !workflow.equals(row.workflow()) || frozen.containsKey(row.id()))
                    throw new InvalidInput("FRB reserve case overlaps primary or workflow differs: " + row.id());
                requireRow(fixture, row, c.arm());
                requireRow(fixture, baseline.rows().get(row.id()), b.arm());
                guardFrbClassBPair(row.id(), fixture, baseline, candidate, adjudicated);
                checkFrbWidth(row.id(), fixture, b.arm(), adjudicated);
                checkFrbWidth(row.id(), fixture, c.arm(), adjudicated);
                RouteVerdictCalculator.ArmResult left = arm(row.id(), fixture, baseline, scores, mapping,
                        freeze.costForecastUsdPerDeliveredCase().get(workflow), adjudicated);
                RouteVerdictCalculator.ArmResult right = arm(row.id(), fixture, candidate, scores, mapping,
                        freeze.costForecastUsdPerDeliveredCase().get(workflow), adjudicated);
                AdjudicatedFacts leftFact = adjudicated.get(row.id(), b.arm());
                AdjudicatedFacts rightFact = adjudicated.get(row.id(), c.arm());
                RouteVerdictCalculator.Evidence leftEvidence = leftFact != null && leftFact.unresolvedQuestions() > 0
                        ? RouteVerdictCalculator.Evidence.UNSCORABLE : left.evidence();
                RouteVerdictCalculator.Evidence rightEvidence = rightFact != null && rightFact.unresolvedQuestions() > 0
                        ? RouteVerdictCalculator.Evidence.UNSCORABLE : right.evidence();
                rows.add(new RouteVerdictCalculator.ReserveCase(row.id(), leftEvidence, rightEvidence,
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

    private static void writeFrbReport(FrbRunInput run, FrbInput frb,
            FrbProvenance.Verified provenance, String freezeHash, FrozenGateParameters parameters,
            EvidenceMapping mapping, ArmEvidence mini, ArmEvidence paid, ArmEvidence candidate,
            ScoreEvidence.Loaded scores, Map<String, CompatibilitySuiteEvidence.State> suites,
            Map<String, Object> costs, List<RouteVerdictCalculator.OwnerDisposition> dispositions,
            Map<String, Map<String, List<RouteVerdictCalculator.ReserveCase>>> reserves,
            Map<String, RouteVerdictCalculator.RouteReport> routes, Map<String, Object> deadline,
            Map<String, FrbClassification> classifications,
            Map<String, String> excludedEvidence) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(FREEZE_SHA_FIELD, freezeHash);
        report.put(PARAMETER_SHA_FIELD, parameters.sha256);
        report.put("mappingSha256", mapping.sha256);
        report.put("caseSetSha256", candidate.receipt().caseSetSha256());
        report.put("candidateArm", candidate.receipt().arm());
        report.put("candidateModel", candidate.receipt().model());
        report.put("receiptSha256", Map.of(BASELINE_MINI_ARM, FreezeRecord.sha256(run.baselineMini().receipt()),
                BASELINE_41_ARM, FreezeRecord.sha256(run.baseline41().receipt()),
                candidate.receipt().arm(), FreezeRecord.sha256(run.candidate().receipt())));
        report.put("deliveredOutputSha256", Map.of(BASELINE_MINI_ARM,
                FreezeRecord.sha256(run.baselineMini().delivered()), BASELINE_41_ARM,
                FreezeRecord.sha256(run.baseline41().delivered()), candidate.receipt().arm(),
                FreezeRecord.sha256(run.candidate().delivered())));
        Map<String, String> recheckHashes = new LinkedHashMap<>();
        for (ArmFiles files : List.of(run.baselineMini(), run.baseline41(), run.candidate()))
            if (files.recheckReceipt() != null)
                recheckHashes.put(Stage1Receipt.read(files.receipt()).arm(), FreezeRecord.sha256(files.recheckReceipt()));
        report.put("recheckReceiptSha256", recheckHashes);
        report.put("scoreFileSha256", scores.hashes());
        Map<String, Object> reserveHashes = new LinkedHashMap<>();
        for (var entry : run.reserves().entrySet()) {
            ReserveArms reserve = entry.getValue();
            reserveHashes.put(entry.getKey(), Map.of(
                    "baselineReceipt", FreezeRecord.sha256(reserve.baseline().receipt()),
                    "candidateReceipt", FreezeRecord.sha256(reserve.candidate().receipt()),
                    "baselineDeliveredOutputs", FreezeRecord.sha256(reserve.baseline().delivered()),
                    "candidateDeliveredOutputs", FreezeRecord.sha256(reserve.candidate().delivered())));
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
        report.put("classification", classifications);
        Map<String, Map<RouteVerdictCalculator.DispositionValue, Set<String>>> dispositionsByRoute =
                new java.util.TreeMap<>();
        routes.forEach((route, value) -> {
            Map<RouteVerdictCalculator.DispositionValue, Set<String>> byValue = new LinkedHashMap<>();
            value.workflows().values().forEach(workflow -> {
                if (workflow.dispositionsByValue() != null)
                    workflow.dispositionsByValue().forEach((kind, ids) ->
                            byValue.computeIfAbsent(kind, ignored -> new java.util.TreeSet<>()).addAll(ids));
            });
            dispositionsByRoute.put(route, byValue);
        });
        report.put("dispositionsByValue", dispositionsByRoute.values().stream().allMatch(Map::isEmpty)
                ? null : dispositionsByRoute);
        Map<String, Map<String, String>> heads = new java.util.TreeMap<>();
        if (frb.routeClassifications() != null) frb.routeClassifications().forEach((route, input) -> {
            if (input != null && input.ledgerHeadHashes() != null)
                heads.put(route, new java.util.TreeMap<>(input.ledgerHeadHashes()));
        });
        report.put("ledgerHeadHashes", heads.isEmpty() ? null : heads);
        Map<String, Integer> margins = new LinkedHashMap<>();
        parameters.routes.forEach((name, route) -> margins.put(name, route.margin()));
        report.put("routeMargins", margins);
        Map<String, Object> frbSection = new LinkedHashMap<>();
        frbSection.put("reserveRecordSha256", provenance.hashes().get("reserveDecisionRecord"));
        frbSection.put("reserveRecordCreatedAtUtc", provenance.record().createdAtUtc());
        frbSection.put("provenanceSha256", provenance.hashes());
        frbSection.put("minimumPrimaryOpenedAtUtc", provenance.minimumPrimaryOpen().toString());
        frbSection.put("reserveDrawnByWorkflow", frb.bundle().reserveDrawnByWorkflow());
        frbSection.put("workflowsAwaitingReserve", frb.bundle().workflowsAwaitingReserve());
        Map<String, Map<String, Object>> rejectionRechecks = new java.util.TreeMap<>();
        for (ArmEvidence arm : List.of(mini, paid, candidate)) {
            for (Stage1Receipt.CaseRow row : arm.receipt().cases()) {
                if (!RecheckPlan.isRejectionCase(row)) continue;
                RecheckReceipt.Case caseRechecks = arm.rechecks() == null ? null : arm.rechecks().cases().stream()
                        .filter(item -> item.caseId().equals(row.id())).findFirst().orElse(null);
                List<Map<String, Object>> repeats = new ArrayList<>();
                if (caseRechecks != null) for (RecheckReceipt.Repeat repeat : caseRechecks.repeats()) {
                    RejectionEquivalence.Result equivalence = repeat.providerRejected()
                            ? RejectionEquivalence.decide(arm.receipt(), row, arm.rechecks(), repeat) : null;
                    List<RecheckReceipt.RejectedCall> rejected = repeat.rejectedCalls();
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("recheckIndex", repeat.recheckIndex());
                    detail.put("providerRejected", repeat.providerRejected());
                    detail.put("statuses", rejected == null ? null : rejected.stream()
                            .map(RecheckReceipt.RejectedCall::status).toList());
                    detail.put("errorCodes", rejected == null ? null : rejected.stream()
                            .map(RecheckReceipt.RejectedCall::errorCode).toList());
                    detail.put("errorTypes", rejected == null ? null : rejected.stream()
                            .map(RecheckReceipt.RejectedCall::errorType).toList());
                    detail.put("equivalence", equivalence == null ? NOT_APPLICABLE
                            : equivalence.equivalence().name());
                    detail.put("equivalenceReason", equivalence == null ? null
                            : equivalence.reason().name());
                    detail.put("deliveredAfterRejection", repeat.providerRejected()
                            && repeat.finalFailureClass() == null);
                    repeats.add(detail);
                }
                boolean reproducedThenRetried = caseRechecks != null && caseRechecks.repeats().stream()
                        .anyMatch(repeat -> repeat.providerRejected() && repeat.finalFailureClass() == null);
                boolean equivalentDelivery = caseRechecks != null && caseRechecks.repeats().stream()
                        .anyMatch(repeat -> repeat.providerRejected() && repeat.finalFailureClass() == null
                                && RejectionEquivalence.decide(arm.receipt(), row, arm.rechecks(), repeat)
                                        .equivalence() == RejectionEquivalence.Equivalence.EQUIVALENT);
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("repeats", repeats);
                detail.put("reason", equivalentDelivery ? "REJECTION_REPRODUCED_WITH_DELIVERED_RECOVERY"
                        : reproducedThenRetried ? "REJECTION_REPRODUCED_THEN_RETRIED" : null);
                rejectionRechecks.computeIfAbsent(row.id(), ignored -> new java.util.TreeMap<>())
                        .put(arm.receipt().arm(), detail);
            }
        }
        frbSection.put("rejectionRechecks", rejectionRechecks);
        frbSection.put("provenanceLimit", "File integrity and ordering do not prove that statuses were computed "
                + "from the locked findings or that caller-built bundle facts came from the opened keys.");
        report.put("frb", frbSection);
        JSON.writeValue(run.report().toFile(), report);
    }

    /** Sol recommends a separate investigation outcome; it never changes Luna's classification or approves a switch. */
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
            Map<String, Integer> lunaLow = null;
            Map<String, Integer> solHigh = null;
            if (parameters.frbEnabled) {
                requireFrbCounts(luna, "Luna");
                requireFrbCounts(sol, "Sol");
                lunaLow = luna.frb().candidateCriticalLowByCaseId();
                solHigh = sol.frb().candidateCriticalHighByCaseId();
                if (!lunaLow.keySet().equals(solHigh.keySet()))
                    throw new InvalidInput("Sol comparison primary case IDs differ");
            }
            SolRule.Comparison comparison = new SolRule.Comparison(luna, sol, pairedD,
                    parameters.frbEnabled ? Map.of() : reserveFlags(lunaJson, owner.route()),
                    parameters.frbEnabled ? Map.of() : reserveFlags(solJson, owner.route()),
                    t1Workflows(luna), t1Workflows(sol), owner.economicsAndOtherGatesPass(),
                    owner.investigationComplete(), owner.samePrimaryInputs(), lunaLow, solHigh);
            boolean justified = SolRule.justified(parameters, comparison);
            if (parameters.frbEnabled) {
                boolean required = luna.failedTiers().contains(RouteVerdictCalculator.Tier.T2);
                List<String> ids = lunaLow.keySet().stream().sorted().toList();
                SolT2Improvement.SolImprovement improvement = required ? SolT2Improvement.evaluate(
                        ids.stream().map(solHigh::get).toList(), ids.stream().map(lunaLow::get).toList()) : null;
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("route", owner.route());
                decision.put("solJustified", justified);
                decision.put("solRecommendation", justified ? "JUSTIFIED" : "NOT_JUSTIFIED");
                decision.put("solVersusLunaD", pairedD);
                decision.put(PARAMETER_SHA_FIELD, parameters.sha256);
                decision.put("lunaReportSha256", owner.lunaReportSha256());
                decision.put("solReportSha256", owner.solReportSha256());
                decision.put("ownerDecisionInputSha256", FreezeRecord.sha256(ownerFile));
                decision.put("economicsAndOtherGatesPass", owner.economicsAndOtherGatesPass());
                decision.put("investigationComplete", owner.investigationComplete());
                decision.put("samePrimaryInputs", owner.samePrimaryInputs());
                decision.put("solT2ConjunctionRequired", required);
                decision.put("pValueOk", improvement == null ? null : improvement.pValueOk());
                decision.put("thirtyPercentOk", improvement == null ? null : improvement.thirtyPercentOk());
                decision.put("threeFewerOk", improvement == null ? null : improvement.threeFewerOk());
                decision.put("lunaCriticalTotal", lunaLow.values().stream().mapToLong(Integer::longValue).sum());
                decision.put("solCriticalTotal", solHigh.values().stream().mapToLong(Integer::longValue).sum());
                decision.put("selectionNote", "Sol recommendation never changes the Luna classification and is not an approval or PASS. Sol can look better "
                        + "than it is after Luna failed because of regression to the mean.");
                JSON.writeValue(output.toFile(), decision);
            } else {
                JSON.writeValue(output.toFile(), Map.of("route", owner.route(), "solJustified", justified,
                        "solVersusLunaD", pairedD, PARAMETER_SHA_FIELD, parameters.sha256,
                        "lunaReportSha256", owner.lunaReportSha256(), "solReportSha256", owner.solReportSha256(),
                        "ownerDecisionInputSha256", FreezeRecord.sha256(ownerFile),
                        "economicsAndOtherGatesPass", owner.economicsAndOtherGatesPass(),
                        "investigationComplete", owner.investigationComplete(),
                        "samePrimaryInputs", owner.samePrimaryInputs()));
            }
            return justified;
        } catch (Exception exception) {
            if (exception instanceof InvalidInput invalid) throw invalid;
            throw new InvalidInput("Sol comparison refused: " + exception.getMessage(), exception);
        }
    }

    private static void requireFrbCounts(RouteVerdictCalculator.RouteReport report, String name) {
        if (report.frb() == null || report.frb().candidateCriticalLowByCaseId() == null
                || report.frb().candidateCriticalHighByCaseId() == null
                || report.frb().candidateCriticalLowByCaseId().isEmpty()
                || report.frb().candidateCriticalHighByCaseId().isEmpty())
            throw new InvalidInput(name + " report lacks FRB primary critical counts");
        if (!report.frb().candidateCriticalLowByCaseId().keySet()
                .equals(report.frb().candidateCriticalHighByCaseId().keySet()))
            throw new InvalidInput(name + " report FRB primary case IDs differ");
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
