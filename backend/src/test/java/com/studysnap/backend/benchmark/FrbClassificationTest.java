package com.studysnap.backend.benchmark;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Mutation targets for the Option II allow-list and verdict-first FRB classification. */
class FrbClassificationTest {
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String CASE = "case-1";
    private static final Map<String, String> HEADS = Map.of("safety", "0:head");
    private static final RouteVerdictCalculator.Tier T1 = RouteVerdictCalculator.Tier.T1;
    private static final RouteVerdictCalculator.Tier T2 = RouteVerdictCalculator.Tier.T2;
    private static final RouteVerdictCalculator.Tier T3 = RouteVerdictCalculator.Tier.T3;
    private static final RouteVerdictCalculator.Verdict PASS = RouteVerdictCalculator.Verdict.PASS;
    private static final RouteVerdictCalculator.Verdict FAIL = RouteVerdictCalculator.Verdict.FAIL;
    private static final RouteVerdictCalculator.Verdict INCONCLUSIVE = RouteVerdictCalculator.Verdict.INCONCLUSIVE;
    private static final DeadlineRule.Classification OWNER = DeadlineRule.Classification.RETURN_TO_OWNER;
    private static final DeadlineRule.Classification FALLBACK = DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE;

    private static FrbClassificationInput input() {
        return new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of(), Map.of(WORKFLOW, "NOT_TRIGGERED"), HEADS);
    }

    private static RouteVerdictCalculator.WorkflowReport workflow(
            RouteVerdictCalculator.Verdict verdict, Set<RouteVerdictCalculator.Tier> failed,
            Set<RouteVerdictCalculator.Tier> inconclusive, List<String> causes,
            Map<String, SortedSet<String>> unresolvedIds, Set<String> veto,
            Set<String> undisposed,
            Map<RouteVerdictCalculator.DispositionValue, Set<String>> dispositions) {
        return new RouteVerdictCalculator.WorkflowReport(verdict, failed, inconclusive,
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false,
                Map.of(), Map.of(), Map.of(), "sha", 0, undisposed, null, causes,
                null, null, unresolvedIds, veto, dispositions,
                veto != null && !veto.isEmpty() || !undisposed.isEmpty() ? true : null);
    }

    private static RouteVerdictCalculator.RouteReport route(
            RouteVerdictCalculator.Verdict verdict, Set<RouteVerdictCalculator.Tier> failed,
            Set<RouteVerdictCalculator.Tier> inconclusive, List<String> causes,
            Map<String, SortedSet<String>> unresolvedIds, Set<String> veto,
            Set<String> undisposed,
            Map<RouteVerdictCalculator.DispositionValue, Set<String>> dispositions) {
        return new RouteVerdictCalculator.RouteReport(verdict, failed, inconclusive, 0,
                false, "sha", Map.of(WORKFLOW, workflow(verdict, failed, inconclusive,
                        causes, unresolvedIds, veto, undisposed, dispositions)));
    }

    private static RouteVerdictCalculator.RouteReport clean() {
        return route(PASS, Set.of(), Set.of(), null, null, null, Set.of(), null);
    }

    private static FrbClassification classify(String cause) {
        return DeadlineRule.classifyFrb(route(INCONCLUSIVE, Set.of(), Set.of(T1),
                List.of(cause), null, null, Set.of(), null), input());
    }

    @ParameterizedTest
    @ValueSource(strings = {"T1", "T2", "OWNER_VETO", "UNDISPOSED_CANDIDATE_CRITICAL",
            "OPEN_SAFETY_FINDING", "OPEN_EVIDENCE_DISPUTE", "DISPUTE_ACCEPTED_FOR_OWNER_DECISION",
            "REVIEW_RECORD_MISSING", "INCONCLUSIVE_SCORER_UNRELIABLE",
            "INCONCLUSIVE_WORKLOAD_EXHAUSTED", "INCONCLUSIVE_UNRESOLVED_FINDING",
            "HUMAN_ADJUDICATION_INCOMPLETE", "UNMETERED", "UNSCORABLE", "INCOMPLETE_RECHECK",
            "RECHECK_MIXED", "RECHECK_UNRESOLVED", "DRAWN_RESERVE_WITH_UNRESOLVED_PRIMARY",
            "RESERVE_RECEIPT_GAP", "RESERVE_NOT_SCORED", "RESERVE_EVIDENCE_UNRESOLVED",
            "DELIVERED_WITH_PROVIDER_REJECTION_UNRECHECKED",
            "REJECTION_REPRODUCED_WITH_DELIVERED_RECOVERY", "REJECTION_REPRODUCED_THEN_RETRIED",
            "HARNESS_REGRESSION", "CONFIRMED_T1", "MISSING", "LEDGER_INTEGRITY_FAILURE",
            "LEDGER_NOT_SUPPLIED", "SUITE_NOT_EXECUTED", "UNATTRIBUTED_INCONCLUSIVE",
            "EXPANSION_OUTCOME_NOT_SUPPLIED", "INCOMPLETE_DOUBLE_MISS_EXPANSION",
            "PENDING_REVIEW_ITEMS", "SHORT_DELIVERY_CANDIDATE", "UNKNOWN_CAUSE"})
    void everyOwnerCauseAndUnknownCauseFailClosed(String cause) {
        assertThat(CauseTable.known(cause)).isEqualTo(!"UNKNOWN_CAUSE".equals(cause));
        assertThat(CauseTable.kind(cause)).isEqualTo(CauseTable.Kind.RETURN_TO_OWNER);
        assertThat(classify(cause).classification()).isEqualTo(OWNER);
    }

    @Test void tablePinsOnlyExplicitFallbackRows() {
        assertThat(CauseTable.known("T3_ONLY")).isTrue();
        assertThat(CauseTable.known("MISSING_RUN_DATA")).isTrue();
        assertThat(CauseTable.known("SUBGROUP_SHORTFALL")).isTrue();
        assertThat(CauseTable.kind("T3_ONLY")).isEqualTo(CauseTable.Kind.FALLBACK);
        assertThat(CauseTable.kind("MISSING_RUN_DATA"))
                .isEqualTo(CauseTable.Kind.CONDITIONAL_FALLBACK);
        assertThat(CauseTable.kind("SUBGROUP_SHORTFALL"))
                .isEqualTo(CauseTable.Kind.CONDITIONAL_FALLBACK);
    }

    @Test void statisticalFailuresAndUndisposedCriticalReturnToOwner() {
        for (var tier : List.of(T1, T2)) {
            var report = route(FAIL, Set.of(tier), Set.of(), null, null, null, Set.of(), null);
            assertThat(DeadlineRule.classifyFrb(report, input()).classification()).isEqualTo(OWNER);
        }
        var undisposed = route(INCONCLUSIVE, Set.of(), Set.of(T2), null, null,
                null, Set.of(CASE), null);
        assertThat(DeadlineRule.classifyFrb(undisposed, input()).causes())
                .contains("UNDISPOSED_CANDIDATE_CRITICAL");
    }

    @Test void ownerInputsOverrideAStatisticalPass() {
        var openSafety = new FrbClassificationInput(Set.of("finding"), Set.of(), Set.of(), Set.of(),
                Set.of(), false, false, Set.of(), Map.of(WORKFLOW, "NOT_TRIGGERED"), HEADS);
        assertThat(DeadlineRule.classifyFrb(clean(), openSafety).causes()).contains("OPEN_SAFETY_FINDING");
        var dispute = new FrbClassificationInput(Set.of(), Set.of("DISPUTE_ACCEPTED_FOR_OWNER_DECISION"),
                Set.of(), Set.of(), Set.of(), false, false, Set.of(),
                Map.of(WORKFLOW, "NOT_TRIGGERED"), HEADS);
        assertThat(DeadlineRule.classifyFrb(clean(), dispute).classification()).isEqualTo(OWNER);
        var veto = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of("resolved-veto-finding"), Map.of(WORKFLOW, "NOT_TRIGGERED"), HEADS);
        assertThat(DeadlineRule.classifyFrb(clean(), veto).causes()).contains("OWNER_VETO");
        var missingReview = new FrbClassificationInput(Set.of(), Set.of(), Set.of("class-b"),
                Set.of(), Set.of(), false, false, Set.of(), Map.of(WORKFLOW, "NOT_TRIGGERED"), HEADS);
        assertThat(DeadlineRule.classifyFrb(clean(), missingReview).classification()).isEqualTo(OWNER);
        var ledger = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), true, false, Set.of(), Map.of(WORKFLOW, "NOT_TRIGGERED"), HEADS);
        assertThat(DeadlineRule.classifyFrb(clean(), ledger).causes()).contains("LEDGER_INTEGRITY_FAILURE");
    }

    @Test void conditionalFallbackRequiresEveryAffectedEstablishedCase() {
        for (String cause : List.of("MISSING_RUN_DATA", "SUBGROUP_SHORTFALL")) {
            var report = route(INCONCLUSIVE, Set.of(), Set.of(T1), List.of(cause),
                    Map.of(cause, new TreeSet<>(Set.of("one", "two"))), null, Set.of(), null);
            var partial = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of("one"),
                    false, false, Set.of(), Map.of(WORKFLOW, "NOT_TRIGGERED"), HEADS);
            var complete = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(),
                    Set.of("one", "two"), false, false, Set.of(),
                    Map.of(WORKFLOW, "NOT_TRIGGERED"), HEADS);
            assertThat(DeadlineRule.classifyFrb(report, input()).classification()).isEqualTo(OWNER);
            assertThat(DeadlineRule.classifyFrb(report, partial).classification()).isEqualTo(OWNER);
            assertThat(DeadlineRule.classifyFrb(report, complete).classification()).isEqualTo(FALLBACK);
        }
    }

    @Test void onlyCleanT3FailureFallsBack() {
        var t3 = route(FAIL, Set.of(T3), Set.of(), null, null, null, Set.of(), null);
        assertThat(DeadlineRule.classifyFrb(t3, input()).classification()).isEqualTo(FALLBACK);
        var unresolved = route(FAIL, Set.of(T3), Set.of(T1), List.of("UNMETERED"),
                null, null, Set.of(), null);
        assertThat(DeadlineRule.classifyFrb(unresolved, input()).classification()).isEqualTo(OWNER);
        var unattributed = route(INCONCLUSIVE, Set.of(), Set.of(T1), null,
                null, null, Set.of(), null);
        assertThat(DeadlineRule.classifyFrb(unattributed, input()).causes())
                .contains("UNATTRIBUTED_INCONCLUSIVE");
        assertThat(DeadlineRule.classifyFrb(unattributed, input()).classification()).isEqualTo(OWNER);
    }

    @Test void passListsDispositionedFindingAndFlagsBlockOnlyTheSwitch() {
        var dispositioned = route(PASS, Set.of(), Set.of(), null, null, null, Set.of(),
                Map.of(RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED, Set.of(CASE)));
        var result = DeadlineRule.classifyFrb(dispositioned, input());
        assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
        assertThat(result.causes()).contains("DISPOSITIONED_CANDIDATE_CRITICAL:" + CASE);
        assertThat(result.flags()).contains("REVIEWS_NOT_MACHINE_CHECKED");
        assertThat(result.blocksProductionSwitch()).isTrue();
        assertThat(ResultsPipeline.renderFrbClassification(result))
                .contains("BENCHMARK_PASS with 1 dispositioned findings")
                .contains("production switch blocked: REVIEWS_NOT_MACHINE_CHECKED");
        var cost = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of("Z_AUDIT", "COST_REVIEW"),
                Set.of(), false, true, Set.of(), Map.of(WORKFLOW, "NOT_TRIGGERED"), HEADS);
        var withCost = DeadlineRule.classifyFrb(clean(), cost);
        assertThat(withCost.classification()).isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
        assertThat(withCost.flags()).containsExactly("COST_REVIEW", "REVIEWS_NOT_MACHINE_CHECKED",
                "SOL_RECOMMENDED", "Z_AUDIT");
        assertThat(withCost.blocksProductionSwitch()).isTrue();
        assertThat(ResultsPipeline.renderFrbClassification(withCost))
                .contains("BENCHMARK_PASS (production switch blocked: COST_REVIEW");
    }

    @Test void missingExpansionAndOmittedRouteInputFailClosed() {
        var withoutExpansion = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), false, false, Set.of(), Map.of(), HEADS);
        assertThat(DeadlineRule.classifyFrb(clean(), withoutExpansion).causes())
                .contains("EXPANSION_OUTCOME_NOT_SUPPLIED");
        assertThat(DeadlineRule.classifyFrb(clean(), null).causes()).contains("LEDGER_NOT_SUPPLIED");
        assertThat(DeadlineRule.classifyFrb(clean(), null).flags())
                .contains("COST_REVIEW_UNDETERMINED");
        var emptyHeads = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), false, false, Set.of(), Map.of(WORKFLOW, "NOT_TRIGGERED"), Map.of());
        assertThat(DeadlineRule.classifyFrb(clean(), emptyHeads).causes())
                .contains("LEDGER_NOT_SUPPLIED");
        var byRoute = ResultsPipeline.classifyFrbRoutes(
                Map.of("one", clean(), "two", clean()), Map.of("one", input()), Set.of());
        assertThat(byRoute.get("one").classification())
                .isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
        assertThat(byRoute.get("two").causes()).contains("LEDGER_NOT_SUPPLIED");
    }

    @Test void productionSwitchBlockSeparatesCostFromQualityAndSolFlag() {
        var qualityPass = DeadlineRule.Classification.BENCHMARK_PASS;
        assertThat(FrbClassification.switchBlocked(qualityPass, Set.of(), false)).isFalse();
        assertThat(FrbClassification.switchBlocked(qualityPass, Set.of("SOL_RECOMMENDED"), false))
                .isFalse();
        assertThat(FrbClassification.switchBlocked(qualityPass, Set.of("CUSTOM_COST_FLAG"), false))
                .isTrue();
        assertThat(FrbClassification.switchBlocked(qualityPass, Set.of(), true)).isTrue();
        assertThat(FrbClassification.switchBlocked(OWNER, Set.of(), false)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INCONCLUSIVE_SCORER_UNRELIABLE", "INCONCLUSIVE_WORKLOAD_EXHAUSTED",
            "INCONCLUSIVE_UNRESOLVED_FINDING", "INCOMPLETE_DOUBLE_MISS_EXPANSION"})
    void classAExpansionOutcomeInputBlocksAStatisticalPass(String outcome) {
        var facts = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of(), Map.of(WORKFLOW, outcome), HEADS);
        var result = DeadlineRule.classifyFrb(clean(), facts);
        assertThat(result.classification()).isEqualTo(OWNER);
        assertThat(result.causes()).contains(outcome);
    }
}
