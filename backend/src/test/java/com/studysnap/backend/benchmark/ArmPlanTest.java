package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sections 3 and 5 route-baseline and Sol-cap mutation guards. */
class ArmPlanTest {
    private static final String MINI = "gpt-4.1-mini";
    private static final String FOUR_ONE = "gpt-4.1";
    private static final String LUNA = "gpt-6-luna";
    private static final String LUNA_ARM = "LUNA";
    private static final String SOL = "gpt-6.1-sol";
    private static final String BASELINE_MINI = "BASELINE_MINI";
    private static final String BASELINE_41 = "BASELINE_41";
    private static final String PAID = "PAID_INTERACTIVE";
    private static final String INTERACTIVE = "INTERACTIVE_QUIZ";
    private static final String CRITIQUE = "CRITIQUE";
    private static final String SOL_PAID = "SOL_PAID_INTERACTIVE";
    @TempDir Path temp;

    private FrozenGateParameters parameters() throws Exception {
        byte[] bytes = Files.readAllBytes(GateTestFixture.SOURCE);
        Path file = temp.resolve("gates.json");
        Files.write(file, bytes);
        Files.writeString(temp.resolve("gates.json.sha256"), FreezeRecord.sha256(bytes));
        return FrozenGateParameters.load(file);
    }

    private static FreezeRecord freeze(FrozenGateParameters parameters, Map<String, FreezeRecord.Arm> overrides) {
        List<String> mini = new ArrayList<>(parameters.routes.get(INTERACTIVE).workflows());
        mini.addAll(parameters.routes.get(CRITIQUE).workflows());
        Map<String, FreezeRecord.Arm> arms = new HashMap<>(Map.of(
                BASELINE_MINI, new FreezeRecord.Arm(MINI, mini),
                BASELINE_41, new FreezeRecord.Arm(FOUR_ONE, parameters.routes.get(PAID).workflows()),
                LUNA_ARM, new FreezeRecord.Arm(LUNA, new ArrayList<>(parameters.workflows.keySet())),
                SOL_PAID, new FreezeRecord.Arm(SOL, parameters.routes.get(PAID).workflows())));
        arms.putAll(overrides);
        return new FreezeRecord("a".repeat(40), "0".repeat(64), parameters.sha256,
                Map.of(), Map.of(), arms,
                Map.of(INTERACTIVE, MINI, PAID, FOUR_ONE, CRITIQUE, MINI),
                LocalDate.of(2026, 11, 14));
    }

    @Test void baselineArmsCoverTheirProductionRoutes() throws Exception {
        FrozenGateParameters parameters = parameters();
        ArmPlan plan = new ArmPlan(freeze(parameters, Map.of()), parameters);
        StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        assertThat(plan.workflows(BASELINE_MINI, MINI, false, ledger)).hasSize(8);
        assertThat(plan.workflows(BASELINE_41, FOUR_ONE, false, ledger)).hasSize(2);
        assertThat(plan.workflows(LUNA_ARM, LUNA, false, ledger)).hasSize(10);
    }

    @Test void interviewAndCollectionOnMiniAreRefusedWithNamedRoute() throws Exception {
        FrozenGateParameters parameters = parameters();
        for (String workflow : parameters.routes.get(PAID).workflows()) {
            List<String> wrong = new ArrayList<>(parameters.routes.get(INTERACTIVE).workflows());
            wrong.addAll(parameters.routes.get(CRITIQUE).workflows());
            wrong.add(workflow);
            ArmPlan plan = new ArmPlan(freeze(parameters,
                    Map.of(BASELINE_MINI, new FreezeRecord.Arm(MINI, wrong))), parameters);
            StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
            assertThatThrownBy(() -> plan.workflows(BASELINE_MINI, MINI, false, ledger))
                    .isInstanceOf(ArmPlan.WrongBaseline.class)
                    .hasMessageContaining(workflow).hasMessageContaining(PAID).hasMessageContaining(FOUR_ONE);
        }
    }

    @Test void interactiveAndCritiqueOnFourOneAreRefused() throws Exception {
        FrozenGateParameters parameters = parameters();
        for (String route : List.of(INTERACTIVE, CRITIQUE)) {
            String workflow = parameters.routes.get(route).workflows().getFirst();
            List<String> wrong = new ArrayList<>(parameters.routes.get(PAID).workflows());
            wrong.add(workflow);
            ArmPlan plan = new ArmPlan(freeze(parameters,
                    Map.of(BASELINE_41, new FreezeRecord.Arm(FOUR_ONE, wrong))), parameters);
            StageCapLedger ledger = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
            assertThatThrownBy(() -> plan.workflows(BASELINE_41, FOUR_ONE, false, ledger))
                    .isInstanceOf(ArmPlan.WrongBaseline.class)
                    .hasMessageContaining(workflow).hasMessageContaining(route).hasMessageContaining(MINI);
        }
    }

    @Test void solRequiresExplicitSelectionAndStageThreeCap() throws Exception {
        FrozenGateParameters parameters = parameters();
        ArmPlan plan = new ArmPlan(freeze(parameters, Map.of()), parameters);
        StageCapLedger noCap = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE));
        assertThatThrownBy(() -> plan.workflows(SOL_PAID, SOL, true, noCap))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessageContaining("STAGE_3");
        StageCapLedger approved = new StageCapLedger(Map.of(StageCapLedger.Stage.STAGE_3, BigDecimal.ONE));
        assertThatThrownBy(() -> plan.workflows(SOL_PAID, SOL, false, approved))
                .isInstanceOf(StageCapLedger.SpendStop.class).hasMessageContaining("explicit selection");
        assertThat(plan.workflows(SOL_PAID, SOL, true, approved)).hasSize(2);
    }
}
