package com.studysnap.backend.benchmark;

import java.util.List;
import java.util.Set;

/** Sections 3 and 5 route-correct baseline and explicitly selected candidate arms. */
final class ArmPlan {
    private static final String LUNA = "LUNA";
    private static final String BASELINE_MINI = "BASELINE_MINI";
    private static final String BASELINE_41 = "BASELINE_41";
    private static final String UNKNOWN_ROUTE = "Unknown route ";
    static final class WrongBaseline extends RuntimeException {
        WrongBaseline(String message) { super(message); }
    }
    private final FreezeRecord record;
    private final FrozenGateParameters parameters;

    ArmPlan(FreezeRecord record, FrozenGateParameters parameters) {
        this.record = record;
        this.parameters = parameters;
        if (!record.arms().keySet().containsAll(Set.of(BASELINE_MINI, BASELINE_41, LUNA))
                || !parameters.routes.keySet().equals(record.baselineModelsByRoute().keySet()))
            throw new FreezeRecord.InvalidFreeze("Freeze arm table or route baselines are incomplete");
    }

    List<String> workflows(String armName, String model, boolean explicitlySelected,
                           StageCapLedger ledger) {
        FreezeRecord.Arm arm = record.arms().get(armName);
        if (arm == null || !arm.model().equals(model))
            throw new FreezeRecord.InvalidFreeze("Unknown arm or model: " + armName + "/" + model);
        if (!armName.equals(LUNA) && !armName.equals(BASELINE_MINI)
                && !armName.equals(BASELINE_41) && !armName.startsWith("SOL_"))
            throw new FreezeRecord.InvalidFreeze("Unknown Stage 1/3 arm " + armName);
        if (armName.equals(LUNA) && !Set.copyOf(arm.workflows()).equals(parameters.workflows.keySet()))
            throw new FreezeRecord.InvalidFreeze("LUNA must cover all frozen workflows");
        if (armName.startsWith("SOL")) {
            if (!explicitlySelected || ledger.cap(StageCapLedger.Stage.STAGE_3) == null)
                throw new StageCapLedger.SpendStop("SOL requires explicit selection and approved STAGE_3 cap");
            String route = null;
            for (String workflow : arm.workflows()) {
                String next = route(workflow);
                if (route != null && !route.equals(next))
                    throw new FreezeRecord.InvalidFreeze("SOL selection must cover one route");
                route = next;
            }
            if (route == null || !arm.workflows().containsAll(parameters.routes.get(route).workflows()))
                throw new FreezeRecord.InvalidFreeze("SOL must cover the selected whole route");
        }
        if (armName.startsWith("BASELINE")) {
            for (String workflow : arm.workflows()) {
                String route = route(workflow);
                String required = record.baselineModelsByRoute().get(route);
                if (required == null) throw new FreezeRecord.InvalidFreeze(UNKNOWN_ROUTE + route);
                if (!required.equals(model))
                    throw new WrongBaseline(workflow + " route " + route + " arm model " + model
                            + " required model " + required);
            }
            Set<String> expected = parameters.workflows.keySet().stream()
                    .filter(workflow -> {
                        String route = route(workflow);
                        String baseline = record.baselineModelsByRoute().get(route);
                        if (baseline == null) throw new FreezeRecord.InvalidFreeze(UNKNOWN_ROUTE + route);
                        return baseline.equals(model);
                    }).collect(java.util.stream.Collectors.toSet());
            if (!expected.equals(Set.copyOf(arm.workflows())))
                throw new FreezeRecord.InvalidFreeze("Baseline arm omits or adds frozen workflows: " + armName);
        }
        return List.copyOf(arm.workflows());
    }

    String route(String workflow) {
        FrozenGateParameters.Workflow row = parameters.workflows.get(workflow);
        if (row == null) throw new FreezeRecord.InvalidFreeze("Unknown workflow " + workflow);
        return row.route();
    }
}
