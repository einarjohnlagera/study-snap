package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.List;

/** Key-free trigger and post-unlock class-B workflow decision. */
final class ClassBReserve {
    private ClassBReserve() {}

    record PairCritical(int criticalA, int criticalB) {
        PairCritical {
            if (criticalA < 0 || criticalA > 1 || criticalB < 0 || criticalB > 1)
                throw new InvalidInput("Class-B output must have zero or one critical");
        }
    }
    enum Outcome { PASS, FAIL }

    static boolean decideReserve(List<PairCritical> primaryPairs) {
        if (primaryPairs == null) throw new InvalidInput("Missing primary pairs");
        boolean triggered = false;
        for (PairCritical pair : primaryPairs) {
            if (pair == null) throw new InvalidInput("Null primary pair");
            triggered |= (pair.criticalA() >= 1) != (pair.criticalB() >= 1);
        }
        return triggered;
    }

    static Outcome workflow(List<Integer> primaryDifferences, List<PairCritical> primaryPairs,
                            boolean reserveWasDrawn, List<Integer> reserveDifferencesOrNull,
                            int declaredReserveSize, Rational level) {
        if (primaryDifferences == null || primaryPairs == null || level == null
                || declaredReserveSize <= 0 || primaryDifferences.size() != primaryPairs.size())
            throw new InvalidInput("Invalid class-B primary evidence");
        boolean triggered = decideReserve(primaryPairs);
        if (triggered != reserveWasDrawn || (!triggered && reserveDifferencesOrNull != null))
            throw new InvalidReserve("Recorded reserve differs from locked trigger");
        for (int i = 0; i < primaryDifferences.size(); i++) {
            Integer difference = primaryDifferences.get(i);
            if (difference == null || difference < -1 || difference > 1
                    || Math.abs(primaryPairs.get(i).criticalA() - primaryPairs.get(i).criticalB())
                    != Math.abs(difference))
                throw new InvalidInput("Class-B pair and difference disagree");
        }
        if (ExactPairedTest.test(primaryDifferences).atMost(level)) return Outcome.FAIL;
        if (!triggered) return Outcome.PASS;
        if (reserveDifferencesOrNull == null || reserveDifferencesOrNull.size() != declaredReserveSize)
            throw new InvalidReserve("Reserve size differs from declaration");
        for (Integer difference : reserveDifferencesOrNull)
            if (difference == null || difference < -1 || difference > 1)
                throw new InvalidInput("Class-B reserve difference out of range");
        List<Integer> combined = new ArrayList<>(primaryDifferences);
        combined.addAll(reserveDifferencesOrNull);
        return ExactPairedTest.test(combined).atMost(level) ? Outcome.FAIL : Outcome.PASS;
    }
}
