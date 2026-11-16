package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SolT2ImprovementTest {
    @Test void allThreeConditionsAndExactThirtyPercentBoundaryPass() {
        List<Integer> luna = Collections.nCopies(20, 1);
        List<Integer> sol = concat(Collections.nCopies(14, 1), Collections.nCopies(6, 0));
        SolT2Improvement.SolImprovement result = SolT2Improvement.evaluate(sol, luna);
        assertThat(result.pValueOk()).isTrue();
        assertThat(result.thirtyPercentOk()).isTrue();
        assertThat(result.threeFewerOk()).isTrue();
        assertThat(SolT2Improvement.improves(sol, luna)).isTrue();
    }

    @Test void eachConjunctCanFailAlone() {
        // A concentrated improvement gives the percentage and count, but one sign flip has p=1/2.
        SolT2Improvement.SolImprovement pFails = SolT2Improvement.evaluate(List.of(0), List.of(10));
        assertThat(pFails).isEqualTo(new SolT2Improvement.SolImprovement(false, true, true));
        List<Integer> lunaThirty = Collections.nCopies(30, 1);
        List<Integer> solThirty = concat(Collections.nCopies(25, 1), Collections.nCopies(5, 0));
        assertThat(SolT2Improvement.evaluate(solThirty, lunaThirty))
                .isEqualTo(new SolT2Improvement.SolImprovement(true, false, true));
        // With integer differences, <=2 fewer cannot reach p<=.05; assert the count boundary directly.
        List<Integer> lunaSix = Collections.nCopies(6, 1);
        List<Integer> solSix = concat(Collections.nCopies(4, 1), Collections.nCopies(2, 0));
        assertThat(SolT2Improvement.evaluate(solSix, lunaSix).threeFewerOk()).isFalse();
        assertThat(SolT2Improvement.evaluate(List.of(0, 0, 0), List.of(1, 1, 1)).threeFewerOk()).isTrue();
    }

    @Test void pValueBracketsFivePercentAndWorseSolDoesNotImprove() {
        assertThat(SolT2Improvement.evaluate(Collections.nCopies(5, 0), Collections.nCopies(5, 1)).pValueOk())
                .isTrue();
        assertThat(SolT2Improvement.evaluate(Collections.nCopies(4, 0), Collections.nCopies(4, 1)).pValueOk())
                .isFalse();
        assertThat(SolT2Improvement.improves(List.of(1, 1), List.of(1, 1))).isFalse();
        assertThat(SolT2Improvement.improves(List.of(2, 2), List.of(1, 1))).isFalse();
    }

    @Test void unequalLengthsAreInvalid() {
        List<Integer> sol = List.of(0);
        List<Integer> luna = List.of();
        assertThatThrownBy(() -> SolT2Improvement.improves(sol, luna)).isInstanceOf(InvalidInput.class);
    }

    private static List<Integer> concat(List<Integer> first, List<Integer> second) {
        java.util.ArrayList<Integer> all = new java.util.ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
