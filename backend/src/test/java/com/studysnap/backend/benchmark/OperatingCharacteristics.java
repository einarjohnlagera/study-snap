package com.studysnap.backend.benchmark;

/** Exact section 1 two-stage and all-tier operating characteristics. */
final class OperatingCharacteristics {
    private OperatingCharacteristics() {}

    static double candidateOnly(double pb, double pc, double rho) {
        checkProbability(pb, pc, rho);
        double shared = (1 - rho) * pb * pc + rho * Math.min(pb, pc);
        return pc - shared;
    }

    static double t3Fail(int cases, int margin, double pb, double pc, double rho) {
        checkProbability(pb, pc, rho);
        if (cases < 0 || margin < 0) throw new IllegalArgumentException("Negative cases or margin");
        double shared = (1 - rho) * pb * pc + rho * Math.min(pb, pc);
        double candidate = pc - shared;
        double baseline = pb - shared;
        double same = 1 - candidate - baseline;
        double[] distribution = new double[2 * cases + 1];
        distribution[cases] = 1;
        for (int step = 0; step < cases; step++) {
            double[] next = new double[distribution.length];
            for (int i = 0; i < distribution.length; i++) {
                double mass = distribution[i];
                if (mass == 0) continue;
                next[i] += mass * same;
                if (i > 0) next[i - 1] += mass * baseline;
                if (i + 1 < next.length) next[i + 1] += mass * candidate;
            }
            distribution = next;
        }
        double fail = 0;
        for (int i = cases + margin + 1; i < distribution.length; i++) fail += distribution[i];
        return fail;
    }

    static double binomialAtMost(int n, int maximum, double q) {
        checkProbability(q);
        if (maximum >= n) return 1;
        if (q == 1) return 0;
        double probability = Math.pow(1 - q, n);
        double total = probability;
        for (int j = 1; j <= maximum; j++) {
            probability *= (n - j + 1.0) / j * q / (1 - q);
            total += probability;
        }
        return total;
    }

    static double t2Fail(FrozenGateParameters parameters, String route, double pb, double pc, double rho) {
        double q = candidateOnly(pb, pc, rho);
        double reservePass = binomialAtMost(parameters.reserveSize, parameters.t2FailCount - 1, q);
        double noFail = 1;
        for (String name : parameters.routes.get(route).workflows()) {
            int n = parameters.workflows.get(name).primarySize();
            double trigger = 1 - Math.pow(1 - q, n);
            noFail *= 1 - trigger * (1 - reservePass);
        }
        return 1 - noFail;
    }

    static double allTierNoFail(FrozenGateParameters parameters, String route, double parseRate,
                                double criticalIncrease) {
        checkProbability(parseRate, criticalIncrease);
        FrozenGateParameters.Route routeValues = parameters.routes.get(route);
        double e = (1 - parseRate) * Math.pow(parseRate, parameters.t1RecheckCount + 1);
        double a = 1 - e;
        double q = candidateOnly(parameters.t2WorstBaseline,
                parameters.t2WorstBaseline + criticalIncrease, parameters.t2WorstRho);
        checkProbability(q);
        double reservePass = binomialAtMost(parameters.reserveSize, parameters.t2FailCount - 1, q);
        double joint = 1;
        int routeCases = 0;
        for (String workflow : routeValues.workflows()) {
            int n = parameters.workflows.get(workflow).primarySize();
            routeCases += n;
            double trigger = 1 - Math.pow(1 - q, n);
            joint *= Math.pow(a, n) * ((1 - trigger)
                    + trigger * reservePass * Math.pow(a, parameters.reserveSize));
        }
        double t3Pass = 1 - t3Fail(routeCases, routeValues.margin(), parameters.t3WorstP,
                parameters.t3WorstP, parameters.t3WorstRho);
        return joint * t3Pass;
    }

    static double t1FailWithReserve(FrozenGateParameters parameters, String route, double parseRate,
                                    double criticalIncrease) {
        checkProbability(parseRate, criticalIncrease);
        double e = (1 - parseRate) * Math.pow(parseRate, parameters.t1RecheckCount + 1);
        double a = 1 - e;
        double q = candidateOnly(parameters.t2WorstBaseline,
                parameters.t2WorstBaseline + criticalIncrease, parameters.t2WorstRho);
        double noFail = 1;
        for (String workflow : parameters.routes.get(route).workflows()) {
            int n = parameters.workflows.get(workflow).primarySize();
            double trigger = 1 - Math.pow(1 - q, n);
            noFail *= Math.pow(a, n) * ((1 - trigger) + trigger * Math.pow(a, parameters.reserveSize));
        }
        return 1 - noFail;
    }

    static double atLeastOne(double rate, int calls) {
        checkProbability(rate);
        return 1 - Math.pow(1 - rate, calls);
    }

    static double unionBound(double... failureProbabilities) {
        double sum = 0;
        for (double probability : failureProbabilities) {
            checkProbability(probability);
            sum += probability;
        }
        return Math.clamp(sum, 0, 1);
    }

    private static void checkProbability(double... values) {
        for (double value : values) if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Invalid probability");
    }
}
