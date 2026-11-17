package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sections 4 and 8 paired case, workflow, subgroup and route gate.
 * FRB alone uses the provider-rejection pair and harness-regression rules; legacy remains unchanged.
 * HTTP 400 and 422 share one rejection identity here. FRB leaves isolatedCriticalIncident false;
 * Brief 5f replaces that legacy classification.
 */
final class RouteVerdictCalculator {
    private static final String ISOLATED_FINAL_FAILURE = "ISOLATED_FINAL_FAILURE";
    private static final String HARNESS_REGRESSION = "HARNESS_REGRESSION";
    private static final String RECHECK_MIXED = "RECHECK_MIXED";
    private static final String RECHECK_UNRESOLVED = "RECHECK_UNRESOLVED";
    private static final String BASELINE = "baseline";
    private static final String CANDIDATE = "candidate";
    private static final String BASELINE_PREFIX = "baseline:";
    private static final String ARM_SEPARATOR = ";";
    private static final String SIDE_SEPARATOR = ":";
    private static final String COUNTS_EXCEED = "Arm counts exceed requested questions";
    private RouteVerdictCalculator() {}

    static final class InvalidCaseSet extends RuntimeException {
        InvalidCaseSet(String message) { super(message); }
    }
    static final class InvalidOwnerDisposition extends RuntimeException {
        InvalidOwnerDisposition(String message) { super(message); }
    }
    enum DispositionValue { NOT_A_DEFECT_ON_REVIEW, DEFECT_ACKNOWLEDGED, DEFECT_BLOCKS_ROUTE }
    enum DispositionReason { REVIEWED_NOT_DEFECT, ACKNOWLEDGED_FOR_MITIGATION, BLOCKING_CORRECTNESS }
    record OwnerDisposition(String workflow, String caseId, DispositionValue value, DispositionReason reasonCode) {}
    enum Verdict { PASS, FAIL, INCONCLUSIVE }
    enum Evidence { SCORED, MISSING, UNMETERED, UNSCORABLE }
    enum Tier { T1, T2, T3 }

