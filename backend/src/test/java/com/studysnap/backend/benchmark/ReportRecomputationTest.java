package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportRecomputationTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String CREATED = "2026-11-16T00:07:00Z";
    private static final String GENESIS = "2026-11-16T00:05:00Z";
    private static final String EVIDENCE = "a".repeat(64);
    private static final String TEMP_PREFIX = "frb-report-recomputation-";
    private static final String BASELINE_MINI = "BASELINE_MINI";
    private static final String BASELINE_41 = "BASELINE_41";
    private static final String REPORT_COMPONENT = "workflows";
    private static final String CALLER_REPORT = "caller-owned-report";
    private static final String FREEZE = "freeze";
    private static final String RECEIPT = "receipt";
    private static final String PARAMETERS = "parameters";
    private static final String SAFETY = "safety";
    private static final String DISPUTES = "disputes";
    private static final String REVIEWS = "reviews";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final KeyDefectWorkload.Parameters WORKLOAD = new KeyDefectWorkload.Parameters(
            BigDecimal.valueOf(30), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(4));
    @TempDir Path temp;

    private record FileState(String sha256, long size, FileTime mtime) {}
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
    // Ledger and setup are copied and narrowed from ProductionSwitchEligibilityTest's Ledger,
    // harness, ledger, input, ledgers and heads; fixture generation remains FrbPipelineTest.Fixture.
    private record Setup(FrbPipelineTest.Fixture fixture, RouteVerdictCalculator.RouteReport report,
            FrbClassificationInput input, Map<String, FindingsLedger> ledgers,
            Map<String, FindingsLedger.ExternalHeads> heads) {}

    private FrbPipelineTest.Fixture fixture() throws Exception {
        FrbPipelineTest builder = new FrbPipelineTest();
        builder.temp = Files.createTempDirectory(temp, "fixture-");
        return builder.fixture(true, 0, 1, true);
    }

    private Setup setup() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture();
        RouteVerdictCalculator.RouteReport report = ResultsPipeline.runFrb(fixture.run(), fixture.frb())
                .routes().get(ROUTE);
        FrozenGateParameters parameters = FrozenGateParameters.load(fixture.run().parameters());
        Set<String> classA = new HashSet<>(parameters.workflows.keySet());
        classA.removeAll(parameters.frbClassBWorkflows);
        Set<FindingsLedger.ReviewSubject> derived = KeyDefectSubjects.derive(fixture.frb().bundle(),
                ResultsPipeline.workflowByCaseId(fixture.run()), classA, parameters.frbClassBWorkflows);
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
        for (FindingsLedger.ReviewSubject subject : derived)
            ledgers.getFirst().review("defect_complete_" + ++count, subject);
        List<FindingsLedger.ReviewCheck> tokens = new ArrayList<>();
        Map<String, FindingsLedger> byHash = new HashMap<>();
        Map<String, FindingsLedger.ExternalHeads> heads = new HashMap<>();
        for (Ledger ledger : ledgers) {
            tokens.add(ledger.token(derived));
            byHash.put(ledger.hash(), ledger.ledger());
            heads.put(ledger.hash(), ledger.heads());
        }
        String candidate = Stage1Receipt.read(fixture.run().candidate().receipt()).arm();
        var composed = ReviewComposition.compose(ROUTE, fixture.run(), fixture.frb(), report,
                tokens, WORKLOAD, Set.of(candidate), Set.of(BASELINE_MINI, BASELINE_41));
        FrbClassificationInput input = new FrbClassificationInput(Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), false, false, Set.of(), Map.of(NOTE, "NOT_TRIGGERED"),
                composed.heads(), composed);
        return new Setup(fixture, report, input, byHash, heads);
    }

    private Ledger ledger(Path lock, Path seal, Path findings, int index,
            FrbPipelineTest.Fixture fixture) throws Exception {
        PostLockContext context = new PostLockContext(lock, findings,
                seal.getParent().resolve("packets"), seal, fixture.run().parameters());
        Path root = Files.createTempDirectory(temp, "ledger-" + index + "-");
        SafetyFindingStore safety = SafetyFindingStore.init(root.resolve(SAFETY), context, GENESIS);
        EvidenceDisputeStore disputes = EvidenceDisputeStore.init(root.resolve(DISPUTES), context, GENESIS);
        ReviewRecordStore reviews = ReviewRecordStore.init(root.resolve(REVIEWS), context, GENESIS);
        return new Ledger(context, safety, disputes, reviews,
                new FindingsLedger(root.resolve(SAFETY), root.resolve(DISPUTES),
                        root.resolve(REVIEWS), context));
    }

    private static FindingsLedger.ExternalHead external(PostLockStoreIndex.Head head) {
        return new FindingsLedger.ExternalHead(head.seq(), head.sha256());
    }

    private static ProductionSwitchEligibility.Decision check(Setup setup, String route,
            RouteVerdictCalculator.RouteReport report, ResultsPipeline.FrbRunInput run,
            boolean enabled, List<ProductionSwitchEligibility.CostLedgerInput> costLedgers) {
        var decision = ProductionSwitchEligibility.check(route, report, setup.input(), run,
                setup.fixture().frb(),
                setup.ledgers(), setup.heads(), Set.of(), costLedgers, enabled);
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
        return decision;
    }

    private static ProductionSwitchEligibility.Decision check(Setup setup,
            RouteVerdictCalculator.RouteReport report, boolean enabled) {
        return check(setup, ROUTE, report, setup.fixture().run(), enabled, null);
    }

    private static Map<Path, FileState> snapshot(Path root) throws IOException {
        Map<Path, FileState> files = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList())
                files.put(root.relativize(path), new FileState(FreezeRecord.sha256(path),
                        Files.size(path), Files.getLastModifiedTime(path)));
        }
        return files;
    }

    private static Set<String> temporaryReports() throws IOException {
        try (Stream<Path> paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(TEMP_PREFIX)).collect(java.util.stream.Collectors.toSet());
        }
    }

    @Test void deterministicAndFixtureFilesUntouchedAndTempRemoved() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture();
        assertThat(Files.exists(fixture.run().report())).isFalse();
        Map<Path, FileState> before = snapshot(temp);
        Set<String> directories = temporaryReports();
        Map<String, RouteVerdictCalculator.RouteReport> first =
                ReportRecomputation.recompute(fixture.run(), fixture.frb());
        Map<String, RouteVerdictCalculator.RouteReport> second =
                ReportRecomputation.recompute(fixture.run(), fixture.frb());
        assertThat(first).isEqualTo(second);
        assertThat(first).containsKey(ROUTE);
        assertThat(snapshot(temp)).isEqualTo(before);
        assertThat(Files.exists(fixture.run().report())).isFalse();
        assertThat(temporaryReports()).isEqualTo(directories);
    }

    @Test void existingCallerReportBytesRemainUntouched() throws Exception {
        FrbPipelineTest.Fixture fixture = fixture();
        Files.writeString(fixture.run().report(), CALLER_REPORT);
        Map<Path, FileState> before = snapshot(temp);
        ReportRecomputation.recompute(fixture.run(), fixture.frb());
        assertThat(snapshot(temp)).isEqualTo(before);
        assertThat(Files.readString(fixture.run().report())).isEqualTo(CALLER_REPORT);
    }

    @Test void faithfulReportPassesAndValidCostStillCannotSwitch() throws Exception {
        Setup setup = setup();
        Path ledger = temp.resolve("cost-ledger.jsonl");
        Map<StageCapLedger.Stage, BigDecimal> caps = Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE);
        try (StageCapLedger ignored = StageCapLedger.open(ledger, caps, true)) { /* synthetic ledger */ }
        Path approvalFile = temp.resolve("approved.json");
        Files.writeString(approvalFile, "{\"schema\":1,\"caps\":{\"STAGE_1\":\"1\"}}");
        ApprovedCaps approved = ApprovedCaps.load(approvalFile);
        var decision = check(setup, ROUTE, setup.report(), setup.fixture().run(), true,
                List.of(new ProductionSwitchEligibility.CostLedgerInput(ledger, approved)));
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED)
                .doesNotContain(ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED,
                        ProductionSwitchEligibility.REPORT_RECOMPUTATION_FAILED,
                        ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION);
    }

    private static <T> T copy(T source, String component, Object replacement) throws Exception {
        RecordComponent[] fields = source.getClass().getRecordComponents();
        Class<?>[] types = new Class<?>[fields.length];
        Object[] values = new Object[fields.length];
        for (int index = 0; index < fields.length; index++) {
            types[index] = fields[index].getType();
            values[index] = fields[index].getName().equals(component) ? replacement
                    : fields[index].getAccessor().invoke(source);
        }
        var constructor = source.getClass().getDeclaredConstructor(types);
        constructor.setAccessible(true);
        @SuppressWarnings("unchecked") T result = (T) constructor.newInstance(values);
        return result;
    }

    private void mutation(String component, Object replacement) throws Exception {
        Setup setup = setup();
        RouteVerdictCalculator.RouteReport changed = copy(setup.report(), component, replacement);
        assertThat(changed).isNotEqualTo(setup.report());
        assertThat(check(setup, setup.report(), true).reasons())
                .doesNotContain(ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION);
        assertThat(check(setup, changed, true).reasons())
                .contains(ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION);
    }

    @Test void changedVerdictIsDetected() throws Exception {
        mutation("verdict", RouteVerdictCalculator.Verdict.FAIL);
    }

    @Test void changedFailedTierIsDetected() throws Exception {
        mutation("failedTiers", Set.of(RouteVerdictCalculator.Tier.T1));
    }

    @Test void changedParameterShaIsDetected() throws Exception {
        mutation("parameterSha256", "0".repeat(64));
    }

    @Test void removedWorkflowIsDetected() throws Exception {
        Setup setup = setup();
        var changed = copy(setup.report(), REPORT_COMPONENT,
                Map.of(NOTE, setup.report().workflows().get(NOTE)));
        assertThat(check(setup, changed, true).reasons())
                .contains(ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION);
    }

    @Test void addedWorkflowIsDetected() throws Exception {
        Setup setup = setup();
        Map<String, RouteVerdictCalculator.WorkflowReport> workflows =
                new HashMap<>(setup.report().workflows());
        workflows.put("SYNTHETIC_EXTRA", workflows.get(NOTE));
        var changed = copy(setup.report(), REPORT_COMPONENT, workflows);
        assertThat(check(setup, changed, true).reasons())
                .contains(ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION);
    }

    @Test void changedNestedWorkflowCountIsDetected() throws Exception {
        Setup setup = setup();
        var original = setup.report().workflows().get(NOTE);
        var changedWorkflow = copy(original, "paired", original.paired() + 1);
        Map<String, RouteVerdictCalculator.WorkflowReport> workflows =
                new HashMap<>(setup.report().workflows());
        workflows.put(NOTE, changedWorkflow);
        var changed = copy(setup.report(), REPORT_COMPONENT, workflows);
        assertThat(changed).isNotEqualTo(setup.report());
        assertThat(check(setup, changed, true).reasons())
                .contains(ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION);
    }

    @Test void changedOptionalFrbComponentIsDetected() throws Exception {
        Setup setup = setup();
        assertThat(setup.report().frb()).isNotNull();
        var changed = copy(setup.report(), "frb", null);
        assertThat(check(setup, changed, true).reasons())
                .contains(ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION);
    }

    @Test void existingOverloadsRemainOptIn() throws Exception {
        Setup setup = setup();
        String sha = FrozenGateParameters.load(setup.fixture().run().parameters()).sha256;
        RouteVerdictCalculator.WorkflowReport row = new RouteVerdictCalculator.WorkflowReport(
                RouteVerdictCalculator.Verdict.PASS, Set.of(), Set.of(),
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false,
                Map.of(), Map.of(), Map.of(), sha, 0, Set.of(), null, null,
                null, null, null, null, null, null);
        RouteVerdictCalculator.RouteReport handmade = new RouteVerdictCalculator.RouteReport(
                RouteVerdictCalculator.Verdict.PASS, Set.of(), Set.of(), 0, false, sha,
                Map.of(NOTE, row, TIP, row));
        assertThat(handmade).isNotEqualTo(setup.report());
        var run = setup.fixture().run();
        var frb = setup.fixture().frb();
        var first = ProductionSwitchEligibility.check(ROUTE, handmade, setup.input(), run, frb,
                setup.ledgers(), setup.heads(), Set.of());
        var second = ProductionSwitchEligibility.check(ROUTE, handmade, setup.input(), run, frb,
                setup.ledgers(), setup.heads(), Set.of(), null);
        for (var decision : List.of(first, second, check(setup, handmade, false)))
            assertThat(decision.reasons()).doesNotContain(
                    ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION,
                    ProductionSwitchEligibility.REPORT_RECOMPUTATION_FAILED);
        assertThat(check(setup, handmade, true).reasons())
                .contains(ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION);
    }

    @Test void corruptedFreezeReceiptAndParametersFailWithNamedReason() throws Exception {
        for (String target : List.of(FREEZE, RECEIPT, PARAMETERS)) {
            Setup setup = setup();
            Path file = switch (target) {
                case FREEZE -> setup.fixture().run().freeze();
                case RECEIPT -> setup.fixture().run().candidate().receipt();
                default -> setup.fixture().run().parameters();
            };
            Files.writeString(file, "corrupted");
            Set<String> directories = temporaryReports();
            assertThatThrownBy(() -> ReportRecomputation.recompute(
                    setup.fixture().run(), setup.fixture().frb()))
                    .isInstanceOf(InvalidReportRecomputation.class);
            assertThat(check(setup, setup.report(), true).reasons())
                    .contains(ProductionSwitchEligibility.REPORT_RECOMPUTATION_FAILED);
            assertThat(temporaryReports()).isEqualTo(directories);
        }
    }

    @Test void missingRouteCannotPassComparison() throws Exception {
        Setup setup = setup();
        assertThat(check(setup, "UNKNOWN_ROUTE", setup.report(), setup.fixture().run(), true, null)
                .reasons()).contains(ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION);
    }

    @Test void nullFrbInputUsesExistingEvaluationFailureOnly() throws Exception {
        Setup setup = setup();
        var result = ProductionSwitchEligibility.check(ROUTE, setup.report(), setup.input(),
                setup.fixture().run(), null, setup.ledgers(), setup.heads(), Set.of(), null, true);
        assertThat(result.reasons()).contains(ProductionSwitchEligibility.EVALUATION_FAILED)
                .doesNotContain(ProductionSwitchEligibility.REPORT_RECOMPUTATION_FAILED,
                        ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION);
    }

    @Test void pendingFactIsClassifiedByRunFrbAndDerivedBySwitchCheck() throws Exception {
        Setup setup = setup();
        FrbEvidenceBundle source = setup.fixture().frb().bundle();
        Map<String, AdjudicatedFacts> facts = new HashMap<>(source.evidence().byCaseArm());
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
        ResultsPipeline.FrbInput original = setup.fixture().frb();
        ResultsPipeline.FrbInput changed = new ResultsPipeline.FrbInput(bundle,
                original.reserveRecordFile(), original.verification(), original.provenance(), null);
        ResultsPipeline.runFrb(setup.fixture().run(), changed);
        JsonNode reportFile = JSON.readTree(setup.fixture().run().report().toFile());
        assertThat(reportFile.path("deadlineClassification").path(ROUTE).asText())
                .isEqualTo("RETURN_TO_OWNER");
        assertThat(reportFile.path("classification").path(ROUTE).path("causes").toString())
                .contains(ProductionSwitchEligibility.PENDING_REVIEW_ITEMS);
        var decision = ProductionSwitchEligibility.check(ROUTE, setup.report(), setup.input(),
                setup.fixture().run(), changed, setup.ledgers(), setup.heads(), Set.of(), null, true);
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.PENDING_REVIEW_ITEMS);
    }
}
