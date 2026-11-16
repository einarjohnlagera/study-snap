package com.studysnap.backend.benchmark;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Disjoint class-A and class-B export evidence with frozen widths and reserve decisions. */
record FrbEvidenceBundle(AdjudicatedLoaded evidence,
        Map<String, Integer> requestedQuestionsByCaseId,
        Map<String, Boolean> reserveDrawnByWorkflow,
        Set<String> workflowsAwaitingReserve) {
    record ReservePart(String workflow, AdjudicatedLoaded facts, Set<String> frozenReserveCaseIds) {}

    FrbEvidenceBundle {
        if (evidence == null || requestedQuestionsByCaseId == null || reserveDrawnByWorkflow == null
                || workflowsAwaitingReserve == null)
            throw new InvalidEvidence("Incomplete FRB evidence bundle");
        requestedQuestionsByCaseId = Collections.unmodifiableMap(new LinkedHashMap<>(requestedQuestionsByCaseId));
        reserveDrawnByWorkflow = Collections.unmodifiableMap(new LinkedHashMap<>(reserveDrawnByWorkflow));
        workflowsAwaitingReserve = Collections.unmodifiableSet(new LinkedHashSet<>(workflowsAwaitingReserve));
    }

    static FrbEvidenceBundle assemble(List<AdjudicatedLoaded> classAParts,
            AdjudicatedLoaded classBPrimary, List<ReservePart> classBReserveParts,
            Set<String> allFrozenClassACaseIds, Set<String> allFrozenClassBPrimaryCaseIds,
            ReserveDecisionRecord verifiedRecord, Set<String> classBWorkflowNames) {
        if (classAParts == null || classBPrimary == null || classBReserveParts == null
                || allFrozenClassACaseIds == null || allFrozenClassBPrimaryCaseIds == null
                || verifiedRecord == null || classBWorkflowNames == null)
            throw new InvalidEvidence("Complete FRB bundle inputs required");
        Map<String, Boolean> decisions = verifiedRecord.reserveDrawnByWorkflow();
        if (!decisions.keySet().equals(classBWorkflowNames))
            throw new InvalidEvidence("Reserve decision workflows differ from frozen class-B workflows");
        AdjudicatedLoaded classA = AdjudicatedLoaded.merge(classAParts, allFrozenClassACaseIds);
        requireClassBPart(classBPrimary, allFrozenClassBPrimaryCaseIds);

        Map<String, AdjudicatedFacts> facts = new LinkedHashMap<>(classA.byCaseArm());
        Map<String, Integer> widths = new LinkedHashMap<>(classA.requestedQuestionsByCaseId());
        Set<String> excluded = new LinkedHashSet<>(classA.excludedCaseIds());
        add(classBPrimary, facts, widths, excluded);
        Set<String> seenWorkflows = new HashSet<>();
        for (ReservePart part : classBReserveParts) {
            if (part == null || part.workflow() == null || part.workflow().isBlank()
                    || part.facts() == null || part.frozenReserveCaseIds() == null)
                throw new InvalidEvidence("Incomplete class-B reserve part");
            if (!Boolean.TRUE.equals(decisions.get(part.workflow())) || !seenWorkflows.add(part.workflow()))
                throw new InvalidEvidence("Undrawn or duplicate class-B reserve: " + part.workflow());
            requireClassBPart(part.facts(), part.frozenReserveCaseIds());
            add(part.facts(), facts, widths, excluded);
        }
        Set<String> awaiting = new LinkedHashSet<>();
        decisions.forEach((workflow, drawn) -> {
            if (drawn == null) throw new InvalidEvidence("Null reserve decision: " + workflow);
            if (drawn && !seenWorkflows.contains(workflow)) awaiting.add(workflow);
        });
        return new FrbEvidenceBundle(new AdjudicatedLoaded(facts, excluded, widths), widths,
                decisions, awaiting);
    }

    ScoreEvidence.Loaded toLoaded(Map<String, String> hashes) { return evidence.toLoaded(hashes); }

    private static void requireClassBPart(AdjudicatedLoaded part, Set<String> frozen) {
        if (!part.excludedCaseIds().isEmpty() || !part.requestedQuestionsByCaseId().keySet().equals(frozen)
                || part.requestedQuestionsByCaseId().values().stream().anyMatch(width -> width != 1))
            throw new InvalidEvidence("Class-B part differs from frozen cases or width one");
        Set<String> factCases = new HashSet<>();
        for (String identity : part.byCaseArm().keySet()) {
            int separator = identity.indexOf('\u0000');
            if (separator < 1 || separator == identity.length() - 1)
                throw new InvalidEvidence("Invalid class-B fact identity: " + identity);
            factCases.add(identity.substring(0, separator));
        }
        if (!factCases.equals(frozen))
            throw new InvalidEvidence("Class-B facts differ from frozen cases");
    }

    private static void add(AdjudicatedLoaded part, Map<String, AdjudicatedFacts> facts,
            Map<String, Integer> widths, Set<String> excluded) {
        for (var entry : part.requestedQuestionsByCaseId().entrySet())
            if (widths.putIfAbsent(entry.getKey(), entry.getValue()) != null || excluded.contains(entry.getKey()))
                throw new InvalidEvidence("Case appears in two FRB parts: " + entry.getKey());
        for (var entry : part.byCaseArm().entrySet())
            if (facts.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                throw new InvalidEvidence("Duplicate FRB case arm: " + entry.getKey());
    }
}
