package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteT2Test {
    private static final Rational ALPHA = Rational.of(4, 100);
    private static final String FIRST = "first";
    private static final String SECOND = "second";

    @Test void allPassAndPooledOnlyFailure() {
        assertThat(decide(List.of(classA(FIRST, List.of(1), List.of()),
                classA(SECOND, List.of(-1), List.of())))).isEqualTo(Tri.PASS_COMPLETE);
        assertThat(decide(List.of(classA(FIRST, Collections.nCopies(4, 1), List.of()),
                classA(SECOND, Collections.nCopies(4, 1), List.of())))).isEqualTo(Tri.FAIL);
    }

    @Test void classAWorkflowCanFailWithoutPooledFailure() {
        assertThat(decide(List.of(classA(FIRST, Collections.nCopies(6, 1), List.of()),
                classA(SECOND, Collections.nCopies(6, -1), List.of())))).isEqualTo(Tri.FAIL);
    }

    @Test void classBPrimaryAndReserveCanEachCauseFailure() {
        RouteT2.WorkflowInput primary = classB(FIRST, Collections.nCopies(6, 1), null);
        assertThat(decide(List.of(primary, classA(SECOND, Collections.nCopies(6, -1), List.of()))))
                .isEqualTo(Tri.FAIL);
        RouteT2.WorkflowInput reserve = classB(FIRST, List.of(1),
                concat(Collections.nCopies(6, 1), Collections.nCopies(24, 0)));
        assertThat(decide(List.of(reserve, classA(SECOND, Collections.nCopies(12, -1), List.of()))))
                .isEqualTo(Tri.FAIL);
    }

    @Test void reserveNeverEntersPooledRouteTest() {
        RouteT2.WorkflowInput reserve = classB(FIRST, List.of(1),
                concat(Collections.nCopies(4, 1), Collections.nCopies(26, 0)));
        assertThat(decide(List.of(reserve, classA(SECOND, List.of(0), List.of()))))
                .isEqualTo(Tri.PASS_COMPLETE);
    }

    @Test void missingEvidenceIsInconclusiveUnlessARealGateDefinitelyFails() {
        RouteT2.WorkflowInput unresolved = classA(FIRST, List.of(0), List.of(new Tri.Unresolved(-1, 1)));
        assertThat(decide(List.of(unresolved, classA(SECOND, List.of(0), List.of()))))
                .isEqualTo(Tri.INCONCLUSIVE);
        assertThat(decide(List.of(unresolved, classA(SECOND, Collections.nCopies(6, 1), List.of()))))
                .isEqualTo(Tri.FAIL);
    }

    @Test void unresolvedClassBDrawsNoReserveAndRejectsRecordedReserve() {
        RouteT2.WorkflowInput missing = new RouteT2.WorkflowInput(FIRST, true, List.of(0),
                List.of(new ClassBReserve.PairCritical(0, 0)), List.of(new Tri.Unresolved(-1, 1)),
                true, Collections.nCopies(30, 0));
        RouteT2.RouteInput input = route(List.of(missing, classA(SECOND, List.of(), List.of())));
        assertThat(RouteT2.decideDetailed(input).workflows().get(FIRST).workflowGate())
                .isEqualTo(Tri.INCONCLUSIVE);
        RouteT2.WorkflowInput permitted = new RouteT2.WorkflowInput(FIRST, true, List.of(0),
                List.of(new ClassBReserve.PairCritical(0, 0)), List.of(new Tri.Unresolved(-1, 1)), false, null);
        assertThat(decide(List.of(permitted, classA(SECOND, List.of(), List.of()))))
                .isEqualTo(Tri.INCONCLUSIVE);
    }

    @Test void misSizedWorkflowDeclarationAndClassBDifferenceAreRejected() {
        RouteT2.RouteInput wrongCount = new RouteT2.RouteInput(3, ALPHA, ALPHA,
                List.of(classA(FIRST, List.of(), List.of())));
        assertThatThrownBy(() -> RouteT2.decide(wrongCount)).isInstanceOf(InvalidInput.class);
        RouteT2.WorkflowInput invalidB = classB(FIRST, List.of(2), null);
        RouteT2.RouteInput invalid = new RouteT2.RouteInput(1, ALPHA, ALPHA, List.of(invalidB));
        assertThatThrownBy(() -> RouteT2.decide(invalid)).isInstanceOf(InvalidInput.class);
    }

    private static Tri decide(List<RouteT2.WorkflowInput> workflows) {
        return RouteT2.decide(route(workflows));
    }

    private static RouteT2.RouteInput route(List<RouteT2.WorkflowInput> workflows) {
        return new RouteT2.RouteInput(workflows.size(), ALPHA, ALPHA, workflows);
    }

    private static RouteT2.WorkflowInput classA(String name, List<Integer> differences,
                                                 List<Tri.Unresolved> unresolved) {
        return new RouteT2.WorkflowInput(name, false, differences, null, unresolved, false, null);
    }

    private static RouteT2.WorkflowInput classB(String name, List<Integer> differences,
                                                 List<Integer> reserve) {
        List<ClassBReserve.PairCritical> pairs = differences.stream().map(d -> switch (d) {
            case 1 -> new ClassBReserve.PairCritical(1, 0);
            case -1 -> new ClassBReserve.PairCritical(0, 1);
            default -> new ClassBReserve.PairCritical(0, 0);
        }).toList();
        return new RouteT2.WorkflowInput(name, true, differences, pairs, List.of(),
                differences.stream().anyMatch(d -> d != 0), reserve);
    }

    private static List<Integer> concat(List<Integer> first, List<Integer> second) {
        List<Integer> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
