package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produces expansion assertions from an already opened, verified class-A export. The expansion
 * outcome is derivable by this producer; it is not bound to the switch here. A switch caller can
 * still pass an arbitrary map until Brief 5r binds this result to that caller.
 *
 * <p>Planning uses preregistration 7.3's 1.25 minutes per question times the pair's question
 * count. The A3 measured median is not bound by this brief. Every triggered workflow whose
 * result depends on a cap or owner flag needs a lock-listed decision, including completion.
 * The decision's approved-larger-cap flag and cap are reported assertions. Planning is against
 * that stated cap, and {@link Expansion#outcome} always receives false for its cap-bypass flag.
 */
final class ExpansionOutcomes {
    // Preregistration 7.3: 1.25 minutes per question times the pair's question count.
    static final double PLANNING_MINUTES_PER_QUESTION = 1.25;
    private static final String PLANNING_NOTE = "planning figure used; measured median not bound";
    private static final String CENSUS_DISCLOSURE = "frame exhausted; every workflow pair human-reviewed as a census; no additional expansion sampling was possible; not proof of scorer reliability";
    private static final String SECOND_MISS_CONTINUED = "Review continued past a second double miss; owner cause required";
    private static final String DECISION_REQUIRED = "Decision record required for cap or owner flags";
    private static final String A = "A";
    private static final String B = "B";

    record Finding(String workflow, String outcome, boolean supplied, String reason,
            String expansionReason, boolean unresolvedFindingPresent, Set<String> triggerPairs,
            List<String> drawnPairs, List<String> reviewedPairs, List<String> declaredPairs,
            int requiredPairs, double plannedMinutes, int capMinutes, boolean approvedLargerCapAsserted,
            boolean frameExhausted, boolean censusClaimed, String decisionSha256OrNull,
            String planningNote) {
        Finding {
            if (supplied != (outcome != null) || outcome != null && outcome.isBlank())
                throw new InvalidExpansionOutcome("Supplied outcome must be nonblank and absent otherwise");
            triggerPairs = Collections.unmodifiableSet(new LinkedHashSet<>(triggerPairs));
            drawnPairs = List.copyOf(drawnPairs);
            reviewedPairs = List.copyOf(reviewedPairs);
            declaredPairs = List.copyOf(declaredPairs);
        }
    }

    record Result(Map<String, String> outcomeByWorkflow, Map<String, Finding> findings) {
        Result {
            if (outcomeByWorkflow.values().stream().anyMatch(value -> value == null || value.isBlank())
                    || findings.values().stream().anyMatch(value -> value == null))
                throw new InvalidExpansionOutcome("Invalid outcome result");
            outcomeByWorkflow = Collections.unmodifiableMap(new LinkedHashMap<>(outcomeByWorkflow));
            findings = Collections.unmodifiableMap(new LinkedHashMap<>(findings));
        }
    }

    private ExpansionOutcomes() {}

    /**
     * Operator entry point. The caller must open the key before this call: ClassASources carries
     * an ArmKeyAccess.OpenedKey and the assembler reads its rows. This class opens no key.
     */
    static Result derive(ClassASources sources) {
        return derive(ClassAEvidenceAssembler.assemble(sources));
    }

    /** Unit-test seam only. A package caller can construct ClassAAssembly and bypass verification. */
    static Result derive(ClassAAssembly assembly) {
        if (assembly == null || assembly.expansionObligations() == null || assembly.declared() == null)
            throw new InvalidExpansionOutcome("Verified class-A assembly required");
        Map<String, PairEvidence> evidence;
        try {
            evidence = PairEvidence.build(assembly.pairs(), assembly.statuses());
        } catch (InvalidEvidence exception) {
            throw new InvalidExpansionOutcome("Verified pair evidence required", exception);
        }
        Map<String, String> supplied = new LinkedHashMap<>();
        Map<String, Finding> findings = new LinkedHashMap<>();
        for (var entry : assembly.expansionObligations().entrySet()) {
            String workflow = entry.getKey();
            ExpansionObligation.Disclosure obligation = entry.getValue();
            ExpansionDecision.Choice choice = assembly.declared().byWorkflow().get(workflow);
            Finding finding = finding(assembly, evidence, workflow, obligation, choice);
            findings.put(workflow, finding);
            if (finding.supplied()) supplied.put(workflow, finding.outcome());
        }
        if (!assembly.expansionObligations().keySet().containsAll(assembly.declared().byWorkflow().keySet()))
            throw new InvalidExpansionOutcome("Decision names a workflow outside the export");
        return new Result(supplied, findings);
    }

    private static Finding finding(ClassAAssembly assembly, Map<String, PairEvidence> evidence,
            String workflow, ExpansionObligation.Disclosure obligation, ExpansionDecision.Choice choice) {
        Set<String> triggers = obligation.triggerPairs();
        List<String> drawn = obligation.drawnPairs();
        int cap = choice == null ? ExpansionDecision.PREREGISTERED_EXPANSION_CAP_MINUTES : choice.capMinutes();
        boolean approved = choice != null && choice.approvedLargerCap();
        List<String> declared = choice == null ? List.of() : drawn.stream()
                .filter(id -> choice.unreviewedPairsByStem().values().stream().anyMatch(ids -> ids.contains(id)))
                .toList();
        String sha = choice == null ? null : assembly.declared().sha256();
        double minutes = 0;
        Expansion.ExpansionResult result = null;
        List<Expansion.PairReview> reviews = List.of();
        String unsuppliedReason = null;
        boolean census = false;
        if (triggers.isEmpty()) {
            if (!drawn.isEmpty() || choice != null)
                throw new InvalidExpansionOutcome("No-trigger workflow has expansion or decision");
            result = Expansion.outcome(null, triggers, List.of(), false, false);
            if (result.outcome() != Expansion.ExpansionOutcome.NOT_TRIGGERED)
                throw new InvalidExpansionOutcome("No-trigger result differs from preregistered outcome");
        } else if (triggers.size() >= 2) {
            result = Expansion.outcome(null, triggers, List.of(), false, false);
        } else if (obligation.requiredPairs() == 0) {
            census = true;
            verifyCensus(assembly, workflow, obligation, choice);
            if (!assembly.facts().excludedCaseIds().isEmpty()) {
                unsuppliedReason = "Excluded frozen cases prevent workflow census: "
                        + assembly.facts().excludedCaseIds();
            } else {
                Expansion.ExpansionPlan plan = plan(assembly.pairs(), drawn, cap);
                result = Expansion.outcome(plan, triggers, List.of(), false, false);
                if (result.outcome() != Expansion.ExpansionOutcome.COMPLETE_NO_SECOND_DOUBLE_MISS)
                    throw new InvalidExpansionOutcome("Census result differs from preregistered outcome");
            }
        } else {
            Expansion.ExpansionPlan plan = plan(assembly.pairs(), drawn, cap);
            minutes = plan.totalMinutes();
            reviews = reviews(assembly, evidence, drawn, choice);
            try {
                result = Expansion.outcome(plan, triggers, reviews,
                        choice != null && choice.declinedOrExhausted(), false);
            } catch (InvalidExpansion exception) {
                if (hasReviewAfterSecondMiss(reviews)) unsuppliedReason = SECOND_MISS_CONTINUED;
                else if (choice == null && "Expansion outcome cannot be decided from inputs"
                        .equals(exception.getMessage()))
                    unsuppliedReason = DECISION_REQUIRED;
                else throw new InvalidExpansionOutcome("Verified decision cannot produce an outcome", exception);
            }
            if (choice == null && result != null
                    && result.outcome() != Expansion.ExpansionOutcome.INCONCLUSIVE_SCORER_UNRELIABLE) {
                unsuppliedReason = DECISION_REQUIRED;
            }
        }
        boolean isSupplied = result != null && unsuppliedReason == null;
        return new Finding(workflow, isSupplied ? result.outcome().name() : null, isSupplied,
                unsuppliedReason == null ? (census ? CENSUS_DISCLOSURE : result.reason()) : unsuppliedReason,
                result == null ? null : result.reason(), result != null && result.unresolvedFindingPresent(),
                triggers, drawn, reviews.stream().map(Expansion.PairReview::pairId).toList(), declared,
                obligation.requiredPairs(), minutes, cap, approved, census || obligation.frameExhausted(),
                census && isSupplied,
                sha, PLANNING_NOTE);
    }

    private static Expansion.ExpansionPlan plan(PairMap pairs, List<String> drawn, int cap) {
        return Expansion.plan(pairs, drawn, ExpansionObligation.K_REQ, cap,
                PLANNING_MINUTES_PER_QUESTION, null);
    }

    private static List<Expansion.PairReview> reviews(ClassAAssembly assembly,
            Map<String, PairEvidence> evidence, List<String> drawn, ExpansionDecision.Choice choice) {
        List<Expansion.PairReview> result = new ArrayList<>();
        for (String id : drawn) {
            PairEvidence pair = evidence.get(id);
            if (pair == null) throw new InvalidExpansionOutcome("Drawn pair missing from export: " + id);
            Expansion.PairReview review = Expansion.PairReview.from(pair);
            if (choice == null || !choice.declinedOrExhausted() && choice.stopReason() == null
                    || reviewedAny(assembly, id)) result.add(review);
            if (choice != null && choice.stopReason() != null && review.hasDoubleMiss()) break;
        }
        return List.copyOf(result);
    }

    private static boolean reviewedAny(ClassAAssembly assembly, String pair) {
        for (String placement : List.of(A, B)) for (String item : assembly.pairs().itemsOf(pair, placement))
            if (assembly.statuses().get(item).state() == QuestionStatus.State.FINAL) return true;
        return false;
    }

    private static boolean hasReviewAfterSecondMiss(List<Expansion.PairReview> reviews) {
        for (int i = 0; i < reviews.size() - 1; i++) if (reviews.get(i).hasDoubleMiss()) return true;
        return false;
    }

    private static void verifyCensus(ClassAAssembly assembly, String workflow,
            ExpansionObligation.Disclosure obligation, ExpansionDecision.Choice choice) {
        Set<String> stems = obligation.allocationByStem().keySet();
        Set<String> spots = new LinkedHashSet<>();
        for (DrawLog.Entry entry : assembly.drawEntries()) {
            if (entry.purpose().equals(SeedStreamRegistry.SPOT_CHECK)) spots.addAll(entry.selectedPairIds());
            if (entry.purpose().equals(SeedStreamRegistry.EXPANSION) && stems.contains(entry.workflow()))
                throw new InvalidExpansionOutcome("Census has an expansion entry: " + workflow);
        }
        if (!obligation.drawnPairs().isEmpty() || choice != null)
            throw new InvalidExpansionOutcome("Census has a draw or decision: " + workflow);
        for (String stem : stems) {
            Set<String> frame = assembly.sets().frameByStem().get(stem);
            if (frame == null || !spots.containsAll(frame))
                throw new InvalidExpansionOutcome("Census does not cover workflow frame: " + stem);
        }
        for (PairMap.Pair pair : assembly.pairs().pairs()) {
            if (!stems.contains(pair.workflow())) continue;
            if (!assembly.reviewRequiredPairs().contains(pair.id()))
                throw new InvalidExpansionOutcome("Census does not cover workflow pair: " + pair.id());
            for (String placement : List.of(A, B)) for (String item : assembly.pairs().itemsOf(pair.id(), placement))
                if (assembly.statuses().get(item).state() != QuestionStatus.State.FINAL
                        || assembly.declared().itemIds().contains(item))
                    throw new InvalidExpansionOutcome("Census contains pending or declared item: " + item);
        }
    }
}

final class InvalidExpansionOutcome extends RuntimeException {
    InvalidExpansionOutcome(String message) { super(message); }
    InvalidExpansionOutcome(String message, Throwable cause) { super(message, cause); }
}
