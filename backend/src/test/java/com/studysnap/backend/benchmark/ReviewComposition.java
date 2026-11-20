package com.studysnap.backend.benchmark;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Composes ledger-minted checks across the verified FRB lock set. This proves coverage of the
 * provenance locks, not that every store directory an operator could have opened was supplied.
 * Exact token lock-hash equality proves one token for each provenance lock and no other lock.
 * A key-defect completion in one class-A primary may satisfy a case from another primary covering
 * the same workflow; every primary still has its open findings, disputes and concerns checked.
 * Provenance verification reads files; the report is otherwise trusted as runFrb output. External
 * heads are only as external as the operator's independent note of each append to every store.
 */
final class ReviewComposition {
    private static final String LOCK_LABEL = "^(primary\\.\\d+|reserve\\.[A-Za-z0-9_-]+\\.(forPrimary|forReserve))\\.lock$";
    private static final List<String> HEAD_STORES = List.of("safety", "disputes", "reviews");
    static final class InvalidComposition extends RuntimeException {
        InvalidComposition(String message) { super(message); }
        InvalidComposition(String message, Throwable cause) { super(message, cause); }
    }

    static final class ComposedReviewCheck {
        private final String route;
        private final Set<String> lockSha256s;
        private final Set<String> coveredWorkflows;
        private final Set<FindingsLedger.ReviewSubject> requiredKeyDefectSubjects;
        private final Map<String, String> heads;
        private final Map<String, Set<FindingsLedger.ReviewSubject>> extrasByLock;
        private final KeyDefectWorkload.Parameters workloadParameters;
        private final Set<String> candidateArms;
        private final Set<String> baselineArms;
        private final KeyDefectWorkload.Estimate workloadEstimate;
        private final String parametersSha256;

        private ComposedReviewCheck(String route, Set<String> lockSha256s, Set<String> coveredWorkflows,
                Set<FindingsLedger.ReviewSubject> requiredKeyDefectSubjects, Map<String, String> heads,
                Map<String, Set<FindingsLedger.ReviewSubject>> extrasByLock,
                KeyDefectWorkload.Parameters workloadParameters, Set<String> candidateArms,
                Set<String> baselineArms, KeyDefectWorkload.Estimate workloadEstimate,
                String parametersSha256) {
            this.route = route;
            this.lockSha256s = sorted(lockSha256s);
            this.coveredWorkflows = sorted(coveredWorkflows);
            this.requiredKeyDefectSubjects = Set.copyOf(requiredKeyDefectSubjects);
            this.heads = Map.copyOf(heads);
            Map<String, Set<FindingsLedger.ReviewSubject>> copied = new TreeMap<>();
            extrasByLock.forEach((hash, subjects) -> copied.put(hash, Set.copyOf(subjects)));
            this.extrasByLock = Collections.unmodifiableMap(copied);
            this.workloadParameters = workloadParameters;
            this.candidateArms = sorted(candidateArms);
            this.baselineArms = sorted(baselineArms);
            this.workloadEstimate = workloadEstimate;
            this.parametersSha256 = parametersSha256;
        }

        private static Set<String> sorted(Set<String> values) {
            return Collections.unmodifiableSortedSet(new TreeSet<>(values));
        }
        String route() { return route; }
        Set<String> lockSha256s() { return lockSha256s; }
        Set<String> coveredWorkflows() { return coveredWorkflows; }
        Set<FindingsLedger.ReviewSubject> requiredKeyDefectSubjects() { return requiredKeyDefectSubjects; }
        Map<String, String> heads() { return heads; }
        Map<String, Set<FindingsLedger.ReviewSubject>> extrasByLock() { return extrasByLock; }
        KeyDefectWorkload.Parameters workloadParameters() { return workloadParameters; }
        Set<String> candidateArms() { return candidateArms; }
        Set<String> baselineArms() { return baselineArms; }
        KeyDefectWorkload.Estimate workloadEstimate() { return workloadEstimate; }
        String parametersSha256() { return parametersSha256; }

