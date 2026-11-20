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

class DispositionFlagNarrowingTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String CREATED = "2026-11-16T00:07:00Z";
    private static final String GENESIS = "2026-11-16T00:05:00Z";
    private static final String EVIDENCE = "a".repeat(64);
    private static final String FLAG = "REVIEWS_NOT_MACHINE_CHECKED";
    private static final String CAUSE_PREFIX = "DISPOSITIONED_CANDIDATE_CRITICAL:";
    private static final String UNCOVERED = "case";
    private static final String NOT_TRIGGERED = "NOT_TRIGGERED";
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
                false, Set.of(), Map.of(NOTE, NOT_TRIGGERED), heads, check);
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

    private static void reasons(ProductionSwitchEligibility.Decision decision, String... extras) {
        Set<String> expected = new java.util.TreeSet<>(Set.of(
                ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED,
                ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED));
        expected.addAll(List.of(extras));
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons()).containsExactlyElementsOf(expected);
    }


    private static String coveredId(ReviewComposition.ComposedReviewCheck composed) {
        return composed.requiredKeyDefectSubjects().stream()
                .filter(subject -> subject.kind() == ReviewRecord.Kind.KEY_DEFECT
                        && NOTE.equals(subject.workflow()))
                .findFirst().orElseThrow().subjectId();
    }

    private static RouteVerdictCalculator.WorkflowReport withDisposition(
            RouteVerdictCalculator.WorkflowReport row,
            Map<RouteVerdictCalculator.DispositionValue, Set<String>> dispositions,
            Set<String> vetoIds) {
        return new RouteVerdictCalculator.WorkflowReport(row.verdict(), row.failedTiers(),
                row.inconclusiveTiers(), row.paired(), row.d(), row.candidateOnlyT3(),
                row.baselineOnlyT3(), row.sharedT3(), row.candidateOnlyT2(), row.baselineOnlyT2(),
                row.sharedT2(), row.candidateOnlyT1(), row.baselineOnlyT1(), row.sharedT1(),
                row.reserveCandidateOnly(), row.isolatedCriticalIncident(), row.protocolDeviation(),
                row.candidateComponents(), row.baselineComponents(), row.subgroups(),
                row.parameterSha256(), row.missingPairs(), row.undisposedCriticalCaseIds(), row.frb(),
                null, row.recheckOutcomesByCaseId(), row.rejectionEvidenceByCaseId(),
                row.unresolvedCaseIdsByCause(), vetoIds, dispositions, row.unresolvedCriticalIncident());
    }

    private static RouteVerdictCalculator.RouteReport withRow(Harness h,
            Map<RouteVerdictCalculator.DispositionValue, Set<String>> dispositions, Set<String> vetoIds) {
        RouteVerdictCalculator.RouteReport original = h.report();
        return new RouteVerdictCalculator.RouteReport(original.verdict(), original.failedTiers(),
                original.inconclusiveTiers(), original.d(), original.isolatedCriticalIncident(),
                original.parameterSha256(), Map.of(NOTE,
                        withDisposition(original.workflows().get(NOTE), dispositions, vetoIds),
                        TIP, original.workflows().get(TIP)), original.frb());
    }

    private static FrbClassification classify(RouteVerdictCalculator.RouteReport report,
            ReviewComposition.ComposedReviewCheck composed) {
        return DeadlineRule.classifyFrb(report, input(composed, composed.heads(), Set.of(), false, Set.of()));
    }

    private static ProductionSwitchEligibility.Decision eligibility(Harness h,
            RouteVerdictCalculator.RouteReport report, ReviewComposition.ComposedReviewCheck composed) {
        return ProductionSwitchEligibility.check(ROUTE, report,
                input(composed, composed.heads(), Set.of(), false, Set.of()),
                h.fixture().run(), h.fixture().frb(), ledgers(h), heads(h), Set.of());
    }

    @Test void coveredDispositionsClearFlagAndRemainVisible() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        String id = coveredId(composed);
        // NOT_A_DEFECT_ON_REVIEW is a hand-built report case; FRB's calculator disallows it.
        for (var value : List.of(RouteVerdictCalculator.DispositionValue.NOT_A_DEFECT_ON_REVIEW,
                RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED)) {
            var report = withRow(h, Map.of(value, Set.of(id)), null);
            var result = classify(report, composed);
            assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
            assertThat(result.causes()).contains(CAUSE_PREFIX + id);
            assertThat(result.flags()).doesNotContain(FLAG);
            assertThat(result.blocksProductionSwitch()).isFalse();
            reasons(eligibility(h, report, composed));
        }
    }

    @Test void uncoveredClassBAndMixedDispositionsRetainFlag() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        String covered = coveredId(composed);
        String classB = ResultsPipeline.workflowByCaseId(h.fixture().run()).entrySet().stream()
                .filter(entry -> TIP.equals(entry.getValue())).findFirst().orElseThrow().getKey();
        for (Set<String> ids : List.of(Set.of(UNCOVERED), Set.of(classB), Set.of(covered, UNCOVERED))) {
            var report = withRow(h, Map.of(RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                    ids), null);
            var result = classify(report, composed);
            assertThat(result.flags()).contains(FLAG);
            assertThat(result.blocksProductionSwitch()).isTrue();
            reasons(eligibility(h, report, composed),
                    ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH);
        }
    }

    @Test void eachIndependentRouteBlockingSourceVetoes() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        String id = coveredId(composed);
        var byIds = withRow(h, null, Set.of(id));
        var byValue = withRow(h, Map.of(RouteVerdictCalculator.DispositionValue.DEFECT_BLOCKS_ROUTE,
                Set.of(id)), null);
        for (var report : List.of(byIds, byValue)) {
            var result = classify(report, composed);
            assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
            assertThat(result.causes()).contains("OWNER_VETO");
            assertThat(result.blocksProductionSwitch()).isTrue();
            reasons(eligibility(h, report, composed),
                    ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH);
        }
    }

    @Test void openSafetyAndDisputePreemptCoveredDisposition() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        var report = withRow(h, Map.of(RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                Set.of(coveredId(composed))), null);
        var safety = new FrbClassificationInput(Set.of("safety"), Set.of(), Set.of(), Set.of(),
                Set.of(), false, false, Set.of(), Map.of(NOTE, NOT_TRIGGERED), composed.heads(),
                composed);
        var dispute = new FrbClassificationInput(Set.of(), Set.of("dispute"), Set.of(), Set.of(),
                Set.of(), false, false, Set.of(), Map.of(NOTE, NOT_TRIGGERED), composed.heads(),
                composed);
        assertThat(DeadlineRule.classifyFrb(report, safety).classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(DeadlineRule.classifyFrb(report, safety).causes()).contains("OPEN_SAFETY_FINDING");
        assertThat(DeadlineRule.classifyFrb(report, dispute).classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(DeadlineRule.classifyFrb(report, dispute).causes()).contains("OPEN_EVIDENCE_DISPUTE");
        h.ledgers().getFirst().finding("finding_after_composition");
        reasons(eligibility(h, report, composed), ProductionSwitchEligibility.COMPOSITION_FAILED);
    }

    @Test void oldInputWithoutCheckRetainsFlag() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        var report = withRow(h, Map.of(RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED,
                Set.of(coveredId(composed))), null);
        var old = new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of(), Map.of(NOTE, NOT_TRIGGERED), composed.heads());
        assertThat(DeadlineRule.classifyFrb(report, old).flags()).contains(FLAG);
    }

    @Test void coverageIsExactIdEqualityNotASubstringMatch() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        String covered = coveredId(composed);
        String shorter = covered.substring(0, covered.length() - 1);
        assertThat(DeadlineRule.uncoveredDispositionIds(List.of(CAUSE_PREFIX + shorter,
                CAUSE_PREFIX + covered + "x", CAUSE_PREFIX + covered), composed))
                .containsExactlyInAnyOrder(shorter, covered + "x");
    }

    @Test void onlyTheExactDispositionPrefixIsParsedFromCauses() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        assertThat(DeadlineRule.uncoveredDispositionIds(List.of("UNDISPOSED_CANDIDATE_CRITICAL",
                "OTHER_CANDIDATE_CRITICAL:" + UNCOVERED, "X" + CAUSE_PREFIX + UNCOVERED), composed)).isEmpty();
    }

    @Test void anEmptyBlockingDispositionEntryDoesNotVeto() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        String id = coveredId(composed);
        var report = withRow(h, Map.of(RouteVerdictCalculator.DispositionValue.DEFECT_BLOCKS_ROUTE, Set.of(),
                RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED, Set.of(id)), null);
        var result = classify(report, composed);
        assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
        assertThat(result.causes()).doesNotContain("OWNER_VETO");
        assertThat(result.flags()).doesNotContain(FLAG);
    }

    @Test void sharedUncoveredRuleHandlesNullSortAndExactPrefix() throws Exception {
        Harness h = harness(true);
        var composed = h.compose(WORKLOAD);
        String covered = coveredId(composed);
        assertThat(DeadlineRule.uncoveredDispositionIds(List.of(), composed)).isEmpty();
        assertThat(DeadlineRule.uncoveredDispositionIds(List.of(CAUSE_PREFIX + covered), composed))
                .isEmpty();
        assertThat(DeadlineRule.uncoveredDispositionIds(List.of(CAUSE_PREFIX + "z",
                "OTHER:" + UNCOVERED, CAUSE_PREFIX + "a:b", CAUSE_PREFIX + covered), composed))
                .containsExactly("a:b", "z");
        assertThat(DeadlineRule.uncoveredDispositionIds(List.of(CAUSE_PREFIX + covered), null))
                .containsExactly(covered);
        assertThatThrownBy(() -> DeadlineRule.uncoveredDispositionIds(null, composed))
                .isInstanceOf(NullPointerException.class);
    }
}
