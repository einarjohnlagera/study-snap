package com.studysnap.backend.benchmark;

import com.studysnap.backend.benchmark.ClassBExactOperatingCharacteristics.Trigger;

/**
 * Union bound for an exactly equivalent candidate: exchangeable arms, independent cases,
 * non-negative within-pair correlation, correct final statuses, equal detection in both arms,
 * T3 at its worst case, the modelled T1 rate, the preregistered workflow structure, and fresh
 * class-B reserve pairs with the primary's discordance rate. Only statistical gates count.
 * This is not a universal guarantee.
 */
final class AnalyticBounds {
    private static final double ALPHA = .04;
    private static final double[] CRITIQUE_DELTAS = {.02, .05, .1, .15, .2, .3, .4, .5};
    private AnalyticBounds() {}

    record Bound(double t3ExactWorst, double t2Bound, double t1Term, double sum) {}

    static Bound union(String route, double t3ExactWorst, double t2Bound, double t1Term) {
        if (route == null || (!route.equals("Interactive") && !route.equals("Paid")
                && !route.equals("Critique")) || !valid(t3ExactWorst) || !valid(t2Bound) || !valid(t1Term))
            throw new InvalidInput("Invalid union-bound terms");
        return new Bound(t3ExactWorst, t2Bound, t1Term, t3ExactWorst + t2Bound + t1Term);
    }

    static double t2(String route, Trigger trigger) {
        if (trigger == null || route == null) throw new InvalidInput("Missing route or trigger");
        return switch (route) {
            case "Interactive" -> ALPHA + 5 * ALPHA / 6 + worstWorkflow(30, ALPHA / 6, trigger);
            case "Paid" -> ALPHA + ALPHA / 2 + worstWorkflow(12, ALPHA / 2, trigger);
            case "Critique" -> worstCritique(trigger);
            default -> throw new InvalidInput("Unknown route");
        };
    }

    static double worstWorkflow(int n, double level, Trigger trigger) {
        double worst = 0;
        for (int i = 1; i <= 50; i++)
            worst = Math.max(worst, ClassBExactOperatingCharacteristics.workflowFalseFail(
                    n, i / 100.0, level, level, trigger));
        return worst;
    }

    static double worstCritique(Trigger trigger) {
        double worst = 0;
        for (double first : CRITIQUE_DELTAS)
            for (double second : CRITIQUE_DELTAS)
                worst = Math.max(worst, ClassBExactOperatingCharacteristics.routeFalseFail(
                        new int[]{12, 12}, new double[]{first, second}, ALPHA, ALPHA, 2, trigger));
        return worst;
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
