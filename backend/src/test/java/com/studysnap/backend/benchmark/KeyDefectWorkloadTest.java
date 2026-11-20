package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyDefectWorkloadTest {
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String CANDIDATE = "LUNA";
    private static final String BASELINE = "BASELINE_MINI";
    private static final String UNKNOWN = "UNMAPPED_ARM";
    private static final Set<String> CLASS_A = Set.of(NOTE);
    private static final Set<String> CLASS_B = Set.of(TIP);
    private static final Set<String> CANDIDATE_ARMS = Set.of(CANDIDATE);
    private static final Set<String> BASELINE_ARMS = Set.of(BASELINE);
    private static final KeyDefectWorkload.Parameters DEFAULT_PARAMETERS = parameters("1", "0", "20", "3");
    private static final AdjudicatedFacts CLEAN = facts(1, 0);
    private static final AdjudicatedFacts CRITICAL = facts(1, 1);

    private static AdjudicatedFacts facts(int scored, int critical) {
        return new AdjudicatedFacts(scored, critical, 0, 0, 0, 0, 0, 0, null);
    }

    private static String identity(String caseId, String arm) {
        return caseId + "\u0000" + arm;
    }

    private static FrbEvidenceBundle bundle(Map<String, AdjudicatedFacts> facts, Set<String> excluded) {
        AdjudicatedLoaded loaded = new AdjudicatedLoaded(facts, excluded, Map.of());
        return new FrbEvidenceBundle(loaded, Map.of(), Map.of(), Set.of());
    }

    private static KeyDefectWorkload.Parameters parameters(String minutes, String other, String limit, String daily) {
        return new KeyDefectWorkload.Parameters(new BigDecimal(minutes), new BigDecimal(other),
                new BigDecimal(limit), new BigDecimal(daily));
    }

    private static KeyDefectWorkload.Estimate estimate(FrbEvidenceBundle bundle, Map<String, String> workflows) {
        return KeyDefectWorkload.estimate(bundle, workflows, CLASS_A, CLASS_B,
                CANDIDATE_ARMS, BASELINE_ARMS, DEFAULT_PARAMETERS);
    }

    private static KeyDefectWorkload.Estimate estimate(FrbEvidenceBundle bundle, Map<String, String> workflows,
            KeyDefectWorkload.Parameters parameters) {
        return KeyDefectWorkload.estimate(bundle, workflows, CLASS_A, CLASS_B,
                CANDIDATE_ARMS, BASELINE_ARMS, parameters);
    }

    @Test void caseSetMatchesDerivedSubjectsAndAllThreePatternsAreOneReviewPerCase() {
        Map<String, AdjudicatedFacts> facts = new LinkedHashMap<>();
        facts.put(identity("one", CANDIDATE), facts(3, 3));
        facts.put(identity("one", BASELINE), CLEAN);
        facts.put(identity("two", BASELINE), CRITICAL);
        facts.put(identity("two", CANDIDATE), CLEAN);
        facts.put(identity("shared", CANDIDATE), CRITICAL);
        facts.put(identity("shared", BASELINE), CRITICAL);
        facts.put(identity("clean", CANDIDATE), CLEAN);
        facts.put(identity("classB", CANDIDATE), CRITICAL);
        FrbEvidenceBundle bundle = bundle(facts, Set.of("excluded"));
        Map<String, String> workflows = Map.of("one", NOTE, "two", NOTE,
                "shared", NOTE, "clean", NOTE, "classB", TIP, "excluded", NOTE);
        KeyDefectWorkload.Estimate result = estimate(bundle, workflows);
        assertThat(result.cases()).isEqualTo(KeyDefectSubjects.derive(bundle, workflows, CLASS_A, CLASS_B).size());
        assertThat(result.cases()).isEqualTo(3);
        assertThat(result.casesByPattern()).containsEntry(KeyDefectWorkload.ArmPattern.CANDIDATE_ONLY, 1)
                .containsEntry(KeyDefectWorkload.ArmPattern.BASELINE_ONLY, 1)
                .containsEntry(KeyDefectWorkload.ArmPattern.SHARED, 1);
        assertThat(result.casesByPattern().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(result.cases());
        assertThat(result.report()).contains("key-defect review cases: 3 (candidate-only 1, baseline-only 1, shared 1)");
    }

    @Test void fractionalReviewHoursRoundUpAndBothDayCountsUseCeiling() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        KeyDefectWorkload.Estimate result = estimate(bundle, Map.of("one", NOTE),
                parameters("1", "3", "4", "2"));
        assertThat(result.reviewHours()).isEqualByComparingTo("0.0167");
        assertThat(result.totalHours()).isEqualByComparingTo("3.0167");
        assertThat(result.reviewDays()).isEqualTo(1);
        assertThat(result.totalDays()).isEqualTo(2);
        assertThat(result.exceedsLimit()).isFalse();
        assertThat(result.report()).contains("days at 2 productive hours per day: review 1, total 2");
    }

    @Test void eachArmPatternIsClassifiedFromItsOwnCriticalFacts() {
        FrbEvidenceBundle candidate = bundle(Map.of(identity("one", CANDIDATE), CRITICAL,
                identity("one", BASELINE), CLEAN), Set.of());
        FrbEvidenceBundle baseline = bundle(Map.of(identity("one", CANDIDATE), CLEAN,
                identity("one", BASELINE), CRITICAL), Set.of());
        FrbEvidenceBundle shared = bundle(Map.of(identity("one", CANDIDATE), CRITICAL,
                identity("one", BASELINE), CRITICAL), Set.of());
        Map<String, String> workflows = Map.of("one", NOTE);
        assertThat(estimate(candidate, workflows).casesByPattern())
                .containsEntry(KeyDefectWorkload.ArmPattern.CANDIDATE_ONLY, 1)
                .containsEntry(KeyDefectWorkload.ArmPattern.BASELINE_ONLY, 0)
                .containsEntry(KeyDefectWorkload.ArmPattern.SHARED, 0);
        assertThat(estimate(baseline, workflows).casesByPattern())
                .containsEntry(KeyDefectWorkload.ArmPattern.CANDIDATE_ONLY, 0)
                .containsEntry(KeyDefectWorkload.ArmPattern.BASELINE_ONLY, 1)
                .containsEntry(KeyDefectWorkload.ArmPattern.SHARED, 0);
        assertThat(estimate(shared, workflows).casesByPattern())
                .containsEntry(KeyDefectWorkload.ArmPattern.CANDIDATE_ONLY, 0)
                .containsEntry(KeyDefectWorkload.ArmPattern.BASELINE_ONLY, 0)
                .containsEntry(KeyDefectWorkload.ArmPattern.SHARED, 1);
    }

    @Test void tinyPositiveMinutesStillConsumeOneRoundingUnit() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        KeyDefectWorkload.Estimate result = estimate(bundle, Map.of("one", NOTE),
                parameters("0.00001", "0", "1", "1"));
        assertThat(result.reviewHours()).isEqualByComparingTo("0.0001");
        assertThat(result.reviewDays()).isEqualTo(1);
    }

    @Test void exactlyAtLimitIsWithinAndOneRoundingUnitAboveExceeds() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        Map<String, String> workflows = Map.of("one", NOTE);
        KeyDefectWorkload.Estimate atLimit = estimate(bundle, workflows,
                parameters("60", "1", "2.0000", "1"));
        KeyDefectWorkload.Estimate above = estimate(bundle, workflows,
                parameters("60", "1.0001", "2.0000", "1"));
        assertThat(atLimit.totalHours()).isEqualByComparingTo("2.0000");
        assertThat(atLimit.exceedsLimit()).isFalse();
        assertThat(atLimit.report()).contains("workload within limit");
        assertThat(above.totalHours()).isEqualByComparingTo("2.0001");
        assertThat(above.exceedsLimit()).isTrue();
        assertThat(above.report()).contains("WORKLOAD EXCEEDS LIMIT: return to owner");
    }

    @Test void zeroCasesRetainOtherPlannedHoursAndCanExceed() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("clean", CANDIDATE), CLEAN), Set.of("excluded"));
        KeyDefectWorkload.Estimate result = estimate(bundle, Map.of("clean", NOTE, "excluded", NOTE),
                parameters("1", "2.0001", "2", "1"));
        assertThat(result.cases()).isZero();
        assertThat(result.reviewHours()).isEqualByComparingTo("0.0000");
        assertThat(result.totalHours()).isEqualByComparingTo("2.0001");
        assertThat(result.reviewDays()).isZero();
        assertThat(result.totalDays()).isEqualTo(3);
        assertThat(result.exceedsLimit()).isTrue();
    }

    @Test void reportIsSortedDeterministicAndStatesAttributionAndParameterLimits() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        Map<String, String> workflows = Map.of("one", NOTE);
        List<String> report = estimate(bundle, workflows).report();
        assertThat(report).containsExactlyElementsOf(report.stream().sorted().toList());
        assertThat(estimate(bundle, workflows).report()).containsExactlyElementsOf(report);
        assertThat(report).anySatisfy(line -> assertThat(line).contains("input-caused findings are not broken out"));
        assertThat(report).anySatisfy(line -> assertThat(line).contains("root cause is not carried in the verified facts"));
        assertThat(report).anySatisfy(line -> assertThat(line).contains("owner-supplied parameters"));
        assertThat(report).anySatisfy(line -> assertThat(line).contains("excluded short-delivery pairs"));
    }

    @Test void estimateMapAndReportAreImmutable() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        KeyDefectWorkload.Estimate result = estimate(bundle, Map.of("one", NOTE));
        Map<KeyDefectWorkload.ArmPattern, Integer> counts = result.casesByPattern();
        List<String> report = result.report();
        assertThatThrownBy(() -> counts.put(KeyDefectWorkload.ArmPattern.SHARED, 5))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(report::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void casesSharingAPatternAreAllCounted() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("a", CANDIDATE), CRITICAL,
                identity("b", CANDIDATE), CRITICAL), Set.of());
        KeyDefectWorkload.Estimate result = estimate(bundle, Map.of("a", NOTE, "b", NOTE));
        assertThat(result.cases()).isEqualTo(2);
        assertThat(result.casesByPattern()).containsEntry(KeyDefectWorkload.ArmPattern.CANDIDATE_ONLY, 2)
                .containsEntry(KeyDefectWorkload.ArmPattern.BASELINE_ONLY, 0)
                .containsEntry(KeyDefectWorkload.ArmPattern.SHARED, 0);
    }

    @Test void casesThatAreNotSubjectsNeverNeedKnownArms() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL,
                identity("clean", UNKNOWN), CLEAN, identity("classB", UNKNOWN), CRITICAL), Set.of());
        KeyDefectWorkload.Estimate result = estimate(bundle, Map.of("one", NOTE, "clean", NOTE, "classB", TIP));
        assertThat(result.cases()).isEqualTo(1);
        assertThat(result.casesByPattern()).containsEntry(KeyDefectWorkload.ArmPattern.CANDIDATE_ONLY, 1);
    }

    @Test void dayCountBeyondIntRangeFailsInsteadOfWrapping() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        Map<String, String> workflows = Map.of("one", NOTE);
        KeyDefectWorkload.Parameters huge = parameters("1", "1E+15", "20", "0.0001");
        assertThatThrownBy(() -> estimate(bundle, workflows, huge)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void unknownArmInSubjectFactsFailsEvenWhenThatArmIsClean() {
        FrbEvidenceBundle control = bundle(Map.of(identity("one", CANDIDATE), CRITICAL,
                identity("one", BASELINE), CLEAN), Set.of());
        FrbEvidenceBundle unknown = bundle(Map.of(identity("one", CANDIDATE), CRITICAL,
                identity("one", UNKNOWN), CLEAN), Set.of());
        Map<String, String> workflows = Map.of("one", NOTE);
        assertThat(estimate(control, workflows).cases()).isEqualTo(1);
        assertThatThrownBy(() -> estimate(unknown, workflows)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void overlappingArmSetsFail() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        Map<String, String> workflows = Map.of("one", NOTE);
        assertThat(estimate(bundle, workflows).cases()).isEqualTo(1);
        assertThatThrownBy(() -> KeyDefectWorkload.estimate(bundle, workflows, CLASS_A, CLASS_B,
                CANDIDATE_ARMS, CANDIDATE_ARMS, DEFAULT_PARAMETERS)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void everyNullEstimateArgumentFails() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        Map<String, String> workflows = Map.of("one", NOTE);
        assertThat(estimate(bundle, workflows).cases()).isEqualTo(1);
        assertThatThrownBy(() -> KeyDefectWorkload.estimate(null, workflows, CLASS_A, CLASS_B,
                CANDIDATE_ARMS, BASELINE_ARMS, DEFAULT_PARAMETERS)).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> KeyDefectWorkload.estimate(bundle, null, CLASS_A, CLASS_B,
                CANDIDATE_ARMS, BASELINE_ARMS, DEFAULT_PARAMETERS)).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> KeyDefectWorkload.estimate(bundle, workflows, null, CLASS_B,
                CANDIDATE_ARMS, BASELINE_ARMS, DEFAULT_PARAMETERS)).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> KeyDefectWorkload.estimate(bundle, workflows, CLASS_A, null,
                CANDIDATE_ARMS, BASELINE_ARMS, DEFAULT_PARAMETERS)).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> KeyDefectWorkload.estimate(bundle, workflows, CLASS_A, CLASS_B,
                null, BASELINE_ARMS, DEFAULT_PARAMETERS)).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> KeyDefectWorkload.estimate(bundle, workflows, CLASS_A, CLASS_B,
                CANDIDATE_ARMS, null, DEFAULT_PARAMETERS)).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> KeyDefectWorkload.estimate(bundle, workflows, CLASS_A, CLASS_B,
                CANDIDATE_ARMS, BASELINE_ARMS, null)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void everyInvalidParameterFailsWhileTheNearestValidValuePasses() {
        assertThat(parameters("1", "0", "1", "1")).isNotNull();
        assertThatThrownBy(() -> parameters("0", "0", "1", "1")).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> parameters("-1", "0", "1", "1")).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> parameters("1", "-1", "1", "1")).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> parameters("1", "0", "0", "1")).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> parameters("1", "0", "-1", "1")).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> parameters("1", "0", "1", "0")).isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> parameters("1", "0", "1", "-1")).isInstanceOf(InvalidEvidence.class);
        BigDecimal one = BigDecimal.ONE;
        BigDecimal zero = BigDecimal.ZERO;
        assertThatThrownBy(() -> new KeyDefectWorkload.Parameters(null, zero, one, one))
                .isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> new KeyDefectWorkload.Parameters(one, null, one, one))
                .isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> new KeyDefectWorkload.Parameters(one, zero, null, one))
                .isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> new KeyDefectWorkload.Parameters(one, zero, one, null))
                .isInstanceOf(InvalidEvidence.class);
    }

    @Test void deriveFailurePropagatesWithoutPartialEstimate() {
        FrbEvidenceBundle control = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        FrbEvidenceBundle invalid = bundle(Map.of(identity("one", CANDIDATE), CRITICAL,
                identity("unmapped", CANDIDATE), CLEAN), Set.of());
        Map<String, String> workflows = Map.of("one", NOTE);
        assertThat(estimate(control, workflows).cases()).isEqualTo(1);
        assertThatThrownBy(() -> estimate(invalid, workflows)).isInstanceOf(InvalidEvidence.class)
                .hasMessageContaining("No workflow");
    }
}
