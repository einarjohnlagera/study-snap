package com.studysnap.backend.benchmark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Second, switch-time FRB check. A BENCHMARK_PASS is not permission to deploy, and an empty
 * caller costFlags set is not financial approval. The classifier's review-flag drop is advisory
 * until this fresh check succeeds. The external heads are only as external as the operator's note.
 *
 * Workload parameters (hMaxHours, otherPlannedHours and minutesPerCaseReview) and candidate and
 * baseline arm labels are replayed from the original composition, including a possibly lax caller
 * value. costFlags, ownerVetoFindingIds, establishedOperationalCaseIds,
 * expansionOutcomeByWorkflow, the report and pendingRoutes remain trusted assertions. The
 * rendered classification text is not eligibility. No pipeline wiring or operator caller exists.
 * The workload estimate is
 * run-wide, so an exceeded limit blocks every route; real-data flagless PASS remains unreachable
 * until the owner resolves T-A through T-D (route-scoped estimation for T-D is separate work).
 * A disposition retains the review flag unless its id has a required class-A KEY_DEFECT subject
 * in the composed check; class-B dispositions keep the flag.
 * The two permanent reasons prevent a switch until ledger-backed cost evidence and validated
 * producers or owner attestations for the other assertions are implemented.
 */
final class ProductionSwitchEligibility {
    static final String COST_EVIDENCE_NOT_LEDGER_BACKED = "COST_EVIDENCE_NOT_LEDGER_BACKED";
    static final String SWITCH_ASSERTIONS_NOT_VERIFIED = "SWITCH_ASSERTIONS_NOT_VERIFIED";
    static final String LEDGER_CHECK_FAILED = "LEDGER_CHECK_FAILED";
    static final String COMPOSITION_FAILED = "COMPOSITION_FAILED";
    static final String COMPOSITION_DIFFERS = "COMPOSITION_DIFFERS";
    static final String CLASSIFICATION_BLOCKS_SWITCH = "CLASSIFICATION_BLOCKS_SWITCH";
    static final String PENDING_REVIEW_ITEMS = "PENDING_REVIEW_ITEMS";
    static final String REVIEW_CHECK_ROUTE_MISMATCH = "REVIEW_CHECK_ROUTE_MISMATCH";
    static final String EVALUATION_FAILED = "EVALUATION_FAILED";

    record Decision(boolean eligible, List<String> reasons) {}

    private ProductionSwitchEligibility() {}

    static Decision check(String route, RouteVerdictCalculator.RouteReport report,
            FrbClassificationInput input, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb, Map<String, FindingsLedger> ledgersByLockSha256,
            Map<String, FindingsLedger.ExternalHeads> freshNotedHeadsByLockSha256,
            Set<String> pendingRoutes) {
        TreeSet<String> reasons = new TreeSet<>();
        reasons.add(COST_EVIDENCE_NOT_LEDGER_BACKED);
        reasons.add(SWITCH_ASSERTIONS_NOT_VERIFIED);
        try {
            if (route == null || report == null || input == null || run == null || frb == null
                    || ledgersByLockSha256 == null || freshNotedHeadsByLockSha256 == null
                    || input.reviewCheck() == null) {
                reasons.add(EVALUATION_FAILED);
                return decision(reasons);
            }
            ReviewComposition.ComposedReviewCheck original = input.reviewCheck();
            if (!route.equals(original.route())) {
                reasons.add(REVIEW_CHECK_ROUTE_MISMATCH);
                return decision(reasons);
            }

            Set<String> allPending = new TreeSet<>();
            if (pendingRoutes != null) allPending.addAll(pendingRoutes);
            Map<String, String> workflowByCaseId = ResultsPipeline.workflowByCaseId(run);
            FrozenGateParameters parameters = FrozenGateParameters.load(run.parameters());
            for (var fact : frb.bundle().evidence().byCaseArm().entrySet()) {
                if (fact.getValue().pendingQuestions() <= 0) continue;
                String caseId = fact.getKey().split("\u0000", 2)[0];
                String workflow = workflowByCaseId.get(caseId);
                allPending.add(parameters.workflows.get(workflow).route());
            }
            if (allPending.contains(route)) reasons.add(PENDING_REVIEW_ITEMS);
            FrbClassification classification = ResultsPipeline.classifyFrbRoutes(
                    Map.of(route, report), Map.of(route, input), allPending).get(route);
            if (classification.classification() != DeadlineRule.Classification.BENCHMARK_PASS
                    || classification.blocksProductionSwitch()
                    || classification.flags().contains("REVIEWS_NOT_MACHINE_CHECKED")
                    || !DeadlineRule.uncoveredDispositionIds(
                            classification.causes(), input.reviewCheck()).isEmpty())
                reasons.add(CLASSIFICATION_BLOCKS_SWITCH);

            List<FindingsLedger.ReviewCheck> freshTokens = new ArrayList<>();
            for (String lock : original.lockSha256s()) {
                FindingsLedger ledger = ledgersByLockSha256.get(lock);
                FindingsLedger.ExternalHeads heads = freshNotedHeadsByLockSha256.get(lock);
                if (ledger == null || heads == null) {
                    reasons.add(EVALUATION_FAILED);
                    return decision(reasons);
                }
                try {
                    freshTokens.add(ledger.checkLedger(route, original.extrasByLock().get(lock), heads));
                } catch (FindingsLedger.InvalidLedger exception) {
                    reasons.add(LEDGER_CHECK_FAILED);
                    return decision(reasons);
                }
            }
            try {
                ReviewComposition.ComposedReviewCheck fresh = ReviewComposition.compose(route, run, frb,
                        report, freshTokens, original.workloadParameters(), original.candidateArms(),
                        original.baselineArms());
                if (!fresh.equals(original)) reasons.add(COMPOSITION_DIFFERS);
            } catch (ReviewComposition.InvalidComposition exception) {
                reasons.add(COMPOSITION_FAILED);
            }
        } catch (IOException | RuntimeException exception) {
            reasons.add(EVALUATION_FAILED);
        }
        return decision(reasons);
    }

    private static Decision decision(TreeSet<String> reasons) {
        return new Decision(reasons.isEmpty(), List.copyOf(reasons));
    }
}