    record ArmResult(Evidence evidence, Set<String> t3Components, boolean critical,
                     String finalFailureClass, List<Recheck> rechecks, boolean providerRejected,
                     boolean unusable, int criticalQuestions, int unresolvedQuestions) {
        ArmResult {
            if (criticalQuestions < 0 || unresolvedQuestions < 0 || critical != (criticalQuestions > 0))
                throw new InvalidCaseSet("Inconsistent arm critical counts");
            t3Components = t3Components == null ? Set.of() : Set.copyOf(t3Components);
            rechecks = rechecks == null ? List.of() : List.copyOf(rechecks);
        }
        ArmResult(Evidence evidence, Set<String> t3Components, boolean critical,
                  String finalFailureClass, List<Recheck> rechecks, boolean providerRejected,
                  boolean unusable) {
            this(evidence, t3Components, critical, finalFailureClass, rechecks,
                    providerRejected, unusable, critical ? 1 : 0, 0);
        }
    }
    record Recheck(Evidence evidence, Set<String> t3Components, boolean critical, String finalFailureClass,
                   int criticalQuestions, int unresolvedQuestions, boolean providerRejected) {
        Recheck {
            if (criticalQuestions < 0 || unresolvedQuestions < 0 || critical != (criticalQuestions > 0))
                throw new InvalidCaseSet("Inconsistent recheck critical counts");
            t3Components = t3Components == null ? Set.of() : Set.copyOf(t3Components);
        }
        Recheck(Evidence evidence, Set<String> t3Components, boolean critical, String finalFailureClass) {
            this(evidence, t3Components, critical, finalFailureClass, critical ? 1 : 0, 0, false);
        }
        Recheck(Evidence evidence, Set<String> t3Components, boolean critical, String finalFailureClass,
                int criticalQuestions, int unresolvedQuestions) {
            this(evidence, t3Components, critical, finalFailureClass, criticalQuestions, unresolvedQuestions, false);
        }
    }
    record CaseRecord(String id, String workflow, String route, String subgroup,
                      String baselineCaseId, String candidateCaseId,
                      ArmResult baseline, ArmResult candidate, int requestedQuestions) {
        CaseRecord(String id, String workflow, String route, String subgroup,
                   String baselineCaseId, String candidateCaseId,
                   ArmResult baseline, ArmResult candidate) {
            this(id, workflow, route, subgroup, baselineCaseId, candidateCaseId, baseline, candidate, 0);
        }
    }
    record ReserveCase(String id, Evidence baselineEvidence, Evidence candidateEvidence,
                       boolean baselineCritical, boolean candidateCritical) {}
    record SubgroupReport(int paired, int candidateOnlyT3, int baselineOnlyT3,
                          int sharedT3, int candidateOnlyT2, int baselineOnlyT2,
                          int sharedT2, int candidateOnlyT1, int baselineOnlyT1,
                          int sharedT1, int d) {}
    /** Exact pValue uses the candidate-favourable lower completion when evidence is unresolved. */
    record FrbWorkflowReport(Tri workflowGate, ExactPairedTest.Result pValue,
                             boolean reserveDrawn, Tri reserveGate, int unresolvedCases,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String reserveCause,
                             @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> unresolvedReasonsByCaseId) {
        FrbWorkflowReport(Tri workflowGate, ExactPairedTest.Result pValue,
                          boolean reserveDrawn, Tri reserveGate, int unresolvedCases) {
            this(workflowGate, pValue, reserveDrawn, reserveGate, unresolvedCases, null, null);
        }
    }
    /** Route pValue follows the same lower-completion convention. */
    record FrbRouteReport(Tri routeGate, ExactPairedTest.Result pValue, int declaredK,
                          String alpha, String familyBudget) {}
    record WorkflowReport(Verdict verdict, Set<Tier> failedTiers, Set<Tier> inconclusiveTiers,
                          int paired, int d,
                          int candidateOnlyT3, int baselineOnlyT3, int sharedT3,
                          int candidateOnlyT2, int baselineOnlyT2, int sharedT2,
                          int candidateOnlyT1, int baselineOnlyT1, int sharedT1,
                          int reserveCandidateOnly,
                          boolean isolatedCriticalIncident, boolean protocolDeviation,
                          Map<String, Integer> candidateComponents,
                          Map<String, Integer> baselineComponents,
                          Map<String, SubgroupReport> subgroups, String parameterSha256, int missingPairs,
                          Set<String> undisposedCriticalCaseIds,
                          @JsonInclude(JsonInclude.Include.NON_NULL) FrbWorkflowReport frb,
                          @JsonInclude(JsonInclude.Include.NON_NULL) List<String> causes,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> recheckOutcomesByCaseId,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> rejectionEvidenceByCaseId) {
        WorkflowReport(Verdict verdict, Set<Tier> failedTiers, Set<Tier> inconclusiveTiers,
                       int paired, int d, int candidateOnlyT3, int baselineOnlyT3, int sharedT3,
                       int candidateOnlyT2, int baselineOnlyT2, int sharedT2,
                       int candidateOnlyT1, int baselineOnlyT1, int sharedT1,
                       int reserveCandidateOnly, boolean isolatedCriticalIncident, boolean protocolDeviation,
                       Map<String, Integer> candidateComponents, Map<String, Integer> baselineComponents,
                       Map<String, SubgroupReport> subgroups, String parameterSha256, int missingPairs,
                       Set<String> undisposedCriticalCaseIds) {
            this(verdict, failedTiers, inconclusiveTiers, paired, d,
                    candidateOnlyT3, baselineOnlyT3, sharedT3, candidateOnlyT2, baselineOnlyT2, sharedT2,
                    candidateOnlyT1, baselineOnlyT1, sharedT1, reserveCandidateOnly,
                    isolatedCriticalIncident, protocolDeviation, candidateComponents, baselineComponents,
                    subgroups, parameterSha256, missingPairs, undisposedCriticalCaseIds, null, null, null, null);
        }
        WorkflowReport(Verdict verdict, Set<Tier> failedTiers, Set<Tier> inconclusiveTiers,
                       int paired, int d, int candidateOnlyT3, int baselineOnlyT3, int sharedT3,
                       int candidateOnlyT2, int baselineOnlyT2, int sharedT2,
                       int candidateOnlyT1, int baselineOnlyT1, int sharedT1,
                       int reserveCandidateOnly, boolean isolatedCriticalIncident, boolean protocolDeviation,
                       Map<String, Integer> candidateComponents, Map<String, Integer> baselineComponents,
                       Map<String, SubgroupReport> subgroups, String parameterSha256, int missingPairs,
                       Set<String> undisposedCriticalCaseIds, FrbWorkflowReport frb) {
            this(verdict, failedTiers, inconclusiveTiers, paired, d,
                    candidateOnlyT3, baselineOnlyT3, sharedT3, candidateOnlyT2, baselineOnlyT2, sharedT2,
                    candidateOnlyT1, baselineOnlyT1, sharedT1, reserveCandidateOnly,
                    isolatedCriticalIncident, protocolDeviation, candidateComponents, baselineComponents,
                    subgroups, parameterSha256, missingPairs, undisposedCriticalCaseIds, frb, null, null, null);
        }
    }
    record RouteReport(Verdict verdict, Set<Tier> failedTiers, Set<Tier> inconclusiveTiers, int d,
                       boolean isolatedCriticalIncident, String parameterSha256,
                       Map<String, WorkflowReport> workflows,
                       @JsonInclude(JsonInclude.Include.NON_NULL) FrbRouteReport frb) {
        RouteReport(Verdict verdict, Set<Tier> failedTiers, Set<Tier> inconclusiveTiers, int d,
                    boolean isolatedCriticalIncident, String parameterSha256,
                    Map<String, WorkflowReport> workflows) {
            this(verdict, failedTiers, inconclusiveTiers, d, isolatedCriticalIncident,
                    parameterSha256, workflows, null);
        }
        Map<String, Set<String>> undisposedCriticalCaseIds() {
            Map<String, Set<String>> result = new LinkedHashMap<>();
            workflows.forEach((workflow, report) -> {
                if (!report.undisposedCriticalCaseIds().isEmpty())
                    result.put(workflow, report.undisposedCriticalCaseIds());
            });
            return Collections.unmodifiableMap(result);
        }
    }

    static RouteReport calculate(FrozenGateParameters parameters, String route,
                                 Map<String, Set<String>> frozenCaseIds,
                                 List<CaseRecord> cases,
                                 Map<String, List<ReserveCase>> reserves,
                                 Map<String, Boolean> compatibilitySuites) {
        return calculate(parameters, route, frozenCaseIds, cases, reserves, compatibilitySuites, List.of());
    }

    static RouteReport calculate(FrozenGateParameters parameters, String route,
                                 Map<String, Set<String>> frozenCaseIds,
                                 List<CaseRecord> cases,
                                 Map<String, List<ReserveCase>> reserves,
                                 Map<String, Boolean> compatibilitySuites,
                                 List<OwnerDisposition> dispositions) {
        return calculate(parameters, route, frozenCaseIds, cases, reserves, compatibilitySuites,
                dispositions, Map.of(), Map.of());
    }

