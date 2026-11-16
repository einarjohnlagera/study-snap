package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One blind expansion per workflow, with one trigger and one cap across all its stems.
 * At planning rates Board Exam generation needs 225 minutes and Long Exam needs 248.75,
 * so each is workload exhausted under the 120-minute cap after a first double miss unless
 * the owner separately approves more time. Exhaustion says nothing about which output is
 * worse or whether the scorers are unreliable. A completed check is not proof of reliability.
 * The overall owner-review planning ceiling remains 20 hours; this class never changes it.
 * "Confirmed and locked" before expansion means final human findings on a pair with no
 * pending question; the workflow LockRecord is written only after expansion settles.
 */
final class Expansion {
    record WorkflowScope(String workflow, Map<String, Integer> expansionPairsByStem) {
        WorkflowScope {
            if (workflow == null || workflow.isBlank() || expansionPairsByStem == null
                    || expansionPairsByStem.isEmpty() || expansionPairsByStem.values().stream().anyMatch(n -> n == null || n < 1))
                throw new InvalidExpansion("Valid workflow and positive per-stem counts required");
            expansionPairsByStem = Collections.unmodifiableMap(new LinkedHashMap<>(expansionPairsByStem));
        }
    }
    record ExpansionPlan(int requiredPairs, double minutesPerPair, double totalMinutes,
                         boolean fitsCap, boolean frameInsufficient, int plannedPairs) {}
    record Feasibility(double totalMinutes, boolean fitsCap, double minutesOverCap) {}
    record PairReview(String pairId, boolean complete, boolean hasDoubleMiss,
                      int doubleMissQuestions, boolean hasUnresolved) {
        static PairReview from(PairEvidence evidence) {
            if (evidence == null) throw new InvalidExpansion("Pair evidence required");
            int misses = evidence.placementA().doubleMisses() + evidence.placementB().doubleMisses();
            boolean pending = evidence.placementA().pendingQuestions() + evidence.placementB().pendingQuestions() > 0;
            boolean unresolved = evidence.placementA().unresolvedQuestions()
                    + evidence.placementB().unresolvedQuestions() > 0;
            return new PairReview(evidence.pairId(), !pending, misses > 0, misses, unresolved);
        }
    }
    enum ExpansionOutcome { NOT_TRIGGERED, COMPLETE_NO_SECOND_DOUBLE_MISS,
        INCONCLUSIVE_SCORER_UNRELIABLE, INCONCLUSIVE_WORKLOAD_EXHAUSTED }
    record ExpansionResult(ExpansionOutcome outcome, boolean unresolvedFindingPresent, String reason) {}
    private Expansion() {}

    static List<String> draw(SeedStreamRegistry registry, DrawLog log, PairMap pairs, ReviewSets sets,
                             Collection<String> alreadyReviewedPairIds, WorkflowScope scope,
                             int round, String clockUtc) {
        if (registry == null || log == null || pairs == null || sets == null
                || alreadyReviewedPairIds == null || scope == null)
            throw new InvalidExpansion("Expansion draw inputs required");
        Set<String> excluded = new HashSet<>(alreadyReviewedPairIds);
        for (DrawLog.Entry entry : log.entries()) if (entry.purpose().equals(SeedStreamRegistry.SPOT_CHECK))
            excluded.addAll(entry.selectedPairIds());
        List<String> selected = new ArrayList<>();
        for (var stem : scope.expansionPairsByStem().entrySet()) {
            Set<String> available = sets.frameByStem().get(stem.getKey());
            if (available == null) throw new InvalidExpansion("Unknown expansion stem: " + stem.getKey());
            List<String> remaining = available.stream().filter(id -> !excluded.contains(id)).toList();
            DrawLog.Entry receipt = PairSampler.draw(registry, log, pairs, stem.getKey(),
                    SeedStreamRegistry.EXPANSION, round, remaining, stem.getValue(), clockUtc);
            selected.addAll(receipt.selectedPairIds());
        }
        return List.copyOf(selected);
    }

    static ExpansionPlan plan(PairMap pairs, List<String> selectedPairIdsInOrder, int kReq,
                              int capMinutes, double minutesPerQuestion, Double measuredMedianMinutesPerPair) {
        if (pairs == null || selectedPairIdsInOrder == null || kReq < 1 || capMinutes < 0
                || !Double.isFinite(minutesPerQuestion) || minutesPerQuestion <= 0
                || measuredMedianMinutesPerPair != null && (!Double.isFinite(measuredMedianMinutesPerPair)
                    || measuredMedianMinutesPerPair <= 0))
            throw new InvalidExpansion("Invalid expansion planning inputs");
        if (new HashSet<>(selectedPairIdsInOrder).size() != selectedPairIdsInOrder.size())
            throw new InvalidExpansion("Repeated selected pair");
        int required = Math.min(kReq, selectedPairIdsInOrder.size());
        double total = 0;
        for (String id : selectedPairIdsInOrder) {
            if (pairs.workflowOf(id) == null) throw new InvalidExpansion("Unknown selected pair: " + id);
            total += measuredMedianMinutesPerPair != null ? measuredMedianMinutesPerPair
                    : minutesPerQuestion * (pairs.itemsOf(id, "A").size() + pairs.itemsOf(id, "B").size());
        }
        double perPair = selectedPairIdsInOrder.isEmpty() ? 0 : total / selectedPairIdsInOrder.size();
        return new ExpansionPlan(required, perPair, total, total <= capMinutes,
                selectedPairIdsInOrder.size() < kReq, required);
    }

