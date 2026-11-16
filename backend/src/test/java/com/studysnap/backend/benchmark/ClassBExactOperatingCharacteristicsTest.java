package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static com.studysnap.backend.benchmark.ClassBExactOperatingCharacteristics.Trigger.CANDIDATE_ONLY;
import static com.studysnap.backend.benchmark.ClassBExactOperatingCharacteristics.Trigger.SYMMETRIC;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassBExactOperatingCharacteristicsTest {
    private static final double SINGLE_TOLERANCE = 1e-9;
    private static final double BOUND_TOLERANCE = 5e-9;

    @Test void tailsIncludeTheNonpositiveThreshold() {
        assertThat(ClassBExactOperatingCharacteristics.tail(0, 0)).isEqualTo(1);
        assertThat(ClassBExactOperatingCharacteristics.tail(8, 0)).isEqualTo(1);
        assertThat(ClassBExactOperatingCharacteristics.tail(8, -2)).isEqualTo(1);
        assertThat(ClassBExactOperatingCharacteristics.tail(5, 5)).isCloseTo(1.0 / 32, within(SINGLE_TOLERANCE));
    }

    @Test void pinnedSinglePointsAndTriggerDifferences() {
        double symmetric12 = ClassBExactOperatingCharacteristics.workflowFalseFail(12, .2, .02, .02, SYMMETRIC);
        double candidate12 = ClassBExactOperatingCharacteristics.workflowFalseFail(12, .2, .02, .02, CANDIDATE_ONLY);
        double symmetric30 = ClassBExactOperatingCharacteristics.workflowFalseFail(30, .1, .04 / 6, .04 / 6, SYMMETRIC);
        double candidate30 = ClassBExactOperatingCharacteristics.workflowFalseFail(30, .1, .04 / 6, .04 / 6, CANDIDATE_ONLY);
        assertThat(roundEight(symmetric12)).isEqualTo(.00951167);
        assertThat(roundEight(candidate12)).isEqualTo(.00943372);
        assertThat(roundEight(symmetric30)).isEqualTo(.00076431);
        assertThat(roundEight(candidate30)).isEqualTo(.00076428);
        assertThat(symmetric12).isGreaterThan(candidate12);
        assertThat(symmetric30).isGreaterThan(candidate30);
        assertThat(symmetric12 - candidate12).isCloseTo(.00951167 - .00943372, within(1e-8));
        assertThat(symmetric30 - candidate30).isCloseTo(.00076431 - .00076428, within(1e-8));
    }

    @Test void pinnedWorstWorkflowAndCritiqueRouteValues() {
        double c30 = AnalyticBounds.worstWorkflow(30, .04 / 6, CANDIDATE_ONLY);
        double s30 = AnalyticBounds.worstWorkflow(30, .04 / 6, SYMMETRIC);
        double c12 = AnalyticBounds.worstWorkflow(12, .02, CANDIDATE_ONLY);
        double s12 = AnalyticBounds.worstWorkflow(12, .02, SYMMETRIC);
        assertThat(roundFive(c30)).isEqualTo(.00697);
        assertThat(roundFive(s30)).isEqualTo(.00697);
        assertThat(roundFive(c12)).isEqualTo(.01674);
        assertThat(roundFive(s12)).isEqualTo(.01675);
        assertThat(c30).isCloseTo(.00696851, within(BOUND_TOLERANCE));
        assertThat(s30).isCloseTo(.00696851, within(BOUND_TOLERANCE));
        assertThat(c12).isCloseTo(.01673517, within(BOUND_TOLERANCE));
        assertThat(s12).isCloseTo(.01674798, within(BOUND_TOLERANCE));
        double critiqueCandidate = AnalyticBounds.worstCritique(CANDIDATE_ONLY);
        double critiqueSymmetric = AnalyticBounds.worstCritique(SYMMETRIC);
        assertThat(roundFive(critiqueCandidate)).isEqualTo(.04733);
        assertThat(roundFive(critiqueSymmetric)).isEqualTo(.04736);
        assertThat(critiqueCandidate).isCloseTo(.04733324, within(BOUND_TOLERANCE));
        assertThat(critiqueSymmetric).isCloseTo(.04735842, within(BOUND_TOLERANCE));
        assertThat(critiqueSymmetric).isGreaterThan(critiqueCandidate);
    }

    @Test void actualWorkflowDecisionReproducesEnumeratedSymmetricFalseFailAtBothPins() {
        oracleTie(12, .2, .02);
        oracleTie(30, .1, .04 / 6);
    }

    private static void oracleTie(int n, double delta, double level) {
        double[] primaryD = binomial(n, delta);
        double[] reserveD = binomial(30, delta);
        double total = 0;
        for (int d = 0; d <= n; d++) {
            double[] primaryB = binomial(d, .5);
            for (int b = 0; b <= d; b++) {
                List<Integer> primary = differences(n, d, b);
                List<ClassBReserve.PairCritical> pairs = pairs(primary);
                boolean triggered = ClassBReserve.decideReserve(pairs);
                boolean primaryRejects = ExactPairedTest.test(primary).atMost(rational(level));
                double conditional = 0;
                if (!triggered || primaryRejects) {
                    ClassBReserve.Outcome outcome = ClassBReserve.workflow(primary, pairs, triggered,
                            null, 30, rational(level));
                    conditional = outcome == ClassBReserve.Outcome.FAIL ? 1 : 0;
                } else {
                    for (int d2 = 0; d2 <= 30; d2++) {
                        if (reserveD[d2] < 1e-15) continue;
                        double[] reserveB = binomial(d2, .5);
                        for (int b2 = 0; b2 <= d2; b2++) {
                            List<Integer> reserve = differences(30, d2, b2);
                            if (ClassBReserve.workflow(primary, pairs, true, reserve, 30, rational(level))
                                    == ClassBReserve.Outcome.FAIL)
                                conditional += reserveD[d2] * reserveB[b2];
                        }
                    }
                }
                total += primaryD[d] * primaryB[b] * conditional;
            }
        }
        assertThat(total).isCloseTo(ClassBExactOperatingCharacteristics.workflowFalseFail(
                n, delta, level, level, SYMMETRIC), within(1e-12));
    }

    private static Rational rational(double value) {
        if (value == .02) return Rational.of(2, 100);
        return Rational.of(4, 600);
    }

    private static List<Integer> differences(int n, int discordant, int plus) {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < plus; i++) values.add(1);
        for (int i = plus; i < discordant; i++) values.add(-1);
        for (int i = discordant; i < n; i++) values.add(0);
        return values;
    }

    private static List<ClassBReserve.PairCritical> pairs(List<Integer> differences) {
        return differences.stream().map(d -> switch (d) {
            case 1 -> new ClassBReserve.PairCritical(1, 0);
            case -1 -> new ClassBReserve.PairCritical(0, 1);
            default -> new ClassBReserve.PairCritical(0, 0);
        }).toList();
    }

    private static double[] binomial(int n, double p) {
        double[] values = new double[n + 1];
        values[0] = Math.pow(1 - p, n);
        for (int i = 1; i <= n; i++) values[i] = values[i - 1] * (n - i + 1.0) / i * p / (1 - p);
        return values;
    }

    private static double roundFive(double value) { return Math.round(value * 100_000) / 100_000.0; }
    private static double roundEight(double value) { return Math.round(value * 100_000_000) / 100_000_000.0; }
    private static org.assertj.core.data.Offset<Double> within(double value) { return org.assertj.core.data.Offset.offset(value); }
}
