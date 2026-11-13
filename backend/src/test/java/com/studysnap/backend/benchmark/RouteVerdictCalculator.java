package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sections 4 and 8 paired case, workflow, subgroup and route gate. */
final class RouteVerdictCalculator {
    private RouteVerdictCalculator() {}

    static final class InvalidCaseSet extends RuntimeException {
        InvalidCaseSet(String message) { super(message); }
    }
    enum Verdict { PASS, FAIL, INCONCLUSIVE }
    enum Evidence { SCORED, MISSING, UNMETERED, UNSCORABLE }
    enum Tier { T1, T2, T3 }

    record ArmResult(Evidence evidence, Set<String> t3Components, boolean critical,
                     String finalFailureClass, List<Recheck> rechecks, boolean providerRejected,
                     boolean unusable) {
        ArmResult {
            t3Components = t3Components == null ? Set.of() : Set.copyOf(t3Components);
            rechecks = rechecks == null ? List.of() : List.copyOf(rechecks);
        }
    }
    record Recheck(Evidence evidence, Set<String> t3Components, boolean critical, String finalFailureClass) {
        Recheck { t3Components = t3Components == null ? Set.of() : Set.copyOf(t3Components); }
    }
    record CaseRecord(String id, String workflow, String route, String subgroup,
                      String baselineCaseId, String candidateCaseId,
                      ArmResult baseline, ArmResult candidate) {}
    record ReserveCase(String id, Evidence baselineEvidence, Evidence candidateEvidence,
                       boolean baselineCritical, boolean candidateCritical) {}
    record SubgroupReport(int paired, int candidateOnlyT3, int baselineOnlyT3,
                          int sharedT3, int candidateOnlyT2, int baselineOnlyT2,
                          int sharedT2, int candidateOnlyT1, int baselineOnlyT1,
                          int sharedT1, int d) {}
    record WorkflowReport(Verdict verdict, Set<Tier> failedTiers, Set<Tier> inconclusiveTiers,
                          int paired, int d,
                          int candidateOnlyT3, int baselineOnlyT3, int sharedT3,
                          int candidateOnlyT2, int baselineOnlyT2, int sharedT2,
                          int candidateOnlyT1, int baselineOnlyT1, int sharedT1,
                          int reserveCandidateOnly,
                          boolean isolatedCriticalIncident, boolean protocolDeviation,
                          Map<String, Integer> candidateComponents,
                          Map<String, Integer> baselineComponents,
                          Map<String, SubgroupReport> subgroups, String parameterSha256, int missingPairs) {}
    record RouteReport(Verdict verdict, Set<Tier> failedTiers, Set<Tier> inconclusiveTiers, int d,
                       boolean isolatedCriticalIncident, String parameterSha256,
                       Map<String, WorkflowReport> workflows) {}