    static Map<String, Feasibility> feasibility(Map<String, Map<String, Double>> plannedOutputQuestionsByWorkflowAndStem,
                                                 Map<String, Integer> pairsByStem, int capMinutes,
                                                 double minutesPerQuestion) {
        if (plannedOutputQuestionsByWorkflowAndStem == null || pairsByStem == null || capMinutes < 0
                || !Double.isFinite(minutesPerQuestion) || minutesPerQuestion <= 0)
            throw new InvalidExpansion("Invalid feasibility inputs");
        Map<String, Feasibility> result = new LinkedHashMap<>();
        for (var workflow : plannedOutputQuestionsByWorkflowAndStem.entrySet()) {
            double total = 0;
            if (workflow.getValue() == null || workflow.getValue().isEmpty())
                throw new InvalidExpansion("Workflow needs stem planning averages");
            for (var stem : workflow.getValue().entrySet()) {
                Integer count = pairsByStem.get(stem.getKey());
                Double questions = stem.getValue();
                if (count == null || count < 1 || questions == null || !Double.isFinite(questions) || questions <= 0)
                    throw new InvalidExpansion("Invalid stem planning average: " + stem.getKey());
                total += count * 2 * questions * minutesPerQuestion;
            }
            result.put(workflow.getKey(), new Feasibility(total, total <= capMinutes,
                    Math.max(0, total - capMinutes)));
        }
        return Map.copyOf(result);
    }

    static ExpansionResult outcome(ExpansionPlan plan, Set<String> lockedDoubleMissPairsBeforeExpansion,
                                   List<PairReview> expansionPairsInReviewOrder,
                                   boolean ownerDeclinedOrCapacityExhausted, boolean ownerApprovedLargerCap) {
        if (lockedDoubleMissPairsBeforeExpansion == null || expansionPairsInReviewOrder == null)
            throw new InvalidExpansion("Expansion outcome inputs required");
        boolean unresolved = expansionPairsInReviewOrder.stream().anyMatch(PairReview::hasUnresolved);
        if (lockedDoubleMissPairsBeforeExpansion.size() >= 2) {
            if (!expansionPairsInReviewOrder.isEmpty())
                throw new InvalidExpansion("Initial second double miss forbids expansion review");
            return new ExpansionResult(ExpansionOutcome.INCONCLUSIVE_SCORER_UNRELIABLE, unresolved,
                    "Second confirmed double-miss pair found in initial review; stop before expansion");
        }
        if (lockedDoubleMissPairsBeforeExpansion.isEmpty()) {
            if (!expansionPairsInReviewOrder.isEmpty()) throw new InvalidExpansion("Expansion without a trigger");
            return new ExpansionResult(ExpansionOutcome.NOT_TRIGGERED, false, "No confirmed double-miss pair");
        }
        if (plan == null || expansionPairsInReviewOrder.size() > plan.plannedPairs())
            throw new InvalidExpansion("Reviews exceed expansion plan");
        String trigger = lockedDoubleMissPairsBeforeExpansion.iterator().next();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < expansionPairsInReviewOrder.size(); i++) {
            PairReview pair = expansionPairsInReviewOrder.get(i);
            if (pair == null || pair.pairId() == null || !seen.add(pair.pairId())
                    || pair.pairId().equals(trigger) || pair.doubleMissQuestions() < 0
                    || pair.hasDoubleMiss() != (pair.doubleMissQuestions() > 0)
                    || !pair.complete() && pair.hasDoubleMiss())
                throw new InvalidExpansion("Invalid expansion pair review");
            if (pair.hasDoubleMiss()) {
                if (i != expansionPairsInReviewOrder.size() - 1)
                    throw new InvalidExpansion("Review continued after second double-miss pair");
                return new ExpansionResult(ExpansionOutcome.INCONCLUSIVE_SCORER_UNRELIABLE, unresolved,
                        "Second distinct confirmed double-miss pair; stop review");
            }
        }
        if (!plan.fitsCap() && !ownerApprovedLargerCap)
            return new ExpansionResult(ExpansionOutcome.INCONCLUSIVE_WORKLOAD_EXHAUSTED, unresolved,
                    "Required review exceeds cap; no scorer unreliability or output disadvantage established");
        long complete = expansionPairsInReviewOrder.stream().filter(PairReview::complete).count();
        if (complete == plan.plannedPairs() && expansionPairsInReviewOrder.size() == plan.plannedPairs())
            return new ExpansionResult(ExpansionOutcome.COMPLETE_NO_SECOND_DOUBLE_MISS, unresolved,
                    "Expansion recorded; no second double-miss pair found, not proof of scorer reliability");
        if (ownerDeclinedOrCapacityExhausted)
            return new ExpansionResult(ExpansionOutcome.INCONCLUSIVE_WORKLOAD_EXHAUSTED, unresolved,
                    "Required review incomplete; no scorer unreliability or output disadvantage established");
        throw new InvalidExpansion("Expansion outcome cannot be decided from inputs");
    }
}
