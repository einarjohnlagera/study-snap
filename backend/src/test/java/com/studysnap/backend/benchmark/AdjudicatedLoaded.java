package com.studysnap.backend.benchmark;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One locked export, or the disjoint union of its class-A exports. */
record AdjudicatedLoaded(Map<String, AdjudicatedFacts> byCaseArm, Set<String> excludedCaseIds,
                         Map<String, Integer> requestedQuestionsByCaseId) {
    private static final String SEPARATOR = "\u0000";

    AdjudicatedLoaded {
        if (byCaseArm == null || excludedCaseIds == null || requestedQuestionsByCaseId == null)
            throw new InvalidEvidence("Adjudicated facts, exclusions and widths required");
        if (byCaseArm.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)
                || excludedCaseIds.stream().anyMatch(java.util.Objects::isNull)
                || requestedQuestionsByCaseId.entrySet().stream()
                        .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null))
            throw new InvalidEvidence("Null adjudicated fact, exclusion or width");
        byCaseArm = Collections.unmodifiableMap(new LinkedHashMap<>(byCaseArm));
        excludedCaseIds = Collections.unmodifiableSet(new LinkedHashSet<>(excludedCaseIds));
        requestedQuestionsByCaseId = Collections.unmodifiableMap(new LinkedHashMap<>(requestedQuestionsByCaseId));
    }

    AdjudicatedFacts get(String caseId, String arm) { return byCaseArm.get(caseId + SEPARATOR + arm); }

    ScoreEvidence.Loaded toLoaded(Map<String, String> hashes) {
        if (hashes == null) throw new InvalidEvidence("Score hashes required");
        if (hashes.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null))
            throw new InvalidEvidence("Null score hash");
        Map<String, ScoreEvidence.Facts> facts = new LinkedHashMap<>();
        byCaseArm.forEach((identity, value) -> facts.put(identity, value.toFacts()));
        return new ScoreEvidence.Loaded(Map.copyOf(facts), Set.copyOf(excludedCaseIds), Map.copyOf(hashes));
    }

    static AdjudicatedLoaded merge(List<AdjudicatedLoaded> parts, Set<String> allFrozenClassACaseIds) {
        if (parts == null || allFrozenClassACaseIds == null)
            throw new InvalidEvidence("Merge parts and frozen cases required");
        Map<String, AdjudicatedFacts> facts = new LinkedHashMap<>();
        Map<String, Integer> widths = new LinkedHashMap<>();
        Set<String> excluded = new LinkedHashSet<>();
        for (AdjudicatedLoaded part : parts) {
            if (part == null) throw new InvalidEvidence("Null export part");
            for (var entry : part.requestedQuestionsByCaseId().entrySet())
                if (widths.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                    throw new InvalidEvidence("Case appears in two exports: " + entry.getKey());
            for (var entry : part.byCaseArm().entrySet()) {
                if (facts.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                    throw new InvalidEvidence("Duplicate case and arm: " + entry.getKey());
                String caseId = caseId(entry.getKey());
                if (!part.requestedQuestionsByCaseId().containsKey(caseId) || part.excludedCaseIds().contains(caseId))
                    throw new InvalidEvidence("Fact outside keyed cases: " + entry.getKey());
            }
            for (String caseId : part.excludedCaseIds()) {
                if (!part.requestedQuestionsByCaseId().containsKey(caseId) || !excluded.add(caseId))
                    throw new InvalidEvidence("Excluded case overlaps an export: " + caseId);
            }
        }
        AdjudicatedConverter.requestedWidths(widths, allFrozenClassACaseIds);
        if (!widths.keySet().equals(allFrozenClassACaseIds))
            throw new InvalidEvidence("Merged exports differ from frozen class-A cases");
        Set<String> keyed = new LinkedHashSet<>();
        facts.keySet().forEach(identity -> keyed.add(caseId(identity)));
        keyed.addAll(excluded);
        if (!keyed.equals(allFrozenClassACaseIds))
            throw new InvalidEvidence("Keyed and excluded union differs from frozen class-A cases");
        return new AdjudicatedLoaded(facts, excluded, widths);
    }

    private static String caseId(String identity) {
        int separator = identity.indexOf(SEPARATOR);
        if (separator < 1 || separator == identity.length() - 1)
            throw new InvalidEvidence("Invalid case-arm identity: " + identity);
        return identity.substring(0, separator);
    }
}