    static RouteReport calculate(FrozenGateParameters parameters, String route,
                                 Map<String, Set<String>> frozenCaseIds,
                                 List<CaseRecord> cases,
                                 Map<String, List<ReserveCase>> reserves,
                                 Map<String, Boolean> compatibilitySuites,
                                 List<OwnerDisposition> dispositions,
                                 Map<String, Boolean> reserveDrawnByWorkflow,
                                 Map<String, Integer> requestedQuestionsByCaseId) {
        cases = cases == null ? List.of() : cases;
        reserves = reserves == null ? Map.of() : reserves;
        compatibilitySuites = compatibilitySuites == null ? Map.of() : compatibilitySuites;
        dispositions = dispositions == null ? List.of() : dispositions;
        if (parameters.frbEnabled && (reserveDrawnByWorkflow == null || requestedQuestionsByCaseId == null))
            throw new InvalidCaseSet("Missing FRB inputs");
        FrozenGateParameters.Route routeValues = parameters.routes.get(route);
        if (routeValues == null) throw new InvalidCaseSet("Unknown route: " + route);
        validateFrozen(parameters, route, frozenCaseIds);
        Map<String, List<CaseRecord>> byWorkflow = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (CaseRecord record : cases) {
            if (record == null || record.id() == null || !seen.add(record.id()))
                throw new InvalidCaseSet("Duplicate or null case ID");
            FrozenGateParameters.Workflow workflow = parameters.workflows.get(record.workflow());
            if (workflow == null || !route.equals(record.route()) || !route.equals(workflow.route()))
                throw new InvalidCaseSet("Unknown or mismatched route/workflow");
            if (!frozenCaseIds.get(record.workflow()).contains(record.id())
                    || (record.baselineCaseId() != null && !record.id().equals(record.baselineCaseId()))
                    || (record.candidateCaseId() != null && !record.id().equals(record.candidateCaseId())))
                throw new InvalidCaseSet("Arm or frozen case ID mismatch");
            if (record.subgroup() != null && !workflow.subgroups().containsKey(record.subgroup()))
                throw new InvalidCaseSet("Unknown subgroup");
            byWorkflow.computeIfAbsent(record.workflow(), unused -> new ArrayList<>()).add(record);
        }
        for (String reserveWorkflow : reserves.keySet())
            if (!routeValues.workflows().contains(reserveWorkflow))
                throw new InvalidCaseSet("Unknown reserve workflow");
        Map<String, Map<String, OwnerDisposition>> dispositionsByWorkflow = new HashMap<>();
        for (OwnerDisposition disposition : dispositions) {
            if (disposition == null || !routeValues.workflows().contains(disposition.workflow())
                    || disposition.caseId() == null || disposition.caseId().isBlank()
                    || disposition.value() == null || disposition.reasonCode() == null)
                throw new InvalidOwnerDisposition("Invalid owner disposition");
            if (dispositionsByWorkflow.computeIfAbsent(disposition.workflow(), unused -> new HashMap<>())
                    .putIfAbsent(disposition.caseId(), disposition) != null)
                throw new InvalidOwnerDisposition("Duplicate owner disposition");
        }
        Map<String, WorkflowReport> reports = new LinkedHashMap<>();
        Set<Tier> routeFailures = new LinkedHashSet<>();
        Set<Tier> routeInconclusive = new LinkedHashSet<>();
        boolean inconclusive = false;
        boolean isolated = false;
        int totalD = 0;
        int totalMissing = 0;
        List<RouteT2.WorkflowInput> frbInputs = new ArrayList<>();
        Map<String, Map<String, String>> frbReasons = new HashMap<>();
        for (String workflow : routeValues.workflows()) {
            WorkflowReport report = workflow(parameters, workflow, frozenCaseIds.get(workflow),
                    byWorkflow.getOrDefault(workflow, List.of()), reserves.get(workflow),
                    compatibilitySuites.get(workflow),
                    dispositionsByWorkflow.getOrDefault(workflow, Map.of()));
            reports.put(workflow, report);
            totalD += report.d();
            totalMissing += report.missingPairs();
            routeFailures.addAll(report.failedTiers());
            routeInconclusive.addAll(report.inconclusiveTiers());
            inconclusive |= report.verdict() == Verdict.INCONCLUSIVE;
            isolated |= report.isolatedCriticalIncident();
            if (parameters.frbEnabled) frbInputs.add(frbWorkflowInput(parameters, workflow,
                    frozenCaseIds.get(workflow), byWorkflow.getOrDefault(workflow, List.of()),
                    reserves.get(workflow), reserveDrawnByWorkflow, requestedQuestionsByCaseId,
                    frbReasons));
        }
        FrbRouteReport frbRoute = null;
        if (parameters.frbEnabled) {
            RouteT2.DetailedDecision gates = RouteT2.decideDetailed(new RouteT2.RouteInput(
                    parameters.declaredK(route), parameters.frbRouteAlpha, parameters.frbFamilyBudget,
                    frbInputs, parameters.reserveSize));
            if (gates.routeGate() == Tri.FAIL) routeFailures.add(Tier.T2);
            if (gates.routeGate() == Tri.INCONCLUSIVE) {
                routeInconclusive.add(Tier.T2);
                inconclusive = true;
            }
            List<Integer> pooledKnown = new ArrayList<>();
            List<Tri.Unresolved> pooledUnresolved = new ArrayList<>();
            for (RouteT2.WorkflowInput input : frbInputs) {
                pooledKnown.addAll(input.knownPrimaryDifferences());
                pooledUnresolved.addAll(input.unresolved());
                WorkflowReport old = reports.get(input.workflow());
                RouteT2.WorkflowDecision gate = gates.workflows().get(input.workflow());
                reports.put(input.workflow(), withFrb(old, gate, input, frbReasons.get(input.workflow())));
                if (gate.workflowGate() == Tri.FAIL) routeFailures.add(Tier.T2);
                if (gate.workflowGate() == Tri.INCONCLUSIVE) {
                    routeInconclusive.add(Tier.T2);
                    inconclusive = true;
                }
            }
            frbRoute = new FrbRouteReport(gates.routeGate(), ExactPairedTest.test(
                    lowest(pooledKnown, pooledUnresolved)), parameters.declaredK(route),
                    fraction(parameters.frbRouteAlpha), fraction(parameters.frbFamilyBudget));
        }
        // Missing pairs are excluded from D, but each could still be a baseline-only event (-1). Only a D that
        // exceeds the margin even after crediting every missing pair as -1 is a T3 FAIL; otherwise the evidence
        // cannot decide it and the route stays INCONCLUSIVE, never a FAIL or PASS on partial evidence.
        if (totalD - totalMissing > routeValues.margin()) routeFailures.add(Tier.T3);
        else if (totalD > routeValues.margin()) { routeInconclusive.add(Tier.T3); inconclusive = true; }
        Verdict verdict = !routeFailures.isEmpty() ? Verdict.FAIL
                : inconclusive ? Verdict.INCONCLUSIVE : Verdict.PASS;
        return new RouteReport(verdict, Set.copyOf(routeFailures), Set.copyOf(routeInconclusive),
                totalD, isolated,
                parameters.sha256, Collections.unmodifiableMap(reports), frbRoute);
    }

