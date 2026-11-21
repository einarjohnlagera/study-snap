package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostEvidenceProducerTest {
    private static final StageCapLedger.Stage ONE = StageCapLedger.Stage.STAGE_1;
    private static final StageCapLedger.Stage TWO = StageCapLedger.Stage.STAGE_2;
    private static final String CELL = "synthetic-cell";
    private static final String FREE_TEXT = "some free text";
    private static final String OTHER_REASON = "OTHER";
    private static final String PROVIDER_REASON = "PROVIDER_BOUND_EXCEEDED";
    private static final String UNMETERED_TYPE = "UNMETERED";
    private static final String STOP_TYPE = "STOP";
    private static final String SAFETY_DIR = "safety";
    private static final String DISPUTES_DIR = "disputes";
    private static final String REVIEWS_DIR = "reviews";
    private static final String JSON_SUFFIX = ".json";
    private static final String NEWLINE = "\n";
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String NOTE = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String GENESIS = "2026-11-16T00:05:00Z";
    private static final String CREATED = "2026-11-16T00:07:00Z";
    private static final String EVIDENCE = "a".repeat(64);
    private static final String MODEL = "gpt-6-luna";
    private static final String BODY = "{\"model\":\"gpt-6-luna\",\"input\":[{\"role\":\"user\",\"content\":[{\"type\":\"input_text\",\"text\":\"x\"}]}],"
            + "\"text\":{\"format\":{\"type\":\"json_schema\",\"name\":\"test_schema\",\"schema\":{},\"strict\":true}}}";
    private static final BigDecimal CAP = new BigDecimal("1");
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal LOW = new BigDecimal("0.4");
    private static final BigDecimal HIGH = new BigDecimal("1.1");
    private static final BigDecimal EPSILON = new BigDecimal("0.000000001");
    private static final KeyDefectWorkload.Parameters WORKLOAD = new KeyDefectWorkload.Parameters(
            BigDecimal.valueOf(30), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(4));
    @TempDir Path temp;

    private Path file(String name) { return temp.resolve(name + ".jsonl"); }
    private Path ledger(String name, Map<StageCapLedger.Stage, BigDecimal> caps) {
        Path file = file(name);
        try (StageCapLedger ignored = StageCapLedger.open(file, caps, true)) { return file; }
    }
    private static Map<StageCapLedger.Stage, BigDecimal> caps(BigDecimal cap) { return Map.of(ONE, cap); }
    private ApprovedCaps approved(Map<StageCapLedger.Stage, BigDecimal> caps) throws Exception {
        Path path = temp.resolve("approved-" + java.util.UUID.randomUUID() + JSON_SUFFIX);
        StringBuilder json = new StringBuilder("{\"schema\":1,\"caps\":{");
        for (var entry : caps.entrySet()) {
            if (json.charAt(json.length() - 1) != '{') json.append(',');
            json.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
        }
        json.append("}}");
        Files.writeString(path, json.toString());
        return ApprovedCaps.load(path);
    }
    private CostEvidence produce(Path file, Map<StageCapLedger.Stage, BigDecimal> caps) throws Exception {
        return CostEvidenceProducer.produce(file, approved(caps));
    }
    private static void has(CostEvidence evidence, String flag) { assertThat(evidence.flags()).contains(flag); }
    private static void lacks(CostEvidence evidence, String flag) { assertThat(evidence.flags()).doesNotContain(flag); }

    @Test void cleanLedgerHasExactNumbersAndDigestAndSmallestPositiveCap() throws Exception {
        Path file = ledger("clean", caps(CAP));
        try (StageCapLedger opened = StageCapLedger.open(file, caps(CAP), false)) {
            opened.reserve(ONE, CELL, HALF).settle(HALF, LOW);
        }
        CostEvidence evidence = produce(file, caps(CAP));
        assertThat(evidence.flags()).isEmpty();
        assertThat(evidence.capByStage().get(ONE)).isEqualByComparingTo(CAP);
        assertThat(evidence.spentByStage().get(ONE)).isEqualByComparingTo(HALF);
        assertThat(evidence.billedByStage().get(ONE)).isEqualByComparingTo(LOW);
        assertThat(evidence.recordCount()).isEqualTo(3);
        assertThat(evidence.ledgerSha256()).isEqualTo(FreezeRecord.sha256(file));
        Path tiny = ledger("tiny", caps(EPSILON));
        assertThat(produce(tiny, caps(EPSILON)).flags()).isEmpty();
    }

    @Test void finalCapsCompareInBothDirectionsAndMissingStages() throws Exception {
        Path file = ledger("cap-difference", caps(CAP));
        has(produce(file, caps(CAP.add(EPSILON))), CostEvidenceProducer.CAPS_DIFFER + ONE);
        has(produce(file, caps(CAP.subtract(EPSILON))), CostEvidenceProducer.CAPS_DIFFER + ONE);
        lacks(produce(file, caps(CAP)), CostEvidenceProducer.CAPS_DIFFER + ONE);
        has(produce(file, Map.of(ONE, CAP, TWO, CAP)), CostEvidenceProducer.CAPS_DIFFER + TWO);
        Path extra = ledger("extra", Map.of(ONE, CAP, TWO, CAP));
        has(produce(extra, caps(CAP)), CostEvidenceProducer.CAPS_DIFFER + TWO);
    }

    @Test void bothAmendDirectionsFlagButFinalApprovalDoesNotDiffer() throws Exception {
        Path raised = ledger("raised", Map.of(ONE, LOW, TWO, CAP));
        try (StageCapLedger opened = StageCapLedger.open(raised, Map.of(ONE, LOW, TWO, CAP), false)) {
            opened.amendCaps(Map.of(ONE, CAP, TWO, CAP));
        }
        CostEvidence raise = produce(raised, Map.of(ONE, CAP, TWO, CAP));
        has(raise, CostEvidenceProducer.CAP_AMENDED + ONE);
        lacks(raise, CostEvidenceProducer.CAPS_DIFFER + ONE);
        lacks(raise, CostEvidenceProducer.CAP_AMENDED + TWO);
        Path lowered = ledger("lowered", caps(CAP));
        try (StageCapLedger opened = StageCapLedger.open(lowered, caps(CAP), false)) {
            opened.amendCaps(caps(LOW));
        }
        has(produce(lowered, caps(LOW)), CostEvidenceProducer.CAP_AMENDED + ONE);
        lacks(produce(ledger("unamended", caps(CAP)), caps(CAP)), CostEvidenceProducer.CAP_AMENDED + ONE);
    }

    @Test void everyHistoricalStopAndResumeIsVisibleWithoutRawText() throws Exception {
        Path file = ledger("stops", caps(CAP));
        try (StageCapLedger opened = StageCapLedger.open(file, caps(CAP), false)) {
            opened.reserve(ONE, CELL, LOW).settle(null, null);
            opened.resume();
            opened.stop(PROVIDER_REASON);
            opened.resume();
            opened.stop(FREE_TEXT);
            opened.resume();
        }
        CostEvidence evidence = produce(file, caps(CAP));
        has(evidence, CostEvidenceProducer.STOP_RECORDED + UNMETERED_TYPE);
        has(evidence, CostEvidenceProducer.STOP_RECORDED + PROVIDER_REASON);
        has(evidence, CostEvidenceProducer.STOP_RECORDED + OTHER_REASON);
        has(evidence, CostEvidenceProducer.RESUMED);
        assertThat(evidence.flags()).allSatisfy(flag -> assertThat(flag).doesNotContain(FREE_TEXT));
        Path noStop = ledger("no-stop", caps(CAP));
        lacks(produce(noStop, caps(CAP)), CostEvidenceProducer.STOP_RECORDED + OTHER_REASON);
        lacks(produce(noStop, caps(CAP)), CostEvidenceProducer.RESUMED);
    }

    @Test void providerBoundOverrunWritesSettleAndStopAndIsFlagged() throws Exception {
        Path file = ledger("provider-bound", caps(CAP));
        ProviderBoundReservation bounds = new ProviderBoundReservation(Map.of(MODEL,
                new ProviderBoundReservation.ModelPrice(10, BigDecimal.ONE, BigDecimal.valueOf(2))),
                1000, 10, LocalDate.of(2026, 11, 19));
        try (StageCapLedger opened = StageCapLedger.open(file, caps(CAP), false)) {
            ArmAssertingInterceptor path = new ArmAssertingInterceptor(new ObjectMapper(), MODEL,
                    "fake-offline-api-key", temp, opened, ONE, BigDecimal.ONE, BigDecimal.valueOf(2),
                    Map.of(CELL, new ForecastReservation(1, 1, CAP, BigDecimal.valueOf(2), CAP)),
                    bounds, null);
            path.begin("synthetic-generation", CELL);
            try {
                path.intercept(new MockClientHttpRequest(HttpMethod.POST,
                                URI.create("https://offline.invalid/responses")),
                        BODY.getBytes(StandardCharsets.UTF_8),
                        (request, outgoing) -> new MockClientHttpResponse(
                                "{\"usage\":{\"input_tokens\":1,\"output_tokens\":11}}"
                                        .getBytes(StandardCharsets.UTF_8), HttpStatus.OK));
            } finally {
                path.end();
            }
        }
        CostEvidence evidence = produce(file, caps(CAP));
        has(evidence, CostEvidenceProducer.STOP_RECORDED + PROVIDER_REASON);
        assertThat(Files.readString(file)).contains("SETTLE", STOP_TYPE);
    }

    @Test void completedUnmeteredRecordBeforeStopAppendStillFlags() throws Exception {
        Path file = ledger("unmetered-before-stop", caps(CAP));
        try (StageCapLedger opened = StageCapLedger.open(file, caps(CAP), false)) {
            opened.reserve(ONE, CELL, LOW).settle(null, null);
        }
        List<String> records = Files.readAllLines(file);
        assertThat(records).hasSize(4);
        assertThat(records.get(2)).contains(UNMETERED_TYPE);
        assertThat(records.get(3)).contains(STOP_TYPE);
        Files.writeString(file, String.join(NEWLINE, records.subList(0, 3)) + NEWLINE);
        CostEvidence evidence = produce(file, caps(CAP));
        has(evidence, CostEvidenceProducer.STOP_RECORDED + UNMETERED_TYPE);
        assertThat(evidence.recordCount()).isEqualTo(3);
    }

    @Test void pendingReservationFlagsUnreconciledAndInFlightButReconcileClearsBoth() throws Exception {
        Path file = ledger("pending", caps(CAP));
        String id;
        try (StageCapLedger opened = StageCapLedger.open(file, caps(CAP), false)) {
            id = opened.reserve(ONE, CELL, LOW).id();
        }
        CostEvidence pending = produce(file, caps(CAP));
        has(pending, CostEvidenceProducer.UNRECONCILED);
        has(pending, CostEvidenceProducer.IN_FLIGHT + ONE);
        try (StageCapLedger opened = StageCapLedger.open(file, caps(CAP), false)) {
            opened.reconcile(id, LOW);
        }
        CostEvidence settled = produce(file, caps(CAP));
        lacks(settled, CostEvidenceProducer.UNRECONCILED);
        lacks(settled, CostEvidenceProducer.IN_FLIGHT + ONE);
    }

    @Test void overshootUsesApprovedCapAndEqualSpendDoesNotFlag() throws Exception {
        Path file = ledger("overshoot", caps(CAP));
        try (StageCapLedger opened = StageCapLedger.open(file, caps(CAP), false)) {
            opened.reserve(ONE, CELL, LOW).settle(HIGH, HIGH);
        }
        has(produce(file, caps(CAP)), CostEvidenceProducer.SPEND_OVER_CAP + ONE);
        has(produce(file, caps(LOW)), CostEvidenceProducer.SPEND_OVER_CAP + ONE);
        Path equal = ledger("equal", caps(CAP));
        try (StageCapLedger opened = StageCapLedger.open(equal, caps(CAP), false)) {
            opened.reserve(ONE, CELL, LOW).settle(CAP, CAP);
        }
        lacks(produce(equal, caps(CAP)), CostEvidenceProducer.SPEND_OVER_CAP + ONE);
        Path approvalOnly = ledger("approval-only-overspend", caps(HIGH));
        try (StageCapLedger opened = StageCapLedger.open(approvalOnly, caps(HIGH), false)) {
            opened.reserve(ONE, CELL, LOW).settle(HALF, HALF);
        }
        has(produce(approvalOnly, caps(LOW)), CostEvidenceProducer.SPEND_OVER_CAP + ONE);
    }

    @Test void billedGreaterThanConservativeIsNamedFailureAndLeavesLedgerUntouched() throws Exception {
        Path file = ledger("bad-billed", caps(CAP));
        try (StageCapLedger opened = StageCapLedger.open(file, caps(CAP), false)) {
            opened.reserve(ONE, CELL, LOW).settle(LOW, HALF);
        }
        String digest = FreezeRecord.sha256(file);
        ApprovedCaps approval = approved(caps(CAP));
        assertThatThrownBy(() -> CostEvidenceProducer.produce(file, approval))
                .isInstanceOf(InvalidCostEvidence.class).hasMessageContaining("Billed exceeds");
        assertThat(FreezeRecord.sha256(file)).isEqualTo(digest);
    }

    @Test void missingEmptyInitlessDuplicateTruncatedUnknownAllFailClosed() throws Exception {
        Path valid = ledger("valid", caps(CAP));
        String init = Files.readString(valid);
        Map<String, String> bad = Map.of(
                "empty", "",
                "initless", "{\"type\":\"RESUME\"}\n",
                "duplicate", init + init,
                "truncated", init + "{\"type\":\"RESUME\"",
                "unknown", init + "{\"type\":\"MYSTERY\"}\n");
        ApprovedCaps approval = approved(caps(CAP));
        for (var entry : bad.entrySet()) {
            Path file = file(entry.getKey());
            Files.writeString(file, entry.getValue());
            String digest = FreezeRecord.sha256(file);
            assertThatThrownBy(() -> CostEvidenceProducer.produce(file, approval))
                    .as(entry.getKey()).isInstanceOf(InvalidCostEvidence.class);
            assertThat(FreezeRecord.sha256(file)).isEqualTo(digest);
        }
        Path missing = file("missing");
        assertThatThrownBy(() -> CostEvidenceProducer.produce(missing, approval))
                .isInstanceOf(InvalidCostEvidence.class);
    }

    @Test void invalidApprovalFilesAndRepoPathsFailClosed() throws Exception {
        Path ledger = ledger("approval-guard", caps(CAP));
        String digest = FreezeRecord.sha256(ledger);
        Map<String, String> bad = Map.of(
                "unknown-stage", "{\"schema\":1,\"caps\":{\"STAGE_4\":\"1\"}}",
                "zero", "{\"schema\":1,\"caps\":{\"STAGE_1\":\"0\"}}",
                "negative", "{\"schema\":1,\"caps\":{\"STAGE_1\":\"-1\"}}",
                "missing-cap", "{\"schema\":1,\"caps\":{}}",
                "unknown-field", "{\"schema\":1,\"caps\":{\"STAGE_1\":\"1\"},\"extra\":true}",
                "malformed", "{");
        for (var entry : bad.entrySet()) {
            Path file = temp.resolve(entry.getKey() + JSON_SUFFIX);
            Files.writeString(file, entry.getValue());
            assertThatThrownBy(() -> ApprovedCaps.load(file)).as(entry.getKey())
                    .isInstanceOf(InvalidCostEvidence.class);
            assertThat(FreezeRecord.sha256(ledger)).isEqualTo(digest);
        }
        Path missing = temp.resolve("missing-approval.json");
        assertThatThrownBy(() -> ApprovedCaps.load(missing)).isInstanceOf(InvalidCostEvidence.class);
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        assertThatThrownBy(() -> CostEvidenceProducer.requireExternal(repo.resolve("AGENTS.md")))
                .isInstanceOf(InvalidCostEvidence.class);
        assertThatThrownBy(() -> ApprovedCaps.load(repo.resolve("approval.json")))
                .isInstanceOf(InvalidCostEvidence.class);
        ApprovedCaps approval = approved(caps(CAP));
        assertThatThrownBy(() -> CostEvidenceProducer.produce(repo.resolve("ledger.jsonl"), approval))
                .isInstanceOf(InvalidCostEvidence.class);
        assertThat(FreezeRecord.sha256(ledger)).isEqualTo(digest);
    }

    @Test void originalIsReadOnlyAndScanTracksTwoAmendsAndResume() throws Exception {
        Path file = ledger("read-only", caps(CAP));
        try (StageCapLedger opened = StageCapLedger.open(file, caps(CAP), false)) {
            opened.amendCaps(caps(HIGH));
            opened.amendCaps(caps(LOW));
            opened.stop(FREE_TEXT);
            opened.resume();
        }
        byte[] before = Files.readAllBytes(file);
        long size = Files.size(file);
        FileTime modified = Files.getLastModifiedTime(file);
        CostEvidence evidence = produce(file, caps(LOW));
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
        assertThat(Files.size(file)).isEqualTo(size);
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(modified);
        assertThat(evidence.recordCount()).isEqualTo(5);
        assertThat(evidence.capByStage().get(ONE)).isEqualByComparingTo(LOW);
        has(evidence, CostEvidenceProducer.CAP_AMENDED + ONE);
        has(evidence, CostEvidenceProducer.RESUMED);
        has(evidence, CostEvidenceProducer.STOP_RECORDED + OTHER_REASON);
    }

    @Test void stoppedOriginalCanStayLockedAndTheTemporaryCopyIsRemoved() throws Exception {
        Path file = ledger("locked-original", caps(CAP));
        Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir"));
        Set<String> before = temporaryCopies(temporaryRoot);
        byte[] bytes;
        long size;
        FileTime modified;
        try (StageCapLedger opened = StageCapLedger.open(file, caps(CAP), false)) {
            opened.stop(FREE_TEXT);
            bytes = Files.readAllBytes(file);
            size = Files.size(file);
            modified = Files.getLastModifiedTime(file);
            has(produce(file, caps(CAP)), CostEvidenceProducer.STOP_RECORDED + OTHER_REASON);
        }
        assertThat(Files.readAllBytes(file)).isEqualTo(bytes);
        assertThat(Files.size(file)).isEqualTo(size);
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(modified);
        assertThat(temporaryCopies(temporaryRoot)).isEqualTo(before);
    }

    private static Set<String> temporaryCopies(Path root) throws Exception {
        try (var entries = Files.list(root)) {
            return entries.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("cost-evidence-"))
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    @Test void eachDistinctAmendAndTheFinalCapAreScanned() throws Exception {
        Path file = ledger("two-amends", caps(CAP));
        try (StageCapLedger opened = StageCapLedger.open(file, caps(CAP), false)) {
            opened.amendCaps(Map.of(ONE, CAP, TWO, CAP));
            opened.amendCaps(Map.of(ONE, HIGH, TWO, CAP));
            opened.stop(FREE_TEXT);
            opened.resume();
        }
        CostEvidence evidence = produce(file, Map.of(ONE, HIGH, TWO, CAP));
        assertThat(evidence.recordCount()).isEqualTo(5);
        has(evidence, CostEvidenceProducer.CAP_AMENDED + ONE);
        has(evidence, CostEvidenceProducer.CAP_AMENDED + TWO);
        has(evidence, CostEvidenceProducer.RESUMED);
        lacks(evidence, CostEvidenceProducer.CAPS_DIFFER + ONE);
        lacks(evidence, CostEvidenceProducer.CAPS_DIFFER + TWO);
    }

    // Copied and narrowed from ProductionSwitchEligibilityTest: Ledger, Harness, harness, ledger, input,
    // ledgers and heads. These keep the real composition and lock checks in the eligibility tests.
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
        ReviewComposition.ComposedReviewCheck compose() {
            List<FindingsLedger.ReviewCheck> tokens = new ArrayList<>();
            for (Ledger ledger : ledgers) tokens.add(ledger.token(derived));
            String candidate = Stage1Receipt.read(fixture.run().candidate().receipt()).arm();
            return ReviewComposition.compose(ROUTE, fixture.run(), fixture.frb(), report, tokens,
                    WORKLOAD, Set.of(candidate), Set.of("BASELINE_MINI", "BASELINE_41"));
        }
    }
    private static FindingsLedger.ExternalHead external(PostLockStoreIndex.Head head) {
        return new FindingsLedger.ExternalHead(head.seq(), head.sha256());
    }
    private Harness harness() throws Exception {
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
        for (FindingsLedger.ReviewSubject subject : derived)
            ledgers.getFirst().review("defect_complete_" + ++count, subject);
        return new Harness(fixture, report, List.copyOf(ledgers), derived);
    }
    private Ledger ledger(Path lock, Path seal, Path findings, int index,
            FrbPipelineTest.Fixture fixture) throws Exception {
        PostLockContext context = new PostLockContext(lock, findings,
                seal.getParent().resolve("packets"), seal, fixture.run().parameters());
        Path root = Files.createDirectories(temp.resolve("ledger-" + index));
        SafetyFindingStore safety = SafetyFindingStore.init(root.resolve(SAFETY_DIR), context, GENESIS);
        EvidenceDisputeStore disputes = EvidenceDisputeStore.init(root.resolve(DISPUTES_DIR), context, GENESIS);
        ReviewRecordStore reviews = ReviewRecordStore.init(root.resolve(REVIEWS_DIR), context, GENESIS);
        return new Ledger(context, safety, disputes, reviews,
                new FindingsLedger(root.resolve(SAFETY_DIR), root.resolve(DISPUTES_DIR),
                        root.resolve(REVIEWS_DIR), context));
    }
    private static FrbClassificationInput input(ReviewComposition.ComposedReviewCheck check,
            Set<String> costs) {
        return new FrbClassificationInput(Set.of(), Set.of(), Set.of(), costs, Set.of(), false,
                false, Set.of(), Map.of(NOTE, "NOT_TRIGGERED"), check.heads(), check);
    }
    private static Map<String, FindingsLedger> ledgers(Harness h) {
        Map<String, FindingsLedger> result = new HashMap<>();
        h.ledgers().forEach(ledger -> result.put(ledger.hash(), ledger.ledger()));
        return result;
    }
    private static Map<String, FindingsLedger.ExternalHeads> heads(Harness h) {
        Map<String, FindingsLedger.ExternalHeads> result = new HashMap<>();
        h.ledgers().forEach(ledger -> result.put(ledger.hash(), ledger.heads()));
        return result;
    }
    private static ProductionSwitchEligibility.Decision check(Harness h, Set<String> costs,
            List<ProductionSwitchEligibility.CostLedgerInput> inputs) {
        var composed = h.compose();
        return ProductionSwitchEligibility.check(ROUTE, h.report(), input(composed, costs),
                h.fixture().run(), h.fixture().frb(), ledgers(h), heads(h), Set.of(), inputs);
    }

    @Test void matchingOneAndTwoLedgersDropOnlyCostReason() throws Exception {
        Harness h = harness();
        Path first = ledger("eligibility-one", caps(CAP));
        ApprovedCaps approval = approved(caps(CAP));
        var one = new ProductionSwitchEligibility.CostLedgerInput(first, approval);
        var valid = check(h, Set.of(), List.of(one));
        assertThat(valid.eligible()).isFalse();
        assertThat(valid.reasons()).contains(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED)
                .doesNotContain(ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
        Path second = ledger("eligibility-two", Map.of(TWO, CAP));
        var two = new ProductionSwitchEligibility.CostLedgerInput(second, approved(Map.of(TWO, CAP)));
        assertThat(check(h, Set.of(), List.of(one, two)).reasons())
                .doesNotContain(ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
        assertThat(check(h, Set.of(), null).reasons())
                .contains(ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
        assertThat(check(h, Set.of(), List.of()).reasons())
                .contains(ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
        var composed = h.compose();
        assertThat(ProductionSwitchEligibility.check(ROUTE, h.report(), input(composed, Set.of()),
                h.fixture().run(), h.fixture().frb(), ledgers(h), heads(h), Set.of()).reasons())
                .contains(ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
    }

    @Test void duplicateOrFailedLedgerCannotRemovePermanentCostReason() throws Exception {
        Harness h = harness();
        Path file = ledger("eligibility-duplicate", caps(CAP));
        String digest = FreezeRecord.sha256(file);
        var item = new ProductionSwitchEligibility.CostLedgerInput(file, approved(caps(CAP)));
        assertThat(check(h, Set.of(), List.of(item, item)).reasons())
                .contains(ProductionSwitchEligibility.COST_EVIDENCE_FAILED,
                        ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
        Path alias = temp.resolve("alias-ledger.jsonl");
        Files.createSymbolicLink(alias, file);
        var aliased = new ProductionSwitchEligibility.CostLedgerInput(alias, item.approved());
        assertThat(check(h, Set.of(), List.of(item, aliased)).reasons())
                .contains(ProductionSwitchEligibility.COST_EVIDENCE_FAILED,
                        ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
        var bad = new ProductionSwitchEligibility.CostLedgerInput(temp.resolve("missing.jsonl"), item.approved());
        assertThat(check(h, Set.of(), List.of(item, bad)).reasons())
                .contains(ProductionSwitchEligibility.COST_EVIDENCE_FAILED,
                        ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
        assertThat(FreezeRecord.sha256(file)).isEqualTo(digest);
    }

    @Test void exactUnionEqualityAndNonemptyMatchingFlagsStillBlock() throws Exception {
        Harness h = harness();
        Path file = ledger("eligibility-flags", caps(CAP));
        try (StageCapLedger opened = StageCapLedger.open(file, caps(CAP), false)) {
            opened.stop(FREE_TEXT);
        }
        String flag = CostEvidenceProducer.STOP_RECORDED + OTHER_REASON;
        var item = new ProductionSwitchEligibility.CostLedgerInput(file, approved(caps(CAP)));
        assertThat(check(h, null, List.of(item)).reasons())
                .contains(ProductionSwitchEligibility.COST_FLAGS_DIFFER_FROM_LEDGER,
                        ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
        assertThat(check(h, Set.of(), List.of(item)).reasons())
                .contains(ProductionSwitchEligibility.COST_FLAGS_DIFFER_FROM_LEDGER,
                        ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
        var matching = check(h, Set.of(flag), List.of(item));
        assertThat(matching.reasons()).contains(ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH)
                .doesNotContain(ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
        Path clean = ledger("eligibility-clean", caps(CAP));
        var cleanItem = new ProductionSwitchEligibility.CostLedgerInput(clean, approved(caps(CAP)));
        assertThat(check(h, Set.of(flag), List.of(cleanItem)).reasons())
                .contains(ProductionSwitchEligibility.COST_FLAGS_DIFFER_FROM_LEDGER,
                        ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED);
    }

    @Test void costFlagEqualityIsSetEqualityAcrossTwoLedgers() throws Exception {
        Harness h = harness();
        Path first = ledger("ordered-one", caps(CAP));
        try (StageCapLedger opened = StageCapLedger.open(first, caps(CAP), false)) {
            opened.stop(FREE_TEXT);
        }
        Path second = ledger("ordered-two", Map.of(TWO, CAP));
        try (StageCapLedger opened = StageCapLedger.open(second, Map.of(TWO, CAP), false)) {
            opened.stop(PROVIDER_REASON);
        }
        var inputs = List.of(
                new ProductionSwitchEligibility.CostLedgerInput(first, approved(caps(CAP))),
                new ProductionSwitchEligibility.CostLedgerInput(second, approved(Map.of(TWO, CAP))));
        Set<String> reverse = new java.util.LinkedHashSet<>();
        reverse.add(CostEvidenceProducer.STOP_RECORDED + PROVIDER_REASON);
        reverse.add(CostEvidenceProducer.STOP_RECORDED + OTHER_REASON);
        assertThat(check(h, reverse, inputs).reasons())
                .doesNotContain(ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED,
                        ProductionSwitchEligibility.COST_FLAGS_DIFFER_FROM_LEDGER)
                .contains(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
    }
}