        @Override public boolean equals(Object other) {
            if (!(other instanceof ComposedReviewCheck that)) return false;
            return Objects.equals(route, that.route) && lockSha256s.equals(that.lockSha256s)
                    && coveredWorkflows.equals(that.coveredWorkflows)
                    && requiredKeyDefectSubjects.equals(that.requiredKeyDefectSubjects)
                    && heads.equals(that.heads) && extrasByLock.equals(that.extrasByLock)
                    && workloadParameters.equals(that.workloadParameters)
                    && candidateArms.equals(that.candidateArms) && baselineArms.equals(that.baselineArms)
                    && workloadEstimate.equals(that.workloadEstimate)
                    && Objects.equals(parametersSha256, that.parametersSha256);
        }
        @Override public int hashCode() {
            return Objects.hash(route, lockSha256s, coveredWorkflows, requiredKeyDefectSubjects, heads,
                    extrasByLock, workloadParameters, candidateArms, baselineArms, workloadEstimate,
                    parametersSha256);
        }
    }

    private ReviewComposition() {}

    static ComposedReviewCheck compose(String route, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb, RouteVerdictCalculator.RouteReport report,
            List<FindingsLedger.ReviewCheck> tokens, KeyDefectWorkload.Parameters workloadParameters,
            Set<String> candidateArms, Set<String> baselineArms) {
        // Keep the existing named provenance failure visible to the caller.
        FrbProvenance.Verified provenance = FrbProvenance.verify(run, frb);
        try {
            if (route == null || tokens == null || workloadParameters == null || candidateArms == null
                    || baselineArms == null) throw new InvalidComposition("Incomplete composition inputs");
            FrozenGateParameters parameters = FrozenGateParameters.load(run.parameters());
            FrozenGateParameters.Route declaration = parameters.routes.get(route);
            if (declaration == null || report == null || report.workflows() == null
                    || report.workflows().isEmpty()
                    || !report.workflows().keySet().equals(Set.copyOf(declaration.workflows()))
                    || !parameters.sha256.equals(report.parameterSha256()))
                throw new InvalidComposition("Report differs from frozen route");

            Map<String, Path> lockFiles = lockFiles(frb.provenance());
            Map<String, Set<String>> coverage = new HashMap<>();
            for (var entry : provenance.hashes().entrySet()) {
                if (!entry.getKey().matches(LOCK_LABEL))
                    continue;
                Path lockFile = lockFiles.get(entry.getKey());
                if (lockFile == null || !IntegritySupport.detachedHash(lockFile).equals(entry.getValue())
                        || coverage.containsKey(entry.getValue()))
                    throw new InvalidComposition("Provenance lock mapping is incomplete or duplicated");
                coverage.put(entry.getValue(), covered(LockRecord.load(lockFile), declaration, parameters));
            }
            if (coverage.isEmpty() || tokens.size() != coverage.size())
                throw new InvalidComposition("Exactly one token per provenance lock required");
            Map<String, FindingsLedger.ReviewCheck> byLock = new HashMap<>();
            for (FindingsLedger.ReviewCheck token : tokens) {
                if (token == null || !route.equals(token.route())
                        || !parameters.sha256.equals(token.parametersSha256())
                        || !coverage.containsKey(token.lockSha256())
                        || byLock.putIfAbsent(token.lockSha256(), token) != null)
                    throw new InvalidComposition("Token route, parameters or lock differs");
            }
            if (!byLock.keySet().equals(coverage.keySet()))
                throw new InvalidComposition("Token lock set differs from provenance");
            Set<String> union = new TreeSet<>();
            coverage.values().forEach(union::addAll);
            requireCoverage(union, report.workflows().keySet());

            Map<String, String> workflowByCaseId = ResultsPipeline.workflowByCaseId(run);
            Set<String> classA = new HashSet<>(parameters.workflows.keySet());
            classA.removeAll(parameters.frbClassBWorkflows);
            Set<FindingsLedger.ReviewSubject> derived = KeyDefectSubjects.derive(frb.bundle(),
                    workflowByCaseId, classA, parameters.frbClassBWorkflows);
            Set<FindingsLedger.ReviewSubject> routeDerived = routeScoped(derived, declaration);
            Set<FindingsLedger.ReviewSubject> required = requiredSubjects(parameters, declaration, routeDerived);

            Map<String, String> heads = new TreeMap<>();
            Map<String, Set<FindingsLedger.ReviewSubject>> extras = new TreeMap<>();
            for (var entry : byLock.entrySet()) {
                FindingsLedger.ReviewCheck token = entry.getValue();
                if (!token.openSafetyFindingIds().isEmpty() || !token.openDisputeIds().isEmpty()
                        || !token.openConcernIds().isEmpty() || !token.extras().containsAll(routeDerived)
                        || token.heads() == null || !token.heads().keySet().equals(Set.copyOf(HEAD_STORES)))
                    throw new InvalidComposition("Lock has open records, omitted extras or incomplete heads");
                for (String store : HEAD_STORES) {
                    String value = token.heads().get(store);
                    if (value == null || !value.matches("[0-9]+:[a-f0-9]{64}"))
                        throw new InvalidComposition("Invalid token head");
                    heads.put(entry.getKey() + "." + store, value);
                }
                extras.put(entry.getKey(), token.extras());
            }
            for (FindingsLedger.ReviewSubject subject : required) {
                String name = subject.kind() + ":" + subject.workflow() + ":" + subject.subjectId();
                boolean completed = false;
                for (var entry : byLock.entrySet()) {
                    if (coverage.get(entry.getKey()).contains(subject.workflow())
                            && !entry.getValue().missing().contains(name)) completed = true;
                }
                if (!completed) throw new InvalidComposition("Required review lacks a covering completion: " + name);
            }
            KeyDefectWorkload.Estimate estimate = KeyDefectWorkload.estimate(frb.bundle(),
                    workflowByCaseId, classA, parameters.frbClassBWorkflows, candidateArms,
                    baselineArms, workloadParameters);
            return new ComposedReviewCheck(route, coverage.keySet(), union, routeDerived, heads,
                    extras, workloadParameters, candidateArms, baselineArms, estimate, parameters.sha256);
        } catch (InvalidComposition exception) { throw exception; }
        catch (Exception exception) { throw new InvalidComposition("Cannot verify review composition", exception); }
    }

