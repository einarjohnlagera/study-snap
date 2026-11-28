package com.studysnap.backend.benchmark;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds the per-route FRB classification inputs from verified sources, in two phases: the route
 * reports are recomputed first (key-free, into a scratch report path), then each route's ledger,
 * review-composition, operational-cause, veto, expansion and cost facts are derived from them.
 *
 * Honest limits. Nothing in the repository calls this class yet (the operator flow is Brief 5r-2b).
 * ProductionSwitchEligibility keeps SWITCH_ASSERTIONS_NOT_VERIFIED under its default options; under
 * the opt-in requireVerifiedAssertions (Brief 5r-2c) it re-derives those assertions at switch time
 * and may then report an eligible decision, which is advice for the owner's attended decision only.
 * The inputs are buildable by this class; that is not an end-to-end verification of the classification.
 *
 * Owner-supplied trusted facts, validated for shape but not verified: the key-defect workload
 * parameters (including H_max), the candidate and baseline arm labels, solRecommended (nothing derives
 * it), and the externally noted ledger heads (only as external as the operator's note). The ledger map
 * is checked for completeness against the provenance locks; completeness against every review-store
 * directory an operator could have opened, and the binding of each store to its lock beyond the
 * token's own lock hash, are not checked. ABSOLUTE_QUALITY subjects are never required, because
 * nothing derives them; an open ABSOLUTE_QUALITY or OPERATIONAL_CAUSE concern reaches the owner only
 * because a failed composition sets missingRequiredReviews to null.
 *
 * Copied logic that can drift: pendingRoutes repeats runFrb's private derivation using the
 * package-visible workflowByCaseId (a case id it lacks is skipped, as runFrb does); the class-A
 * subtraction and the NO_VETO loop repeat ReviewComposition.compose. A noted head that differs from
 * the current head is a ledger failure, not a composition failure.
 */
final class RouteClassificationInputs {
    private static final String CASE_ARM_SEPARATOR = "\u0000";
    private static final String INCOMPLETE_INPUTS = "Route ledgers and owner facts are required";
    private static final String LEDGER_SET_DIFFERS = "Ledger lock set differs from the provenance locks";
    private static final String TOKEN_LOCK_DIFFERS = "Ledger token lock hash differs from its key";
    private static final String LEDGER_FAILURE = "Ledger failure: ";
    private static final String UNREADABLE = "Cannot read FRB inputs";
    private static final Set<String> TRUSTED_OWNER_FACTS = Collections.unmodifiableSortedSet(new TreeSet<>(Set.of(
            "WORKLOAD_PARAMETERS", "CANDIDATE_ARMS", "BASELINE_ARMS", "SOL_RECOMMENDED", "NOTED_LEDGER_HEADS")));

    private RouteClassificationInputs() {}

    static final class InvalidRouteInputs extends RuntimeException {
        InvalidRouteInputs(String message) { super(message); }
        InvalidRouteInputs(String message, Throwable cause) { super(message, cause); }
    }

    /** All four facts are owner-supplied and trusted; arm overlap or an unknown arm fails composition. */
    record OwnerFacts(KeyDefectWorkload.Parameters workloadParameters, Set<String> candidateArms,
            Set<String> baselineArms, boolean solRecommended) {
        OwnerFacts {
            if (workloadParameters == null || candidateArms == null || baselineArms == null
                    || candidateArms.isEmpty() || baselineArms.isEmpty()
                    || candidateArms.stream().anyMatch(Objects::isNull)
                    || baselineArms.stream().anyMatch(Objects::isNull))
                throw new InvalidRouteInputs("Owner facts and non-empty arm sets are required");
            candidateArms = sortedCopy(candidateArms);
            baselineArms = sortedCopy(baselineArms);
        }
    }

    /** The whole composition's ledgers, identical for every route, keyed by provenance lock hash. */
    record RouteLedgers(Map<String, FindingsLedger> ledgersByLockSha256,
            Map<String, FindingsLedger.ExternalHeads> notedHeadsByLockSha256) {
        RouteLedgers {
            if (ledgersByLockSha256 == null || notedHeadsByLockSha256 == null || ledgersByLockSha256.isEmpty()
                    || !ledgersByLockSha256.keySet().equals(notedHeadsByLockSha256.keySet())
                    || ledgersByLockSha256.values().stream().anyMatch(Objects::isNull)
                    || notedHeadsByLockSha256.values().stream().anyMatch(Objects::isNull))
                throw new InvalidRouteInputs("Equal, non-empty ledger and noted-head key sets are required");
            ledgersByLockSha256 = Collections.unmodifiableMap(new TreeMap<>(ledgersByLockSha256));
            notedHeadsByLockSha256 = Collections.unmodifiableMap(new TreeMap<>(notedHeadsByLockSha256));
        }
    }

    record Notes(String route, boolean composed, String compositionFailure, Set<String> openConcernIds,
            Set<String> missingFromTokens, OperationalCauses.Result operationalCauses,
            String operationalCauseFailure, String costFailure, Set<String> unmetOwnerFacts) {
        Notes {
            openConcernIds = sortedCopy(openConcernIds);
            missingFromTokens = sortedCopy(missingFromTokens);
            unmetOwnerFacts = sortedCopy(unmetOwnerFacts);
        }
    }

    record Built(Map<String, RouteVerdictCalculator.RouteReport> reports,
            Map<String, FrbClassificationInput> inputs, Set<String> pendingRoutes,
            Map<String, SortedSet<String>> derivedCostFlags, Map<String, Notes> notes) {
        Built {
            reports = Collections.unmodifiableMap(new TreeMap<>(reports));
            inputs = Collections.unmodifiableMap(new TreeMap<>(inputs));
            pendingRoutes = sortedCopy(pendingRoutes);
            Map<String, SortedSet<String>> flags = new TreeMap<>();
            derivedCostFlags.forEach((route, values) ->
                    flags.put(route, Collections.unmodifiableSortedSet(new TreeSet<>(values))));
            derivedCostFlags = Collections.unmodifiableMap(flags);
            notes = Collections.unmodifiableMap(new TreeMap<>(notes));
        }
    }

    /** One route's input and notes; the per-route result of build. */
    record RouteInput(FrbClassificationInput input, Notes notes) {}

    /** A cost result applied to every route: null flags mean undetermined. */
    record CostFacts(Set<String> flags, String failure) {}

    static Built build(FrbBundleAssembler.Assembled assembled, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb, RouteLedgers ledgers, OwnerFacts owner,
            List<ProductionSwitchEligibility.CostLedgerInput> costLedgers) {
        FrbBundleAssembler.requireBound(assembled, run, frb);
        if (ledgers == null || owner == null) throw new InvalidRouteInputs(INCOMPLETE_INPUTS);
        Map<String, RouteVerdictCalculator.RouteReport> reports = ReportRecomputation.recompute(run, frb);
        try {
            requireCompleteLockSet(frb, ledgers);
            FrozenGateParameters parameters = FrozenGateParameters.load(run.parameters());
            Set<String> pending = pendingRoutes(run, frb, parameters);
            Map<String, SortedSet<String>> derivedCostFlags = frb.operationalCostReview()
                    ? OperationalCostReview.flagsByRoute(ResultsPipeline.operationalCostReview(run), parameters)
                    : Map.of();
            CostFacts cost = costFacts(costLedgers);
            Map<String, String> expansion = new LinkedHashMap<>(assembled.expansionOutcomeByWorkflow());
            Map<String, FrbClassificationInput> inputs = new TreeMap<>();
            Map<String, Notes> notes = new TreeMap<>();
            for (String route : new TreeSet<>(reports.keySet())) {
                RouteInput result = routeInput(route, reports.get(route), run, frb, ledgers, owner,
                        expansion, cost);
                inputs.put(route, result.input());
                notes.put(route, result.notes());
            }
            return new Built(reports, inputs, pending, derivedCostFlags, notes);
        } catch (IOException exception) {
            throw new InvalidRouteInputs(UNREADABLE, exception);
        }
    }

    static Map<String, FrbClassification> classify(Built built) {
        return ResultsPipeline.classifyFrbRoutes(built.reports(), built.inputs(), built.pendingRoutes(),
                built.derivedCostFlags());
    }

    /**
     * Test-only seam, like FrbBundleAssembler.assembleParts: the per-route step build runs on its own
     * recomputed report. Other callers must use build, which never accepts a caller-supplied report.
     */
    static RouteInput routeInput(String route, RouteVerdictCalculator.RouteReport report,
            ResultsPipeline.FrbRunInput run, ResultsPipeline.FrbInput frb, RouteLedgers ledgers,
            OwnerFacts owner, Map<String, String> expansion, CostFacts cost) throws IOException {
        List<String> locks = new ArrayList<>(new TreeSet<>(ledgers.ledgersByLockSha256().keySet()));
        boolean ledgerFailure = false;
        String compositionFailure = null;
        Set<FindingsLedger.ReviewSubject> extras = null;
        try {
            extras = extras(route, run, frb);
        } catch (InvalidEvidence exception) {
            // compose derives the same subjects and would refuse the same evidence: shape (b).
            compositionFailure = "Cannot derive review extras: " + exception.getMessage();
        }
        List<FindingsLedger.ReviewCheck> tokens = new ArrayList<>();
        boolean tokenFailed = extras == null;
        Set<String> safety = new TreeSet<>();
        Set<String> disputes = new TreeSet<>();
        Set<String> concerns = new TreeSet<>();
        Set<String> missing = new TreeSet<>();
        if (extras != null) for (String lock : locks) {
            try {
                FindingsLedger.ReviewCheck token = ledgers.ledgersByLockSha256().get(lock)
                        .checkLedger(route, extras, ledgers.notedHeadsByLockSha256().get(lock));
                if (!lock.equals(token.lockSha256())) throw new InvalidRouteInputs(TOKEN_LOCK_DIFFERS);
                tokens.add(token);
                safety.addAll(token.openSafetyFindingIds());
                disputes.addAll(token.openDisputeIds());
                concerns.addAll(token.openConcernIds());
                missing.addAll(token.missing());
            } catch (FindingsLedger.InvalidLedger exception) {
                ledgerFailure = true;
                tokenFailed = true;
                compositionFailure = LEDGER_FAILURE + exception.getMessage();
            }
        }
        ReviewComposition.ComposedReviewCheck composed = null;
        if (!tokenFailed) {
            try {
                composed = ReviewComposition.compose(route, run, frb, report, tokens,
                        owner.workloadParameters(), owner.candidateArms(), owner.baselineArms(), true);
            } catch (ReviewComposition.InvalidComposition exception) {
                compositionFailure = exception.getMessage();
            }
        }
        Set<String> vetoes = new TreeSet<>();
        for (String lock : locks) {
            try {
                vetoes.addAll(ledgers.ledgersByLockSha256().get(lock).openOwnerVetoFindingIds(route));
            } catch (FindingsLedger.InvalidLedger exception) {
                ledgerFailure = true;
                vetoes = null;
                break;
            }
        }
        OperationalCauses.Result operational = null;
        String operationalFailure = null;
        try {
            List<FindingsLedger> ordered = new ArrayList<>();
            for (String lock : locks) ordered.add(ledgers.ledgersByLockSha256().get(lock));
            operational = OperationalCauses.derive(ordered, route, report);
        } catch (FindingsLedger.InvalidLedger | OperationalCauses.InvalidOperationalCause exception) {
            ledgerFailure = true;
            operationalFailure = exception.getMessage();
        }
        Set<String> established = operational == null ? null : operational.establishedCaseIds();
        Map<String, String> outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(expansion));
        FrbClassificationInput input;
        if (composed != null && !ledgerFailure) {
            input = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), cost.flags(), established,
                    false, owner.solRecommended(), sortedCopy(vetoes), outcomes, composed.heads(), composed);
        } else {
            input = new FrbClassificationInput(tokenFailed ? null : sortedCopy(safety),
                    tokenFailed ? null : sortedCopy(disputes), null, cost.flags(), established,
                    ledgerFailure, owner.solRecommended(), vetoes == null ? null : sortedCopy(vetoes),
                    outcomes, null, null);
        }
        Notes notes = new Notes(route, composed != null, compositionFailure, concerns, missing, operational,
                operationalFailure, cost.failure(), TRUSTED_OWNER_FACTS);
        return new RouteInput(input, notes);
    }

    private static Set<FindingsLedger.ReviewSubject> extras(String route, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb) throws IOException {
        FrozenGateParameters parameters = FrozenGateParameters.load(run.parameters());
        FrozenGateParameters.Route declaration = parameters.routes.get(route);
        if (declaration == null) throw new InvalidRouteInputs("Unknown route: " + route);
        Set<String> classA = new HashSet<>(parameters.workflows.keySet());
        classA.removeAll(parameters.frbClassBWorkflows);
        Set<FindingsLedger.ReviewSubject> result = new HashSet<>(ReviewComposition.routeScoped(
                KeyDefectSubjects.derive(frb.bundle(), ResultsPipeline.workflowByCaseId(run), classA,
                        parameters.frbClassBWorkflows), declaration));
        for (String workflow : declaration.workflows())
            result.add(new FindingsLedger.ReviewSubject(ReviewRecord.Kind.NO_VETO, workflow, route));
        return Set.copyOf(result);
    }

    private static void requireCompleteLockSet(ResultsPipeline.FrbInput frb, RouteLedgers ledgers)
            throws IOException {
        Set<String> expected = new TreeSet<>();
        for (ResultsPipeline.PrimaryExport export : frb.provenance().primaries())
            expected.add(IntegritySupport.detachedHash(export.lockFile()));
        for (ResultsPipeline.ReserveExport export : frb.provenance().reserves().values()) {
            expected.add(IntegritySupport.detachedHash(export.lockForPrimaryOpening()));
            expected.add(IntegritySupport.detachedHash(export.lockForReserveOpening()));
        }
        if (!ledgers.ledgersByLockSha256().keySet().equals(expected))
            throw new InvalidRouteInputs(LEDGER_SET_DIFFERS);
    }

    private static Set<String> pendingRoutes(ResultsPipeline.FrbRunInput run, ResultsPipeline.FrbInput frb,
            FrozenGateParameters parameters) throws IOException {
        Map<String, String> workflowByCaseId = ResultsPipeline.workflowByCaseId(run);
        Set<String> pending = new TreeSet<>();
        for (var fact : frb.bundle().evidence().byCaseArm().entrySet()) {
            if (fact.getValue().pendingQuestions() <= 0) continue;
            String workflow = workflowByCaseId.get(fact.getKey().split(CASE_ARM_SEPARATOR, 2)[0]);
            if (workflow != null) pending.add(parameters.workflows.get(workflow).route());
        }
        return pending;
    }

    /** Mirrors ProductionSwitchEligibility.checkWithOptions; an empty produced set means reviewed, no flags. */
    static CostFacts costFacts(List<ProductionSwitchEligibility.CostLedgerInput> costLedgers) {
        if (costLedgers == null || costLedgers.isEmpty()) return new CostFacts(null, null);
        try {
            Set<Path> seen = new HashSet<>();
            TreeSet<String> produced = new TreeSet<>();
            for (ProductionSwitchEligibility.CostLedgerInput item : costLedgers) {
                if (item == null || item.ledgerFile() == null)
                    throw new InvalidCostEvidence("Missing cost ledger input");
                CostEvidenceProducer.requireExternal(item.ledgerFile());
                if (!seen.add(item.ledgerFile().toRealPath()))
                    throw new InvalidCostEvidence("Duplicate cost ledger file");
                produced.addAll(CostEvidenceProducer.produce(item.ledgerFile(), item.approved()).flags());
            }
            return new CostFacts(Collections.unmodifiableSortedSet(produced), null);
        } catch (IOException | InvalidCostEvidence exception) {
            return new CostFacts(null, exception.getMessage());
        }
    }

    private static Set<String> sortedCopy(Set<String> values) {
        return Collections.unmodifiableSortedSet(new TreeSet<>(values));
    }
}
