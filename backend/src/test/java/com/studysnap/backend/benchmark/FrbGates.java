package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.List;

/** Primary-only route and workflow FRB gates. */
final class FrbGates {
    private FrbGates() {}

    record WorkflowCases(String workflow, boolean classB, List<Integer> primaryDifferences) {}
    record WorkflowGate(String workflow, boolean classB, ExactPairedTest.Result test, boolean rejects) {}
    record GateResult(ExactPairedTest.Result routeTest, boolean routeRejects,
                      List<WorkflowGate> workflows, boolean failsT2) {}

    static GateResult evaluate(List<WorkflowCases> workflows, Rational routeAlpha,
                               Rational familyBudget, int declaredK) {
        if (workflows == null || declaredK <= 0 || workflows.isEmpty() || workflows.size() != declaredK
                || routeAlpha == null || familyBudget == null)
            throw new InvalidInput("Invalid route workflow declaration");
        Rational level = familyBudget.dividedBy(declaredK);
        List<Integer> pooled = new ArrayList<>();
        List<WorkflowGate> gates = new ArrayList<>();
        boolean anyWorkflowRejects = false;
        for (WorkflowCases workflow : workflows) {
            if (workflow == null || workflow.workflow() == null || workflow.workflow().isBlank()
                    || workflow.primaryDifferences() == null)
                throw new InvalidInput("Invalid workflow");
            if (workflow.classB() && workflow.primaryDifferences().stream()
                    .anyMatch(d -> d == null || d < -1 || d > 1))
                throw new InvalidInput("Class-B difference out of range");
            ExactPairedTest.Result test = ExactPairedTest.test(workflow.primaryDifferences());
            boolean rejects = test.atMost(level);
            gates.add(new WorkflowGate(workflow.workflow(), workflow.classB(), test, rejects));
            anyWorkflowRejects |= rejects;
            pooled.addAll(workflow.primaryDifferences());
        }
        ExactPairedTest.Result route = ExactPairedTest.test(pooled);
        boolean routeRejects = route.atMost(routeAlpha);
        return new GateResult(route, routeRejects, List.copyOf(gates), routeRejects || anyWorkflowRejects);
    }
}