    /** Derived key-defect subjects restricted to the workflows of one route. */
    static Set<FindingsLedger.ReviewSubject> routeScoped(Set<FindingsLedger.ReviewSubject> derived,
            FrozenGateParameters.Route declaration) {
        Set<FindingsLedger.ReviewSubject> result = new HashSet<>();
        for (FindingsLedger.ReviewSubject subject : derived)
            if (declaration.workflows().contains(subject.workflow())) result.add(subject);
        return Set.copyOf(result);
    }

    /** The route's class-B workflow subjects plus its derived key-defect subjects. */
    static Set<FindingsLedger.ReviewSubject> requiredSubjects(FrozenGateParameters parameters,
            FrozenGateParameters.Route declaration, Set<FindingsLedger.ReviewSubject> routeDerived) {
        Set<FindingsLedger.ReviewSubject> required = new HashSet<>(routeDerived);
        for (String workflow : parameters.frbClassBWorkflows)
            if (declaration.workflows().contains(workflow))
                required.add(new FindingsLedger.ReviewSubject(ReviewRecord.Kind.CLASS_B_WORKFLOW,
                        workflow, workflow));
        return Set.copyOf(required);
    }

    private static Map<String, Path> lockFiles(ResultsPipeline.ProvenanceLogs logs) {
        Map<String, Path> result = new HashMap<>();
        int index = 0;
        for (ResultsPipeline.PrimaryExport export : logs.primaries())
            result.put("primary." + index++ + ".lock", export.lockFile());
        logs.reserves().forEach((workflow, export) -> {
            result.put("reserve." + workflow + ".forPrimary.lock", export.lockForPrimaryOpening());
            result.put("reserve." + workflow + ".forReserve.lock", export.lockForReserveOpening());
        });
        return result;
    }

    static void requireCoverage(Set<String> covered, Set<String> reported) {
        if (covered == null || reported == null || reported.isEmpty() || !covered.containsAll(reported))
            throw new InvalidComposition("Report workflows lack lock coverage");
    }

    private static Set<String> covered(LockRecord lock, FrozenGateParameters.Route route,
            FrozenGateParameters parameters) {
        Set<String> result = new TreeSet<>();
        for (String workflow : route.workflows()) {
            FrozenGateParameters.Workflow frozen = parameters.workflows.get(workflow);
            if (frozen == null) throw new InvalidComposition("Route has undeclared workflow");
            if (frozen.subgroups().isEmpty()) {
                if (lock.workflows().contains(workflow)) result.add(workflow);
            } else if (frozen.subgroups().keySet().stream()
                    .allMatch(subgroup -> lock.workflows().contains(workflow + "_" + subgroup))) {
                result.add(workflow);
            }
        }
        return Set.copyOf(result);
    }
}