    private static String fraction(Rational value) { return value.num() + "/" + value.den(); }

    private static List<Integer> lowest(List<Integer> known, List<Tri.Unresolved> ranges) {
        List<Integer> result = new ArrayList<>(known);
        for (Tri.Unresolved range : ranges) result.add(range.lowestDifference());
        return result;
    }

    private static WorkflowReport withFrb(WorkflowReport old, RouteT2.WorkflowDecision gate,
                                          RouteT2.WorkflowInput input, Map<String, String> reasons) {
        Set<Tier> failed = new LinkedHashSet<>(old.failedTiers());
        Set<Tier> unresolved = new LinkedHashSet<>(old.inconclusiveTiers());
        if (gate.workflowGate() == Tri.FAIL) failed.add(Tier.T2);
        if (gate.workflowGate() == Tri.INCONCLUSIVE) unresolved.add(Tier.T2);
        Verdict verdict = !failed.isEmpty() ? Verdict.FAIL
                : old.verdict() == Verdict.INCONCLUSIVE || gate.workflowGate() == Tri.INCONCLUSIVE
                    ? Verdict.INCONCLUSIVE : Verdict.PASS;
        FrbWorkflowReport frb = new FrbWorkflowReport(gate.primaryGate(),
                ExactPairedTest.test(lowest(input.knownPrimaryDifferences(), input.unresolved())),
                input.reserveWasDrawn(), gate.reserveGate(),
                input.unresolved().size() + (gate.reserveGate() == null ? 0 : input.reserveUnresolved().size()),
                gate.cause(), reasons);
        return new WorkflowReport(verdict, Set.copyOf(failed), Set.copyOf(unresolved), old.paired(), old.d(),
                old.candidateOnlyT3(), old.baselineOnlyT3(), old.sharedT3(),
                old.candidateOnlyT2(), old.baselineOnlyT2(), old.sharedT2(),
                old.candidateOnlyT1(), old.baselineOnlyT1(), old.sharedT1(), old.reserveCandidateOnly(),
                old.isolatedCriticalIncident(), old.protocolDeviation(), old.candidateComponents(),
                old.baselineComponents(), old.subgroups(), old.parameterSha256(), old.missingPairs(),
                old.undisposedCriticalCaseIds(), frb, old.causes(), old.recheckOutcomesByCaseId(),
                old.rejectionEvidenceByCaseId());
    }

    private static void validateFrozen(FrozenGateParameters parameters, String route,
                                       Map<String, Set<String>> frozen) {
        if (frozen == null || !frozen.keySet().equals(Set.copyOf(parameters.routes.get(route).workflows())))
            throw new InvalidCaseSet("Frozen workflow set mismatch");
        Set<String> all = new HashSet<>();
        for (String name : parameters.routes.get(route).workflows()) {
            Set<String> ids = frozen.get(name);
            if (ids == null || ids.size() != parameters.workflows.get(name).primarySize())
                throw new InvalidCaseSet("Frozen case count mismatch");
            for (String id : ids) if (id == null || !all.add(id))
                throw new InvalidCaseSet("Duplicate frozen case ID");
        }
    }

