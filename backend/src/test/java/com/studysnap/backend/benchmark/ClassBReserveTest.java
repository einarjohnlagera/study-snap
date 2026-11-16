package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassBReserveTest {
    private static final Rational FIVE_PERCENT = Rational.of(5, 100);
    private static final ClassBReserve.PairCritical PLUS = new ClassBReserve.PairCritical(1, 0);
    private static final ClassBReserve.PairCritical MINUS = new ClassBReserve.PairCritical(0, 1);
    private static final ClassBReserve.PairCritical BOTH = new ClassBReserve.PairCritical(1, 1);
    private static final ClassBReserve.PairCritical NEITHER = new ClassBReserve.PairCritical(0, 0);

    @Test void symmetricTriggerNeedsDiscordantCriticalStatusAndIgnoresOrientation() {
        assertThat(ClassBReserve.decideReserve(List.of())).isFalse();
        assertThat(ClassBReserve.decideReserve(List.of(PLUS))).isTrue();
        assertThat(ClassBReserve.decideReserve(List.of(MINUS))).isTrue();
        assertThat(ClassBReserve.decideReserve(List.of(BOTH))).isFalse();
        assertThat(ClassBReserve.decideReserve(List.of(NEITHER))).isFalse();
        List<ClassBReserve.PairCritical> original = List.of(BOTH, PLUS, NEITHER, MINUS);
        List<ClassBReserve.PairCritical> swapped = original.stream()
                .map(pair -> new ClassBReserve.PairCritical(pair.criticalB(), pair.criticalA())).toList();
        assertThat(ClassBReserve.decideReserve(swapped)).isEqualTo(ClassBReserve.decideReserve(original));
    }

    @Test void primaryFailureIsNeverHiddenByReserve() {
        List<Integer> primary = Collections.nCopies(5, 1);
        List<ClassBReserve.PairCritical> pairs = Collections.nCopies(5, PLUS);
        assertThat(ClassBReserve.workflow(primary, pairs, true, null, 30, FIVE_PERCENT))
                .isEqualTo(ClassBReserve.Outcome.FAIL);
        assertThat(ClassBReserve.workflow(primary, pairs, true, Collections.nCopies(30, 0), 30,
                FIVE_PERCENT)).isEqualTo(ClassBReserve.Outcome.FAIL);
    }

    @Test void reserveCanCauseFailureAndMustBeCombinedWithPrimary() {
        assertThat(ClassBReserve.workflow(Collections.nCopies(4, 1), Collections.nCopies(4, PLUS),
                true, List.of(1, 1), 2, FIVE_PERCENT)).isEqualTo(ClassBReserve.Outcome.FAIL);
        assertThat(ClassBReserve.workflow(List.of(-1), List.of(MINUS), true,
                Collections.nCopies(5, 1), 5, FIVE_PERCENT)).isEqualTo(ClassBReserve.Outcome.PASS);
        assertThat(ClassBReserve.workflow(List.of(0, 0), List.of(BOTH, NEITHER), false,
                null, 30, FIVE_PERCENT)).isEqualTo(ClassBReserve.Outcome.PASS);
    }

    @Test void protocolViolationAndWrongSizeThrowNamedReserveError() {
        List<Integer> primary = List.of(1);
        List<ClassBReserve.PairCritical> pairs = List.of(PLUS);
        assertThatThrownBy(() -> ClassBReserve.workflow(primary, pairs, false, null, 30, FIVE_PERCENT))
                .isInstanceOf(InvalidReserve.class);
        assertThatThrownBy(() -> ClassBReserve.workflow(primary, pairs, true, List.of(), 30, FIVE_PERCENT))
                .isInstanceOf(InvalidReserve.class);
        List<Integer> zero = List.of(0);
        List<ClassBReserve.PairCritical> neither = List.of(NEITHER);
        assertThatThrownBy(() -> ClassBReserve.workflow(zero, neither, true, null, 30, FIVE_PERCENT))
                .isInstanceOf(InvalidReserve.class);
        assertThatThrownBy(() -> ClassBReserve.workflow(zero, neither, false, List.of(1), 1, FIVE_PERCENT))
                .isInstanceOf(InvalidReserve.class);
    }

    @Test void pairsAndDifferencesMustAgree() {
        List<Integer> tooShort = List.of(1);
        List<ClassBReserve.PairCritical> twoPairs = List.of(PLUS, PLUS);
        assertThatThrownBy(() -> ClassBReserve.workflow(tooShort, twoPairs, true, null, 30, FIVE_PERCENT))
                .isInstanceOf(InvalidInput.class);
        List<Integer> zero = List.of(0);
        List<ClassBReserve.PairCritical> onePlus = List.of(PLUS);
        assertThatThrownBy(() -> ClassBReserve.workflow(zero, onePlus, true, null, 30, FIVE_PERCENT))
                .isInstanceOf(InvalidInput.class);
    }
}
