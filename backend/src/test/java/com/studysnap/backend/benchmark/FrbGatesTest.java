package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrbGatesTest {
    private static final Rational ALPHA = Rational.of(4, 100);
    private static final String FIRST = "first";
    private static final String SECOND = "second";

    @Test void passingRouteHasExactNonrejectingTests() {
        FrbGates.GateResult result = FrbGates.evaluate(List.of(w(FIRST, 1), w(SECOND, -1)), ALPHA, ALPHA, 2);
        assertThat(result.failsT2()).isFalse();
        assertThat(result.routeTest().tailCount()).isEqualTo(3);
        assertThat(result.workflows()).allMatch(gate -> !gate.rejects());
    }

    @Test void pooledRouteAloneCanFail() {
        FrbGates.GateResult result = FrbGates.evaluate(List.of(w(FIRST, 1, 1, 1, 1),
                w(SECOND, 1, 1, 1, 1)), ALPHA, ALPHA, 2);
        assertThat(result.routeRejects()).isTrue();
        assertThat(result.workflows()).allMatch(gate -> !gate.rejects());
        assertThat(result.failsT2()).isTrue();
    }

    @Test void oneWorkflowAloneCanFailAtDividedFamilyLevel() {
        FrbGates.GateResult result = FrbGates.evaluate(List.of(w(FIRST, 1, 1, 1, 1, 1, 1),
                w(SECOND, -1, -1, -1, -1, -1, -1)), ALPHA, ALPHA, 2);
        assertThat(result.routeRejects()).isFalse();
        assertThat(result.workflows().getFirst().rejects()).isTrue();
        assertThat(result.workflows().getLast().rejects()).isFalse();
        assertThat(result.failsT2()).isTrue();
    }

    @Test void equalityAndDecimalThresholdsBracketWithOnes() {
        List<FrbGates.WorkflowCases> five = List.of(w(FIRST, 1, 1, 1, 1, 1));
        List<FrbGates.WorkflowCases> four = List.of(w(FIRST, 1, 1, 1, 1));
        assertThat(FrbGates.evaluate(five, Rational.of(1, 32), Rational.of(1, 32), 1).failsT2()).isTrue();
        assertThat(FrbGates.evaluate(four, Rational.of(1, 32), Rational.of(1, 32), 1).failsT2()).isFalse();
        assertThat(FrbGates.evaluate(five, ALPHA, ALPHA, 1).failsT2()).isTrue();
        assertThat(FrbGates.evaluate(four, ALPHA, ALPHA, 1).failsT2()).isFalse();
        List<FrbGates.WorkflowCases> below = sixWithFirst(8);
        List<FrbGates.WorkflowCases> above = sixWithFirst(7);
        assertThat(FrbGates.evaluate(below, ALPHA, ALPHA, 6).workflows().getFirst().rejects()).isTrue();
        assertThat(FrbGates.evaluate(above, ALPHA, ALPHA, 6).workflows().getFirst().rejects()).isFalse();
    }

    @Test void declaredCountAndClassBRangeAreEnforced() {
        List<FrbGates.WorkflowCases> one = List.of(w(FIRST, 1));
        assertThatThrownBy(() -> FrbGates.evaluate(one, ALPHA, ALPHA, 2)).isInstanceOf(InvalidInput.class);
        List<FrbGates.WorkflowCases> bad = List.of(new FrbGates.WorkflowCases(FIRST, true, List.of(2)));
        assertThatThrownBy(() -> FrbGates.evaluate(bad, ALPHA, ALPHA, 1)).isInstanceOf(InvalidInput.class);
        List<FrbGates.WorkflowCases> empty = List.of();
        assertThatThrownBy(() -> FrbGates.evaluate(empty, ALPHA, ALPHA, 0)).isInstanceOf(InvalidInput.class);
    }

    @Test void allZeroWorkflowsNeverReject() {
        FrbGates.GateResult result = FrbGates.evaluate(List.of(w(FIRST, 0, 0, 0), w(SECOND, 0, 0)),
                ALPHA, ALPHA, 2);
        assertThat(result.routeTest().p()).isEqualTo(1);
        assertThat(result.failsT2()).isFalse();
    }

    private static FrbGates.WorkflowCases w(String name, int... differences) {
        return new FrbGates.WorkflowCases(name, false, java.util.Arrays.stream(differences).boxed().toList());
    }

    private static List<FrbGates.WorkflowCases> sixWithFirst(int positives) {
        List<FrbGates.WorkflowCases> workflows = new ArrayList<>();
        workflows.add(new FrbGates.WorkflowCases(FIRST, false, Collections.nCopies(positives, 1)));
        for (int i = 1; i < 6; i++) workflows.add(new FrbGates.WorkflowCases(SECOND + i, false,
                Collections.nCopies(positives, -1)));
        return workflows;
    }
}