    private static RouteT2.WorkflowInput frbWorkflowInput(FrozenGateParameters p, String name,
            Set<String> frozen, List<CaseRecord> cases, List<ReserveCase> reserve,
            Map<String, Boolean> decisions, Map<String, Integer> requested,
            Map<String, Map<String, String>> reasonsByWorkflow) {
        boolean classB = p.frbClassBWorkflows.contains(name);
        if (!classB && (reserve != null || decisions.containsKey(name)))
            throw new InvalidReserve("Class-A workflow has reserve input");
        if (classB && !decisions.containsKey(name))
            throw new InvalidCaseSet("Class-B workflow lacks recorded reserve decision");
        if (classB && decisions.get(name) == null)
            throw new InvalidCaseSet("Null recorded reserve decision");
        Map<String, CaseRecord> byId = new HashMap<>();
        for (CaseRecord item : cases) byId.put(item.id(), item);
        List<Integer> known = new ArrayList<>();
        List<ClassBReserve.PairCritical> pairs = classB ? new ArrayList<>() : null;
        List<Tri.Unresolved> ranges = new ArrayList<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        for (String id : frozen) {
            CaseRecord item = byId.get(id);
            Integer mapped = requested.get(id);
            int recordWidth = item == null ? 0 : item.requestedQuestions();
            if (recordWidth < 0 || mapped != null && mapped <= 0
                    || mapped != null && recordWidth > 0 && recordWidth != mapped)
                throw new InvalidCaseSet("Invalid or conflicting requested question count: " + id);
            int width = classB ? 1 : recordWidth > 0 ? recordWidth : mapped == null ? 0 : mapped;
            if (width <= 0) throw new InvalidCaseSet("Missing requested question count: " + id);
            if (item != null) {
                validateRawArm(item.baseline(), width, classB);
                validateRawArm(item.candidate(), width, classB);
            }
            ArmResolution baseline = item == null || item.baseline() == null ? null
                    : resolve(item.baseline(), p.t1RecheckCount, false, p.frbEnabled);
            ArmResolution candidate = item == null || item.candidate() == null ? null
                    : resolve(item.candidate(), p.t1RecheckCount, true, p.frbEnabled);
            validateFrbArm(baseline, width, classB);
            validateFrbArm(candidate, width, classB);
            boolean baselineUsable = baseline != null && !baseline.incomplete && !baseline.confirmedT1;
            boolean candidateUsable = candidate != null && !candidate.incomplete && !candidate.confirmedT1;
            if (baselineUsable && candidateUsable && baseline.unresolvedQuestions == 0
                    && candidate.unresolvedQuestions == 0) {
                known.add(candidate.criticalQuestions - baseline.criticalQuestions);
                if (classB) pairs.add(new ClassBReserve.PairCritical(
                        candidate.criticalQuestions, baseline.criticalQuestions));
            } else {
                if (classB && Boolean.TRUE.equals(decisions.get(name))) {
                    String baselineReason = unresolvedReason(item == null ? null : item.baseline(), baseline);
                    String candidateReason = unresolvedReason(item == null ? null : item.candidate(), candidate);
                    String reason = baselineReason == null ? "candidate:" + candidateReason
                            : candidateReason == null ? BASELINE_PREFIX + baselineReason
                            : BASELINE_PREFIX + baselineReason + ",candidate:" + candidateReason;
                    reasons.put(id, reason);
                }
                int baselineLow = baselineUsable ? baseline.criticalQuestions : 0;
                int baselineHigh = baselineUsable
                        ? baseline.criticalQuestions + baseline.unresolvedQuestions : width;
                int candidateLow = candidateUsable ? candidate.criticalQuestions : 0;
                int candidateHigh = candidateUsable
                        ? candidate.criticalQuestions + candidate.unresolvedQuestions : width;
                ranges.add(new Tri.Unresolved(candidateLow - baselineHigh,
                        candidateHigh - baselineLow));
            }
        }
        List<Integer> reserveKnown = null;
        List<Tri.Unresolved> reserveUnknown = new ArrayList<>();
        if (reserve != null) {
            reserveKnown = new ArrayList<>();
            for (ReserveCase item : reserve) {
                if (item.baselineEvidence() == Evidence.SCORED && item.candidateEvidence() == Evidence.SCORED)
                    reserveKnown.add((item.candidateCritical() ? 1 : 0) - (item.baselineCritical() ? 1 : 0));
                else reserveUnknown.add(new Tri.Unresolved(-1, 1));
            }
        }
        boolean drawn = classB && decisions.get(name);
        if (classB && drawn && (reserve == null || reserve.size() != p.reserveSize))
            throw new InvalidReserve("Reserve size differs from declaration");
        if (drawn && !ranges.isEmpty()) {
            Map<String, String> ordered = new LinkedHashMap<>();
            reasons.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
            reasonsByWorkflow.put(name, Collections.unmodifiableMap(ordered));
        }
        return new RouteT2.WorkflowInput(name, classB, known, pairs, ranges, drawn,
                reserveKnown, reserveUnknown);
    }

    private static String unresolvedReason(ArmResult arm, ArmResolution resolution) {
        if (resolution != null && !resolution.incomplete && !resolution.confirmedT1
                && resolution.unresolvedQuestions == 0) return null;
        if (resolution != null && resolution.cause != null) return resolution.cause;
        if (arm == null || arm.evidence() == Evidence.MISSING) return "MISSING";
        if (arm.evidence() == Evidence.UNMETERED) return "UNMETERED";
        if (resolution.confirmedT1) return "CONFIRMED_T1";
        if (resolution.incomplete && arm.finalFailureClass() != null) return "INCOMPLETE_RECHECK";
        if (arm.evidence() == Evidence.UNSCORABLE || resolution.unresolvedQuestions > 0)
            return "UNSCORABLE";
        return null;
    }

    private static void validateFrbArm(ArmResolution arm, int width, boolean classB) {
        if (arm == null) return;
        if ((long) arm.criticalQuestions + arm.unresolvedQuestions > (classB ? 1 : width))
            throw new InvalidCaseSet(COUNTS_EXCEED);
    }

