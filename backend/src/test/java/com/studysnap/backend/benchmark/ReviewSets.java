package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pair-level blind review sets; every selected pair requires review of both placements. */
record ReviewSets(Set<String> mandatory, Set<String> criticalCleanFrame,
                  Map<String, Set<String>> frameByStem, Map<String, Set<String>> mandatoryByStem) {
    static ReviewSets compute(PairMap pairs, Map<String, ClassAScoreFilesV2.ItemScores> scores,
                              EvidenceMapping mapping) {
        if (pairs == null || scores == null || mapping == null) throw new InvalidEvidence("Review inputs required");
        Set<String> mandatory = new LinkedHashSet<>(), frame = new LinkedHashSet<>();
        Map<String, Set<String>> byStem = new LinkedHashMap<>(), mByStem = new LinkedHashMap<>();
        for (PairMap.Pair pair : pairs.pairs()) {
            if (!pair.items().keySet().equals(Set.of("A", "B")))
                throw new InvalidEvidence("Pair needs exactly placements A and B: " + pair.id());
            byStem.computeIfAbsent(pair.workflow(), ignored -> new LinkedHashSet<>());
            mByStem.computeIfAbsent(pair.workflow(), ignored -> new LinkedHashSet<>());
            boolean needsReview = false;
            for (String placement : List.of("A", "B")) for (String id : pairs.itemsOf(pair.id(), placement)) {
                ClassAScoreFilesV2.ItemScores item = scores.get(id);
                if (item == null || !id.equals(item.id())) throw new InvalidEvidence("Missing ItemScores: " + id);
                needsReview |= ClassAScoreFilesV2.needsHumanReview(item, mapping);
            }
            if (needsReview) {
                mandatory.add(pair.id());
                mByStem.get(pair.workflow()).add(pair.id());
            } else {
                frame.add(pair.id());
                byStem.get(pair.workflow()).add(pair.id());
            }
        }
        for (String id : scores.keySet()) if (pairs.pairOf(id) == null)
            throw new InvalidEvidence("Score item absent from packets: " + id);
        return new ReviewSets(Set.copyOf(mandatory), Set.copyOf(frame), freeze(byStem), freeze(mByStem));
    }

    static Set<String> reviewRequiredPairs(ReviewSets sets, Collection<String> selectedPairIds) {
        if (sets == null || selectedPairIds == null) throw new InvalidEvidence("Review selection required");
        Set<String> result = new LinkedHashSet<>(sets.mandatory());
        for (String id : selectedPairIds) {
            if (!sets.criticalCleanFrame().contains(id))
                throw new InvalidEvidence("Selected pair is outside critical-clean frame: " + id);
            result.add(id);
        }
        return Set.copyOf(result);
    }

    static List<String> spotCheck(ReviewSets sets, SeedStreamRegistry registry, DrawLog log,
                                  PairMap pairs, String stem, int round, int count, String clockUtc) {
        if (sets == null || !sets.frameByStem().containsKey(stem))
            throw new InvalidEvidence("Unknown spot-check stem: " + stem);
        return PairSampler.draw(registry, log, pairs, stem, SeedStreamRegistry.SPOT_CHECK,
                round, new ArrayList<>(sets.frameByStem().get(stem)), count, clockUtc).selectedPairIds();
    }

    private static Map<String, Set<String>> freeze(Map<String, Set<String>> source) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        source.forEach((stem, ids) -> result.put(stem, Collections.unmodifiableSet(new LinkedHashSet<>(ids))));
        return Collections.unmodifiableMap(result);
    }
}
