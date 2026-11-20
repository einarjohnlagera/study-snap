package com.studysnap.backend.benchmark;

import java.util.Collection;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/** Section 9 deadline fallback classification, distinct from benchmark PASS. */
final class DeadlineRule {
    private DeadlineRule() {}
    enum Classification { LUNA_FALLBACK_ELIGIBLE, RETURN_TO_OWNER, BENCHMARK_PASS }

    /** A disposition is uncovered unless its id is a required class-A KEY_DEFECT subject; class-B dispositions keep the flag. */
    static SortedSet<String> uncoveredDispositionIds(Collection<String> causes,
            ReviewComposition.ComposedReviewCheck check) {
        Objects.requireNonNull(causes);
        String prefix = "DISPOSITIONED_CANDIDATE_CRITICAL:";
        SortedSet<String> uncovered = new TreeSet<>();
        for (String cause : causes) {
            if (!cause.startsWith(prefix)) continue;
            String id = cause.substring(prefix.length());
            if (check == null || check.requiredKeyDefectSubjects().stream()
                    .noneMatch(subject -> subject.subjectId().equals(id))) uncovered.add(id);
        }
        return uncovered;
    }

    static Classification classify(RouteVerdictCalculator.RouteReport report) {
        if (!report.undisposedCriticalCaseIds().isEmpty()
                || report.failedTiers().contains(RouteVerdictCalculator.Tier.T1)
                || report.failedTiers().contains(RouteVerdictCalculator.Tier.T2)
                || report.isolatedCriticalIncident()) return Classification.RETURN_TO_OWNER;
        if (report.verdict() == RouteVerdictCalculator.Verdict.INCONCLUSIVE
                || report.failedTiers().equals(java.util.Set.of(RouteVerdictCalculator.Tier.T3)))
            return Classification.LUNA_FALLBACK_ELIGIBLE;
        return Classification.BENCHMARK_PASS;
    }