    private static void validateRawArm(ArmResult arm, int width, boolean classB) {
        if (arm == null) return;
        if ((long) arm.criticalQuestions() + arm.unresolvedQuestions() > (classB ? 1 : width))
            throw new InvalidCaseSet(COUNTS_EXCEED);
        for (Recheck check : arm.rechecks())
            if ((long) check.criticalQuestions() + check.unresolvedQuestions() > (classB ? 1 : width))
                throw new InvalidCaseSet("Recheck counts exceed requested questions");
    }

    private static WorkflowReport workflow(FrozenGateParameters p, String name, Set<String> frozen,
                                           List<CaseRecord> cases, List<ReserveCase> reserve,
                                           Boolean suitePassed, Map<String, OwnerDisposition> dispositions) {
        Set<Tier> failures = new LinkedHashSet<>();
        Set<Tier> unresolved = new LinkedHashSet<>();
        boolean incomplete = cases.size() != frozen.size() || suitePassed == null;
        if (incomplete) unresolved.add(Tier.T1);
        Set<String> causes = new LinkedHashSet<>();
        if (Boolean.FALSE.equals(suitePassed)) {
            if (p.frbEnabled) {
                incomplete = true;
                unresolved.add(Tier.T1);
                causes.add(HARNESS_REGRESSION);
            } else failures.add(Tier.T1);
        }
        Map<String, String> recheckOutcomes = new LinkedHashMap<>();
        Map<String, String> rejectionEvidence = new LinkedHashMap<>();
        int d = 0, candidateT3 = 0, baselineT3 = 0, sharedT3 = 0;
        int candidateT2 = 0, baselineT2 = 0, sharedT2 = 0;
        int candidateT1 = 0, baselineT1 = 0, sharedT1 = 0;
        int unusable = 0;
        int included = 0;
        Set<String> candidateOnlyCriticalIds = new LinkedHashSet<>();
        Map<String, Integer> candidateComponents = new LinkedHashMap<>();
        Map<String, Integer> baselineComponents = new LinkedHashMap<>();
        Map<String, MutableGroup> groups = new LinkedHashMap<>();
        for (String group : p.workflows.get(name).subgroups().keySet()) groups.put(group, new MutableGroup());
        for (CaseRecord record : cases) {
            if (p.frbEnabled) {
                String outcomes = armOutcomes(BASELINE, record.baseline(), false, p.t1RecheckCount);
                String candidateOutcomes = armOutcomes(CANDIDATE, record.candidate(), true, p.t1RecheckCount);
                if (candidateOutcomes != null) outcomes = outcomes == null ? candidateOutcomes
                        : outcomes + ARM_SEPARATOR + candidateOutcomes;
                if (outcomes != null) recheckOutcomes.put(record.id(), outcomes);
                String evidence = rejectionEvidence(BASELINE, record.baseline());
                String candidateEvidence = rejectionEvidence(CANDIDATE, record.candidate());
                if (candidateEvidence != null) evidence = evidence == null ? candidateEvidence
                        : evidence + ARM_SEPARATOR + candidateEvidence;
                if (evidence != null) rejectionEvidence.put(record.id(), evidence);
            }
            if (record.baseline() == null || record.candidate() == null) {
                incomplete = true;
                unresolved.add(Tier.T1);
                continue;
            }
            ArmResolution baseline = resolve(record.baseline(), p.t1RecheckCount, false, p.frbEnabled);
            ArmResolution candidate = resolve(record.candidate(), p.t1RecheckCount, true, p.frbEnabled);
            if (p.frbEnabled) {
                if (baseline.cause != null) causes.add(baseline.cause);
                if (candidate.cause != null) causes.add(candidate.cause);
            }
            boolean baselineOriginalSucceeded = record.baseline().finalFailureClass() == null
                    && record.baseline().evidence() == Evidence.SCORED;
            MutableGroup group = record.subgroup() == null ? null : groups.get(record.subgroup());
            if (candidate.confirmedT1 && baseline.confirmedT1) {
                sharedT1++;
                if (group != null) group.sharedT1++;
            } else if (candidate.confirmedT1 && !baseline.incomplete && baselineOriginalSucceeded) {
                candidateT1++;
                if (group != null) group.candidateT1++;
            } else if (baseline.confirmedT1 && !candidate.incomplete) {
                baselineT1++;
                if (group != null) group.baselineT1++;
            }
            if (baseline.incomplete || candidate.incomplete) {
                incomplete = true;
                unresolved.add(Tier.T1);
            }
            if (candidate.confirmedT1 && !baselineOriginalSucceeded && !baseline.confirmedT1) {
                incomplete = true;
                unresolved.add(Tier.T1);
            }
            if (candidate.confirmedT1 && baselineOriginalSucceeded && !baseline.confirmedT1)
                failures.add(Tier.T1);
            if (!p.frbEnabled && record.candidate().providerRejected()
                    && !record.baseline().providerRejected()) failures.add(Tier.T1);
            if (record.candidate().unusable()) unusable++;
            if (baseline.incomplete || candidate.incomplete) continue;
            included++;
            Set<String> bc = baseline.components;
            Set<String> cc = candidate.components;
            bc.forEach(component -> baselineComponents.merge(component, 1, Integer::sum));
            cc.forEach(component -> candidateComponents.merge(component, 1, Integer::sum));
            boolean b3 = !bc.isEmpty(), c3 = !cc.isEmpty();
            boolean b2 = baseline.critical, c2 = candidate.critical;
            int delta = (c3 && !b3 ? p.perCaseEventCap : 0)
                    - (b3 && !c3 ? p.perCaseEventCap : 0);
            d += delta;
            if (c3 && !b3) candidateT3++;
            if (b3 && !c3) baselineT3++;
            if (b3 && c3) sharedT3++;
            if (c2 && !b2) {
                candidateT2++;
                candidateOnlyCriticalIds.add(record.id());
            }
            if (b2 && !c2) baselineT2++;
            if (c2 && b2) sharedT2++;
            if (group != null) {
                group.paired++;
                group.d += delta;
                if (c3 && !b3) group.candidateT3++;
                if (b3 && !c3) group.baselineT3++;
                if (b3 && c3) group.sharedT3++;
                if (c2 && !b2) group.candidateT2++;
                if (b2 && !c2) group.baselineT2++;
                if (c2 && b2) group.sharedT2++;
            }
        }
        if (cases.size() >= p.unusableMinCases
                && unusable / (double) cases.size() >= p.unusableFraction) failures.add(Tier.T1);
        int reserveCandidateOnly = 0;
        boolean deviation = !p.frbEnabled && candidateT2 == 0 && reserve != null;
        Set<String> reserveIds = new HashSet<>();
        if (reserve != null) for (ReserveCase item : reserve) {
            if (item == null || item.id() == null || !reserveIds.add(item.id()) || frozen.contains(item.id()))
                throw new InvalidCaseSet("Duplicate or overlapping reserve case");
            if (item.candidateEvidence() == Evidence.SCORED && item.baselineEvidence() == Evidence.SCORED
                    && item.candidateCritical() && !item.baselineCritical())
                candidateOnlyCriticalIds.add(item.id());
        }
        if (p.frbEnabled && reserve != null) for (ReserveCase item : reserve)
            if (item.candidateEvidence() == Evidence.SCORED
                    && item.baselineEvidence() == Evidence.SCORED
                    && item.candidateCritical() && !item.baselineCritical()) reserveCandidateOnly++;
        if (!p.frbEnabled && candidateT2 > 0) {
            if (reserve == null || reserve.size() != p.reserveSize) {
                incomplete = true;
                unresolved.add(Tier.T2);
            }
            else {
                boolean reserveComplete = true;
                for (ReserveCase item : reserve) {
                    if (item.baselineEvidence() != Evidence.SCORED
                            || item.candidateEvidence() != Evidence.SCORED) reserveComplete = false;
                    if (item.candidateCritical() && !item.baselineCritical()) reserveCandidateOnly++;
                }
                if (!reserveComplete) {
                    incomplete = true;
                    unresolved.add(Tier.T2);
                }
                if (reserveComplete && reserveCandidateOnly >= p.t2FailCount) failures.add(Tier.T2);
            }
        }
        for (String caseId : dispositions.keySet())
            if (!candidateOnlyCriticalIds.contains(caseId))
                throw new InvalidOwnerDisposition("Disposition is not for a candidate-only critical case");
        if (dispositions.values().stream().anyMatch(item -> item.value() == DispositionValue.DEFECT_BLOCKS_ROUTE))
            failures.add(Tier.T2);
        Set<String> undisposed = new LinkedHashSet<>(candidateOnlyCriticalIds);
        undisposed.removeAll(dispositions.keySet());
        if (!undisposed.isEmpty()) {
            incomplete = true;
            unresolved.add(Tier.T2);
        }
        Map<String, SubgroupReport> subgroupReports = new LinkedHashMap<>();
        for (Map.Entry<String, MutableGroup> entry : groups.entrySet()) {
            MutableGroup group = entry.getValue();
            if (group.paired != p.workflows.get(name).subgroups().get(entry.getKey())) {
                incomplete = true;
                unresolved.add(Tier.T1);
            }
            subgroupReports.put(entry.getKey(), new SubgroupReport(group.paired,
                    group.candidateT3, group.baselineT3, group.sharedT3,
                    group.candidateT2, group.baselineT2, group.sharedT2,
                    group.candidateT1, group.baselineT1, group.sharedT1, group.d));
        }
        Verdict verdict = !failures.isEmpty() ? Verdict.FAIL
                : incomplete ? Verdict.INCONCLUSIVE : Verdict.PASS;
        List<String> orderedCauses = List.of(HARNESS_REGRESSION, RECHECK_MIXED, RECHECK_UNRESOLVED)
                .stream().filter(causes::contains).toList();
        return new WorkflowReport(verdict, Set.copyOf(failures), Set.copyOf(unresolved), cases.size(), d,
                candidateT3, baselineT3, sharedT3, candidateT2, baselineT2, sharedT2,
                candidateT1, baselineT1, sharedT1,
                reserveCandidateOnly, !p.frbEnabled && candidateT2 > 0 && !failures.contains(Tier.T2), deviation,
                Map.copyOf(candidateComponents), Map.copyOf(baselineComponents),
                Collections.unmodifiableMap(subgroupReports), p.sha256, frozen.size() - included,
                Collections.unmodifiableSet(new LinkedHashSet<>(undisposed)), null,
                orderedCauses.isEmpty() ? null : orderedCauses,
                orderedByCaseId(recheckOutcomes), orderedByCaseId(rejectionEvidence));
    }