    static RouteReport calculate(FrozenGateParameters parameters, String route,
                                 Map<String, Set<String>> frozenCaseIds,
                                 List<CaseRecord> cases,
                                 Map<String, List<ReserveCase>> reserves,
                                 Map<String, Boolean> compatibilitySuites) {
        cases = cases == null ? List.of() : cases;
        reserves = reserves == null ? Map.of() : reserves;
        compatibilitySuites = compatibilitySuites == null ? Map.of() : compatibilitySuites;
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
        Map<String, WorkflowReport> reports = new LinkedHashMap<>();
        Set<Tier> routeFailures = new LinkedHashSet<>();
        Set<Tier> routeInconclusive = new LinkedHashSet<>();
        boolean inconclusive = false;
        boolean isolated = false;
        int totalD = 0;
        int totalMissing = 0;
        for (String workflow : routeValues.workflows()) {
            WorkflowReport report = workflow(parameters, workflow, frozenCaseIds.get(workflow),
                    byWorkflow.getOrDefault(workflow, List.of()), reserves.get(workflow),
                    compatibilitySuites.get(workflow));
            reports.put(workflow, report);
            totalD += report.d();
            totalMissing += report.missingPairs();
            routeFailures.addAll(report.failedTiers());
            routeInconclusive.addAll(report.inconclusiveTiers());
            inconclusive |= report.verdict() == Verdict.INCONCLUSIVE;
            isolated |= report.isolatedCriticalIncident();
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
                parameters.sha256, Collections.unmodifiableMap(reports));
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

    private static WorkflowReport workflow(FrozenGateParameters p, String name, Set<String> frozen,
                                           List<CaseRecord> cases, List<ReserveCase> reserve,
                                           Boolean suitePassed) {
        Set<Tier> failures = new LinkedHashSet<>();
        Set<Tier> unresolved = new LinkedHashSet<>();
        boolean incomplete = cases.size() != frozen.size() || suitePassed == null;
        if (incomplete) unresolved.add(Tier.T1);
        if (Boolean.FALSE.equals(suitePassed)) failures.add(Tier.T1);
        int d = 0, candidateT3 = 0, baselineT3 = 0, sharedT3 = 0;
        int candidateT2 = 0, baselineT2 = 0, sharedT2 = 0;
        int candidateT1 = 0, baselineT1 = 0, sharedT1 = 0;
        int unusable = 0;
        int included = 0;
        Map<String, Integer> candidateComponents = new LinkedHashMap<>();
        Map<String, Integer> baselineComponents = new LinkedHashMap<>();
        Map<String, MutableGroup> groups = new LinkedHashMap<>();
        for (String group : p.workflows.get(name).subgroups().keySet()) groups.put(group, new MutableGroup());
        for (CaseRecord record : cases) {
            if (record.baseline() == null || record.candidate() == null) {
                incomplete = true;
                unresolved.add(Tier.T1);
                continue;
            }
            ArmResolution baseline = resolve(record.baseline(), p.t1RecheckCount, false);
            ArmResolution candidate = resolve(record.candidate(), p.t1RecheckCount, true);
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
            if (record.candidate().providerRejected() && !record.baseline().providerRejected()) failures.add(Tier.T1);
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
            if (c2 && !b2) candidateT2++;
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
        boolean deviation = candidateT2 == 0 && reserve != null;
        if (candidateT2 > 0) {
            if (reserve == null || reserve.size() != p.reserveSize) {
                incomplete = true;
                unresolved.add(Tier.T2);
            }
            else {
                boolean reserveComplete = true;
                Set<String> reserveIds = new HashSet<>();
                for (ReserveCase item : reserve) {
                    if (item.id() == null || !reserveIds.add(item.id()) || frozen.contains(item.id()))
                        throw new InvalidCaseSet("Duplicate or overlapping reserve case");
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
        return new WorkflowReport(verdict, Set.copyOf(failures), Set.copyOf(unresolved), cases.size(), d,
                candidateT3, baselineT3, sharedT3, candidateT2, baselineT2, sharedT2,
                candidateT1, baselineT1, sharedT1,
                reserveCandidateOnly, candidateT2 > 0 && !failures.contains(Tier.T2), deviation,
                Map.copyOf(candidateComponents), Map.copyOf(baselineComponents),
                Collections.unmodifiableMap(subgroupReports), p.sha256, frozen.size() - included);
    }

    private record ArmResolution(boolean incomplete, boolean confirmedT1, Set<String> components,
                                 boolean critical) {}

    private static ArmResolution resolve(ArmResult arm, int requiredRechecks, boolean candidate) {
        if (arm.evidence() != Evidence.SCORED) return new ArmResolution(true, false, Set.of(), false);
        if (arm.finalFailureClass() == null) return new ArmResolution(false, false,
                arm.t3Components(), arm.critical());
        if (arm.rechecks().size() != requiredRechecks) return new ArmResolution(true, false, Set.of(), false);
        Recheck firstSuccess = null;
        boolean allSame = true;
        for (Recheck check : arm.rechecks()) {
            if (check.evidence() != Evidence.SCORED) return new ArmResolution(true, false, Set.of(), false);
            if (check.finalFailureClass() == null && firstSuccess == null) firstSuccess = check;
            if (!arm.finalFailureClass().equals(check.finalFailureClass())) allSame = false;
        }
        if (firstSuccess != null) {
            Set<String> components = new LinkedHashSet<>(firstSuccess.t3Components());
            components.add("ISOLATED_FINAL_FAILURE");
            return new ArmResolution(false, false, Set.copyOf(components), firstSuccess.critical());
        }
        if (allSame) return new ArmResolution(!candidate, true, Set.of(), false);
        return new ArmResolution(true, false, Set.of(), false);
    }

    private static final class MutableGroup {
        int paired, candidateT3, baselineT3, sharedT3, candidateT2, baselineT2, sharedT2;
        int candidateT1, baselineT1, sharedT1, d;
    }
}
