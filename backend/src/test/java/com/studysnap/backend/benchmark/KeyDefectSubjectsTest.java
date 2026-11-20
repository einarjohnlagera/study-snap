package com.studysnap.backend.benchmark;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyDefectSubjectsTest {
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String PLAN = "PLAN_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String CUSTOM = "CUSTOM_QUESTION_WORKFLOW";
    private static final String CANDIDATE = "LUNA";
    private static final String BASELINE = "BASELINE_MINI";
    private static final Set<String> CLASS_A = Set.of(NOTE, PLAN, CUSTOM);
    private static final Set<String> CLASS_B = Set.of(TIP);
    private static final AdjudicatedFacts CLEAN = facts(1, 0, 0, 0);
    private static final AdjudicatedFacts CRITICAL = facts(1, 1, 0, 0);

    private static AdjudicatedFacts facts(int scored, int critical, int scorerFailure, int unresolved) {
        return new AdjudicatedFacts(scored, critical, 0, unresolved, 0, 0, scorerFailure, 0, null);
    }

    private static String identity(String caseId, String arm) {
        return caseId + "\u0000" + arm;
    }

    private static FrbEvidenceBundle bundle(Map<String, AdjudicatedFacts> facts, Set<String> excluded) {
        AdjudicatedLoaded loaded = new AdjudicatedLoaded(facts, excluded, Map.of());
        return new FrbEvidenceBundle(loaded, Map.of(), Map.of(), Set.of());
    }

    private static Set<FindingsLedger.ReviewSubject> derive(FrbEvidenceBundle bundle,
            Map<String, String> workflows) {
        return KeyDefectSubjects.derive(bundle, workflows, CLASS_A, CLASS_B);
    }

    private static FindingsLedger.ReviewSubject subject(String workflow, String caseId) {
        return new FindingsLedger.ReviewSubject(ReviewRecord.Kind.KEY_DEFECT, workflow, caseId);
    }

    @Test void candidateOnlyCriticalRequiresOneCaseReview() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", BASELINE), CLEAN,
                identity("one", CANDIDATE), CRITICAL), Set.of());
        assertThat(derive(bundle, Map.of("one", NOTE))).containsExactly(subject(NOTE, "one"));
    }

    @Test void baselineOnlyCriticalRequiresOneCaseReview() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", BASELINE), CRITICAL,
                identity("one", CANDIDATE), CLEAN), Set.of());
        assertThat(derive(bundle, Map.of("one", NOTE))).containsExactly(subject(NOTE, "one"));
    }

    @Test void sharedCriticalRequiresOnlyOneCaseReview() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", BASELINE), CRITICAL,
                identity("one", CANDIDATE), CRITICAL), Set.of());
        assertThat(derive(bundle, Map.of("one", NOTE))).containsExactly(subject(NOTE, "one"));
    }

    @Test void threeCriticalQuestionsStillRequireOnlyOneCaseReview() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE),
                facts(3, 3, 0, 0)), Set.of());
        assertThat(derive(bundle, Map.of("one", NOTE))).containsExactly(subject(NOTE, "one"));
    }

    @Test void cleanAndExcludedCasesRequireNoReviews() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("clean", CANDIDATE), CLEAN), Set.of("excluded"));
        assertThat(derive(bundle, Map.of("clean", NOTE, "excluded", NOTE))).isEmpty();
    }

    @Test void twoWorkflowsAreSortedAndResultIsImmutableAndDeterministic() {
        Map<String, AdjudicatedFacts> facts = new LinkedHashMap<>();
        facts.put(identity("zeta", CANDIDATE), CRITICAL);
        facts.put(identity("alpha", CANDIDATE), CRITICAL);
        FrbEvidenceBundle bundle = bundle(facts, Set.of());
        Map<String, String> workflows = Map.of("zeta", PLAN, "alpha", NOTE);
        Set<FindingsLedger.ReviewSubject> result = derive(bundle, workflows);
        assertThat(result).containsExactly(subject(NOTE, "alpha"), subject(PLAN, "zeta"));
        assertThat(derive(bundle, workflows)).containsExactlyElementsOf(result);
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void classBCriticalYieldsNothing() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        assertThat(derive(bundle, Map.of("one", TIP))).isEmpty();
    }

    @Test void unknownWorkflowFails() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        Map<String, String> workflows = Map.of("one", "UNKNOWN");
        assertThatThrownBy(() -> derive(bundle, workflows))
                .isInstanceOf(InvalidEvidence.class);
    }

    @Test void unmappedCaseFailsEvenWhenClean() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CLEAN), Set.of());
        Map<String, String> workflows = Map.of();
        assertThatThrownBy(() -> derive(bundle, workflows)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void overlappingWorkflowSetsFail() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        Map<String, String> workflows = Map.of("one", NOTE);
        Set<String> overlapping = Set.of(NOTE);
        assertThatThrownBy(() -> KeyDefectSubjects.derive(bundle, workflows, CLASS_A, overlapping))
                .isInstanceOf(InvalidEvidence.class);
    }

    @Test void invalidSubjectIdFailsRatherThanBeingSkipped() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("luna-case", CANDIDATE), CRITICAL), Set.of());
        Map<String, String> workflows = Map.of("luna-case", NOTE);
        assertThatThrownBy(() -> derive(bundle, workflows))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining("luna-case");
    }

    @Test void onlyACaseThatMustBecomeASubjectNeedsAUsableSubjectId() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("Fixture.B-1", CANDIDATE), CRITICAL,
                identity("Clean.A-1", CANDIDATE), CLEAN), Set.of());
        assertThat(derive(bundle, Map.of("Fixture.B-1", TIP, "Clean.A-1", NOTE))).isEmpty();
    }

    @Test void everyCriticalCaseInOneWorkflowKeepsItsOwnReview() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL,
                identity("two", CANDIDATE), CRITICAL), Set.of());
        assertThat(derive(bundle, Map.of("one", NOTE, "two", NOTE)))
                .containsExactly(subject(NOTE, "one"), subject(NOTE, "two"));
    }

    @Test void aCaseBothExcludedAndCarryingFactsFails() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of("one"));
        Map<String, String> workflows = Map.of("one", NOTE);
        assertThatThrownBy(() -> derive(bundle, workflows)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void everyNullArgumentFails() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        Map<String, String> workflows = Map.of("one", NOTE);
        assertThatThrownBy(() -> KeyDefectSubjects.derive(null, workflows, CLASS_A, CLASS_B))
                .isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> KeyDefectSubjects.derive(bundle, null, CLASS_A, CLASS_B))
                .isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> KeyDefectSubjects.derive(bundle, workflows, null, CLASS_B))
                .isInstanceOf(InvalidEvidence.class);
        assertThatThrownBy(() -> KeyDefectSubjects.derive(bundle, workflows, CLASS_A, null))
                .isInstanceOf(InvalidEvidence.class);
    }

    @Test void confirmedScorerFailureCriticalCountsButUnresolvedDoesNot() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("confirmed", CANDIDATE), facts(1, 1, 1, 0),
                identity("unresolved", CANDIDATE), facts(1, 0, 0, 1)), Set.of());
        assertThat(derive(bundle, Map.of("confirmed", NOTE, "unresolved", NOTE)))
                .containsExactly(subject(NOTE, "confirmed"));
    }

    @Test void callerSuppliedClassAWorkflowIsHonored() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), CRITICAL), Set.of());
        assertThat(derive(bundle, Map.of("one", CUSTOM))).containsExactly(subject(CUSTOM, "one"));
    }

    @Test void malformedIdentityFailsInsteadOfReturningEmpty() {
        FrbEvidenceBundle bundle = bundle(Map.of("one", CLEAN), Set.of());
        Map<String, String> workflows = Map.of("one", NOTE);
        assertThatThrownBy(() -> derive(bundle, workflows)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void malformedCriticalCountsFailInsteadOfReturningEmpty() {
        FrbEvidenceBundle bundle = bundle(Map.of(identity("one", CANDIDATE), facts(1, -1, 0, 0)), Set.of());
        Map<String, String> workflows = Map.of("one", NOTE);
        assertThatThrownBy(() -> derive(bundle, workflows)).isInstanceOf(InvalidEvidence.class);
    }
}
