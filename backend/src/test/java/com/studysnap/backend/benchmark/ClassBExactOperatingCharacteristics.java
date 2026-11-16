package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.List;

/** Exact finite class-B enumeration; candidate-only exists solely for the recorded comparison. */
final class ClassBExactOperatingCharacteristics {
    private static final int RESERVE_SIZE = 30;
    private static final double NEGLIGIBLE_RESERVE_MASS = 1e-15;
    private static final int MAX_PAIRS = 300;
    private static final double[][] TAIL = buildTails(MAX_PAIRS);

    private ClassBExactOperatingCharacteristics() {}

    enum Trigger { CANDIDATE_ONLY, SYMMETRIC }
    record State(int discordant, int plus, double probability, double conditionalFail) {}

    static double tail(int discordant, int plus) {
        if (discordant < 0 || discordant > MAX_PAIRS) throw new InvalidInput("Invalid discordant count");
        if (discordant == 0 || plus <= 0) return 1;
        if (plus > discordant) return 0;
        return TAIL[discordant][plus];
    }

    static double workflowFalseFail(int n, double delta, double a1, double a2, Trigger trigger) {
        double result = 0;
        for (State state : states(n, delta, a1, a2, trigger)) result += state.probability() * state.conditionalFail();
        return result;
    }

    static double routeFalseFail(int[] ns, double[] deltas, double alphaRoute,
                                 double familyBudget, int k, Trigger trigger) {
        if (ns == null || deltas == null || ns.length != deltas.length || ns.length != k || k <= 0)
            throw new InvalidInput("Invalid route configuration");
        checkProbability(alphaRoute);
        checkProbability(familyBudget);
        List<List<State>> workflows = new ArrayList<>();
        for (int i = 0; i < k; i++)
            workflows.add(states(ns[i], deltas[i], familyBudget / k, familyBudget / k, trigger));
        return routeRecursion(workflows, 0, 0, 0, 1, 1, alphaRoute);
    }

    private static double routeRecursion(List<List<State>> workflows, int index, int sumD, int sumB,
                                         double probability, double noWorkflowFail, double alphaRoute) {
        if (index == workflows.size())
            return probability * (tail(sumD, sumB) <= alphaRoute ? 1 : 1 - noWorkflowFail);
        double result = 0;
        for (State state : workflows.get(index))
            result += routeRecursion(workflows, index + 1, sumD + state.discordant(),
                    sumB + state.plus(), probability * state.probability(),
                    noWorkflowFail * (1 - state.conditionalFail()), alphaRoute);
        return result;
    }

    private static List<State> states(int n, double delta, double a1, double a2, Trigger trigger) {
        if (n < 0 || n > RESERVE_SIZE || trigger == null) throw new InvalidInput("Invalid workflow size");
        checkProbability(delta);
        checkProbability(a1);
        checkProbability(a2);
        double[] discordance = binomial(n, delta);
        double[] reserveDiscordance = binomial(RESERVE_SIZE, delta);
        double[][] half = new double[RESERVE_SIZE + 1][];
        for (int d = 0; d <= RESERVE_SIZE; d++) half[d] = binomial(d, .5);
        List<State> result = new ArrayList<>();
        for (int d = 0; d <= n; d++) {
            for (int b = 0; b <= d; b++) {
                double g;
                if (tail(d, b) <= a1) g = 1;
                else if ((trigger == Trigger.CANDIDATE_ONLY && b == 0)
                        || (trigger == Trigger.SYMMETRIC && d == 0)) g = 0;
                else {
                    g = 0;
                    for (int d2 = 0; d2 <= RESERVE_SIZE; d2++) {
                        if (reserveDiscordance[d2] < NEGLIGIBLE_RESERVE_MASS) continue;
                        for (int b2 = 0; b2 <= d2; b2++)
                            if (tail(d + d2, b + b2) <= a2)
                                g += reserveDiscordance[d2] * half[d2][b2];
                    }
                }
                result.add(new State(d, b, discordance[d] * half[d][b], g));
            }
        }
        return result;
    }

    private static double[] binomial(int n, double p) {
        double[] pmf = new double[n + 1];
        if (p == 1) { pmf[n] = 1; return pmf; }
        pmf[0] = Math.pow(1 - p, n);
        for (int i = 1; i <= n; i++) pmf[i] = pmf[i - 1] * (n - i + 1.0) / i * p / (1 - p);
        return pmf;
    }

    private static double[][] buildTails(int maximum) {
        double[][] tails = new double[maximum + 1][];
        for (int d = 0; d <= maximum; d++) {
            tails[d] = new double[d + 1];
            double[] probabilities = binomial(d, .5);
            double running = 0;
            for (int b = d; b >= 0; b--) {
                running += probabilities[b];
                tails[d][b] = running;
            }
        }
        return tails;
    }

    private static void checkProbability(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new InvalidInput("Invalid probability");
    }
}