    /** Option II, FRB only. Verdict and tier state are checked independently of recorded causes. */
    static FrbClassification classifyFrb(RouteVerdictCalculator.RouteReport report,
                                          FrbClassificationInput input) {
        java.util.SortedSet<String> causes = new java.util.TreeSet<>();
        java.util.SortedSet<String> flags = new java.util.TreeSet<>();
        if (input == null) {
            causes.add("LEDGER_NOT_SUPPLIED");
            causes.add("REVIEW_RECORD_MISSING");
            flags.add("COST_REVIEW_UNDETERMINED");
        }
        else {
            if (input.openSafetyFindingIds() == null || input.openDisputeIds() == null
                    || input.ownerVetoFindingIds() == null || input.ledgerHeadHashes() == null
                    || input.ledgerHeadHashes().isEmpty())
                causes.add("LEDGER_NOT_SUPPLIED");
            if (input.ledgerFailure()) causes.add("LEDGER_INTEGRITY_FAILURE");
            if (input.openSafetyFindingIds() != null && !input.openSafetyFindingIds().isEmpty())
                causes.add("OPEN_SAFETY_FINDING");
            if (input.openDisputeIds() != null && !input.openDisputeIds().isEmpty())
                causes.add("OPEN_EVIDENCE_DISPUTE");
            if (input.ownerVetoFindingIds() != null && !input.ownerVetoFindingIds().isEmpty())
                causes.add("OWNER_VETO");
            if (input.missingRequiredReviews() == null || !input.missingRequiredReviews().isEmpty())
                causes.add("REVIEW_RECORD_MISSING");
            if (input.costFlags() == null) flags.add("COST_REVIEW_UNDETERMINED");
            else flags.addAll(input.costFlags());
            if (input.solRecommended()) flags.add("SOL_RECOMMENDED");
            if (input.reviewCheck() != null && input.reviewCheck().workloadEstimate().exceedsLimit())
                causes.add("KEY_DEFECT_REVIEW_WORKLOAD_EXCEEDS_LIMIT");
        }
        if (report.failedTiers().contains(RouteVerdictCalculator.Tier.T1)) causes.add("T1");
        if (report.failedTiers().contains(RouteVerdictCalculator.Tier.T2)) causes.add("T2");
        boolean inconclusive = !report.inconclusiveTiers().isEmpty()
                || report.verdict() == RouteVerdictCalculator.Verdict.INCONCLUSIVE;
        java.util.Map<String, java.util.Set<String>> conditionalIds = new java.util.TreeMap<>();
        for (var entry : report.workflows().entrySet()) {
            String workflow = entry.getKey();
            var row = entry.getValue();
            if (row.causes() != null) causes.addAll(row.causes());
            if (row.frb() != null && row.frb().reserveCause() != null)
                causes.add(row.frb().reserveCause());
            if (!row.undisposedCriticalCaseIds().isEmpty()) causes.add("UNDISPOSED_CANDIDATE_CRITICAL");
            if (row.ownerVetoCaseIds() != null && !row.ownerVetoCaseIds().isEmpty()) causes.add("OWNER_VETO");
            if (row.dispositionsByValue() != null && row.dispositionsByValue().get(
                    RouteVerdictCalculator.DispositionValue.DEFECT_BLOCKS_ROUTE) != null
                    && !row.dispositionsByValue().get(
                    RouteVerdictCalculator.DispositionValue.DEFECT_BLOCKS_ROUTE).isEmpty()) causes.add("OWNER_VETO");
            if (row.unresolvedCaseIdsByCause() != null)
                row.unresolvedCaseIdsByCause().forEach((cause, ids) -> {
                    causes.add(cause);
                    if (CauseTable.kind(cause) == CauseTable.Kind.CONDITIONAL_FALLBACK)
                        conditionalIds.computeIfAbsent(cause, ignored -> new java.util.TreeSet<>()).addAll(ids);
                });
            inconclusive |= !row.inconclusiveTiers().isEmpty()
                    || row.verdict() == RouteVerdictCalculator.Verdict.INCONCLUSIVE;
            if (java.util.Arrays.stream(ClassBCaseManifest.Workflow.values())
                    .noneMatch(value -> value.name().equals(workflow))) {
                String outcome = input == null || input.expansionOutcomeByWorkflow() == null
                        ? null : input.expansionOutcomeByWorkflow().get(workflow);
                if (outcome == null || outcome.isBlank()) causes.add("EXPANSION_OUTCOME_NOT_SUPPLIED");
                else if (!"NOT_TRIGGERED".equals(outcome)
                        && !"COMPLETE_NO_SECOND_DOUBLE_MISS".equals(outcome)) causes.add(outcome);
            }
        }
        if (inconclusive && causes.isEmpty()) causes.add("UNATTRIBUTED_INCONCLUSIVE");
        boolean conditional = causes.stream().anyMatch(cause -> CauseTable.kind(cause)
                == CauseTable.Kind.CONDITIONAL_FALLBACK);
        if (conditional) {
            java.util.Set<String> established = input == null ? null : input.establishedOperationalCaseIds();
            boolean unattributed = false;
            for (String cause : causes) {
                if (CauseTable.kind(cause) != CauseTable.Kind.CONDITIONAL_FALLBACK) continue;
                java.util.Set<String> ids = conditionalIds.get(cause);
                if (ids == null || ids.isEmpty() || established == null || !established.containsAll(ids)) {
                    unattributed = true;
                    break;
                }
            }
            if (unattributed) causes.add("UNATTRIBUTED_INCONCLUSIVE");
        }
        boolean ownerCause = causes.stream().anyMatch(cause -> CauseTable.kind(cause)
                == CauseTable.Kind.RETURN_TO_OWNER);
        Classification classification;
        if (ownerCause) classification = Classification.RETURN_TO_OWNER;
        else if (report.failedTiers().equals(java.util.Set.of(RouteVerdictCalculator.Tier.T3))
                && !inconclusive && causes.isEmpty()) classification = Classification.LUNA_FALLBACK_ELIGIBLE;
        else if (conditional && report.failedTiers().isEmpty() && inconclusive)
            classification = Classification.LUNA_FALLBACK_ELIGIBLE;
        else if (report.verdict() == RouteVerdictCalculator.Verdict.PASS && !inconclusive && causes.isEmpty()) {
            classification = Classification.BENCHMARK_PASS;
            report.workflows().values().forEach(row -> {
                if (row.dispositionsByValue() != null)
                    row.dispositionsByValue().values().forEach(ids -> ids.forEach(
                            id -> causes.add("DISPOSITIONED_CANDIDATE_CRITICAL:" + id)));
            });
            if (input == null || input.reviewCheck() == null || report.workflows().isEmpty()
                    || !report.workflows().keySet().equals(input.reviewCheck().coveredWorkflows())
                    || !report.parameterSha256().equals(input.reviewCheck().parametersSha256())
                    || !input.ledgerHeadHashes().equals(input.reviewCheck().heads())
                    || !uncoveredDispositionIds(causes, input.reviewCheck()).isEmpty())
                flags.add("REVIEWS_NOT_MACHINE_CHECKED");
        } else {
            causes.add("UNATTRIBUTED_INCONCLUSIVE");
            classification = Classification.RETURN_TO_OWNER;
        }
        boolean blocked = FrbClassification.switchBlocked(classification, flags,
                input != null && input.ledgerFailure());
        return new FrbClassification(classification,
                java.util.Collections.unmodifiableSortedSet(causes),
                java.util.Collections.unmodifiableSortedSet(flags), blocked);
    }
}
