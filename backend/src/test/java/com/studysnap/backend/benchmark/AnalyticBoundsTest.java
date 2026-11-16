package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static com.studysnap.backend.benchmark.ClassBExactOperatingCharacteristics.Trigger.CANDIDATE_ONLY;
import static com.studysnap.backend.benchmark.ClassBExactOperatingCharacteristics.Trigger.SYMMETRIC;

import org.junit.jupiter.api.Test;

class AnalyticBoundsTest {
    private static final double PIN_TOLERANCE = 5e-9;

    @Test void bothTriggerBoundsReproduceEveryPinnedRouteTermAndSum() {
        check(CANDIDATE_ONLY, .08030184, .07673517, .04733324,
                .19317004, .19170675, .14406700);
        check(SYMMETRIC, .08030184, .07674798, .04735842,
                .19317004, .19171956, .14409218);
    }

    @Test void symmetricUnionIncreaseStaysWithinThreeHundredThousandths() {
        for (String route : new String[]{"Interactive", "Paid", "Critique"}) {
            double increase = AnalyticBounds.t2(route, SYMMETRIC) - AnalyticBounds.t2(route, CANDIDATE_ONLY);
            assertThat(increase).isBetween(0.0, 3e-5);
        }
    }

    private static void check(ClassBExactOperatingCharacteristics.Trigger trigger,
                              double interactiveT2, double paidT2, double critiqueT2,
                              double interactiveUnion, double paidUnion, double critiqueUnion) {
        double interactive = AnalyticBounds.t2("Interactive", trigger);
        double paid = AnalyticBounds.t2("Paid", trigger);
        double critique = AnalyticBounds.t2("Critique", trigger);
        assertThat(interactive).isCloseTo(interactiveT2, within());
        assertThat(paid).isCloseTo(paidT2, within());
        assertThat(critique).isCloseTo(critiqueT2, within());
        AnalyticBounds.Bound i = AnalyticBounds.union("Interactive", .11269, interactive,
                180 * .99 * Math.pow(.01, 3));
        AnalyticBounds.Bound p = AnalyticBounds.union("Paid", .11493, paid,
                42 * .99 * Math.pow(.01, 3));
        AnalyticBounds.Bound c = AnalyticBounds.union("Critique", .09671, critique,
                24 * .99 * Math.pow(.01, 3));
        assertThat(i.sum()).isCloseTo(interactiveUnion, within());
        assertThat(p.sum()).isCloseTo(paidUnion, within());
        assertThat(c.sum()).isCloseTo(critiqueUnion, within());
        assertThat(Math.round(i.sum() * 10_000) / 10_000.0).isEqualTo(.1932);
        assertThat(Math.round(p.sum() * 10_000) / 10_000.0).isEqualTo(.1917);
        assertThat(Math.round(c.sum() * 10_000) / 10_000.0).isEqualTo(.1441);
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(PIN_TOLERANCE);
    }
}
