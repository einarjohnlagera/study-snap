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

class ProductionSwitchEligibilityTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String CREATED = "2026-11-16T00:07:00Z";
    private static final String GENESIS = "2026-11-16T00:05:00Z";
    private static final String EVIDENCE = "a".repeat(64);
    private static final KeyDefectWorkload.Parameters WORKLOAD = new KeyDefectWorkload.Parameters(
            BigDecimal.valueOf(30), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(4));
    @TempDir Path temp;

    private record Ledger(PostLockContext context, SafetyFindingStore safety,
            EvidenceDisputeStore disputes, ReviewRecordStore reviews, FindingsLedger ledger) {
        String hash() { return context.verify().lockSha256(); }
        FindingsLedger.ExternalHeads heads() {
            return new FindingsLedger.ExternalHeads(external(safety.headSha256()),
                    external(disputes.headSha256()), external(reviews.headSha256()));
        }
        FindingsLedger.ReviewCheck token(Set<FindingsLedger.ReviewSubject> extras) {
            return ledger.checkLedger(ROUTE, extras, heads());
        }
        void review(String id, FindingsLedger.ReviewSubject subject) {
            reviews.create(new ReviewRecord(id, CREATED, hash(), subject.kind(), subject.workflow(),
                    subject.subjectId(), ReviewRecord.Outcome.COMPLETE, "owner",
                    ReviewRecord.SignerRole.OWNER, EVIDENCE));
        }
        String firstItem() {
            return PairMap.fromPackets(context.packetDirectory())
                    .blindItemIds().stream().sorted().findFirst().orElseThrow();
        }
        String workflow() { return context.verify().lock().workflows().getFirst(); }
        void finding(String id) {
            safety.create(new SafetyFinding(id, CREATED, hash(), firstItem(), workflow(), null,
                    List.of(ROUTE), "Incorrect answer", "reviewer",
                    SafetyFinding.Attribution.GENERATOR, false), null);
        }
        void dispute(String id) {
            disputes.create(new EvidenceDispute(id, CREATED, hash(), workflow(), firstItem(), null,
                    "reviewer", "Critical answer disputed", List.of(ROUTE)),
                    (context, verified, workflow, itemId) -> {}, null);
        }
        void concern(String id) {
            reviews.create(new ReviewRecord(id, CREATED, hash(), ReviewRecord.Kind.ABSOLUTE_QUALITY,
                    workflow(), "quality", ReviewRecord.Outcome.BLOCKING_CONCERN, "owner",
                    ReviewRecord.SignerRole.OWNER, EVIDENCE));
        }
    }

    private record Harness(FrbPipelineTest.Fixture fixture, RouteVerdictCalculator.RouteReport report,
            List<Ledger> ledgers, Set<FindingsLedger.ReviewSubject> derived) {
        ReviewComposition.ComposedReviewCheck compose(KeyDefectWorkload.Parameters workload) {
            List<FindingsLedger.ReviewCheck> tokens = new ArrayList<>();
            for (Ledger ledger : ledgers) tokens.add(ledger.token(derived));
            String candidate = Stage1Receipt.read(fixture.run().candidate().receipt()).arm();
            return ReviewComposition.compose(ROUTE, fixture.run(), fixture.frb(), report, tokens,
                    workload, Set.of(candidate), Set.of("BASELINE_MINI", "BASELINE_41"));
        }
    }

    private static FindingsLedger.ExternalHead external(PostLockStoreIndex.Head head) {
        return new FindingsLedger.ExternalHead(head.seq(), head.sha256());
    }

    private Harness harness(boolean completeDefects) throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = temp;
        FrbPipelineTest.Fixture fixture = builder.fixture(true, 0, 1, true);
        String sha = FrozenGateParameters.load(fixture.run().parameters()).sha256;
        RouteVerdictCalculator.WorkflowReport row = new RouteVerdictCalculator.WorkflowReport(
                RouteVerdictCalculator.Verdict.PASS, Set.of(), Set.of(),
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false,
                Map.of(), Map.of(), Map.of(), sha, 0, Set.of(), null, null,
                null, null, null, null, null, null);
        RouteVerdictCalculator.RouteReport report = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.PASS, Set.of(), Set.of(), 0, false, sha,
                Map.of(NOTE, row, TIP, row));
        FrozenGateParameters parameters = FrozenGateParameters.load(fixture.run().parameters());
        Map<String, String> workflows = ResultsPipeline.workflowByCaseId(fixture.run());
        Set<String> classA = new HashSet<>(parameters.workflows.keySet());
        classA.removeAll(parameters.frbClassBWorkflows);
        Set<FindingsLedger.ReviewSubject> derived = KeyDefectSubjects.derive(fixture.frb().bundle(),
                workflows, classA, parameters.frbClassBWorkflows);
        List<Ledger> ledgers = new ArrayList<>();
        for (ResultsPipeline.PrimaryExport export : fixture.frb().provenance().primaries())
            ledgers.add(ledger(export.lockFile(), export.sealFile(), export.findingsDirectory(),
                    ledgers.size(), fixture));
        for (ResultsPipeline.ReserveExport export : fixture.frb().provenance().reserves().values()) {
            ledgers.add(ledger(export.lockForPrimaryOpening(), export.sealFile(),
                    export.findingsDirectory(), ledgers.size(), fixture));
            ledgers.add(ledger(export.lockForReserveOpening(), export.sealFile(),
                    export.findingsDirectory(), ledgers.size(), fixture));
        }
        ledgers.get(2).review("class_b_complete", new FindingsLedger.ReviewSubject(
                ReviewRecord.Kind.CLASS_B_WORKFLOW, TIP, TIP));
        int count = 0;
        if (completeDefects) for (FindingsLedger.ReviewSubject subject : derived)
            ledgers.getFirst().review("defect_complete_" + ++count, subject);
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
        return new Ledger(context, SafetyFindingStore.init(safety, context, GENESIS),
                EvidenceDisputeStore.init(disputes, context, GENESIS),
                ReviewRecordStore.init(reviews, context, GENESIS),
                new FindingsLedger(safety, disputes, reviews, context));
    }

    private static FrbClassificationInput input(ReviewComposition.ComposedReviewCheck check,
            Map<String, String> heads, Set<String> costs, boolean failure, Set<String> missing) {
        return new FrbClassificationInput(Set.of(), Set.of(), missing, costs, Set.of(), failure,
                false, Set.of(), Map.of(NOTE, "NOT_TRIGGERED"), heads, check);
    }

    private static Map<String, FindingsLedger> ledgers(Harness h) {
        Map<String, FindingsLedger> result = new java.util.HashMap<>();
        h.ledgers().forEach(ledger -> result.put(ledger.hash(), ledger.ledger()));
        return result;
    }

    private static Map<String, FindingsLedger.ExternalHeads> heads(Harness h) {
        Map<String, FindingsLedger.ExternalHeads> result = new java.util.HashMap<>();
        h.ledgers().forEach(ledger -> result.put(ledger.hash(), ledger.heads()));
        return result;
    }

    private static ProductionSwitchEligibility.Decision check(Harness h,
            ReviewComposition.ComposedReviewCheck composed,
            Map<String, FindingsLedger> ledgers,
            Map<String, FindingsLedger.ExternalHeads> noted,
            Set<String> pendingRoutes) {
        return ProductionSwitchEligibility.check(ROUTE, h.report(),
                input(composed, composed.heads(), Set.of(), false, Set.of()),
                h.fixture().run(), h.fixture().frb(), ledgers, noted, pendingRoutes);
    }

    private static void reasons(ProductionSwitchEligibility.Decision decision, String... extras) {
        Set<String> expected = new java.util.TreeSet<>(Set.of(
                ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED,
                ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED));
        expected.addAll(List.of(extras));
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons()).containsExactlyElementsOf(expected);
    }

    @Test void cleanControlHasExactlyTwoPermanentReasons() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        reasons(check(h, composed, ledgers(h), heads(h), Set.of()));
    }

    @Test void staleHeadAndMissingLedgerFailBeforeComposition() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        Map<String, FindingsLedger> missing = ledgers(h);
        missing.remove(h.ledgers().getFirst().hash());
        reasons(check(h, composed, missing, heads(h), Set.of()),
                ProductionSwitchEligibility.EVALUATION_FAILED);
        Map<String, FindingsLedger.ExternalHeads> stale = heads(h);
        h.ledgers().getFirst().review("late_clean", new FindingsLedger.ReviewSubject(
                ReviewRecord.Kind.ABSOLUTE_QUALITY, NOTE, "late"));
        reasons(check(h, composed, ledgers(h), stale, Set.of()),
                ProductionSwitchEligibility.LEDGER_CHECK_FAILED);
    }

    @Test void tamperedReviewStoreFailsTheFreshLedgerCheck() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        Map<String, FindingsLedger.ExternalHeads> noted = heads(h);
        Files.writeString(temp.resolve("ledger-0/reviews/index.jsonl"), "tampered\n");
        reasons(check(h, composed, ledgers(h), noted, Set.of()),
                ProductionSwitchEligibility.LEDGER_CHECK_FAILED);
    }

    @Test void cleanAppendWithFreshNotesMustDiffer() throws Exception {
        for (int index = 0; index < 5; index++) {
            temp = Files.createDirectories(temp.resolve("lock-" + index));
            Harness h = harness(true);
            var composed = h.compose(WORKLOAD);
            Ledger changed = h.ledgers().get(index);
            String workflow = changed.context().verify().lock().workflows().getFirst();
            changed.review("late_clean", new FindingsLedger.ReviewSubject(
                    ReviewRecord.Kind.ABSOLUTE_QUALITY, workflow, "late"));
            reasons(check(h, composed, ledgers(h), heads(h), Set.of()),
                    ProductionSwitchEligibility.COMPOSITION_DIFFERS);
        }
    }

    @Test void openFindingDisputeAndConcernInAnyLockRefuseFreshComposition() throws Exception {
        Path base = temp;
        for (String type : List.of("finding", "dispute", "concern")) {
            for (int index = 0; index < 5; index++) {
                temp = Files.createDirectories(base.resolve(type + "-" + index));
                Harness h = harness(true);
                Ledger changed = h.ledgers().get(index);
                var composed = h.compose(WORKLOAD);
                if ("finding".equals(type)) changed.finding("late_finding");
                if ("dispute".equals(type)) changed.dispute("late_dispute");
                if ("concern".equals(type)) changed.concern("late_concern");
                reasons(check(h, composed, ledgers(h), heads(h), Set.of()),
                        ProductionSwitchEligibility.COMPOSITION_FAILED);
            }
        }
    }

    @Test void costAndCallerPendingAreSeparatelyReported() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        FrbClassificationInput cost = input(composed, composed.heads(), Set.of("COST_REVIEW"),
                false, Set.of());
        reasons(ProductionSwitchEligibility.check(ROUTE, h.report(), cost, h.fixture().run(),
                h.fixture().frb(), ledgers(h), heads(h), Set.of()),
                ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH);
        reasons(check(h, composed, ledgers(h), heads(h), Set.of(ROUTE)),
                ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH,
                ProductionSwitchEligibility.PENDING_REVIEW_ITEMS);
    }

    @Test void routeMismatchAndReportMismatchFailClosed() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        ProductionSwitchEligibility.Decision route = ProductionSwitchEligibility.check("CRITIQUE",
                h.report(), input(composed, composed.heads(), Set.of(), false, Set.of()),
                h.fixture().run(), h.fixture().frb(), ledgers(h), heads(h), Set.of());
        reasons(route, ProductionSwitchEligibility.REVIEW_CHECK_ROUTE_MISMATCH);
        var report = new RouteVerdictCalculator.RouteReport(h.report().verdict(),
                h.report().failedTiers(), h.report().inconclusiveTiers(), h.report().d(),
                h.report().isolatedCriticalIncident(), h.report().parameterSha256(),
                Map.of(NOTE, h.report().workflows().get(NOTE)), h.report().frb());
        reasons(ProductionSwitchEligibility.check(ROUTE, report,
                input(composed, composed.heads(), Set.of(), false, Set.of()),
                h.fixture().run(), h.fixture().frb(), ledgers(h), heads(h), Set.of()),
                ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH,
                ProductionSwitchEligibility.COMPOSITION_FAILED);
    }

    @Test void nullsAndMalformedReportReturnEvaluationFailed() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        var facts = input(composed, composed.heads(), Set.of(), false, Set.of());
        reasons(ProductionSwitchEligibility.check(ROUTE, h.report(), null, h.fixture().run(),
                h.fixture().frb(), ledgers(h), heads(h), null),
                ProductionSwitchEligibility.EVALUATION_FAILED);
        reasons(ProductionSwitchEligibility.check(ROUTE, h.report(),
                input(null, composed.heads(), Set.of(), false, Set.of()), h.fixture().run(),
                h.fixture().frb(), ledgers(h), heads(h), null),
                ProductionSwitchEligibility.EVALUATION_FAILED);
        reasons(ProductionSwitchEligibility.check(ROUTE, null, facts, h.fixture().run(),
                h.fixture().frb(), ledgers(h), heads(h), null),
                ProductionSwitchEligibility.EVALUATION_FAILED);
        reasons(ProductionSwitchEligibility.check(ROUTE, h.report(), facts, h.fixture().run(),
                h.fixture().frb(), null, heads(h), null),
                ProductionSwitchEligibility.EVALUATION_FAILED);
        reasons(ProductionSwitchEligibility.check(null, h.report(), facts, h.fixture().run(),
                h.fixture().frb(), ledgers(h), heads(h), null),
                ProductionSwitchEligibility.EVALUATION_FAILED);
        reasons(ProductionSwitchEligibility.check(ROUTE, h.report(), facts, null,
                h.fixture().frb(), ledgers(h), heads(h), null),
                ProductionSwitchEligibility.EVALUATION_FAILED);
        reasons(ProductionSwitchEligibility.check(ROUTE, h.report(), facts, h.fixture().run(),
                null, ledgers(h), heads(h), null),
                ProductionSwitchEligibility.EVALUATION_FAILED);
        reasons(ProductionSwitchEligibility.check(ROUTE, h.report(), facts, h.fixture().run(),
                h.fixture().frb(), ledgers(h), null, null),
                ProductionSwitchEligibility.EVALUATION_FAILED);
        var malformed = new RouteVerdictCalculator.RouteReport(h.report().verdict(),
                h.report().failedTiers(), h.report().inconclusiveTiers(), h.report().d(),
                h.report().isolatedCriticalIncident(), h.report().parameterSha256(), null);
        reasons(ProductionSwitchEligibility.check(ROUTE, malformed, facts, h.fixture().run(),
                h.fixture().frb(), ledgers(h), heads(h), null),
                ProductionSwitchEligibility.EVALUATION_FAILED);
    }

    @Test void evidencePendingIsDerivedEvenWhenCallerPendingIsNull() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        FrbEvidenceBundle source = h.fixture().frb().bundle();
        Map<String, AdjudicatedFacts> facts = new java.util.HashMap<>(source.evidence().byCaseArm());
        String identity = facts.keySet().iterator().next();
        AdjudicatedFacts previous = facts.get(identity);
        facts.put(identity, new AdjudicatedFacts(previous.scoredQuestions(), previous.criticalQuestions(),
                previous.qualityFailQuestions(), previous.unresolvedQuestions(), 1,
                previous.doubleMisses(), previous.scorerFailureCriticals(), previous.humanCleared(),
                previous.subgroup()));
        AdjudicatedLoaded evidence = new AdjudicatedLoaded(facts, source.evidence().excludedCaseIds(),
                source.evidence().requestedQuestionsByCaseId(), source.evidence().excludedReasons());
        FrbEvidenceBundle bundle = new FrbEvidenceBundle(evidence, source.requestedQuestionsByCaseId(),
                source.reserveDrawnByWorkflow(), source.workflowsAwaitingReserve());
        ResultsPipeline.FrbInput original = h.fixture().frb();
        ResultsPipeline.FrbInput changed = new ResultsPipeline.FrbInput(bundle,
                original.reserveRecordFile(), original.verification(), original.provenance(),
                original.routeClassifications());
        ProductionSwitchEligibility.Decision result = ProductionSwitchEligibility.check(ROUTE,
                h.report(), input(composed, composed.heads(), Set.of(), false, Set.of()),
                h.fixture().run(), changed, ledgers(h), heads(h), null);
        assertThat(result.reasons()).contains(ProductionSwitchEligibility.PENDING_REVIEW_ITEMS);
    }

    @Test void classifierBindingFailuresRemainSwitchFailures() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        var shaReport = new RouteVerdictCalculator.RouteReport(h.report().verdict(),
                h.report().failedTiers(), h.report().inconclusiveTiers(), h.report().d(),
                h.report().isolatedCriticalIncident(), "0".repeat(64), h.report().workflows());
        reasons(ProductionSwitchEligibility.check(ROUTE, shaReport,
                input(composed, composed.heads(), Set.of(), false, Set.of()),
                h.fixture().run(), h.fixture().frb(), ledgers(h), heads(h), Set.of()),
                ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH,
                ProductionSwitchEligibility.COMPOSITION_FAILED);
        Map<String, String> wrongHeads = new java.util.HashMap<>(composed.heads());
        wrongHeads.put(wrongHeads.keySet().iterator().next(), "0:" + EVIDENCE);
        reasons(ProductionSwitchEligibility.check(ROUTE, h.report(),
                input(composed, wrongHeads, Set.of(), false, Set.of()),
                h.fixture().run(), h.fixture().frb(), ledgers(h), heads(h), Set.of()),
                ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH);
    }
}
