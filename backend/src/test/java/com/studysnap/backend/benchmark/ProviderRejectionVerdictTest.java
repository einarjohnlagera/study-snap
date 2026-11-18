package com.studysnap.backend.benchmark;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderRejectionVerdictTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String CASE = WORKFLOW + "-0";
    private static final String HTTP_ERROR = "HTTP_ERROR";
    private static final String DELIVERED_CAUSE = "REJECTION_REPRODUCED_WITH_DELIVERED_RECOVERY";
    @TempDir Path temp;

    private static RouteVerdictCalculator.Recheck check(boolean delivered) {
        return new RouteVerdictCalculator.Recheck(delivered ? RouteVerdictCalculator.Evidence.MISSING
                : RouteVerdictCalculator.Evidence.SCORED, Set.of(), false,
                delivered ? null : HTTP_ERROR, 0, 0, true, "EQUIVALENT", delivered);
    }

    private static RouteVerdictCalculator.ArmResult failedBaseline(List<RouteVerdictCalculator.Recheck> checks) {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.UNSCORABLE,
                Set.of(), false, HTTP_ERROR, checks, true, true, 0, 0);
    }

    private static RouteVerdictCalculator.ArmResult clean() {
        return new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, null, List.of(), false, false, 0, 0);
    }

    @SuppressWarnings("unchecked")
    private RouteVerdictCalculator.RouteReport route(RouteVerdictCalculator.ArmResult baseline) throws Exception {
        T1MixedOutcomesTest fixture = new T1MixedOutcomesTest();
        Field directory = T1MixedOutcomesTest.class.getDeclaredField("temp");
        directory.setAccessible(true);
        directory.set(fixture, temp);
        Method parametersMethod = T1MixedOutcomesTest.class.getDeclaredMethod("parameters", boolean.class);
        parametersMethod.setAccessible(true);
        FrozenGateParameters parameters = (FrozenGateParameters) parametersMethod.invoke(fixture, true);
        Method casesMethod = T1MixedOutcomesTest.class.getDeclaredMethod("cases",
                FrozenGateParameters.class, String.class);
        casesMethod.setAccessible(true);
        List<RouteVerdictCalculator.CaseRecord> records = (List<RouteVerdictCalculator.CaseRecord>)
                casesMethod.invoke(null, parameters, ROUTE);
        Method set = T1MixedOutcomesTest.class.getDeclaredMethod("set", List.class, String.class,
                RouteVerdictCalculator.ArmResult.class, RouteVerdictCalculator.ArmResult.class);
        set.setAccessible(true);
        set.invoke(null, records, CASE, baseline, clean());
        Method run = T1MixedOutcomesTest.class.getDeclaredMethod("run", FrozenGateParameters.class,
                String.class, List.class, Map.class);
        run.setAccessible(true);
        return (RouteVerdictCalculator.RouteReport) run.invoke(null, parameters, ROUTE, records, Map.of());
    }

    @Test void reproducedBaselineRejectionWithOrWithoutDeliveryIsNeverCandidateCharge() throws Exception {
        var noDelivery = route(failedBaseline(List.of(check(false), check(false))));
        assertThat(noDelivery.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(noDelivery.workflows().get(WORKFLOW).candidateOnlyT1()).isZero();
        var delivered = route(failedBaseline(List.of(check(false), check(true))));
        assertThat(delivered.verdict()).isEqualTo(RouteVerdictCalculator.Verdict.INCONCLUSIVE);
        assertThat(delivered.workflows().get(WORKFLOW).causes()).contains(DELIVERED_CAUSE);
        assertThat(delivered.workflows().get(WORKFLOW).candidateOnlyT1()).isZero();
        assertThat(delivered.workflows().get(WORKFLOW).candidateOnlyT3()).isZero();
    }

    @Test void baselineReproductionRemainsIncompleteAtTheArmResolution() throws Exception {
        Method resolve = RouteVerdictCalculator.class.getDeclaredMethod("resolveRejection",
                RouteVerdictCalculator.ArmResult.class, int.class, boolean.class);
        resolve.setAccessible(true);
        Method incomplete = Class.forName(RouteVerdictCalculator.class.getName() + "$ArmResolution")
                .getDeclaredMethod("incomplete");
        incomplete.setAccessible(true);
        Object failed = resolve.invoke(null, failedBaseline(List.of(check(false), check(false))), 2, false);
        Object delivered = resolve.invoke(null, failedBaseline(List.of(check(false), check(true))), 2, false);
        assertThat(incomplete.invoke(failed)).isEqualTo(true);
        assertThat(incomplete.invoke(delivered)).isEqualTo(true);
    }

    @Test void recoveredBaselineOriginalIsReportedWithoutCandidateCharge() throws Exception {
        var recovered = new RouteVerdictCalculator.ArmResult(RouteVerdictCalculator.Evidence.SCORED,
                Set.of(), false, null, List.of(), true, false, 0, 0);
        var report = route(recovered);
        assertThat(report.workflows().get(WORKFLOW).candidateOnlyT1()).isZero();
        assertThat(report.workflows().get(WORKFLOW).candidateOnlyT3()).isZero();
        assertThat(report.workflows().get(WORKFLOW).rejectionEvidenceByCaseId())
                .containsEntry(CASE, "baseline:REJECTED_THEN_RETRY_SUCCEEDED");
    }
}
