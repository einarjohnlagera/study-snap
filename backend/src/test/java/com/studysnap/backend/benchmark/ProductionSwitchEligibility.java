package com.studysnap.backend.benchmark;

import java.io.IOException;
import java.nio.file.Path;
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
 * value. costFlags is checked against replayed external ledgers only when costLedgers is supplied;
 * ownerVetoFindingIds is ledger-derived when owner attestations are required;
 * The trusted assertions are re-derivable by ProductionSwitchEligibility under
 * requireVerifiedAssertions: missing reviews, operational cases and expansion outcomes are checked
 * against the same evidence used for classification. Caller additions to pendingRoutes
 * can only add a block. A successful re-derivation does not prove that the evidence is the right
 * evidence; the freeze, lock anchors and owner's notes establish that. The rendered classification
 * text is not eligibility.
 * The workload estimate is
 * run-wide, so an exceeded limit blocks every route; real-data flagless PASS remains unreachable
 * until the owner resolves T-A through T-D (route-scoped estimation for T-D is separate work).
 * A disposition retains the review flag unless its id has a required class-A KEY_DEFECT subject
 * in the composed check; class-B dispositions keep the flag.
 * The cost reason remains until matching caller-supplied ledger-backed evidence is supplied. Those
 * cost files and ApprovedCaps are not bound to a run, freeze digest or lock. Workload parameters,
 * arm labels, solRecommended, externally noted heads, review-store completeness and binding, and
 * ABSOLUTE_QUALITY subjects remain owner-supplied trusted facts; composition does not require
 * ABSOLUTE_QUALITY subjects that were never listed. COST_APPROVAL is not yet recorded, so a
 * TRIGGERED or UNDETERMINED cost flag remains blocking. An eligible decision is advice for the
 * owner's attended decision: it deploys nothing, saves no environment variable and switches no
 * model. Only the owner's attended Render save switches a model; each switch needs a fresh run
 * with freshly noted ledger heads.
 */
final class ProductionSwitchEligibility {
    static final String COST_EVIDENCE_NOT_LEDGER_BACKED = "COST_EVIDENCE_NOT_LEDGER_BACKED";
    static final String COST_EVIDENCE_FAILED = "COST_EVIDENCE_FAILED";
    static final String COST_FLAGS_DIFFER_FROM_LEDGER = "COST_FLAGS_DIFFER_FROM_LEDGER";
    static final String SWITCH_ASSERTIONS_NOT_VERIFIED = "SWITCH_ASSERTIONS_NOT_VERIFIED";
    static final String LEDGER_CHECK_FAILED = "LEDGER_CHECK_FAILED";
    static final String COMPOSITION_FAILED = "COMPOSITION_FAILED";
    static final String COMPOSITION_DIFFERS = "COMPOSITION_DIFFERS";
    static final String CLASSIFICATION_BLOCKS_SWITCH = "CLASSIFICATION_BLOCKS_SWITCH";
    static final String PENDING_REVIEW_ITEMS = "PENDING_REVIEW_ITEMS";
    static final String REVIEW_CHECK_ROUTE_MISMATCH = "REVIEW_CHECK_ROUTE_MISMATCH";
    static final String EVALUATION_FAILED = "EVALUATION_FAILED";
    static final String REPORT_RECOMPUTATION_FAILED = "REPORT_RECOMPUTATION_FAILED";
    static final String REPORT_DIFFERS_FROM_RECOMPUTATION = "REPORT_DIFFERS_FROM_RECOMPUTATION";
    static final String OWNER_VETO_DIFFERS_FROM_LEDGER = "OWNER_VETO_DIFFERS_FROM_LEDGER";
    static final String COST_REVIEW_REQUIRED = "COST_REVIEW_REQUIRED";
    static final String COST_REVIEW_RECOMPUTATION_FAILED = "COST_REVIEW_RECOMPUTATION_FAILED";
    static final String ASSERTION_OPTIONS_INCOMPLETE = "ASSERTION_OPTIONS_INCOMPLETE";
    static final String ASSERTION_SOURCES_MISSING = "ASSERTION_SOURCES_MISSING";
    static final String MISSING_REVIEWS_ASSERTION_DIFFERS = "MISSING_REVIEWS_ASSERTION_DIFFERS";
    static final String OPERATIONAL_CASES_DIFFER_FROM_LEDGER = "OPERATIONAL_CASES_DIFFER_FROM_LEDGER";
    static final String OPERATIONAL_CASES_RECOMPUTATION_FAILED = "OPERATIONAL_CASES_RECOMPUTATION_FAILED";
    static final String EXPANSION_OUTCOMES_DIFFER_FROM_REASSEMBLY = "EXPANSION_OUTCOMES_DIFFER_FROM_REASSEMBLY";
    static final String EXPANSION_REASSEMBLY_FAILED = "EXPANSION_REASSEMBLY_FAILED";
    static final String BUNDLE_DIFFERS_FROM_REASSEMBLY = "BUNDLE_DIFFERS_FROM_REASSEMBLY";