    private static Map<String, String> orderedByCaseId(Map<String, String> entries) {
        if (entries.isEmpty()) return null;
        Map<String, String> ordered = new LinkedHashMap<>();
        entries.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(ordered);
    }

    private static String rejectionEvidence(String side, ArmResult arm) {
        if (arm == null || !arm.providerRejected()) return null;
        return side + SIDE_SEPARATOR + (arm.finalFailureClass() == null
                ? "REJECTED_THEN_RETRY_SUCCEEDED" : "REJECTED_FINAL_FAILURE");
    }

    private static String armOutcomes(String side, ArmResult arm, boolean candidate, int requiredRechecks) {
        if (arm == null || !rejectionPairApplies(arm, candidate)
                || arm.rechecks().size() != requiredRechecks) return null;
        return side + SIDE_SEPARATOR + String.join("+", arm.rechecks().stream()
                .map(check -> rejectionOutcome(check).name()).toList());
    }

    private record ArmResolution(boolean incomplete, boolean confirmedT1, Set<String> components,
                                 boolean critical, int criticalQuestions, int unresolvedQuestions,
                                 String cause) {
        ArmResolution(boolean incomplete, boolean confirmedT1, Set<String> components,
                      boolean critical, int criticalQuestions, int unresolvedQuestions) {
            this(incomplete, confirmedT1, components, critical, criticalQuestions, unresolvedQuestions, null);
        }
    }

