package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.List;

/** Interim missing-evidence rule: no complete pass while a pair is unresolved. */
enum Tri {
    FAIL, PASS_COMPLETE, INCONCLUSIVE;
    record Unresolved(int lowestDifference, int highestDifference) {
        Unresolved {
            if (lowestDifference > highestDifference)
                throw new InvalidInput("Reversed unresolved range");
        }
    }

    static Tri evaluate(List<Integer> knownDifferences, List<Unresolved> unresolved, Rational level) {
        if (knownDifferences == null || unresolved == null || level == null)
            throw new InvalidInput("Missing gate evidence");
        List<Integer> lowest = new ArrayList<>(knownDifferences);
        for (Unresolved pair : unresolved) {
            if (pair == null) throw new InvalidInput("Null unresolved range");
            lowest.add(pair.lowestDifference());
        }
        if (ExactPairedTest.test(lowest).atMost(level)) return FAIL;
        return unresolved.isEmpty() ? PASS_COMPLETE : INCONCLUSIVE;
    }
}