    record Decision(boolean eligible, List<String> reasons) {}
    record CostLedgerInput(Path ledgerFile, ApprovedCaps approved) {}
    record SwitchOptions(List<CostLedgerInput> costLedgers, boolean recomputeReport,
            boolean requireOwnerAttestations, boolean requireOperationalCostReview,
            FrbBundleAssembler.Sources reassemblySources, boolean requireVerifiedAssertions) {
        SwitchOptions(List<CostLedgerInput> costLedgers, boolean recomputeReport,
                boolean requireOwnerAttestations, boolean requireOperationalCostReview) {
            this(costLedgers, recomputeReport, requireOwnerAttestations,
                    requireOperationalCostReview, null, false);
        }
        SwitchOptions(List<CostLedgerInput> costLedgers, boolean recomputeReport,
                boolean requireOwnerAttestations) {
            this(costLedgers, recomputeReport, requireOwnerAttestations, false, null, false);
        }
    }

    private ProductionSwitchEligibility() {}

    static Decision check(String route, RouteVerdictCalculator.RouteReport report,
            FrbClassificationInput input, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb, Map<String, FindingsLedger> ledgersByLockSha256,
            Map<String, FindingsLedger.ExternalHeads> freshNotedHeadsByLockSha256,
            Set<String> pendingRoutes) {
        return check(route, report, input, run, frb, ledgersByLockSha256,
                freshNotedHeadsByLockSha256, pendingRoutes, null, false);
    }

    static Decision check(String route, RouteVerdictCalculator.RouteReport report,
            FrbClassificationInput input, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb, Map<String, FindingsLedger> ledgersByLockSha256,
            Map<String, FindingsLedger.ExternalHeads> freshNotedHeadsByLockSha256,
            Set<String> pendingRoutes, List<CostLedgerInput> costLedgers) {
        return checkWithOptions(route, report, input, run, frb, ledgersByLockSha256,
                freshNotedHeadsByLockSha256, pendingRoutes, new SwitchOptions(costLedgers, false, false));
    }

    static Decision check(String route, RouteVerdictCalculator.RouteReport report,
            FrbClassificationInput input, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb, Map<String, FindingsLedger> ledgersByLockSha256,
            Map<String, FindingsLedger.ExternalHeads> freshNotedHeadsByLockSha256,
            Set<String> pendingRoutes, List<CostLedgerInput> costLedgers, boolean recomputeReport) {
        return checkWithOptions(route, report, input, run, frb, ledgersByLockSha256,
                freshNotedHeadsByLockSha256, pendingRoutes,
                new SwitchOptions(costLedgers, recomputeReport, false));
    }

