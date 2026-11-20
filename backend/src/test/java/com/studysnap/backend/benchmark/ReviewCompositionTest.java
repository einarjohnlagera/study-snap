package com.studysnap.backend.benchmark;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** File-backed cross-lock composition, using the actual FRB fixture locks and sealed exports. */
class ReviewCompositionTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String CREATED = "2026-11-16T00:07:00Z";
    private static final String GENESIS = "2026-11-16T00:05:00Z";
    private static final String EVIDENCE = "a".repeat(64);
    private static final String BASELINE_MINI = "BASELINE_MINI";
    private static final String BASELINE_41 = "BASELINE_41";
    private static final KeyDefectWorkload.Parameters WORKLOAD = new KeyDefectWorkload.Parameters(
            BigDecimal.valueOf(30), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(4));
    @TempDir Path temp;

    private record Ledger(Path lock, Path seal, PostLockContext context, SafetyFindingStore safety,
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
        String firstItem() { return PairMap.fromPackets(seal.getParent().resolve("packets"))
                .blindItemIds().stream().sorted().findFirst().orElseThrow(); }
        void finding(String id, List<String> routes) {
            safety.create(new SafetyFinding(id, CREATED, hash(), firstItem(), NOTE, null,
                    routes, "Incorrect answer", "reviewer", SafetyFinding.Attribution.GENERATOR,
                    false), null);
        }
        void dispute(String id) {
            disputes.create(new EvidenceDispute(id, CREATED, hash(), NOTE, firstItem(), null,
                    "reviewer", "Critical answer disputed", List.of(ROUTE)),
                    (context, verified, workflow, itemId) -> {}, null);
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
        return new Ledger(lock, seal, context, safetyStore, disputeStore, reviewStore,
                new FindingsLedger(safety, disputes, reviews, context));
    }

    private static FindingsLedger.ReviewCheck changed(FindingsLedger.ReviewCheck original,
            int index, Object replacement) throws Exception {
        Constructor<?> constructor = FindingsLedger.ReviewCheck.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] values = {original.route(), original.coveredWorkflows(), original.missing(),
                original.extras(), original.openSafetyFindingIds(), original.openDisputeIds(),
                original.openConcernIds(), original.heads(), original.lockSha256(),
                original.parametersSha256()};
        values[index] = replacement;
        return (FindingsLedger.ReviewCheck) constructor.newInstance(values);
    }

    private static List<FindingsLedger.ReviewCheck> replaced(List<FindingsLedger.ReviewCheck> original,
            int index, FindingsLedger.ReviewCheck replacement) {
        List<FindingsLedger.ReviewCheck> values = new ArrayList<>(original);
        values.set(index, replacement);
        return values;
    }

    private static RouteVerdictCalculator.RouteReport reportWith(RouteVerdictCalculator.RouteReport original,
            String sha, Map<String, RouteVerdictCalculator.WorkflowReport> workflows) {
        return new RouteVerdictCalculator.RouteReport(original.verdict(), original.failedTiers(),
                original.inconclusiveTiers(), original.d(), original.isolatedCriticalIncident(), sha,
                workflows, original.frb());
    }

    @Test void cleanFiveLockControlCoversRouteAndRecordsEveryHeadAndDefect() throws Exception {
        Harness h = harness();
        var composed = h.compose();
        assertThat(h.ledgers()).hasSize(5);
        assertThat(composed.lockSha256s()).hasSize(5);
        assertThat(composed.coveredWorkflows()).containsExactly(NOTE, TIP);
        assertThat(composed.requiredKeyDefectSubjects()).containsExactlyInAnyOrderElementsOf(h.derived());
        assertThat(composed.heads()).hasSize(15);
        assertThat(composed.extrasByLock()).hasSize(5);
        for (FindingsLedger.ReviewCheck token : h.tokens()) {
            assertThat(token.parametersSha256()).isEqualTo(composed.parametersSha256());
            for (String store : Set.of("safety", "disputes", "reviews"))
                assertThat(composed.heads()).containsEntry(token.lockSha256() + "." + store,
                        token.heads().get(store));
            assertThat(composed.extrasByLock()).containsEntry(token.lockSha256(), token.extras());
        }
        assertThat(h.classA().token(h.derived()).missing()).contains("CLASS_B_WORKFLOW:" + TIP + ":" + TIP);
    }

    @Test void estimateUsesFrozenCasesAndOwnerInputsAndExcessIsRecorded() throws Exception {
        Harness h = harness();
        var composed = h.compose();
        FrozenGateParameters parameters = FrozenGateParameters.load(h.fixture().run().parameters());
        Set<String> classA = new HashSet<>(parameters.workflows.keySet());
        classA.removeAll(parameters.frbClassBWorkflows);
        var expected = KeyDefectWorkload.estimate(h.fixture().frb().bundle(),
                ResultsPipeline.workflowByCaseId(h.fixture().run()), classA, parameters.frbClassBWorkflows,
                Set.of(h.candidateArm()), Set.of(BASELINE_MINI, BASELINE_41), WORKLOAD);
        assertThat(composed.workloadEstimate()).isEqualTo(expected);
        var limited = new KeyDefectWorkload.Parameters(WORKLOAD.minutesPerCaseReview(),
                WORKLOAD.otherPlannedHours(), BigDecimal.valueOf(1), WORKLOAD.productiveHoursPerDay());
        var exceeded = ReviewComposition.compose(ROUTE, h.fixture().run(), h.fixture().frb(), h.report(),
                h.tokens(), limited, Set.of(h.candidateArm()), Set.of(BASELINE_MINI, BASELINE_41));
        assertThat(exceeded.workloadEstimate().exceedsLimit()).isTrue();
        assertThat(exceeded).isNotEqualTo(composed);
        assertThat(exceeded.hashCode()).isNotEqualTo(composed.hashCode());
    }

    @Test void missingDuplicateAndExtraLockTokensAreRefused() throws Exception {
        Harness h = harness();
        List<FindingsLedger.ReviewCheck> clean = h.tokens();
        assertThat(h.compose(clean)).isNotNull();
        List<FindingsLedger.ReviewCheck> missing = new ArrayList<>(clean);
        missing.removeLast();
        assertThatThrownBy(() -> h.compose(missing)).isInstanceOf(ReviewComposition.InvalidComposition.class);
        List<FindingsLedger.ReviewCheck> duplicate = replaced(clean, 4, clean.getFirst());
        assertThatThrownBy(() -> h.compose(duplicate)).isInstanceOf(ReviewComposition.InvalidComposition.class);
        List<FindingsLedger.ReviewCheck> extra = new ArrayList<>(clean);
        extra.add(changed(clean.getFirst(), 8, "0".repeat(64)));
        assertThatThrownBy(() -> h.compose(extra)).isInstanceOf(ReviewComposition.InvalidComposition.class);
    }

    @Test void routeAndParameterBoundTokensAreRequired() throws Exception {
        Harness h = harness();
        List<FindingsLedger.ReviewCheck> clean = h.tokens();
        List<FindingsLedger.ReviewCheck> wrongRoute = replaced(clean, 0, changed(clean.getFirst(), 0, "CRITIQUE"));
        List<FindingsLedger.ReviewCheck> wrongParameters = replaced(clean, 0,
                changed(clean.getFirst(), 9, "0".repeat(64)));
        assertThatThrownBy(() -> h.compose(wrongRoute))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
        assertThatThrownBy(() -> h.compose(wrongParameters))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
    }

    @Test void safetyInAnotherLockBlocksEvenWithCleanClassBToken() throws Exception {
        Harness h = harness();
        h.classA().finding("finding_one", List.of(ROUTE));
        assertThat(h.classB().token(h.derived()).openSafetyFindingIds()).isEmpty();
        assertThatThrownBy(h::compose).isInstanceOf(ReviewComposition.InvalidComposition.class);
    }

    @Test void allRoutesFindingInAnotherLockBlocks() throws Exception {
        Harness h = harness();
        h.classA().finding("finding_all", List.of("ALL_ROUTES"));
        assertThatThrownBy(h::compose).isInstanceOf(ReviewComposition.InvalidComposition.class);
    }

    @Test void disputeInAnotherLockBlocks() throws Exception {
        Harness h = harness();
        h.classA().dispute("dispute_one");
        assertThat(h.classB().token(h.derived()).openDisputeIds()).isEmpty();
        assertThatThrownBy(h::compose).isInstanceOf(ReviewComposition.InvalidComposition.class);
    }

    @Test void concernInAnotherLockBlocks() throws Exception {
        Harness h = harness();
        h.classA().review("concern_one", new FindingsLedger.ReviewSubject(
                ReviewRecord.Kind.ABSOLUTE_QUALITY, NOTE, "quality"), ReviewRecord.Outcome.BLOCKING_CONCERN);
        assertThat(h.classB().token(h.derived()).openConcernIds()).isEmpty();
        assertThatThrownBy(h::compose).isInstanceOf(ReviewComposition.InvalidComposition.class);
    }

    @Test void classBReviewOnlyInForeignClassALedgerDoesNotSatisfyCoveringRule() throws Exception {
        Harness h = harness(false, true, false);
        String subject = "CLASS_B_WORKFLOW:" + TIP + ":" + TIP;
        h.classA().review("foreign_b_complete", new FindingsLedger.ReviewSubject(
                ReviewRecord.Kind.CLASS_B_WORKFLOW, TIP, TIP), ReviewRecord.Outcome.COMPLETE);
        assertThat(h.classA().token(h.derived()).missing()).doesNotContain(subject);
        assertThat(h.classB().token(h.derived()).missing()).contains(subject);
        assertThatThrownBy(h::compose).isInstanceOf(ReviewComposition.InvalidComposition.class);
    }

    @Test void missingKeyDefectReviewIsRefusedByRealLedgerTokens() throws Exception {
        Harness h = harness(true, false, false);
        var subject = h.derived().iterator().next();
        String name = subject.kind() + ":" + subject.workflow() + ":" + subject.subjectId();
        assertThat(h.classA().token(h.derived()).missing()).contains(name);
        assertThatThrownBy(h::compose).isInstanceOf(ReviewComposition.InvalidComposition.class);
    }

    @Test void omittedExtraIsRefusedEvenWhenLedgerHasTheCompletedReview() throws Exception {
        Harness h = harness();
        List<FindingsLedger.ReviewCheck> clean = h.tokens();
        List<FindingsLedger.ReviewCheck> omitted = replaced(clean, 0, h.classA().token(Set.of()));
        assertThatThrownBy(() -> h.compose(omitted)).isInstanceOf(ReviewComposition.InvalidComposition.class);
        assertThat(h.compose(clean)).isNotNull();
    }

    @Test void aDefectReviewInTheOtherCoveringClassALedgerCanSatisfyTheSubject() throws Exception {
        Harness h = harness(true, true, true);
        var subject = h.derived().iterator().next();
        String name = subject.kind() + ":" + subject.workflow() + ":" + subject.subjectId();
        assertThat(h.classA().token(h.derived()).missing()).contains(name);
        assertThat(h.ledgers().get(1).token(h.derived()).missing()).doesNotContain(name);
        assertThat(h.compose().requiredKeyDefectSubjects()).contains(subject);
    }

    @Test void reportMustHaveExactNonemptyWorkflowMapAndFrozenHash() throws Exception {
        Harness h = harness();
        var report = h.report();
        var empty = reportWith(report, report.parameterSha256(), Map.of());
        var partial = reportWith(report, report.parameterSha256(), Map.of(NOTE, report.workflows().get(NOTE)));
        var wrongHash = reportWith(report, "0".repeat(64), report.workflows());
        List<FindingsLedger.ReviewCheck> tokens = h.tokens();
        assertThatThrownBy(() -> h.composeReport(empty, tokens))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
        assertThatThrownBy(() -> h.composeReport(partial, tokens))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
        assertThatThrownBy(() -> h.composeReport(wrongHash, tokens))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
    }

    @Test void coverageMustContainEveryReportedWorkflow() {
        Set<String> partial = Set.of(NOTE);
        Set<String> reported = Set.of(NOTE, TIP);
        assertThatThrownBy(() -> ReviewComposition.requireCoverage(partial, reported))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
        ReviewComposition.requireCoverage(Set.of(NOTE, TIP), Set.of(NOTE, TIP));
    }

    @Test void tamperedLockPropagatesNamedProvenanceFailure() throws Exception {
        Harness h = harness();
        List<FindingsLedger.ReviewCheck> tokens = h.tokens();
        Files.writeString(h.classA().lock(), "{}\n");
        assertThatThrownBy(() -> h.compose(tokens)).isInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void longExamRequiresEveryFrozenSubgroupStem() throws Exception {
        Path source = Path.of("src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json");
        Path copied = temp.resolve("long-exam-parameters.json");
        Files.copy(source, copied);
        Files.copy(IntegritySupport.detached(source), IntegritySupport.detached(copied));
        FrozenGateParameters parameters = FrozenGateParameters.load(copied);
        String longExam = "LONG_EXAM";
        Set<String> stems = new HashSet<>();
        parameters.workflows.get(longExam).subgroups().keySet().forEach(group -> stems.add(longExam + "_" + group));
        Method method = ReviewComposition.class.getDeclaredMethod("covered", LockRecord.class,
                FrozenGateParameters.Route.class, FrozenGateParameters.class);
        method.setAccessible(true);
        LockRecord complete = new LockRecord(1, LockRecord.Kind.PRIMARY, stems.stream().sorted().toList(),
                List.of(), EVIDENCE, List.of(), CREATED, EVIDENCE);
        @SuppressWarnings("unchecked")
        Set<String> full = (Set<String>) method.invoke(null, complete, parameters.routes.get(ROUTE), parameters);
        assertThat(full).contains(longExam);
        stems.remove(stems.iterator().next());
        LockRecord partial = new LockRecord(1, LockRecord.Kind.PRIMARY, stems.stream().sorted().toList(),
                List.of(), EVIDENCE, List.of(), CREATED, EVIDENCE);
        @SuppressWarnings("unchecked")
        Set<String> incomplete = (Set<String>) method.invoke(null, partial, parameters.routes.get(ROUTE), parameters);
        assertThat(incomplete).doesNotContain(longExam);
    }

    @Test void requiredSubjectsAreScopedToEachRouteUnderTheRealFrozenParameters() throws Exception {
        Path file = temp.resolve("real-parameters.json");
        Files.copy(Path.of(ReviewRecordStoreTest.PARAMS), file);
        Files.copy(Path.of(ReviewRecordStoreTest.PARAMS + ".sha256"), IntegritySupport.detached(file));
        FrozenGateParameters real = FrozenGateParameters.load(file);
        var tip = new FindingsLedger.ReviewSubject(ReviewRecord.Kind.CLASS_B_WORKFLOW, "STUDY_TIP", "STUDY_TIP");
        var companion = new FindingsLedger.ReviewSubject(ReviewRecord.Kind.CLASS_B_WORKFLOW, "COLLECTION_COMPANION",
                "COLLECTION_COMPANION");
        var ask = new FindingsLedger.ReviewSubject(ReviewRecord.Kind.CLASS_B_WORKFLOW, "ASK_COMPANION", "ASK_COMPANION");
        var critique = new FindingsLedger.ReviewSubject(ReviewRecord.Kind.CLASS_B_WORKFLOW, "INTERVIEW_CRITIQUE",
                "INTERVIEW_CRITIQUE");
        var note = new FindingsLedger.ReviewSubject(ReviewRecord.Kind.KEY_DEFECT, "NOTE_ADAPTIVE", "case_one");
        var interview = new FindingsLedger.ReviewSubject(ReviewRecord.Kind.KEY_DEFECT, "INTERVIEW_GENERATION", "case_two");
        Set<FindingsLedger.ReviewSubject> derived = Set.of(note, interview);
        var interactive = real.routes.get("INTERACTIVE_QUIZ");
        var paid = real.routes.get("PAID_INTERACTIVE");
        var critiqueRoute = real.routes.get("CRITIQUE");
        assertThat(ReviewComposition.routeScoped(derived, interactive)).containsExactly(note);
        assertThat(ReviewComposition.routeScoped(derived, paid)).containsExactly(interview);
        assertThat(ReviewComposition.routeScoped(derived, critiqueRoute)).isEmpty();
        assertThat(ReviewComposition.requiredSubjects(real, interactive, Set.of(note)))
                .containsExactlyInAnyOrder(tip, note);
        assertThat(ReviewComposition.requiredSubjects(real, paid, Set.of(interview)))
                .containsExactlyInAnyOrder(companion, interview);
        assertThat(ReviewComposition.requiredSubjects(real, critiqueRoute, Set.of()))
                .containsExactlyInAnyOrder(ask, critique);
    }

    @Test void composedTypeIsPrivateImmutableAndEveryComponentControlsEquality() throws Exception {
        Harness h = harness();
        var original = h.compose();
        Class<?> type = ReviewComposition.ComposedReviewCheck.class;
        assertThat(type.isRecord()).isFalse();
        assertThat(type.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue());
        assertThatThrownBy(original.lockSha256s()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(original.coveredWorkflows()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(original.requiredKeyDefectSubjects()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(original.heads()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(original.extrasByLock()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(original.candidateArms()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(original.baselineArms()::clear).isInstanceOf(UnsupportedOperationException.class);
        String hash = original.lockSha256s().iterator().next();
        Set<FindingsLedger.ReviewSubject> lockedExtras = original.extrasByLock().get(hash);
        assertThatThrownBy(lockedExtras::clear).isInstanceOf(UnsupportedOperationException.class);
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] values = {original.route(), original.lockSha256s(), original.coveredWorkflows(),
                original.requiredKeyDefectSubjects(), original.heads(), original.extrasByLock(),
                original.workloadParameters(), original.candidateArms(), original.baselineArms(),
                original.workloadEstimate(), original.parametersSha256()};
        Map<String, String> otherHeads = new HashMap<>(original.heads());
        otherHeads.put(otherHeads.keySet().iterator().next(), "0:" + EVIDENCE);
        Map<String, Set<FindingsLedger.ReviewSubject>> otherExtras = new HashMap<>(original.extrasByLock());
        otherExtras.put(hash, Set.of());
        Object[] alternatives = {"CRITIQUE", Set.of("0".repeat(64)), Set.of("OTHER"), Set.of(),
                otherHeads, otherExtras, new KeyDefectWorkload.Parameters(BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.ONE), Set.of("other"), Set.of("other"),
                new KeyDefectWorkload.Estimate(0, Map.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                        0, 0, false, List.of()), "0".repeat(64)};
        for (int index = 0; index < values.length; index++) {
            Object[] changed = values.clone();
            changed[index] = alternatives[index];
            var different = (ReviewComposition.ComposedReviewCheck) constructor.newInstance(changed);
            assertThat(different).as("component " + index).isNotEqualTo(original);
            assertThat(different.hashCode()).as("hash component " + index).isNotEqualTo(original.hashCode());
        }
    }
}
