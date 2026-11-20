package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

class ReviewFlagDropTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String CREATED = "2026-11-16T00:07:00Z";
    private static final String GENESIS = "2026-11-16T00:05:00Z";
    private static final String EVIDENCE = "a".repeat(64);
    private static final String REVIEW_FLAG = "REVIEWS_NOT_MACHINE_CHECKED";
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
        return harness(completeDefects, false);
    }

    private Harness harness(boolean completeDefects, boolean excludedShortDelivery) throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = temp;
        FrbPipelineTest.Fixture fixture = excludedShortDelivery
                ? builder.fixture(false, 0, 2, false, false, true) : builder.fixture(true, 0, 1, true);
        if (excludedShortDelivery) failArm(fixture.run().candidate());
        String sha = FrozenGateParameters.load(fixture.run().parameters()).sha256;
        RouteVerdictCalculator.WorkflowReport row = new RouteVerdictCalculator.WorkflowReport(
                RouteVerdictCalculator.Verdict.PASS, Set.of(), Set.of(),
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false,
                Map.of(), Map.of(), Map.of(), sha, 0, Set.of(), null, null,
                null, null, null, null, null, null);
        RouteVerdictCalculator.RouteReport report = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.PASS, Set.of(), Set.of(), 0, false, sha,
                Map.of(NOTE, row, TIP, row));
        if (excludedShortDelivery)
            report = ResultsPipeline.runFrb(fixture.run(), fixture.frb()).routes().get(ROUTE);
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

    private static void failArm(ResultsPipeline.ArmFiles arm) throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        ObjectNode receipt = (ObjectNode) json.readTree(arm.receipt().toFile());
        List<String> lines = new ArrayList<>(Files.readAllLines(arm.delivered()));
        for (int index = 0; index < receipt.path("cases").size(); index++) {
            ObjectNode row = (ObjectNode) receipt.path("cases").get(index);
            if (!"a-three".equals(row.path("id").asText())) continue;
            row.put("state", "FAILED").put("finalSuccess", false).put("firstAttemptClean", false)
                    .put("failureClass", "SHORT_DELIVERY").put("deliveredQuestionCount", 1);
            String line = json.writeValueAsString(new DeliveredOutputs.Line("a-three", NOTE,
                    receipt.path("arm").asText(), row.path("manifestRowSha256").asText(), 0,
                    CaseArmOutcome.State.FAILED, "SHORT_DELIVERY", null));
            lines.set(index, line);
            row.put("deliveredOutputSha256", FreezeRecord.sha256(line.getBytes(StandardCharsets.UTF_8)));
        }
        Files.writeString(arm.delivered(), String.join("\n", lines) + "\n");
        receipt.put("deliveredOutputsFileSha256", FreezeRecord.sha256(arm.delivered()));
        json.writeValue(arm.receipt().toFile(), receipt);
    }

    private static FrbClassificationInput input(ReviewComposition.ComposedReviewCheck check,
            Map<String, String> heads, Set<String> costs, boolean failure, Set<String> missing) {
        return new FrbClassificationInput(Set.of(), Set.of(), missing, costs, Set.of(), failure,
                false, Set.of(), Map.of(NOTE, "NOT_TRIGGERED"), heads, check);
    }

    private static void flag(RouteVerdictCalculator.RouteReport report, FrbClassificationInput facts) {
        FrbClassification result = DeadlineRule.classifyFrb(report, facts);
        assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
        assertThat(result.flags()).contains(REVIEW_FLAG);
        assertThat(result.blocksProductionSwitch()).isTrue();
    }

    @Test void cleanCompositionDropsFlagAndAllowsClassifierLevelSwitch() throws Exception {
        Harness h = harness(true);
        var check = h.compose(WORKLOAD);
        assertThat(check.workloadEstimate().exceedsLimit()).isFalse();
        FrbClassification result = DeadlineRule.classifyFrb(h.report(),
                input(check, check.heads(), Set.of(), false, Set.of()));
        assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
        assertThat(result.flags()).doesNotContain(REVIEW_FLAG);
        assertThat(result.blocksProductionSwitch()).isFalse();
    }

    @Test void eachReachableBindingKeepsFlag() throws Exception {
        Harness h = harness(true);
        var check = h.compose(WORKLOAD);
        flag(h.report(), new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                false, false, Set.of(), Map.of(NOTE, "NOT_TRIGGERED"), check.heads()));
        var row = h.report().workflows().get(NOTE);
        flag(report(h.report(), Map.of(NOTE, row), h.report().parameterSha256()),
                input(check, check.heads(), Set.of(), false, Set.of()));
        flag(report(h.report(), Map.of(), h.report().parameterSha256()),
                input(check, check.heads(), Set.of(), false, Set.of()));
        flag(report(h.report(), h.report().workflows(), "0".repeat(64)),
                input(check, check.heads(), Set.of(), false, Set.of()));
        var missingHead = new java.util.HashMap<>(check.heads());
        String first = missingHead.keySet().iterator().next();
        missingHead.remove(first);
        flag(h.report(), input(check, missingHead, Set.of(), false, Set.of()));
        missingHead.put(first, "0:" + EVIDENCE);
        flag(h.report(), input(check, missingHead, Set.of(), false, Set.of()));
    }

    private static RouteVerdictCalculator.RouteReport report(RouteVerdictCalculator.RouteReport source,
            Map<String, RouteVerdictCalculator.WorkflowReport> workflows, String sha) {
        return new RouteVerdictCalculator.RouteReport(source.verdict(), source.failedTiers(),
                source.inconclusiveTiers(), source.d(), source.isolatedCriticalIncident(),
                sha, workflows, source.frb());
    }

    @Test void dispositionRetainsFlagAndCostStillBlocksSwitch() throws Exception {
        Harness h = harness(true);
        var check = h.compose(WORKLOAD);
        var row = h.report().workflows().get(NOTE);
        var disposition = new RouteVerdictCalculator.WorkflowReport(row.verdict(), row.failedTiers(),
                row.inconclusiveTiers(), row.paired(), row.d(), row.candidateOnlyT3(), row.baselineOnlyT3(),
                row.sharedT3(), row.candidateOnlyT2(), row.baselineOnlyT2(), row.sharedT2(),
                row.candidateOnlyT1(), row.baselineOnlyT1(), row.sharedT1(), row.reserveCandidateOnly(),
                row.isolatedCriticalIncident(), row.protocolDeviation(), row.candidateComponents(),
                row.baselineComponents(), row.subgroups(), row.parameterSha256(), row.missingPairs(),
                row.undisposedCriticalCaseIds(), row.frb(), row.causes(), row.recheckOutcomesByCaseId(),
                row.rejectionEvidenceByCaseId(),
                row.unresolvedCaseIdsByCause(), row.ownerVetoCaseIds(),
                Map.of(RouteVerdictCalculator.DispositionValue.DEFECT_ACKNOWLEDGED, Set.of("case")),
                row.unresolvedCriticalIncident());
        flag(report(h.report(), Map.of(NOTE, disposition, TIP, h.report().workflows().get(TIP)),
                h.report().parameterSha256()), input(check, check.heads(), Set.of(), false, Set.of()));
        FrbClassification cost = DeadlineRule.classifyFrb(h.report(),
                input(check, check.heads(), Set.of("COST_REVIEW"), false, Set.of()));
        assertThat(cost.flags()).doesNotContain(REVIEW_FLAG).contains("COST_REVIEW");
        assertThat(cost.blocksProductionSwitch()).isTrue();
    }

    @Test void prePassFailuresDoNotGainReviewFlag() throws Exception {
        Harness h = harness(true);
        var check = h.compose(WORKLOAD);
        List<FrbClassificationInput> failures = new ArrayList<>();
        failures.add(null);
        failures.add(input(check, check.heads(), Set.of(), true, Set.of()));
        failures.add(input(check, check.heads(), Set.of(), false, null));
        failures.add(input(check, check.heads(), Set.of(), false, Set.of("missing")));
        failures.add(input(check, null, Set.of(), false, Set.of()));
        failures.add(input(check, Map.of(), Set.of(), false, Set.of()));
        for (FrbClassificationInput facts : failures) {
            FrbClassification result = DeadlineRule.classifyFrb(h.report(), facts);
            assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
            assertThat(result.flags()).doesNotContain(REVIEW_FLAG);
            assertThat(result.blocksProductionSwitch()).isTrue();
        }
    }

    @Test void workloadLimitIsKnownOwnerCauseAndExactlyAtLimitDropsFlag() throws Exception {
        Harness h = harness(true);
        var check = h.compose(WORKLOAD);
        BigDecimal total = check.workloadEstimate().totalHours();
        var atLimit = h.compose(new KeyDefectWorkload.Parameters(BigDecimal.valueOf(30),
                BigDecimal.ONE, total, BigDecimal.valueOf(4)));
        assertThat(atLimit.workloadEstimate().exceedsLimit()).isFalse();
        assertThat(DeadlineRule.classifyFrb(h.report(), input(atLimit, atLimit.heads(),
                Set.of(), false, Set.of())).flags()).doesNotContain(REVIEW_FLAG);
        var over = h.compose(new KeyDefectWorkload.Parameters(BigDecimal.valueOf(30),
                BigDecimal.ONE, total.subtract(BigDecimal.valueOf(0.01)), BigDecimal.valueOf(4)));
        FrbClassification result = DeadlineRule.classifyFrb(h.report(),
                input(over, over.heads(), Set.of(), false, Set.of()));
        assertThat(CauseTable.known("KEY_DEFECT_REVIEW_WORKLOAD_EXCEEDS_LIMIT")).isTrue();
        assertThat(CauseTable.kind("KEY_DEFECT_REVIEW_WORKLOAD_EXCEEDS_LIMIT"))
                .isEqualTo(CauseTable.Kind.RETURN_TO_OWNER);
        assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(result.causes()).contains("KEY_DEFECT_REVIEW_WORKLOAD_EXCEEDS_LIMIT");
        assertThat(result.flags()).doesNotContain(REVIEW_FLAG);
        assertThat(result.blocksProductionSwitch()).isTrue();
    }

    @Test void missingSharedArmDefectReviewCannotMintCheck() throws Exception {
        Harness h = harness(false);
        assertThat(h.derived()).isNotEmpty();
        String candidate = Stage1Receipt.read(h.fixture().run().candidate().receipt()).arm();
        Map<String, AdjudicatedFacts> facts = h.fixture().frb().bundle().evidence().byCaseArm();
        assertThat(h.derived()).anySatisfy(subject -> {
            String stem = subject.subjectId() + "\u0000";
            assertThat(facts.get(stem + candidate).criticalQuestions()).isPositive();
            assertThat(facts.get(stem + "BASELINE_MINI").criticalQuestions()).isPositive();
        });
        assertThatThrownBy(() -> h.compose(WORKLOAD))
                .isInstanceOf(ReviewComposition.InvalidComposition.class);
        FrbClassification result = DeadlineRule.classifyFrb(h.report(),
                new FrbClassificationInput(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                        false, false, Set.of(), Map.of(NOTE, "NOT_TRIGGERED"), Map.of("safety", "0:head")));
        assertThat(result.flags()).contains(REVIEW_FLAG);
        assertThat(result.blocksProductionSwitch()).isTrue();
    }

    @Test void excludedShortDeliveryCannotBecomeFlaglessPass() throws Exception {
        Harness h = harness(true, true);
        var check = h.compose(WORKLOAD);
        FrbClassification result = DeadlineRule.classifyFrb(h.report(),
                input(check, check.heads(), Set.of(), false, Set.of()));
        assertThat(h.report().workflows().get(NOTE).causes()).contains("SHORT_DELIVERY_CANDIDATE");
        assertThat(result.causes()).contains("SHORT_DELIVERY_CANDIDATE");
        assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(result.blocksProductionSwitch()).isTrue();
        assertThat(ResultsPipeline.renderFrbClassification(result)).startsWith("RETURN_TO_OWNER");
        assertThat(check.requiredKeyDefectSubjects()).noneMatch(subject -> "a-three".equals(subject.subjectId()));
        Map<String, FindingsLedger> ledgers = new java.util.HashMap<>();
        Map<String, FindingsLedger.ExternalHeads> heads = new java.util.HashMap<>();
        for (Ledger ledger : h.ledgers()) {
            ledgers.put(ledger.hash(), ledger.ledger());
            heads.put(ledger.hash(), ledger.heads());
        }
        var decision = ProductionSwitchEligibility.check(ROUTE, h.report(),
                input(check, check.heads(), Set.of(), false, Set.of()), h.fixture().run(),
                h.fixture().frb(), ledgers, heads, Set.of());
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH,
                ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED,
                ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
    }
}