    static Decision checkWithOptions(String route, RouteVerdictCalculator.RouteReport report,
            FrbClassificationInput input, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb, Map<String, FindingsLedger> ledgersByLockSha256,
            Map<String, FindingsLedger.ExternalHeads> freshNotedHeadsByLockSha256,
            Set<String> pendingRoutes, SwitchOptions options) {
        TreeSet<String> reasons = new TreeSet<>();
        reasons.add(COST_EVIDENCE_NOT_LEDGER_BACKED);
        reasons.add(SWITCH_ASSERTIONS_NOT_VERIFIED);
        if (options == null) {
            reasons.add(EVALUATION_FAILED);
            return decision(reasons);
        }
        if (options.requireOperationalCostReview()) {
            try {
                var review = ResultsPipeline.operationalCostReview(run);
                var parametersForCost = FrozenGateParameters.load(run.parameters());
                if (!OperationalCostReview.flagsByRoute(review, parametersForCost)
                        .getOrDefault(route, new TreeSet<>()).isEmpty())
                    reasons.add(COST_REVIEW_REQUIRED);
            } catch (IOException | RuntimeException exception) {
                reasons.add(COST_REVIEW_RECOMPUTATION_FAILED);
            }
        }
        List<CostLedgerInput> costLedgers = options.costLedgers();
        if (costLedgers != null && !costLedgers.isEmpty()) {
            try {
                Set<Path> seen = new java.util.HashSet<>();
                TreeSet<String> produced = new TreeSet<>();
                for (CostLedgerInput item : costLedgers) {
                    if (item == null || item.ledgerFile() == null)
                        throw new InvalidCostEvidence("Missing cost ledger input");
                    CostEvidenceProducer.requireExternal(item.ledgerFile());
                    if (!seen.add(item.ledgerFile().toRealPath()))
                        throw new InvalidCostEvidence("Duplicate cost ledger file");
                    produced.addAll(CostEvidenceProducer.produce(item.ledgerFile(), item.approved()).flags());
                }
                if (input == null || input.costFlags() == null || !input.costFlags().equals(produced))
                    reasons.add(COST_FLAGS_DIFFER_FROM_LEDGER);
                else reasons.remove(COST_EVIDENCE_NOT_LEDGER_BACKED);
            } catch (IOException | InvalidCostEvidence exception) {
                reasons.add(COST_EVIDENCE_FAILED);
            }
        }
        try {
            if (route == null || report == null || input == null || run == null || frb == null
                    || ledgersByLockSha256 == null || freshNotedHeadsByLockSha256 == null
                    || input.reviewCheck() == null) {
                reasons.add(EVALUATION_FAILED);
                return decision(reasons);
            }
            if (options.recomputeReport()) {
                try {
                    if (!report.equals(ReportRecomputation.recompute(run, frb).get(route)))
                        reasons.add(REPORT_DIFFERS_FROM_RECOMPUTATION);
                } catch (InvalidReportRecomputation exception) {
                    reasons.add(REPORT_RECOMPUTATION_FAILED);
                }
            }
            ReviewComposition.ComposedReviewCheck original = input.reviewCheck();
            if (!route.equals(original.route())) {
                reasons.add(REVIEW_CHECK_ROUTE_MISMATCH);
                return decision(reasons);
            }

            FrbClassificationInput classificationInput = input;
            if (options.requireOwnerAttestations()) {
                TreeSet<String> derived = new TreeSet<>();
                boolean derivedSuccessfully = true;
                for (String lock : original.lockSha256s()) {
                    FindingsLedger ledger = ledgersByLockSha256.get(lock);
                    if (ledger == null) {
                        reasons.add(LEDGER_CHECK_FAILED);
                        derivedSuccessfully = false;
                        break;
                    }
                    try { derived.addAll(ledger.openOwnerVetoFindingIds(route)); }
                    catch (FindingsLedger.InvalidLedger exception) {
                        reasons.add(LEDGER_CHECK_FAILED);
                        derivedSuccessfully = false;
                        break;
                    }
                }
                if (derivedSuccessfully) {
                    if (!derived.equals(input.ownerVetoFindingIds()))
                        reasons.add(OWNER_VETO_DIFFERS_FROM_LEDGER);
                    classificationInput = new FrbClassificationInput(input.openSafetyFindingIds(),
                            input.openDisputeIds(), input.missingRequiredReviews(), input.costFlags(),
                            input.establishedOperationalCaseIds(), input.ledgerFailure(),
                            input.solRecommended(), Set.copyOf(derived), input.expansionOutcomeByWorkflow(),
                            input.ledgerHeadHashes(), input.reviewCheck());
                }
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
                    Map.of(route, report), Map.of(route, classificationInput), allPending).get(route);
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
            boolean freshMatched = false;
            try {
                ReviewComposition.ComposedReviewCheck fresh = ReviewComposition.compose(route, run, frb,
                        report, freshTokens, original.workloadParameters(), original.candidateArms(),
                        original.baselineArms(), options.requireOwnerAttestations());
                if (!fresh.equals(original)) reasons.add(COMPOSITION_DIFFERS);
                else freshMatched = true;
            } catch (ReviewComposition.InvalidComposition exception) {
                reasons.add(COMPOSITION_FAILED);
            } catch (RuntimeException exception) {
                if (!options.requireVerifiedAssertions()) throw exception;
                reasons.add(COMPOSITION_FAILED);
            }
            if (options.requireVerifiedAssertions()) {
                if (!options.requireOwnerAttestations() || !options.recomputeReport()
                        || !options.requireOperationalCostReview()) {
                    reasons.add(ASSERTION_OPTIONS_INCOMPLETE);
                    return decision(reasons);
                }
                if (options.reassemblySources() == null) {
                    reasons.add(ASSERTION_SOURCES_MISSING);
                    return decision(reasons);
                }
                boolean verified = true;
                if (!freshMatched || reasons.contains(EVALUATION_FAILED)
                        || reasons.contains(LEDGER_CHECK_FAILED)
                        || input.missingRequiredReviews() == null || !input.missingRequiredReviews().isEmpty()) {
                    reasons.add(MISSING_REVIEWS_ASSERTION_DIFFERS);
                    verified = false;
                }
                try {
                    List<FindingsLedger> routeLedgers = new ArrayList<>();
                    for (String lock : original.lockSha256s()) routeLedgers.add(ledgersByLockSha256.get(lock));
                    Set<String> derived = OperationalCauses.derive(routeLedgers, route, report).establishedCaseIds();
                    if (input.establishedOperationalCaseIds() == null
                            || !derived.equals(input.establishedOperationalCaseIds())) {
                        reasons.add(OPERATIONAL_CASES_DIFFER_FROM_LEDGER);
                        verified = false;
                    }
                } catch (RuntimeException exception) {
                    reasons.add(OPERATIONAL_CASES_RECOMPUTATION_FAILED);
                    verified = false;
                }
                try {
                    FrbBundleAssembler.Assembled reassembled = FrbBundleAssembler.assemble(options.reassemblySources());
                    if (!reassembled.bundle().equals(frb.bundle())) {
                        reasons.add(BUNDLE_DIFFERS_FROM_REASSEMBLY);
                        verified = false;
                    }
                    try {
                        FrbBundleAssembler.requireBound(reassembled, run, frb);
                    } catch (RuntimeException exception) {
                        reasons.add(BUNDLE_DIFFERS_FROM_REASSEMBLY);
                        verified = false;
                    }
                    if (input.expansionOutcomeByWorkflow() == null
                            || !reassembled.expansionOutcomeByWorkflow().equals(input.expansionOutcomeByWorkflow())) {
                        reasons.add(EXPANSION_OUTCOMES_DIFFER_FROM_REASSEMBLY);
                        verified = false;
                    }
                } catch (RuntimeException exception) {
                    reasons.add(EXPANSION_REASSEMBLY_FAILED);
                    verified = false;
                }
                if (verified) reasons.remove(SWITCH_ASSERTIONS_NOT_VERIFIED);
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
