package com.studysnap.backend.benchmark;

/** Section 1 independent 32-bit xorshift route-draw check. */
final class XorShiftMonteCarlo {
    private XorShiftMonteCarlo() {}

    static double noFail(FrozenGateParameters parameters, String route, double parseRate,
                         double criticalIncrease, int seed) {
        Generator random = new Generator(seed);
        double e = (1 - parseRate) * Math.pow(parseRate, parameters.t1RecheckCount + 1);
        double q = OperatingCharacteristics.candidateOnly(parameters.t2WorstBaseline,
                parameters.t2WorstBaseline + criticalIncrease, parameters.t2WorstRho);
        int passed = 0;
        FrozenGateParameters.Route routeValues = parameters.routes.get(route);
        for (int draw = 0; draw < parameters.monteCarloDraws; draw++) {
            int discordance = 0;
            boolean fail = false;
            for (String workflow : routeValues.workflows()) {
                boolean trigger = false;
                int primary = parameters.workflows.get(workflow).primarySize();
                for (int caseIndex = 0; caseIndex < primary; caseIndex++) {
                    boolean baselineT3 = random.next() < parameters.t3WorstP;
                    boolean candidateT3 = random.next() < parameters.t3WorstP;
                    discordance += (candidateT3 ? 1 : 0) - (baselineT3 ? 1 : 0);
                    trigger |= random.next() < q;
                    fail |= random.next() < e;
                }
                if (trigger) {
                    int critical = 0;
                    for (int caseIndex = 0; caseIndex < parameters.reserveSize; caseIndex++) {
                        if (random.next() < q) critical++;
                        fail |= random.next() < e;
                    }
                    fail |= critical >= parameters.t2FailCount;
                }
            }
            if (!fail && discordance <= routeValues.margin()) passed++;
        }
        return passed / (double) parameters.monteCarloDraws;
    }

    static final class Generator {
        private int x;
        Generator(int seed) { x = seed; }
        double next() {
            x ^= x << 13;
            x ^= x >>> 17;
            x ^= x << 5;
            return Integer.toUnsignedLong(x) / 4294967296.0;
        }
    }
}
