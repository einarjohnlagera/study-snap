package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** One route-level FRB decision, including the interim missing-evidence rule. */
final class RouteT2 {
    private RouteT2() {}

    record WorkflowInput(String workflow, boolean classB, List<Integer> knownPrimaryDifferences,
                         List<ClassBReserve.PairCritical> primaryPairsOrNull,
                         List<Tri.Unresolved> unresolved, boolean reserveWasDrawn,
                         List<Integer> reserveDifferencesOrNull,
                         List<Tri.Unresolved> reserveUnresolved) {
        WorkflowInput(String workflow, boolean classB, List<Integer> knownPrimaryDifferences,
                      List<ClassBReserve.PairCritical> primaryPairsOrNull,
                      List<Tri.Unresolved> unresolved, boolean reserveWasDrawn,
                      List<Integer> reserveDifferencesOrNull) {
            this(workflow, classB, knownPrimaryDifferences, primaryPairsOrNull,
                    unresolved, reserveWasDrawn, reserveDifferencesOrNull, List.of());
        }
    }
    record RouteInput(int declaredK, Rational routeAlpha, Rational familyBudget,
                      List<WorkflowInput> workflows, int declaredReserveSize) {
        RouteInput(int declaredK, Rational routeAlpha, Rational familyBudget, List<WorkflowInput> workflows) {
            this(declaredK, routeAlpha, familyBudget, workflows, 30);
        }
    }

    record WorkflowDecision(Tri primaryGate, Tri reserveGate) {
        Tri workflowGate() {
            if (primaryGate == Tri.FAIL || reserveGate == Tri.FAIL) return Tri.FAIL;
            if (primaryGate == Tri.INCONCLUSIVE || reserveGate == Tri.INCONCLUSIVE) return Tri.INCONCLUSIVE;
            return Tri.PASS_COMPLETE;
        }
    }
    record DetailedDecision(Tri routeGate, Map<String, WorkflowDecision> workflows) {
        Tri overall() {
            if (routeGate == Tri.FAIL || workflows.values().stream()
                    .anyMatch(gate -> gate.workflowGate() == Tri.FAIL)) return Tri.FAIL;
            if (routeGate == Tri.INCONCLUSIVE || workflows.values().stream()
                    .anyMatch(gate -> gate.workflowGate() == Tri.INCONCLUSIVE)) return Tri.INCONCLUSIVE;
            return Tri.PASS_COMPLETE;
        }
    }

    static Tri decide(RouteInput input) {
        return decideDetailed(input).overall();
    }

    static DetailedDecision decideDetailed(RouteInput input) {
        if (input == null || input.declaredK() <= 0 || input.workflows() == null
                || input.workflows().isEmpty() || input.workflows().size() != input.declaredK()
                || input.routeAlpha() == null || input.familyBudget() == null
                || input.declaredReserveSize() <= 0)
            throw new InvalidInput("Invalid route declaration");
        Rational level = input.familyBudget().dividedBy(input.declaredK());
        List<Integer> pooled = new ArrayList<>();
        List<Tri.Unresolved> pooledUnresolved = new ArrayList<>();
        Map<String, WorkflowDecision> gates = new LinkedHashMap<>();
        for (WorkflowInput workflow : input.workflows()) {
            if (workflow == null || workflow.workflow() == null || workflow.workflow().isBlank()
                    || workflow.knownPrimaryDifferences() == null || workflow.unresolved() == null
                    || workflow.reserveUnresolved() == null)
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
            Tri reserveGate = null;
            if (!workflow.classB()) {
                if (workflow.reserveWasDrawn() || workflow.reserveDifferencesOrNull() != null
                        || workflow.primaryPairsOrNull() != null || !workflow.reserveUnresolved().isEmpty())
                    throw new InvalidReserve("Class-A workflow has reserve or pair trigger");
                gates.put(workflow.workflow(), new WorkflowDecision(primary, null));
                continue;
            }
            if (workflow.primaryPairsOrNull() == null || workflow.primaryPairsOrNull().size() != known.size())
                throw new InvalidInput("Class-B primary pairs differ from known cases");
            if (!workflow.unresolved().isEmpty()) {
                if (workflow.reserveWasDrawn() || workflow.reserveDifferencesOrNull() != null
                        || !workflow.reserveUnresolved().isEmpty())
                    throw new InvalidReserve("Reserve with unresolved primary evidence");
                validatePairs(known, workflow.primaryPairsOrNull());
                gates.put(workflow.workflow(), new WorkflowDecision(primary, null));
                continue;
            }
            if (workflow.reserveUnresolved().isEmpty()) {
                ClassBReserve.workflow(known, workflow.primaryPairsOrNull(),
                        workflow.reserveWasDrawn(), workflow.reserveDifferencesOrNull(),
                        input.declaredReserveSize(), level);
                if (workflow.reserveWasDrawn() && workflow.reserveDifferencesOrNull() != null
                        && workflow.reserveDifferencesOrNull().size() == input.declaredReserveSize()
                        && workflow.reserveDifferencesOrNull().stream()
                            .allMatch(difference -> difference != null && difference >= -1 && difference <= 1)) {
                    List<Integer> combined = new ArrayList<>(known);
                    combined.addAll(workflow.reserveDifferencesOrNull());
                    reserveGate = Tri.evaluate(combined, List.of(), level);
                }
            } else {
                List<Integer> reserveKnown = workflow.reserveDifferencesOrNull();
                if (reserveKnown == null || !workflow.reserveWasDrawn()
                        || reserveKnown.size() + workflow.reserveUnresolved().size() != input.declaredReserveSize())
                    throw new InvalidReserve("Reserve size differs from declaration");
                List<Integer> padded = new ArrayList<>(reserveKnown);
                for (int i = 0; i < workflow.reserveUnresolved().size(); i++) padded.add(0);
                ClassBReserve.workflow(known, workflow.primaryPairsOrNull(), true, padded,
                        input.declaredReserveSize(), level);
                List<Integer> combined = new ArrayList<>(known);
                combined.addAll(reserveKnown);
                reserveGate = Tri.evaluate(combined, workflow.reserveUnresolved(), level);
            }
            gates.put(workflow.workflow(), new WorkflowDecision(primary, reserveGate));
        }
        return new DetailedDecision(Tri.evaluate(pooled, pooledUnresolved, input.routeAlpha()),
                Map.copyOf(gates));
    }

    private static void validatePairs(List<Integer> differences, List<ClassBReserve.PairCritical> pairs) {
        for (int i = 0; i < differences.size(); i++) {
            ClassBReserve.PairCritical pair = pairs.get(i);
            if (pair == null || Math.abs(pair.criticalA() - pair.criticalB()) != Math.abs(differences.get(i)))
                throw new InvalidInput("Class-B pair and difference disagree");
        }
    }
}
