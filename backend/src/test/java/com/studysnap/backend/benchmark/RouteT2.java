package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.List;

/** One route-level FRB decision, including the interim missing-evidence rule. */
final class RouteT2 {
    private RouteT2() {}

    record WorkflowInput(String workflow, boolean classB, List<Integer> knownPrimaryDifferences,
                         List<ClassBReserve.PairCritical> primaryPairsOrNull,
                         List<Tri.Unresolved> unresolved, boolean reserveWasDrawn,
                         List<Integer> reserveDifferencesOrNull) {}
    record RouteInput(int declaredK, Rational routeAlpha, Rational familyBudget,
                      List<WorkflowInput> workflows) {}

    static Tri decide(RouteInput input) {
        if (input == null || input.declaredK() <= 0 || input.workflows() == null
                || input.workflows().isEmpty() || input.workflows().size() != input.declaredK()
                || input.routeAlpha() == null || input.familyBudget() == null)
            throw new InvalidInput("Invalid route declaration");
        Rational level = input.familyBudget().dividedBy(input.declaredK());
        List<Integer> pooled = new ArrayList<>();
        List<Tri.Unresolved> pooledUnresolved = new ArrayList<>();
        boolean failed = false;
        boolean incomplete = false;
        for (WorkflowInput workflow : input.workflows()) {
            if (workflow == null || workflow.workflow() == null || workflow.workflow().isBlank()
                    || workflow.knownPrimaryDifferences() == null || workflow.unresolved() == null)
                throw new InvalidInput("Invalid workflow evidence");
            List<Integer> known = workflow.knownPrimaryDifferences();
            if (workflow.classB() && known.stream().anyMatch(d -> d == null || d < -1 || d > 1))
                throw new InvalidInput("Class-B difference out of range");
            for (Tri.Unresolved range : workflow.unresolved()) {
                if (range == null || (workflow.classB()
                        && (range.lowestDifference() < -1 || range.highestDifference() > 1)))
                    throw new InvalidInput("Invalid unresolved range");
            }
            pooled.addAll(known);
            pooledUnresolved.addAll(workflow.unresolved());
            Tri primary = Tri.evaluate(known, workflow.unresolved(), level);
            failed |= primary == Tri.FAIL;
            incomplete |= !workflow.unresolved().isEmpty();
            if (!workflow.classB()) {
                if (workflow.reserveWasDrawn() || workflow.reserveDifferencesOrNull() != null
                        || workflow.primaryPairsOrNull() != null)
                    throw new InvalidReserve("Class-A workflow has reserve or pair trigger");
                continue;
            }
            if (workflow.primaryPairsOrNull() == null || workflow.primaryPairsOrNull().size() != known.size())
                throw new InvalidInput("Class-B primary pairs differ from known cases");
            if (!workflow.unresolved().isEmpty()) {
                if (workflow.reserveWasDrawn() || workflow.reserveDifferencesOrNull() != null)
                    throw new InvalidReserve("Reserve with unresolved primary evidence");
                validatePairs(known, workflow.primaryPairsOrNull());
                continue;
            }
            failed |= ClassBReserve.workflow(known, workflow.primaryPairsOrNull(),
                    workflow.reserveWasDrawn(), workflow.reserveDifferencesOrNull(), 30, level)
                    == ClassBReserve.Outcome.FAIL;
        }
        failed |= Tri.evaluate(pooled, pooledUnresolved, input.routeAlpha()) == Tri.FAIL;
        if (failed) return Tri.FAIL;
        return incomplete ? Tri.INCONCLUSIVE : Tri.PASS_COMPLETE;
    }

    private static void validatePairs(List<Integer> differences, List<ClassBReserve.PairCritical> pairs) {
        for (int i = 0; i < differences.size(); i++) {
            ClassBReserve.PairCritical pair = pairs.get(i);
            if (pair == null || Math.abs(pair.criticalA() - pair.criticalB()) != Math.abs(differences.get(i)))
                throw new InvalidInput("Class-B pair and difference disagree");
        }
    }
}
