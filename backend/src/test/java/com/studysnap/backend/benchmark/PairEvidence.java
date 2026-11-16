package com.studysnap.backend.benchmark;

import com.studysnap.backend.benchmark.ClassAScoreSchemaV2.Verdict;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Blind per-pair evidence, indexed only by packet placement A or B. */
record PairEvidence(String pairId, String stem, OutputEvidence placementA,
                    OutputEvidence placementB, boolean complete) {
    record OutputEvidence(int questions, int criticalQuestions, int qualityFailQuestions,
                          int unresolvedQuestions, int pendingQuestions, int doubleMisses,
                          int scorerFailureCriticals, int humanCleared, int humanDowngraded,
                          int overturnedAgreedQualityFail, int agreedQualityReviewed,
                          int unreviewedAgreedQualityFail, int scorerMissNoncritical) {
        boolean t2Event() { return criticalQuestions >= 1; }
        boolean t3Event() { return qualityFailQuestions >= 1; }
        /** Pending contributes only before the evidence is locked. */
        CountRange criticalRange() {
            return new CountRange(criticalQuestions,
                    criticalQuestions + unresolvedQuestions + pendingQuestions);
        }
    }

    static Map<String, PairEvidence> build(PairMap pairs, Map<String, QuestionStatus> statuses) {
        if (pairs == null || statuses == null) throw new InvalidEvidence("Pair evidence inputs required");
        Map<String, PairEvidence> result = new LinkedHashMap<>();
        for (PairMap.Pair pair : pairs.pairs()) {
            if (!pair.items().keySet().equals(Set.of("A", "B")))
                throw new InvalidEvidence("Pair needs exactly placements A and B: " + pair.id());
            OutputEvidence a = output(pairs.itemsOf(pair.id(), "A"), statuses);
            OutputEvidence b = output(pairs.itemsOf(pair.id(), "B"), statuses);
            result.put(pair.id(), new PairEvidence(pair.id(), pair.workflow(), a, b,
                    a.unresolvedQuestions() + a.pendingQuestions() + b.unresolvedQuestions() + b.pendingQuestions() == 0));
        }
        for (String id : statuses.keySet()) if (pairs.pairOf(id) == null)
            throw new InvalidEvidence("Status item absent from packets: " + id);
        return Map.copyOf(result);
    }

    static CountRange differenceRange(OutputEvidence candidate, OutputEvidence baseline) {
        if (candidate == null || baseline == null) throw new InvalidEvidence("Both output evidence values required");
        return new CountRange(candidate.criticalQuestions() - baseline.criticalRange().high(),
                candidate.criticalRange().high() - baseline.criticalQuestions());
    }

    private static OutputEvidence output(List<String> ids, Map<String, QuestionStatus> statuses) {
        if (ids.isEmpty()) throw new InvalidEvidence("Output has no questions");
        int critical = 0, quality = 0, unresolved = 0, pending = 0, misses = 0, failures = 0;
        int cleared = 0, downgraded = 0, overturned = 0, reviewedQ = 0, unreviewedQ = 0, noncritical = 0;
        for (String id : ids) {
            QuestionStatus s = statuses.get(id);
            if (s == null || !id.equals(s.itemId())) throw new InvalidEvidence("Missing QuestionStatus: " + id);
            critical += s.f() == Verdict.CRITICAL ? 1 : 0;
            quality += s.f() == Verdict.QUALITY_FAIL ? 1 : 0;
            unresolved += s.f() == Verdict.UNRESOLVED ? 1 : 0;
            pending += s.state() == QuestionStatus.State.PENDING_REVIEW ? 1 : 0;
            misses += s.doubleMiss() ? 1 : 0;
            failures += s.scorerFailureCritical() ? 1 : 0;
            cleared += s.humanCleared() ? 1 : 0;
            downgraded += s.humanDowngraded() ? 1 : 0;
            overturned += s.overturnedAgreedQualityFail() ? 1 : 0;
            reviewedQ += s.agreedQualityReviewed() ? 1 : 0;
            unreviewedQ += s.unreviewedAgreedQualityFail() ? 1 : 0;
            noncritical += s.scorerMissNoncritical() ? 1 : 0;
        }
        return new OutputEvidence(ids.size(), critical, quality, unresolved, pending, misses, failures,
                cleared, downgraded, overturned, reviewedQ, unreviewedQ, noncritical);
    }
}
