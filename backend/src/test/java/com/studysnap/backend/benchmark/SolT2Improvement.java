package com.studysnap.backend.benchmark;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Exact Sol-versus-Luna T2 improvement conjunction, without the Sol baseline gate. */
final class SolT2Improvement {
    private SolT2Improvement() {}

    record SolImprovement(boolean pValueOk, boolean thirtyPercentOk, boolean threeFewerOk) {
        boolean improves() { return pValueOk && thirtyPercentOk && threeFewerOk; }
    }

    static boolean improves(List<Integer> solCounts, List<Integer> lunaCounts) {
        return evaluate(solCounts, lunaCounts).improves();
    }

    static SolImprovement evaluate(List<Integer> solCounts, List<Integer> lunaCounts) {
        if (solCounts == null || lunaCounts == null || solCounts.size() != lunaCounts.size())
            throw new InvalidInput("Sol and Luna cases differ");
        List<Integer> differences = new ArrayList<>();
        long solTotal = 0;
        long lunaTotal = 0;
        for (int i = 0; i < solCounts.size(); i++) {
            Integer sol = solCounts.get(i);
            Integer luna = lunaCounts.get(i);
            if (sol == null || luna == null || sol < 0 || luna < 0)
                throw new InvalidInput("Invalid critical count");
            solTotal += sol;
            lunaTotal += luna;
            differences.add(luna - sol);
        }
        boolean p = ExactPairedTest.test(differences)
                .atMost(BigInteger.ONE, BigInteger.valueOf(20));
        return new SolImprovement(p, 10 * solTotal <= 7 * lunaTotal, lunaTotal - solTotal >= 3);
    }
}
