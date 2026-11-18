package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R8 B boundary cases, including the validations formerly reached through Tri.evaluate. */
class RouteT2ReserveGapRuleTest {
    private static final String WORKFLOW = "ASK_COMPANION";
    private static final Rational ALPHA = Rational.of(4, 100);
    private static final Tri.Unresolved GAP = new Tri.Unresolved(-1, 1);
    private static final int RESERVE_SIZE = 30;

    @Test void rejectingCombinedReserveCannotFailOrPassWhileReceiptIsGapped() {
        List<Integer> reserve = new ArrayList<>(Collections.nCopies(29, 0));
        for (int i = 0; i < 20; i++) reserve.set(i, 1);
        var decision = RouteT2.decideDetailed(input(List.of(1), pairs(1), List.of(), true,
                reserve, List.of(GAP)));
        assertThat(decision.workflows().get(WORKFLOW).primaryGate()).isEqualTo(Tri.PASS_COMPLETE);
        assertThat(decision.workflows().get(WORKFLOW).reserveGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(decision.workflows().get(WORKFLOW).workflowGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(decision.overall()).isEqualTo(Tri.INCONCLUSIVE);
    }

    @Test void harmlessCombinedReserveCannotPassWhileReceiptIsGapped() {
        var decision = RouteT2.decideDetailed(input(List.of(1), pairs(1), List.of(), true,
                Collections.nCopies(29, 0), List.of(GAP)));
        assertThat(decision.workflows().get(WORKFLOW).reserveGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(decision.overall()).isEqualTo(Tri.INCONCLUSIVE);
    }

    @Test void establishedPrimaryFailureStillFailsWorkflowAndRoute() {
        var decision = RouteT2.decideDetailed(input(Collections.nCopies(6, 1), pairs(6),
                List.of(), true, Collections.nCopies(29, 0), List.of(GAP)));
        assertThat(decision.workflows().get(WORKFLOW).primaryGate()).isEqualTo(Tri.FAIL);
        assertThat(decision.workflows().get(WORKFLOW).reserveGate()).isEqualTo(Tri.INCONCLUSIVE);
        assertThat(decision.workflows().get(WORKFLOW).workflowGate()).isEqualTo(Tri.FAIL);
        assertThat(decision.overall()).isEqualTo(Tri.FAIL);
    }

    @Test void unresolvedPrimaryStillLeavesDrawnReserveUnevaluated() {
        var decision = RouteT2.decideDetailed(input(List.of(0), List.of(new ClassBReserve.PairCritical(0, 0)),
                List.of(GAP), true, null, List.of(GAP)));
        assertThat(decision.workflows().get(WORKFLOW).reserveGate()).isNull();
        assertThat(decision.workflows().get(WORKFLOW).cause())
                .isEqualTo("DRAWN_RESERVE_WITH_UNRESOLVED_PRIMARY");
        assertThat(decision.overall()).isEqualTo(Tri.INCONCLUSIVE);
    }

    @Test void sizeMismatchNullReserveAndUndrawnGapsRemainInvalidReserve() {
        var shortReserve = input(List.of(1), pairs(1), List.of(), true,
                Collections.nCopies(28, 0), List.of(GAP));
        var nullReserve = input(List.of(1), pairs(1), List.of(), true, null, List.of(GAP));
        var undrawn = input(List.of(1), pairs(1), List.of(), false,
                Collections.nCopies(29, 0), List.of(GAP));
        assertThatThrownBy(() -> RouteT2.decideDetailed(shortReserve)).isInstanceOf(InvalidReserve.class);
        assertThatThrownBy(() -> RouteT2.decideDetailed(nullReserve)).isInstanceOf(InvalidReserve.class);
        assertThatThrownBy(() -> RouteT2.decideDetailed(undrawn)).isInstanceOf(InvalidReserve.class);
    }

    @Test void sizeMismatchStillThrowsWhenPrimaryAlreadyFails() {
        var failedPrimaryShortReserve = input(Collections.nCopies(6, 1), pairs(6), List.of(), true,
                Collections.nCopies(28, 0), List.of(GAP));
        assertThatThrownBy(() -> RouteT2.decideDetailed(failedPrimaryShortReserve))
                .isInstanceOf(InvalidReserve.class);
    }

    @Test void concordantPrimaryCannotClaimDrawnReserve() {
        var input = input(List.of(0), List.of(new ClassBReserve.PairCritical(0, 0)),
                List.of(), true, Collections.nCopies(29, 0), List.of(GAP));
        assertThatThrownBy(() -> RouteT2.decideDetailed(input)).isInstanceOf(InvalidReserve.class);
    }

    @Test void reserveKnownValueOutsideClassBRangeRemainsInvalidInput() {
        List<Integer> reserve = new ArrayList<>(Collections.nCopies(29, 0));
        reserve.set(0, 2);
        var input = input(List.of(1), pairs(1), List.of(), true, reserve, List.of(GAP));
        assertThatThrownBy(() -> RouteT2.decideDetailed(input)).isInstanceOf(InvalidInput.class);
    }

    @Test void nullAndOutOfRangeReserveGapsAreInvalidInput() {
        var nullRange = input(List.of(1), pairs(1), List.of(), true,
                Collections.nCopies(29, 0), Arrays.asList((Tri.Unresolved) null));
        var low = input(List.of(1), pairs(1), List.of(), true,
                Collections.nCopies(29, 0), List.of(new Tri.Unresolved(-2, 1)));
        var high = input(List.of(1), pairs(1), List.of(), true,
                Collections.nCopies(29, 0), List.of(new Tri.Unresolved(-1, 2)));
        assertThatThrownBy(() -> RouteT2.decideDetailed(nullRange)).isInstanceOf(InvalidInput.class);
        assertThatThrownBy(() -> RouteT2.decideDetailed(low)).isInstanceOf(InvalidInput.class);
        assertThatThrownBy(() -> RouteT2.decideDetailed(high)).isInstanceOf(InvalidInput.class);
    }

    private static List<ClassBReserve.PairCritical> pairs(int count) {
        return Collections.nCopies(count, new ClassBReserve.PairCritical(1, 0));
    }

    private static RouteT2.RouteInput input(List<Integer> known,
            List<ClassBReserve.PairCritical> pairs, List<Tri.Unresolved> primaryGaps,
            boolean drawn, List<Integer> reserve, List<Tri.Unresolved> reserveGaps) {
        var workflow = new RouteT2.WorkflowInput(WORKFLOW, true, known, pairs,
                primaryGaps, drawn, reserve, reserveGaps);
        return new RouteT2.RouteInput(1, ALPHA, ALPHA, List.of(workflow), RESERVE_SIZE);
    }
}