    private enum RejectionOutcome { SAME_REJECTION, SUCCESS, DIFFERENT_REJECTION, TIMEOUT, OTHER }

    private static boolean rejectionPairApplies(ArmResult arm, boolean candidate) {
        return arm.providerRejected() && (candidate || arm.finalFailureClass() != null);
    }

    private static RejectionOutcome rejectionOutcome(Recheck check) {
        if (check.evidence() != Evidence.SCORED) return RejectionOutcome.OTHER;
        if ("TIMEOUT".equals(check.finalFailureClass())) return RejectionOutcome.TIMEOUT;
        if (check.finalFailureClass() == null) return RejectionOutcome.SUCCESS;
        return check.providerRejected() ? RejectionOutcome.SAME_REJECTION
                : RejectionOutcome.DIFFERENT_REJECTION;
    }

    private static ArmResolution resolveRejection(ArmResult arm, int requiredRechecks, boolean candidate) {
        if (arm.evidence() == Evidence.MISSING || arm.evidence() == Evidence.UNMETERED
                || arm.finalFailureClass() == null && arm.evidence() != Evidence.SCORED)
            return new ArmResolution(true, false, Set.of(), false, 0, 0, RECHECK_UNRESOLVED);
        if (arm.rechecks().size() != requiredRechecks)
            return new ArmResolution(true, false, Set.of(), false, 0, 0, RECHECK_UNRESOLVED);
        List<RejectionOutcome> outcomes = arm.rechecks().stream().map(RouteVerdictCalculator::rejectionOutcome).toList();
        if (outcomes.stream().allMatch(outcome -> outcome == RejectionOutcome.SAME_REJECTION))
            return new ArmResolution(!candidate, true, Set.of(), false, 0, 0);
        if (outcomes.stream().allMatch(outcome -> outcome == RejectionOutcome.SUCCESS)) {
            Recheck first = arm.rechecks().get(0);
            Set<String> components = new LinkedHashSet<>(arm.finalFailureClass() == null
                    ? arm.t3Components() : first.t3Components());
            components.add(ISOLATED_FINAL_FAILURE);
            return arm.finalFailureClass() == null
                    ? new ArmResolution(false, false, Set.copyOf(components), arm.critical(),
                        arm.criticalQuestions(), arm.unresolvedQuestions())
                    : new ArmResolution(false, false, Set.copyOf(components), first.critical(),
                        first.criticalQuestions(), first.unresolvedQuestions());
        }
        String cause = outcomes.contains(RejectionOutcome.OTHER) ? RECHECK_UNRESOLVED : RECHECK_MIXED;
        return new ArmResolution(true, false, Set.of(), false, 0, 0, cause);
    }

    private static ArmResolution resolve(ArmResult arm, int requiredRechecks, boolean candidate,
                                         boolean frbEnabled) {
        if (frbEnabled && rejectionPairApplies(arm, candidate))
            return resolveRejection(arm, requiredRechecks, candidate);
        if (arm.finalFailureClass() == null) return arm.evidence() == Evidence.SCORED
                ? new ArmResolution(false, false, arm.t3Components(), arm.critical(),
                    arm.criticalQuestions(), arm.unresolvedQuestions())
                : new ArmResolution(true, false, Set.of(), false, 0, 0);
        if (arm.evidence() == Evidence.MISSING || arm.evidence() == Evidence.UNMETERED)
            return new ArmResolution(true, false, Set.of(), false, 0, 0);
        if (arm.rechecks().size() != requiredRechecks) return new ArmResolution(true, false, Set.of(), false, 0, 0);
        Recheck firstSuccess = null;
        boolean allSame = true;
        for (Recheck check : arm.rechecks()) {
            if (check.evidence() != Evidence.SCORED) return new ArmResolution(true, false, Set.of(), false, 0, 0);
            if (check.finalFailureClass() == null && firstSuccess == null) firstSuccess = check;
            if (!arm.finalFailureClass().equals(check.finalFailureClass())) allSame = false;
        }
        if (firstSuccess != null) {
            Set<String> components = new LinkedHashSet<>(firstSuccess.t3Components());
            components.add(ISOLATED_FINAL_FAILURE);
            return new ArmResolution(false, false, Set.copyOf(components), firstSuccess.critical(),
                    firstSuccess.criticalQuestions(), firstSuccess.unresolvedQuestions());
        }
        if (allSame) return new ArmResolution(!candidate, true, Set.of(), false, 0, 0);
        return new ArmResolution(true, false, Set.of(), false, 0, 0);
    }

    private static final class MutableGroup {
        int paired, candidateT3, baselineT3, sharedT3, candidateT2, baselineT2, sharedT2;
        int candidateT1, baselineT1, sharedT1, d;
    }
}
