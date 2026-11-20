package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** File-backed cross-lock composition, using the actual FRB fixture locks and sealed exports. */
class ReviewCompositionExtrasTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String CREATED = "2026-11-16T00:07:00Z";
    private static final String GENESIS = "2026-11-16T00:05:00Z";
    private static final String EVIDENCE = "a".repeat(64);
    private static final String BASELINE_MINI = "BASELINE_MINI";
    private static final String BASELINE_41 = "BASELINE_41";
    private static final String QUALITY_ID = "absolute_quality";
    private static final String QUALITY_COMPLETE = "quality_complete";
    private static final FindingsLedger.ReviewSubject QUALITY = new FindingsLedger.ReviewSubject(
            ReviewRecord.Kind.ABSOLUTE_QUALITY, NOTE, QUALITY_ID);
    private static final KeyDefectWorkload.Parameters WORKLOAD = new KeyDefectWorkload.Parameters(
            BigDecimal.valueOf(30), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(4));
    @TempDir Path temp;

    private record Ledger(PostLockContext context, SafetyFindingStore safety,
            EvidenceDisputeStore disputes, ReviewRecordStore reviews, FindingsLedger ledger) {
        String hash() { return context.verify().lockSha256(); }
        FindingsLedger.ExternalHeads heads() {
            return new FindingsLedger.ExternalHeads(
                    external(safety.headSha256()), external(disputes.headSha256()),
                    external(reviews.headSha256()));
        }
        FindingsLedger.ReviewCheck token(Set<FindingsLedger.ReviewSubject> extras) {
            return ledger.checkLedger(ROUTE, extras, heads());
        }
        void review(String id, FindingsLedger.ReviewSubject subject, ReviewRecord.Outcome outcome) {
            reviews.create(new ReviewRecord(id, CREATED, hash(), subject.kind(), subject.workflow(),
                    subject.subjectId(), outcome, "owner", ReviewRecord.SignerRole.OWNER, EVIDENCE));
        }
    }

    private record Harness(FrbPipelineTest.Fixture fixture, RouteVerdictCalculator.RouteReport report,
            List<Ledger> ledgers, Set<FindingsLedger.ReviewSubject> derived) {
        List<FindingsLedger.ReviewCheck> tokens() {
            List<FindingsLedger.ReviewCheck> result = new ArrayList<>();
            for (Ledger ledger : ledgers) result.add(ledger.token(derived));
            return result;
        }
        ReviewComposition.ComposedReviewCheck compose(List<FindingsLedger.ReviewCheck> tokens) {
            return composeReport(report, tokens);
        }
        ReviewComposition.ComposedReviewCheck composeReport(RouteVerdictCalculator.RouteReport candidate,
                List<FindingsLedger.ReviewCheck> tokens) {
            return ReviewComposition.compose(ROUTE, fixture.run(), fixture.frb(), candidate, tokens,
                    WORKLOAD, Set.of(candidateArm()), Set.of(BASELINE_MINI, BASELINE_41));
        }
        String candidateArm() { return Stage1Receipt.read(fixture.run().candidate().receipt()).arm(); }
        ReviewComposition.ComposedReviewCheck compose() { return compose(tokens()); }
        Ledger classA() { return ledgers.getFirst(); }
        Ledger classB() { return ledgers.get(2); }
    }

    private static FindingsLedger.ExternalHead external(PostLockStoreIndex.Head head) {
        return new FindingsLedger.ExternalHead(head.seq(), head.sha256());
    }

    private Harness harness() throws Exception {
        return harness(true, true, false);
    }

    private Harness harness(boolean completeClassB, boolean completeDefects,
            boolean defectsInSecondLedger) throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = temp;
        FrbPipelineTest.Fixture fixture = builder.fixture(true, 0, 1, true);
        RouteVerdictCalculator.RouteReport report = ResultsPipeline.runFrb(fixture.run(), fixture.frb())
                .routes().get(ROUTE);
        FrozenGateParameters parameters = FrozenGateParameters.load(fixture.run().parameters());
        Map<String, String> workflows = ResultsPipeline.workflowByCaseId(fixture.run());
        Set<String> classA = new HashSet<>(parameters.workflows.keySet());
        classA.removeAll(parameters.frbClassBWorkflows);
        Set<FindingsLedger.ReviewSubject> derived = KeyDefectSubjects.derive(fixture.frb().bundle(),
                workflows, classA, parameters.frbClassBWorkflows);
        List<Ledger> ledgers = new ArrayList<>();
        for (ResultsPipeline.PrimaryExport export : fixture.frb().provenance().primaries())
            ledgers.add(ledger(export.lockFile(), export.sealFile(), export.findingsDirectory(), ledgers.size(), fixture));
        for (ResultsPipeline.ReserveExport export : fixture.frb().provenance().reserves().values()) {
            ledgers.add(ledger(export.lockForPrimaryOpening(), export.sealFile(),
                    export.findingsDirectory(), ledgers.size(), fixture));
            ledgers.add(ledger(export.lockForReserveOpening(), export.sealFile(),
                    export.findingsDirectory(), ledgers.size(), fixture));
        }
        if (completeClassB) ledgers.get(2).review("class_b_complete", new FindingsLedger.ReviewSubject(
                ReviewRecord.Kind.CLASS_B_WORKFLOW, TIP, TIP), ReviewRecord.Outcome.COMPLETE);
        int number = 0;
        if (completeDefects) for (FindingsLedger.ReviewSubject subject : derived) {
            int location = defectsInSecondLedger ? 1 : number;
            ledgers.get(location).review("defect_complete_" + ++number, subject, ReviewRecord.Outcome.COMPLETE);
        }
        return new Harness(fixture, report, List.copyOf(ledgers), derived);
    }

    private Ledger ledger(Path lock, Path seal, Path findings, int index,
            FrbPipelineTest.Fixture fixture) throws Exception {
        PostLockContext context = new PostLockContext(lock, findings,
                seal.getParent().resolve("packets"), seal, fixture.run().parameters());
        Path root = Files.createDirectories(temp.resolve("ledger-" + index));
        Path safety = root.resolve("safety");
        Path disputes = root.resolve("disputes");
        Path reviews = root.resolve("reviews");
        SafetyFindingStore safetyStore = SafetyFindingStore.init(safety, context, GENESIS);
        EvidenceDisputeStore disputeStore = EvidenceDisputeStore.init(disputes, context, GENESIS);
        ReviewRecordStore reviewStore = ReviewRecordStore.init(reviews, context, GENESIS);
        return new Ledger(context, safetyStore, disputeStore, reviewStore,
                new FindingsLedger(safety, disputes, reviews, context));
    }


    private static List<FindingsLedger.ReviewCheck> tokens(Harness h, Set<Integer> extraLocks) {
        List<FindingsLedger.ReviewCheck> result = new ArrayList<>();
        for (int index = 0; index < h.ledgers().size(); index++) {
            Set<FindingsLedger.ReviewSubject> extras = new HashSet<>(h.derived());
            if (extraLocks.contains(index)) extras.add(QUALITY);
            result.add(h.ledgers().get(index).token(extras));
        }
        return result;
    }

    private static Set<Integer> everyLock(Harness h) {
        Set<Integer> result = new HashSet<>();
        for (int index = 0; index < h.ledgers().size(); index++) result.add(index);
        return result;
    }

    @Test void listedAbsoluteQualityNeedsCoveringOwnerCompletion() throws Exception {
        Harness h = harness();
        var control = h.compose();
        assertThat(control.extrasByLock()).hasSize(5);
        for (var token : h.tokens())
            assertThat(control.extrasByLock()).containsEntry(token.lockSha256(), token.extras());
        var withExtra = tokens(h, everyLock(h));
        assertThatThrownBy(() -> h.compose(withExtra))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
        h.classA().review(QUALITY_COMPLETE, QUALITY, ReviewRecord.Outcome.COMPLETE);
        var completed = h.compose(tokens(h, everyLock(h)));
        assertThat(completed.extrasByLock()).hasSize(5);
        for (var token : tokens(h, everyLock(h)))
            assertThat(completed.extrasByLock()).containsEntry(token.lockSha256(), token.extras());
        assertThat(completed.requiredKeyDefectSubjects()).isEqualTo(control.requiredKeyDefectSubjects());
    }

    @Test void blockingConcernStillRefusesCompletedExtra() throws Exception {
        Harness h = harness();
        h.classA().review(QUALITY_COMPLETE, QUALITY, ReviewRecord.Outcome.COMPLETE);
        assertThat(h.compose(tokens(h, everyLock(h)))).isNotNull();
        h.classA().review("quality_concern", QUALITY, ReviewRecord.Outcome.BLOCKING_CONCERN);
        var withExtra = tokens(h, everyLock(h));
        assertThatThrownBy(() -> h.compose(withExtra))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
    }

    @Test void oneTokenExtraIsRefusedOnCoveringAndNonCoveringLocks() throws Exception {
        Harness h = harness();
        h.classA().review(QUALITY_COMPLETE, QUALITY, ReviewRecord.Outcome.COMPLETE);
        assertThat(h.compose(h.tokens())).isNotNull();
        assertThat(h.compose(tokens(h, everyLock(h)))).isNotNull();
        for (int index : List.of(0, 2)) {
            var single = tokens(h, Set.of(index));
            assertThatThrownBy(() -> h.compose(single))
                    .isInstanceOf(ReviewComposition.InvalidComposition.class);
        }
    }

    @Test void nonCoveringCompletionDoesNotSatisfyExtra() throws Exception {
        Harness h = harness();
        h.classB().review("foreign_quality_complete", QUALITY, ReviewRecord.Outcome.COMPLETE);
        var withExtra = tokens(h, everyLock(h));
        assertThatThrownBy(() -> h.compose(withExtra))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
        h.classA().review("covering_quality_complete", QUALITY, ReviewRecord.Outcome.COMPLETE);
        assertThat(h.compose(tokens(h, everyLock(h)))).isNotNull();
    }
}
