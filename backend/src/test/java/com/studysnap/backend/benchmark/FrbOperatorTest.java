package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FrbOperator end to end on the 5r-2a combined fixture. Copied privately, and named here:
 * RouteClassificationInputsTest.setup(withReserve, expensiveCandidate) (itself copied from
 * FrbBundleAssemblerTest with FrbPipelineTest's receipt re-stamp; the class-A key opens after the
 * reserve decision and, with a drawn reserve, binds the reserve forPrimary lock), its detached and
 * restampReceipts helpers, its Ledger, Harness, harness, ledger, costLedger and approved helpers;
 * LockAnchorsTest.secondLock and rewriteLock. Tests whose name starts with "seam" drive the flow
 * through a test-only FrbRunner or SwitchChecker, or call requireNoDrift directly; every other test
 * runs the real ResultsPipeline.runFrb and ProductionSwitchEligibility.checkWithOptions.
 */
class FrbOperatorTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String STEM = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String MINI = "BASELINE_MINI";
    private static final String PAID = "BASELINE_41";
    private static final String LUNA = "LUNA";
    private static final String CLOCK = "2026-11-16T00:00:30Z";
    private static final String OPENED = "2026-11-16T00:07:00Z";
    private static final String A_OPENED = "2026-11-16T00:05:30Z";
    private static final String CREATED = "2026-11-16T00:07:00Z";
    private static final String LATER = "2026-11-16T00:08:00Z";
    private static final String GENESIS = "2026-11-16T00:05:00Z";
    private static final String HUMAN = "HUMAN_OWNER";
    private static final String CASE_ONE = "a-one";
    private static final String CASE_TWO = "a-two";
    private static final String PACKETS_NAME = "packets";
    private static final String FINDINGS_NAME = "findings";
    private static final String CLASS_A_KEY_FILE = "key.jsonl";
    private static final String ARM_KEY_FILE = "arm-key.jsonl";
    private static final String ACCESS_LOG_NAME = "access.jsonl";
    private static final String ASSEMBLER_ACCESS_LOG = "bundle-assembler-access.jsonl";
    private static final String SPOT_PLAN = "classA.spotCheckPlan";
    private static final String MANIFESTS_FIELD = "manifests";
    private static final String BIOLOGY_LABEL = "Biology";
    private static final String CLEAN_GRADE = "C";
    private static final String SHA_SUFFIX = ".sha256";
    private static final String NEWLINE = "\n";
    private static final String FREEZE_SHA_FIELD = "freezeSha256";
    private static final String SAFETY = "safety";
    private static final String DISPUTES = "disputes";
    private static final String REVIEWS = "reviews";
    private static final String OWNER = "owner";
    private static final String EVIDENCE = "a".repeat(64);
    private static final String OTHER_HASH = "0".repeat(64);
    private static final String JSON_SUFFIX = ".json";
    private static final String TEMP_ROOT = "java.io.tmpdir";
    private static final String COST_CAPS = "{\"schema\":1,\"caps\":{\"STAGE_1\":\"1\"}}";
    private static final String CLASSIFICATION_FIELD = "classification";
    private static final String DEADLINE_FIELD = "deadlineClassification";
    private static final String HEADS_FIELD = "ledgerHeadHashes";
    private static final String FLAGS_FIELD = "flags";
    private static final String NOT_ANCHORED = "not in the noted anchors";
    private static final String SET_DIFFERS = "Noted anchor set differs";
    private static final String DRIFT = "Classification drift";
    private static final String INVALID_REPORT = "report on disk is INVALID";
    private static final String CLASSIFICATION_A_PRIMARY = "classA.primary";
    private static final String SYNTHETIC = "synthetic";
    private static final KeyDefectWorkload.Parameters WORKLOAD = new KeyDefectWorkload.Parameters(
            BigDecimal.valueOf(30), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(4));
    private static final Map<String, ClassBScoreFiles.ScorerKind> SCORERS =
            Map.of(HUMAN, ClassBScoreFiles.ScorerKind.HUMAN);
    private static final RouteClassificationInputs.OwnerFacts OWNER_FACTS = new RouteClassificationInputs.OwnerFacts(
            WORKLOAD, Set.of(LUNA), Set.of(MINI, PAID), false);
    private static final List<FrbOperator.Step> ALL_STEPS = List.of(FrbOperator.Step.values());
    @TempDir Path temp;

    // ---- Copied from RouteClassificationInputsTest.setup (the 5r-2a combined fixture) ----

    private record Setup(FrbBundleAssembler.Assembled assembled, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb, ClassASources aSources, ClassBSources b, Path aRoot) {
        FrbBundleAssembler.Sources bundleSources() { return new FrbBundleAssembler.Sources(List.of(aSources), b); }
        ResultsPipeline.PrimaryExport aPrimary() { return frb.provenance().primaries().getFirst(); }
        ResultsPipeline.PrimaryExport bPrimary() { return frb.provenance().primaries().getLast(); }
        ResultsPipeline.ReserveExport reserve() { return frb.provenance().reserves().get(TIP); }
    }

    private Setup setup(boolean withReserve) throws Exception {
        return setup(withReserve, false);
    }

    private Setup setup(boolean withReserve, boolean expensiveCandidate) throws Exception {
        Path base = Files.createTempDirectory(temp, "fixture-");
        FrbPipelineTest pipeline = new FrbPipelineTest();
        pipeline.temp = base;
        FrbPipelineTest.Fixture pipelineFixture = pipeline.fixture(withReserve, 0, 2, false);
        ResultsPipeline.FrbRunInput run = pipelineFixture.run();
        Path plan = base.resolve("spot-plan.json");
        Files.writeString(plan, "{\"stems\":[{\"stem\":\"" + STEM + "\",\"count\":1}],\"expansion\":[{\"workflow\":\""
                + STEM + "\",\"stemPriority\":[\"" + STEM + "\"],\"initialAllocation\":{\"" + STEM + "\":3}}]}");
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.freeze()));
        ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(plan.toAbsolutePath().toString(), FreezeRecord.sha256(plan));
        Files.writeString(run.freeze(), freeze.toString());
        detached(run.freeze());
        restampReceipts(run, expensiveCandidate);
        Map<String, Path> manifests = new HashMap<>(run.manifests());
        manifests.put(SPOT_PLAN, plan);
        Path root = base.resolve("assembled-a");
        Files.createDirectories(root);
        var source = new ClassACaseManifest.Source("Cells", ClassAScoreSchemaV2Test.SOURCE, "Boundary",
                List.of("membrane"), List.of("cells"), BIOLOGY_LABEL, BIOLOGY_LABEL, null, LearnerLevel.COLLEGE);
        List<ClassACaseManifest.Case> cases = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        ArrayNode questions = ClassAScoreSchemaV2Test.JSON.createArrayNode();
        for (int i = 0; i < 2; i++) questions.add(ClassAScoreSchemaV2Test.packet("MCQ").question().deepCopy());
        for (String id : List.of(CASE_ONE, CASE_TWO)) {
            cases.add(new ClassACaseManifest.Case(id, ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                    SYNTHETIC, LearnerLevel.COLLEGE, null, BIOLOGY_LABEL, false,
                    List.of(source), 2, null, false, null));
            outputs.add(new ClassABlindPacketExport.Output(id, MINI, questions));
            outputs.add(new ClassABlindPacketExport.Output(id, LUNA, questions));
        }
        var exported = ClassABlindPacketExport.export(cases, outputs, MINI, LUNA,
                root.resolve(PACKETS_NAME), root.resolve(CLASS_A_KEY_FILE), 17L);
        PairMap pairs = PairMap.fromPackets(root.resolve(PACKETS_NAME));
        Path directory = root.resolve(FINDINGS_NAME);
        Files.createDirectories(directory);
        Path registry = directory.resolve("seeds.json");
        Files.writeString(registry, "{\"seed\":\"synthetic-seed-123456789\",\"streams\":["
                + "{\"workflow\":\"" + STEM + "\",\"purpose\":\"SPOT_CHECK\",\"rounds\":1},"
                + "{\"workflow\":\"" + STEM + "\",\"purpose\":\"EXPANSION\",\"rounds\":3}]}");
        detached(registry);
        Path log = directory.resolve("draws.jsonl");
        DrawLog.Entry draw = PairSampler.draw(SeedStreamRegistry.load(registry), new DrawLog(log), pairs,
                STEM, SeedStreamRegistry.SPOT_CHECK, 1,
                pairs.pairs().stream().map(PairMap.Pair::id)
                        .filter(id -> STEM.equals(pairs.workflowOf(id))).toList(), 1, CLOCK);
        Set<String> selections = Set.copyOf(draw.selectedPairIds());
        List<String> pRows = new ArrayList<>(), sRows = new ArrayList<>(), hRows = new ArrayList<>();
        for (String id : pairs.blindItemIds().stream().sorted().toList()) {
            pRows.add(ClassAAdjudicationFixture.scorer(id, "P", CLEAN_GRADE, false).toString());
            sRows.add(ClassAAdjudicationFixture.scorer(id, "S", CLEAN_GRADE, false).toString());
            if (selections.contains(pairs.pairOf(id)))
                hRows.add(ClassAAdjudicationFixture.human(id, CLEAN_GRADE, false).toString());
        }
        Path p = directory.resolve("p.jsonl"), s = directory.resolve("s.jsonl"), h = directory.resolve("human.jsonl");
        Files.write(p, pRows); Files.write(s, sRows); Files.write(h, hRows);
        Path seal = ArmKeyGateFixtures.seal(root, exported.armKey());
        Path lock = root.resolve("lock.json");
        ArmKeySeal.Seal sealed = ArmKeySeal.load(seal);
        LockRecord.create(lock, LockRecord.Kind.PRIMARY, sealed.workflowStems(), List.of(p, s, h, log, registry),
                sealed, seal, files -> {}, ArmKeyGateFixtures.LOCKED);
        List<ArmKeyAccess.LockBinding> bindings = new ArrayList<>(List.of(ArmKeyGateFixtures.binding(lock, seal, h)));
        Set<LockRecord.Kind> kinds = Set.of(LockRecord.Kind.PRIMARY);
        if (withReserve) {
            ResultsPipeline.ReserveExport reserve = pipelineFixture.frb().provenance().reserves().get(TIP);
            bindings.add(new ArmKeyAccess.LockBinding(reserve.lockForPrimaryOpening(), reserve.sealFile(),
                    reserve.findingsDirectory()));
            kinds = Set.of(LockRecord.Kind.PRIMARY, LockRecord.Kind.RESERVE);
        }
        ArmKeyAccess.OpenedKey openedA = ArmKeyAccess.open(exported.armKey(), seal, bindings, kinds,
                root.resolve(ACCESS_LOG_NAME), A_OPENED, "route inputs test");
        ClassAExport a = new ClassAExport(root.resolve(PACKETS_NAME), p, s, h, openedA, seal, lock,
                exported.exclusions(), FreezeRecord.sha256(exported.exclusions()));
        ClassASources aSources = new ClassASources(run.freeze(), run.parameters(), run.mapping(),
                Map.copyOf(manifests), a, log, registry);

        ResultsPipeline.FrbInput original = pipelineFixture.frb();
        ResultsPipeline.ReserveVerification verification = original.verification();
        Path primaryRoot = verification.sealFile().getParent();
        ArmKeyAccess.OpenedKey primaryOpened = ArmKeyAccess.open(primaryRoot.resolve(ARM_KEY_FILE),
                verification.sealFile(), List.of(ArmKeyGateFixtures.binding(verification.primaryLockFile(),
                        verification.sealFile(), verification.scoresFile())), Set.of(LockRecord.Kind.PRIMARY),
                primaryRoot.resolve(ASSEMBLER_ACCESS_LOG), OPENED, "route inputs test primary");
        ClassBExport primary = new ClassBExport(verification.packetDirectory(), verification.scoresFile(),
                SCORERS, primaryOpened);
        Map<String, ClassBExport> reserves = new HashMap<>();
        Map<String, ResultsPipeline.ReserveExport> reserveLogs = new HashMap<>();
        if (withReserve) {
            ResultsPipeline.ReserveExport reserve = original.provenance().reserves().get(TIP);
            Path reserveRoot = reserve.sealFile().getParent();
            Path scores = reserveRoot.resolve("findings/scores.jsonl");
            ArmKeyAccess.OpenedKey opened = ArmKeyAccess.open(reserveRoot.resolve(ARM_KEY_FILE), reserve.sealFile(),
                    List.of(ArmKeyGateFixtures.binding(reserve.lockForReserveOpening(), reserve.sealFile(), scores)),
                    Set.of(LockRecord.Kind.PRIMARY), reserveRoot.resolve(ASSEMBLER_ACCESS_LOG),
                    OPENED, "route inputs test reserve");
            reserves.put(TIP, new ClassBExport(reserveRoot.resolve(PACKETS_NAME), scores, SCORERS, opened));
            reserveLogs.put(TIP, new ResultsPipeline.ReserveExport(TIP, reserve.reserveKeyAccessLog(),
                    reserve.sealFile(), reserve.lockForPrimaryOpening(), reserve.lockForReserveOpening(),
                    reserve.findingsDirectory(), reserveRoot.resolve(PACKETS_NAME), scores, SCORERS));
        }
        ClassBSources b = new ClassBSources(run.freeze(), run.parameters(), run.mapping(),
                run.candidate().receipt(), run.manifests(), primary, reserves, original.reserveRecordFile(),
                verification.sealFile(), verification.primaryLockFile());
        FrbBundleAssembler.Assembled assembled = FrbBundleAssembler.assemble(
                new FrbBundleAssembler.Sources(List.of(aSources), b));
        ResultsPipeline.PrimaryExport aPrimary = new ResultsPipeline.PrimaryExport(root.resolve(ACCESS_LOG_NAME),
                seal, lock, p.getParent(), a.exclusionsFileOrNull(), a.expectedExclusionsSha256OrNull());
        ResultsPipeline.PrimaryExport bPrimary = original.provenance().primaries().getLast();
        ResultsPipeline.FrbInput frb = new ResultsPipeline.FrbInput(assembled.bundle(), original.reserveRecordFile(),
                verification, new ResultsPipeline.ProvenanceLogs(List.of(aPrimary, bPrimary), reserveLogs));
        return new Setup(assembled, run, frb, aSources, b, root);
    }

    private static void detached(Path file) throws Exception {
        Files.writeString(file.resolveSibling(file.getFileName() + SHA_SUFFIX), FreezeRecord.sha256(file) + NEWLINE);
    }

    private static void restampReceipts(ResultsPipeline.FrbRunInput run, boolean expensiveCandidate) throws Exception {
        String newFreezeHash = FreezeRecord.sha256(run.freeze());
        List<ResultsPipeline.ArmFiles> arms = new ArrayList<>(List.of(run.baselineMini(), run.baseline41(),
                run.candidate()));
        run.reserves().values().forEach(reserve -> {
            arms.add(reserve.baseline());
            arms.add(reserve.candidate());
        });
        for (ResultsPipeline.ArmFiles arm : arms) {
            ObjectNode receipt = (ObjectNode) IntegritySupport.JSON.readTree(arm.receipt().toFile());
            receipt.put(FREEZE_SHA_FIELD, newFreezeHash);
            if (expensiveCandidate && arm == run.candidate())
                for (JsonNode row : receipt.get("cases"))
                    ((ObjectNode) row).put("conservativeUsd", new BigDecimal("1.00")).put("billedUsd", new BigDecimal("1.00"));
            IntegritySupport.JSON.writeValue(arm.receipt().toFile(), receipt);
        }
    }

    // ---- Ledger helpers copied from RouteClassificationInputsTest ----

    private record Ledger(Path root, PostLockContext context, SafetyFindingStore safety,
            EvidenceDisputeStore disputes, ReviewRecordStore reviews, FindingsLedger ledger, boolean classA) {
        String hash() { return context.verify().lockSha256(); }
        FindingsLedger.ExternalHeads heads() {
            return new FindingsLedger.ExternalHeads(external(safety.headSha256()),
                    external(disputes.headSha256()), external(reviews.headSha256()));
        }
        ReviewRecord complete(String id, FindingsLedger.ReviewSubject subject) {
            return reviews.create(new ReviewRecord(id, CREATED, hash(), subject.kind(), subject.workflow(),
                    subject.subjectId(), ReviewRecord.Outcome.COMPLETE, OWNER, ReviewRecord.SignerRole.OWNER, EVIDENCE));
        }
        Path reviewFile(String id) { return root.resolve(REVIEWS).resolve(id + JSON_SUFFIX); }
    }

    private record Harness(Setup setup, List<Ledger> ledgers) {
        Ledger classA() { return ledgers.stream().filter(Ledger::classA).findFirst().orElseThrow(); }
        Ledger classB() { return ledgers.stream().filter(item -> !item.classA()).findFirst().orElseThrow(); }
        Map<String, FindingsLedger> ledgerMap() {
            Map<String, FindingsLedger> result = new HashMap<>();
            for (Ledger item : ledgers) result.put(item.hash(), item.ledger());
            return result;
        }
        Map<String, FindingsLedger.ExternalHeads> heads() {
            Map<String, FindingsLedger.ExternalHeads> result = new HashMap<>();
            for (Ledger item : ledgers) result.put(item.hash(), item.heads());
            return result;
        }
        RouteClassificationInputs.RouteLedgers routeLedgers() {
            return new RouteClassificationInputs.RouteLedgers(ledgerMap(), heads());
        }
        void attest() {
            classA().complete("no_veto_note", new FindingsLedger.ReviewSubject(ReviewRecord.Kind.NO_VETO, STEM, ROUTE));
            classB().complete("no_veto_tip", new FindingsLedger.ReviewSubject(ReviewRecord.Kind.NO_VETO, TIP, ROUTE));
        }
        ResultsPipeline.FrbRunInput run() { return setup.run(); }
    }

    private static FindingsLedger.ExternalHead external(PostLockStoreIndex.Head head) {
        return new FindingsLedger.ExternalHead(head.seq(), head.sha256());
    }

    private Harness harness(boolean withReserve) throws Exception {
        return harness(setup(withReserve), true);
    }

    private Harness harness(Setup setup, boolean attest) throws Exception {
        List<Ledger> ledgers = new ArrayList<>();
        List<ResultsPipeline.PrimaryExport> primaries = setup.frb().provenance().primaries();
        for (ResultsPipeline.PrimaryExport export : primaries)
            ledgers.add(ledger(export.lockFile(), export.sealFile(), export.findingsDirectory(), setup,
                    export == primaries.getFirst()));
        for (ResultsPipeline.ReserveExport export : setup.frb().provenance().reserves().values()) {
            ledgers.add(ledger(export.lockForPrimaryOpening(), export.sealFile(), export.findingsDirectory(), setup, false));
            ledgers.add(ledger(export.lockForReserveOpening(), export.sealFile(), export.findingsDirectory(), setup, false));
        }
        Harness h = new Harness(setup, List.copyOf(ledgers));
        h.classB().complete("class_b_complete",
                new FindingsLedger.ReviewSubject(ReviewRecord.Kind.CLASS_B_WORKFLOW, TIP, TIP));
        if (attest) h.attest();
        return h;
    }

    private Ledger ledger(Path lock, Path seal, Path findings, Setup setup, boolean classA) throws Exception {
        PostLockContext context = new PostLockContext(lock, findings, seal.getParent().resolve(PACKETS_NAME), seal,
                setup.run().parameters());
        Path root = Files.createTempDirectory(temp, "ledger-");
        Path safety = root.resolve(SAFETY), disputes = root.resolve(DISPUTES), reviews = root.resolve(REVIEWS);
        return new Ledger(root, context, SafetyFindingStore.init(safety, context, GENESIS),
                EvidenceDisputeStore.init(disputes, context, GENESIS), ReviewRecordStore.init(reviews, context, GENESIS),
                new FindingsLedger(safety, disputes, reviews, context), classA);
    }

    private List<ProductionSwitchEligibility.CostLedgerInput> costLedgers() throws Exception {
        Path ledger = Files.createTempFile(temp, "cost-", ".jsonl");
        Files.delete(ledger);
        try (StageCapLedger ignored = StageCapLedger.open(ledger, Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE), true)) {
            // synthetic empty ledger
        }
        Path file = Files.createTempFile(temp, "approved-", JSON_SUFFIX);
        Files.writeString(file, COST_CAPS);
        return List.of(new ProductionSwitchEligibility.CostLedgerInput(ledger, ApprovedCaps.load(file)));
    }

    // ---- Operator helpers ----

    /** The expected anchors per access log, from the provenance and the files on disk (fact 2). */
    private static Map<Path, List<LockAnchors.Anchor>> expectedAnchors(ResultsPipeline.ProvenanceLogs provenance) {
        Map<Path, List<LockAnchors.Anchor>> result = new LinkedHashMap<>();
        for (ResultsPipeline.PrimaryExport export : provenance.primaries()) {
            List<LockAnchors.Anchor> anchors = new ArrayList<>();
            anchors.add(LockAnchors.anchorOf(export.lockFile(), export.sealFile()));
            for (ResultsPipeline.ReserveExport reserve : provenance.reserves().values())
                anchors.add(LockAnchors.anchorOf(reserve.lockForPrimaryOpening(), reserve.sealFile()));
            result.put(export.accessLog(), anchors);
        }
        for (ResultsPipeline.ReserveExport reserve : provenance.reserves().values())
            result.put(reserve.reserveKeyAccessLog(),
                    List.of(LockAnchors.anchorOf(reserve.lockForReserveOpening(), reserve.sealFile())));
        return result;
    }

    private static String note(List<LockAnchors.Anchor> anchors, Path logFile) {
        return LockAnchors.render(new LockAnchors.Noted(anchors, LockAnchors.headOf(logFile)));
    }

    private static Map<Path, String> notes(ResultsPipeline.ProvenanceLogs provenance) {
        Map<Path, String> result = new LinkedHashMap<>();
        expectedAnchors(provenance).forEach((logFile, anchors) -> result.put(logFile, note(anchors, logFile)));
        return result;
    }

    private static FrbOperator.Sources sources(Harness h, Map<Path, String> notes,
            Map<String, FindingsLedger.ExternalHeads> fresh,
            List<ProductionSwitchEligibility.CostLedgerInput> cost) {
        return new FrbOperator.Sources(h.setup().bundleSources(), h.run(), h.setup().frb(), notes, h.routeLedgers(),
                fresh, OWNER_FACTS, cost);
    }

    private static FrbOperator.Sources sources(Harness h) {
        return sources(h, notes(h.setup().frb().provenance()), h.heads(), null);
    }

    private static FrbOperator.Sources withFrb(FrbOperator.Sources s, ResultsPipeline.FrbInput frb,
            Map<Path, String> notes) {
        return new FrbOperator.Sources(s.bundleSources(), s.run(), frb, notes, s.ledgers(),
                s.freshNotedHeadsByLockSha256(), s.owner(), s.costLedgers());
    }

    private static FrbOperator.Sources withRun(FrbOperator.Sources s, ResultsPipeline.FrbRunInput run) {
        return new FrbOperator.Sources(s.bundleSources(), run, s.frb(), s.notedAnchorTextByAccessLog(), s.ledgers(),
                s.freshNotedHeadsByLockSha256(), s.owner(), s.costLedgers());
    }

    private static FrbOperator.Sources withNotes(FrbOperator.Sources s, Map<Path, String> notes) {
        return withFrb(s, s.frb(), notes);
    }

    private static ResultsPipeline.FrbInput withProvenance(ResultsPipeline.FrbInput frb,
            List<ResultsPipeline.PrimaryExport> primaries, Map<String, ResultsPipeline.ReserveExport> reserves) {
        return new ResultsPipeline.FrbInput(frb.bundle(), frb.reserveRecordFile(), frb.verification(),
                new ResultsPipeline.ProvenanceLogs(primaries, reserves));
    }

    private static ResultsPipeline.PrimaryExport withLog(ResultsPipeline.PrimaryExport export, Path logFile) {
        return new ResultsPipeline.PrimaryExport(logFile, export.sealFile(), export.lockFile(),
                export.findingsDirectory(), export.exclusionsFileOrNull(), export.expectedExclusionsSha256OrNull());
    }

    private static ResultsPipeline.FrbRunInput runWith(ResultsPipeline.FrbRunInput run, Path freeze,
            Map<String, Path> manifests) {
        return new ResultsPipeline.FrbRunInput(run.repoRoot(), freeze, run.parameters(), run.mapping(), manifests,
                run.baselineMini(), run.baseline41(), run.candidate(), run.reserves(), run.compatibilityReports(),
                run.headCommitTime(), run.ownerDispositions(), run.report(), run.synthetic());
    }

    /** A byte-different copy (one trailing space) with a fresh detached sidecar. */
    private Path changedCopy(Path file) throws Exception {
        Path copy = Files.createTempDirectory(temp, "changed-").resolve(file.getFileName());
        Files.writeString(copy, Files.readString(file) + " ");
        detached(copy);
        return copy;
    }

    private static void deleteReport(Harness h) throws Exception {
        Files.deleteIfExists(h.run().report());
    }

    private static final FrbOperator.FrbRunner REAL_RUNNER = ResultsPipeline::runFrb;
    private static final FrbOperator.SwitchChecker REAL_CHECKER = ProductionSwitchEligibility::checkWithOptions;

    /** Fail-closed content: nothing produced by the failed or a later step is present. */
    private static void assertFailedAt(FrbOperator.Outcome outcome, FrbOperator.Step step,
            ResultsPipeline.FrbRunInput run) {
        int index = step.ordinal();
        assertThat(outcome.failedStep()).isEqualTo(step);
        assertThat(outcome.failure()).isNotBlank();
        assertThat(outcome.completedSteps()).containsExactlyElementsOf(ALL_STEPS.subList(0, index));
        assertThat(outcome.report()).isNull();
        assertThat(outcome.decisions()).isEmpty();
        if (index <= FrbOperator.Step.ASSEMBLE.ordinal()) assertThat(outcome.assembled()).isNull();
        else assertThat(outcome.assembled()).isNotNull();
        if (index <= FrbOperator.Step.BUILD_INPUTS.ordinal()) assertThat(outcome.built()).isNull();
        else assertThat(outcome.built()).isNotNull();
        if (index <= FrbOperator.Step.RUN_FRB.ordinal()) assertThat(outcome.classifications()).isNull();
        else assertThat(outcome.classifications()).isNotNull();
        if (index < FrbOperator.Step.RUN_FRB.ordinal()) assertThat(Files.exists(run.report())).isFalse();
    }

    private static void assertCompleted(FrbOperator.Outcome outcome, ResultsPipeline.FrbRunInput run) {
        assertThat(outcome.failedStep()).isNull();
        assertThat(outcome.failure()).isNull();
        assertThat(outcome.completedSteps()).containsExactlyElementsOf(ALL_STEPS);
        assertThat(outcome.report()).isEqualTo(run.report());
        assertThat(Files.exists(run.report())).isTrue();
    }

    private static FrbOperator.Outcome anchorsFailure(Harness h, FrbOperator.Sources sources, String message) {
        FrbOperator.Outcome outcome = FrbOperator.run(sources);
        assertFailedAt(outcome, FrbOperator.Step.ANCHORS, h.run());
        assertThat(outcome.failure()).contains(message);
        return outcome;
    }

    // ---- First step: smoke ----

    @Test void smokeTheWholeFlowCompletesOnTheCombinedSetupWithCorrectAnchorNotes() throws Exception {
        for (boolean withReserve : List.of(false, true)) {
            Harness h = harness(withReserve);
            var provenance = h.setup().frb().provenance();
            assertThat(expectedAnchors(provenance)).hasSize(withReserve ? 3 : 2);
            FrbOperator.Outcome outcome = FrbOperator.run(sources(h));
            assertThat(outcome.failure()).as("failure").isNull();
            assertCompleted(outcome, h.run());
            assertThat(outcome.decisions()).containsOnlyKeys(ROUTE);
        }
    }

    // ---- Happy path ----

    @Test void happyPathClassifiesLikeTheBuilderPinsTheReportAndClearsAssertionReason() throws Exception {
        Harness h = harness(true);
        Map<Path, String> notes = notes(h.setup().frb().provenance());
        // Note keys are compared after normalization: a dotted path still names the class-A log.
        Path aLog = h.setup().aPrimary().accessLog();
        String aNote = notes.remove(aLog);
        notes.put(aLog.getParent().resolve(".").resolve(aLog.getFileName()), aNote);
        FrbOperator.Outcome outcome = FrbOperator.run(sources(h, notes, h.heads(), costLedgers()));
        assertCompleted(outcome, h.run());
        assertThat(outcome.classifications()).isEqualTo(RouteClassificationInputs.classify(outcome.built()));
        assertThat(outcome.classifications().get(ROUTE).classification())
                .isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
        JsonNode report = IntegritySupport.JSON.readTree(h.run().report().toFile());
        FrbOperator.requireNoDrift(outcome.classifications(), report,
                new ResultsPipeline.Result(outcome.built().reports(), h.run().report(), null, Map.of(), Map.of()),
                outcome.built().reports(), outcome.built().inputs());
        assertThat(report.get(HEADS_FIELD).get(ROUTE).isObject()).isTrue();
        assertThat(outcome.decisions()).containsOnlyKeys(outcome.built().reports().keySet());
        assertThat(outcome.decisions().values()).allSatisfy(decision -> {
            assertThat(decision.eligible()).isFalse();
            assertThat(decision.reasons()).containsExactly(ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH);
        });
        assertThat(outcome.assembled().bundle()).isEqualTo(h.setup().assembled().bundle());
    }

    @Test void withoutCostLedgersTheCostEvidenceReasonStaysAndAPrimaryLogWithoutAReserveNeedsOnlyItsLock()
            throws Exception {
        Harness h = harness(false);
        Map<Path, String> notes = notes(h.setup().frb().provenance());
        assertThat(notes).hasSize(2);
        FrbOperator.Outcome outcome = FrbOperator.run(sources(h, notes, h.heads(), null));
        assertCompleted(outcome, h.run());
        assertThat(outcome.decisions().get(ROUTE).eligible()).isFalse();
        assertThat(outcome.decisions().get(ROUTE).reasons()).contains(
                ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED)
                .doesNotContain(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
        assertThat(outcome.classifications().get(ROUTE).flags()).contains("COST_REVIEW_UNDETERMINED");
    }

    @Test void seamCountingRunnerSeesRunFrbExactlyOnceAfterBuildWithTheBuiltInputs() throws Exception {
        Harness h = harness(true);
        AtomicInteger calls = new AtomicInteger();
        List<ResultsPipeline.FrbInput> seen = new ArrayList<>();
        FrbOperator.FrbRunner counting = (run, frb) -> {
            calls.incrementAndGet();
            seen.add(frb);
            assertThat(Files.exists(run.report())).isFalse();
            return ResultsPipeline.runFrb(run, frb);
        };
        FrbOperator.Outcome outcome = FrbOperator.run(sources(h), counting, REAL_CHECKER);
        assertCompleted(outcome, h.run());
        assertThat(calls).hasValue(1);
        ResultsPipeline.FrbInput frb = seen.getFirst();
        assertThat(frb.routeClassifications()).isEqualTo(outcome.built().inputs()).isNotEmpty();
        assertThat(frb.operationalCostReview()).isTrue();
        assertThat(frb.bundle()).isEqualTo(outcome.assembled().bundle());
        assertThat(frb.provenance()).isEqualTo(h.setup().frb().provenance());
    }

    private record Call(String route, RouteVerdictCalculator.RouteReport report, FrbClassificationInput input,
            ResultsPipeline.FrbRunInput run, ResultsPipeline.FrbInput frb, Map<String, FindingsLedger> ledgers,
            Map<String, FindingsLedger.ExternalHeads> fresh, Set<String> pending,
            ProductionSwitchEligibility.SwitchOptions options) {}

    @Test void seamRecordingCheckerSeesAllThreeOptionsTrueAndTheFinalInputs() throws Exception {
        Harness h = harness(true);
        List<Call> calls = new ArrayList<>();
        List<ResultsPipeline.FrbInput> runnerFrb = new ArrayList<>();
        FrbOperator.FrbRunner runner = (run, frb) -> {
            runnerFrb.add(frb);
            return ResultsPipeline.runFrb(run, frb);
        };
        FrbOperator.SwitchChecker recording = (route, report, input, run, frb, ledgers, fresh, pending, options) -> {
            calls.add(new Call(route, report, input, run, frb, ledgers, fresh, pending, options));
            return ProductionSwitchEligibility.checkWithOptions(route, report, input, run, frb, ledgers, fresh,
                    pending, options);
        };
        var cost = costLedgers();
        FrbOperator.Sources sources = sources(h, notes(h.setup().frb().provenance()), h.heads(), cost);
        FrbOperator.Outcome outcome = FrbOperator.run(sources, runner, recording);
        assertCompleted(outcome, h.run());
        assertThat(calls).hasSize(1);
        Call call = calls.getFirst();
        assertThat(call.route()).isEqualTo(ROUTE);
        assertThat(call.options().recomputeReport()).isTrue();
        assertThat(call.options().requireOwnerAttestations()).isTrue();
        assertThat(call.options().requireOperationalCostReview()).isTrue();
        assertThat(call.options().requireVerifiedAssertions()).isTrue();
        assertThat(call.options().reassemblySources()).isSameAs(sources.bundleSources());
        assertThat(call.options().costLedgers()).isEqualTo(cost);
        assertThat(call.frb()).isSameAs(runnerFrb.getFirst());
        assertThat(call.frb().routeClassifications()).isEqualTo(outcome.built().inputs());
        assertThat(call.input()).isEqualTo(outcome.built().inputs().get(ROUTE));
        assertThat(call.report()).isEqualTo(outcome.built().reports().get(ROUTE));
        // Identity, not equality: the fixture's pending set is empty, so an empty stand-in would be equal.
        assertThat(call.pending()).isSameAs(outcome.built().pendingRoutes());
        assertThat(call.fresh()).isEqualTo(sources.freshNotedHeadsByLockSha256());
        assertThat(call.ledgers()).isEqualTo(sources.ledgers().ledgersByLockSha256());
        assertThat(call.run()).isEqualTo(h.run());
    }

    // ---- ANCHORS failures ----

    @Test void anchorsSecondLockAtANewPathUsedToOpenTheKeyIsAnUnanchoredLogEntry() throws Exception {
        Harness h = harness(false);
        FrbOperator.Sources sources = sources(h);
        ResultsPipeline.PrimaryExport a = h.setup().aPrimary();
        Path directory = Files.createDirectory(h.setup().aRoot().resolve("second-findings"));
        Path finding = directory.resolve("human.jsonl");
        Files.writeString(finding, "review complete\n");
        Path second = h.setup().aRoot().resolve("second.json");
        ArmKeySeal.Seal seal = ArmKeySeal.load(a.sealFile());
        LockRecord.create(second, LockRecord.Kind.PRIMARY, seal.workflowStems(), List.of(finding), seal, a.sealFile(),
                files -> {}, ArmKeyGateFixtures.LOCKED);
        ArmKeyAccess.open(h.setup().aRoot().resolve(CLASS_A_KEY_FILE), a.sealFile(),
                List.of(ArmKeyGateFixtures.binding(second, a.sealFile(), finding)), Set.of(LockRecord.Kind.PRIMARY),
                a.accessLog(), LATER, "second lock");
        anchorsFailure(h, sources, "Unanchored lock in access-log entry seq 1");
    }

    @Test void anchorsLockRewrittenAfterNotingFailsWithTheVerifyLocksOnDiskMessage() throws Exception {
        Harness h = harness(false);
        FrbOperator.Sources sources = sources(h);
        Path lock = h.setup().aPrimary().lockFile();
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(lock));
        row.put("lockedAtUtc", "2026-11-16T00:01:01Z");
        Files.write(lock, IntegritySupport.JSON.writeValueAsBytes(row));
        Files.writeString(IntegritySupport.detached(lock), FreezeRecord.sha256(lock));
        FrbOperator.Outcome outcome = anchorsFailure(h, sources, NOT_ANCHORED);
        assertThat(outcome.failure()).doesNotContain(SET_DIFFERS);
    }

    @Test void anchorsExtraAnchorOfADifferentSealOnTheClassALogIsRefusedByEquality() throws Exception {
        Harness h = harness(false);
        var provenance = h.setup().frb().provenance();
        Map<Path, String> notes = notes(provenance);
        Path aLog = h.setup().aPrimary().accessLog();
        List<LockAnchors.Anchor> anchors = new ArrayList<>(expectedAnchors(provenance).get(aLog));
        anchors.add(LockAnchors.anchorOf(h.setup().bPrimary().lockFile(), h.setup().bPrimary().sealFile()));
        notes.put(aLog, note(anchors, aLog));
        anchorsFailure(h, withNotes(sources(h), notes), SET_DIFFERS);
    }

    @Test void anchorsExtraReserveKindAnchorBesideTheReserveOpeningLockIsRefusedByEquality() throws Exception {
        Harness h = harness(true);
        var provenance = h.setup().frb().provenance();
        Map<Path, String> notes = notes(provenance);
        ResultsPipeline.ReserveExport reserve = h.setup().reserve();
        notes.put(reserve.reserveKeyAccessLog(), note(List.of(
                LockAnchors.anchorOf(reserve.lockForReserveOpening(), reserve.sealFile()),
                LockAnchors.anchorOf(reserve.lockForPrimaryOpening(), reserve.sealFile())),
                reserve.reserveKeyAccessLog()));
        anchorsFailure(h, withNotes(sources(h), notes), SET_DIFFERS);
    }

    @Test void anchorsPrimaryLogMissingTheDrawnReservesReserveKindAnchorIsRefused() throws Exception {
        Harness h = harness(true);
        Map<Path, String> notes = notes(h.setup().frb().provenance());
        ResultsPipeline.PrimaryExport b = h.setup().bPrimary();
        notes.put(b.accessLog(), note(List.of(LockAnchors.anchorOf(b.lockFile(), b.sealFile())), b.accessLog()));
        anchorsFailure(h, withNotes(sources(h), notes), NOT_ANCHORED);
    }

    @Test void anchorsReserveLogNotedWithTheReserveKindLockInPlaceOfTheReserveOpeningLockIsRefused()
            throws Exception {
        Harness h = harness(true);
        Map<Path, String> notes = notes(h.setup().frb().provenance());
        ResultsPipeline.ReserveExport reserve = h.setup().reserve();
        notes.put(reserve.reserveKeyAccessLog(), note(List.of(
                LockAnchors.anchorOf(reserve.lockForPrimaryOpening(), reserve.sealFile())), reserve.reserveKeyAccessLog()));
        anchorsFailure(h, withNotes(sources(h), notes), NOT_ANCHORED);
    }

    @Test void anchorsNoteForALogNotInTheProvenanceAndAProvenanceLogWithNoNoteAreRefused() throws Exception {
        Harness h = harness(true);
        FrbOperator.Sources base = sources(h);
        Map<Path, String> extra = new LinkedHashMap<>(base.notedAnchorTextByAccessLog());
        Path otherLog = h.setup().aRoot().resolveSibling("class-a-one").resolve(ACCESS_LOG_NAME);
        assertThat(otherLog).exists();
        extra.put(otherLog, extra.get(h.setup().aPrimary().accessLog()));
        anchorsFailure(h, withNotes(base, extra), "does not list");
        for (Path dropped : List.of(h.setup().aPrimary().accessLog(), h.setup().reserve().reserveKeyAccessLog())) {
            Map<Path, String> missing = new LinkedHashMap<>(base.notedAnchorTextByAccessLog());
            missing.remove(dropped);
            anchorsFailure(h, withNotes(base, missing), "has no noted anchors");
        }
    }

    @Test void anchorsDuplicateAccessLogAndTwoNotesForOneLogAreRefused() throws Exception {
        Harness h = harness(true);
        FrbOperator.Sources base = sources(h);
        ResultsPipeline.PrimaryExport a = h.setup().aPrimary();
        Path dotted = a.accessLog().getParent().resolve(".").resolve(a.accessLog().getFileName());
        ResultsPipeline.FrbInput frb = h.setup().frb();
        ResultsPipeline.FrbInput duplicated = withProvenance(frb, List.of(a, withLog(a, dotted), h.setup().bPrimary()),
                frb.provenance().reserves());
        anchorsFailure(h, withFrb(base, duplicated, base.notedAnchorTextByAccessLog()), "Duplicate access log");
        ResultsPipeline.ReserveExport r = h.setup().reserve();
        ResultsPipeline.ReserveExport shared = new ResultsPipeline.ReserveExport(TIP, a.accessLog(), r.sealFile(),
                r.lockForPrimaryOpening(), r.lockForReserveOpening(), r.findingsDirectory(), r.reservePacketDirectory(),
                r.scoresFile(), r.scorers());
        ResultsPipeline.FrbInput sharedLog = withProvenance(frb, frb.provenance().primaries(), Map.of(TIP, shared));
        anchorsFailure(h, withFrb(base, sharedLog, base.notedAnchorTextByAccessLog()), "Duplicate access log");
        Map<Path, String> twice = new LinkedHashMap<>(base.notedAnchorTextByAccessLog());
        twice.put(dotted, twice.get(a.accessLog()));
        anchorsFailure(h, withNotes(base, twice), "Two notes name the same access log");
    }

    @Test void anchorsNotedHeadWithADifferentHistoryHashIsRefused() throws Exception {
        Harness h = harness(false);
        var provenance = h.setup().frb().provenance();
        Map<Path, String> notes = notes(provenance);
        Path aLog = h.setup().aPrimary().accessLog();
        notes.put(aLog, LockAnchors.render(new LockAnchors.Noted(expectedAnchors(provenance).get(aLog),
                new LockAnchors.LogHead(1, OTHER_HASH))));
        anchorsFailure(h, withNotes(sources(h), notes), "Noted access-log history changed");
    }

    @Test void anchorsAbsentLogAndEmptyLogFileAreRefused() throws Exception {
        Harness h = harness(false);
        FrbOperator.Sources base = sources(h);
        ResultsPipeline.FrbInput frb = h.setup().frb();
        ResultsPipeline.PrimaryExport a = h.setup().aPrimary();
        List<LockAnchors.Anchor> anchors = expectedAnchors(frb.provenance()).get(a.accessLog());
        Path absent = h.setup().aRoot().resolve("absent.jsonl");
        Path empty = Files.writeString(h.setup().aRoot().resolve("empty.jsonl"), "");
        for (Path logFile : List.of(absent, empty)) {
            ResultsPipeline.FrbInput moved = withProvenance(frb, List.of(withLog(a, logFile), h.setup().bPrimary()),
                    frb.provenance().reserves());
            Map<Path, String> notes = new LinkedHashMap<>(base.notedAnchorTextByAccessLog());
            notes.remove(a.accessLog());
            notes.put(logFile, LockAnchors.render(new LockAnchors.Noted(anchors, new LockAnchors.LogHead(0, null))));
            anchorsFailure(h, withFrb(base, moved, notes), logFile == absent ? "Access log required"
                    : "LF-terminated");
        }
    }

    @Test void anchorsUnparsableAndNoncanonicalNotesOnTheFirstAndTheLastLogAreRefused() throws Exception {
        Harness h = harness(true);
        FrbOperator.Sources base = sources(h);
        Path first = h.setup().aPrimary().accessLog();
        Path last = h.setup().reserve().reserveKeyAccessLog();
        assertThat(new TreeSet<>(base.notedAnchorTextByAccessLog().keySet()).first()).isEqualTo(first);
        assertThat(new TreeSet<>(base.notedAnchorTextByAccessLog().keySet()).last()).isEqualTo(last);
        for (Path logFile : List.of(first, last)) {
            Map<Path, String> unparsable = new LinkedHashMap<>(base.notedAnchorTextByAccessLog());
            unparsable.put(logFile, "not a note");
            anchorsFailure(h, withNotes(base, unparsable), "Cannot parse anchor note");
            Map<Path, String> noncanonical = new LinkedHashMap<>(base.notedAnchorTextByAccessLog());
            noncanonical.put(logFile, noncanonical.get(logFile).replace("\"anchors\":[", "\"anchors\": ["));
            anchorsFailure(h, withNotes(base, noncanonical), "Noncanonical anchor note");
        }
    }

    // ---- Later steps ----

    @Test void assembleCommonDigestDifferenceBetweenTheExportAndClassBFailsAtAssemble() throws Exception {
        Harness h = harness(false);
        FrbOperator.Sources base = sources(h);
        ClassASources a = h.setup().aSources();
        // The same mapping bytes with a byte-different (trailing newline) detached sidecar: the
        // frozen content still matches, only the common sidecar digest differs from class B's.
        Path mapping = Files.createTempDirectory(temp, "sidecar-").resolve(a.mappingFile().getFileName());
        Files.copy(a.mappingFile(), mapping);
        Files.writeString(IntegritySupport.detached(mapping), FreezeRecord.sha256(mapping) + NEWLINE);
        assertThat(Files.readString(IntegritySupport.detached(mapping)))
                .isNotEqualTo(Files.readString(IntegritySupport.detached(a.mappingFile())));
        ClassASources changed = new ClassASources(a.freezeFile(), a.parametersFile(), mapping,
                a.manifests(), a.export(), a.drawLogFile(), a.seedRegistryFile());
        FrbOperator.Sources sources = new FrbOperator.Sources(
                new FrbBundleAssembler.Sources(List.of(changed), h.setup().b()), base.run(), base.frb(),
                base.notedAnchorTextByAccessLog(), base.ledgers(), base.freshNotedHeadsByLockSha256(), base.owner(),
                base.costLedgers());
        FrbOperator.Outcome outcome = FrbOperator.run(sources);
        assertFailedAt(outcome, FrbOperator.Step.ASSEMBLE, h.run());
        assertThat(outcome.failure()).contains("InvalidFrbBundle", "Digest differs: mapping.sha256");
    }

    @Test void bindAChangedFreezeManifestReserveRecordOrVerificationOnlySealFailsAtBind() throws Exception {
        Harness h = harness(false);
        FrbOperator.Sources base = sources(h);
        ResultsPipeline.FrbRunInput run = h.run();
        Map<String, Path> manifests = new HashMap<>(run.manifests());
        manifests.put(CLASSIFICATION_A_PRIMARY, changedCopy(run.manifests().get(CLASSIFICATION_A_PRIMARY)));
        ResultsPipeline.FrbInput frb = h.setup().frb();
        ResultsPipeline.FrbInput record = new ResultsPipeline.FrbInput(frb.bundle(), changedCopy(frb.reserveRecordFile()),
                frb.verification(), frb.provenance());
        ResultsPipeline.ReserveVerification v = frb.verification();
        Path stagedSeal = changedCopy(v.sealFile());
        ResultsPipeline.FrbInput seal = new ResultsPipeline.FrbInput(frb.bundle(), frb.reserveRecordFile(),
                new ResultsPipeline.ReserveVerification(v.packetDirectory(), stagedSeal, v.scoresFile(), v.scorers(),
                        v.workflowNames(), v.primaryLockFile()), frb.provenance());
        Map<String, FrbOperator.Sources> cases = new LinkedHashMap<>();
        cases.put("freeze", withRun(base, runWith(run, changedCopy(run.freeze()), run.manifests())));
        cases.put("manifest." + CLASSIFICATION_A_PRIMARY, withRun(base, runWith(run, run.freeze(), manifests)));
        cases.put("reserve.record", withFrb(base, record, base.notedAnchorTextByAccessLog()));
        cases.put("primary.seal", withFrb(base, seal, base.notedAnchorTextByAccessLog()));
        for (var entry : cases.entrySet()) {
            FrbOperator.Outcome outcome = FrbOperator.run(entry.getValue());
            assertFailedAt(outcome, FrbOperator.Step.BIND, run);
            assertThat(outcome.failure()).as(entry.getKey()).contains("Binding differs: " + entry.getKey());
        }
    }

    @Test void buildInputsLedgerSetDifferentFromTheProvenanceLocksFailsAtBuildInputs() throws Exception {
        Harness h = harness(true);
        Map<String, FindingsLedger> ledgers = h.ledgerMap();
        Map<String, FindingsLedger.ExternalHeads> heads = h.heads();
        String dropped = h.classB().hash();
        ledgers.remove(dropped);
        heads.remove(dropped);
        FrbOperator.Sources sources = new FrbOperator.Sources(h.setup().bundleSources(), h.run(), h.setup().frb(),
                notes(h.setup().frb().provenance()), new RouteClassificationInputs.RouteLedgers(ledgers, heads), heads,
                OWNER_FACTS, null);
        FrbOperator.Outcome outcome = FrbOperator.run(sources);
        assertFailedAt(outcome, FrbOperator.Step.BUILD_INPUTS, h.run());
        assertThat(outcome.failure()).contains("InvalidRouteInputs", "provenance locks");
    }

    /** A runner that runs the real runFrb and then rewrites the report it wrote. */
    private static FrbOperator.FrbRunner tampering(java.util.function.Consumer<ObjectNode> change) {
        return (run, frb) -> {
            ResultsPipeline.Result result = ResultsPipeline.runFrb(run, frb);
            try {
                ObjectNode report = (ObjectNode) IntegritySupport.JSON.readTree(run.report().toFile());
                change.accept(report);
                IntegritySupport.JSON.writeValue(run.report().toFile(), report);
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
            return result;
        };
    }

    private static ObjectNode routeNode(ObjectNode report) {
        return (ObjectNode) report.get(CLASSIFICATION_FIELD).get(ROUTE);
    }

    @Test void seamRunFrbDriftInTheReportFailsAtRunFrbAndLeavesTheReportInvalid() throws Exception {
        Harness h = harness(false);
        // Cost ledgers make the route a clean BENCHMARK_PASS (no causes, no flags, not blocked), so a
        // missing array or boolean read with a default would look like a match.
        FrbOperator.Sources sources = sources(h, notes(h.setup().frb().provenance()), h.heads(), costLedgers());
        Map<String, java.util.function.Consumer<ObjectNode>> changes = new LinkedHashMap<>();
        changes.put("classification", report -> routeNode(report).put(CLASSIFICATION_FIELD,
                DeadlineRule.Classification.RETURN_TO_OWNER.name()));
        changes.put("flag", report -> ((ArrayNode) routeNode(report).get(FLAGS_FIELD)).add("SOL_RECOMMENDED"));
        changes.put("cause", report -> ((ArrayNode) routeNode(report).get("causes")).add("OWNER_VETO"));
        changes.put("blocks", report -> routeNode(report).put("blocksProductionSwitch", "true"));
        changes.put("deadline", report -> ((ObjectNode) report.get(DEADLINE_FIELD)).put(ROUTE, "RETURN_TO_OWNER"));
        changes.put("missing classification", report -> report.remove(CLASSIFICATION_FIELD));
        changes.put("missing deadline", report -> report.remove(DEADLINE_FIELD));
        changes.put("missing route flags", report -> routeNode(report).remove(FLAGS_FIELD));
        changes.put("missing blocks", report -> routeNode(report).remove("blocksProductionSwitch"));
        changes.put("missing route causes", report -> routeNode(report).remove("causes"));
        changes.put("missing route classification", report -> routeNode(report).remove(CLASSIFICATION_FIELD));
        changes.put("wrong type classification", report -> report.put(CLASSIFICATION_FIELD, "BENCHMARK_PASS"));
        changes.put("wrong type deadline", report -> report.putArray(DEADLINE_FIELD));
        changes.put("ledger head", report -> {
            ObjectNode heads = (ObjectNode) report.get(HEADS_FIELD).get(ROUTE);
            heads.put(heads.fieldNames().next(), OTHER_HASH);
        });
        changes.put("missing ledger heads", report -> report.remove(HEADS_FIELD));
        for (var entry : changes.entrySet()) {
            FrbOperator.Outcome outcome = FrbOperator.run(sources, tampering(entry.getValue()), REAL_CHECKER);
            assertFailedAt(outcome, FrbOperator.Step.RUN_FRB, h.run());
            assertThat(outcome.failure()).as(entry.getKey()).contains(DRIFT, INVALID_REPORT);
            assertThat(h.run().report()).as(entry.getKey()).exists();
            deleteReport(h);
        }
    }

    @Test void seamRunnerResultRoutesThatDifferFromTheBuiltReportsAreDrift() throws Exception {
        Harness h = harness(false);
        FrbOperator.FrbRunner runner = (run, frb) -> {
            ResultsPipeline.Result result = ResultsPipeline.runFrb(run, frb);
            return new ResultsPipeline.Result(Map.of(), result.report(), null, Map.of(), Map.of());
        };
        FrbOperator.Outcome outcome = FrbOperator.run(sources(h), runner, REAL_CHECKER);
        assertFailedAt(outcome, FrbOperator.Step.RUN_FRB, h.run());
        assertThat(outcome.failure()).contains("result routes differ", INVALID_REPORT);
    }

    @Test void seamRequireNoDriftRefusesRouteSetDifferencesInEachDirectionAndDifferentResultRoutes()
            throws Exception {
        Harness h = harness(false);
        List<ResultsPipeline.Result> results = new ArrayList<>();
        FrbOperator.FrbRunner capturing = (run, frb) -> {
            ResultsPipeline.Result result = ResultsPipeline.runFrb(run, frb);
            results.add(result);
            return result;
        };
        FrbOperator.Outcome outcome = FrbOperator.run(sources(h), capturing, REAL_CHECKER);
        assertCompleted(outcome, h.run());
        JsonNode report = IntegritySupport.JSON.readTree(h.run().report().toFile());
        ResultsPipeline.Result result = results.getFirst();
        Map<String, FrbClassification> expected = outcome.classifications();
        var reports = outcome.built().reports();
        var inputs = outcome.built().inputs();
        FrbOperator.requireNoDrift(expected, report, result, reports, inputs);
        FrbClassification only = expected.get(ROUTE);
        Map<String, FrbClassification> moreExpected = new TreeMap<>(expected);
        moreExpected.put("CRITIQUE", only);
        ObjectNode moreReport = report.deepCopy();
        ((ObjectNode) moreReport.get(CLASSIFICATION_FIELD)).set("CRITIQUE", moreReport.get(CLASSIFICATION_FIELD).get(ROUTE));
        ((ObjectNode) moreReport.get(DEADLINE_FIELD)).put("CRITIQUE", ResultsPipeline.renderFrbClassification(only));
        Map<String, RouteVerdictCalculator.RouteReport> moreReports = new TreeMap<>(reports);
        moreReports.put("CRITIQUE", reports.get(ROUTE));
        ObjectNode deadlineOnly = report.deepCopy();
        ((ObjectNode) deadlineOnly.get(DEADLINE_FIELD)).put("CRITIQUE", "BENCHMARK_PASS");
        ResultsPipeline.Result otherRoutes = new ResultsPipeline.Result(moreReports, result.report(), null, Map.of(),
                Map.of());
        assertDrift(moreExpected, report, result, reports, inputs, "route sets differ");
        assertDrift(expected, moreReport, result, reports, inputs, "route sets differ");
        assertDrift(expected, deadlineOnly, result, reports, inputs, "route sets differ");
        assertDrift(expected, report, result, moreReports, inputs, "route sets differ");
        assertDrift(expected, report, otherRoutes, reports, inputs, "result routes differ");
        assertDrift(moreExpected, moreReport, result, moreReports, inputs, "result routes differ");
        // With no routes at all, only the node check itself can refuse a missing node.
        ObjectNode noClassification = report.deepCopy();
        noClassification.remove(CLASSIFICATION_FIELD);
        ObjectNode emptyNodes = noClassification.deepCopy();
        emptyNodes.putObject(CLASSIFICATION_FIELD);
        emptyNodes.putObject(DEADLINE_FIELD);
        emptyNodes.putNull(HEADS_FIELD);
        ((ObjectNode) noClassification).putObject(DEADLINE_FIELD);
        noClassification.putNull(HEADS_FIELD);
        ResultsPipeline.Result noRoutes = new ResultsPipeline.Result(Map.of(), result.report(), null, Map.of(), Map.of());
        FrbOperator.requireNoDrift(Map.of(), emptyNodes, noRoutes, Map.of(), Map.of());
        assertDrift(Map.of(), noClassification, noRoutes, Map.of(), Map.of(), "missing or non-object node");
        ObjectNode nullHeads = report.deepCopy();
        nullHeads.putNull(HEADS_FIELD);
        assertDrift(expected, nullHeads, result, reports, inputs, "ledger heads differ");
    }

    private static void assertDrift(Map<String, FrbClassification> expected, JsonNode report,
            ResultsPipeline.Result result, Map<String, RouteVerdictCalculator.RouteReport> reports,
            Map<String, FrbClassificationInput> inputs, String message) {
        assertThatThrownBy(() -> FrbOperator.requireNoDrift(expected, report, result, reports, inputs))
                .isInstanceOf(FrbOperator.OperatorRefusal.class).hasMessageContaining(message);
    }

    @Test void seamRunnerAndCheckerExceptionsAreStepFailuresAndAnErrorIsNotCaught() throws Exception {
        Harness h = harness(false);
        FrbOperator.Sources sources = sources(h);
        FrbOperator.FrbRunner throwing = (run, frb) -> { throw new IllegalStateException("synthetic runner"); };
        FrbOperator.Outcome runnerFailure = FrbOperator.run(sources, throwing, REAL_CHECKER);
        assertFailedAt(runnerFailure, FrbOperator.Step.RUN_FRB, h.run());
        assertThat(runnerFailure.failure()).isEqualTo("java.lang.IllegalStateException: synthetic runner"
                + "; the report on disk is INVALID and must not be read");
        assertThat(h.run().report()).doesNotExist();
        FrbOperator.SwitchChecker failing = (route, report, input, run, frb, ledgers, fresh, pending, options) -> {
            throw new IllegalStateException("synthetic checker");
        };
        FrbOperator.Outcome checkerFailure = FrbOperator.run(sources, REAL_RUNNER, failing);
        assertFailedAt(checkerFailure, FrbOperator.Step.SWITCH_CHECK, h.run());
        assertThat(checkerFailure.failure()).isEqualTo("java.lang.IllegalStateException: synthetic checker");
        assertThat(checkerFailure.classifications()).isEqualTo(RouteClassificationInputs.classify(checkerFailure.built()));
        deleteReport(h);
        FrbOperator.SwitchChecker silent = (route, report, input, run, frb, ledgers, fresh, pending, options) -> null;
        FrbOperator.Outcome nullDecision = FrbOperator.run(sources, REAL_RUNNER, silent);
        assertFailedAt(nullDecision, FrbOperator.Step.SWITCH_CHECK, h.run());
        deleteReport(h);
        FrbOperator.FrbRunner fatal = (run, frb) -> { throw new OutOfMemoryError(SYNTHETIC); };
        assertThatThrownBy(() -> FrbOperator.run(sources, fatal, REAL_CHECKER))
                .isInstanceOf(OutOfMemoryError.class).hasMessage(SYNTHETIC);
    }

    @Test void switchCheckStaleFreshHeadIsALedgerReasonNotAStepFailureWhileTheClassificationHeadsAreCurrent()
            throws Exception {
        Harness h = harness(setup(false), false);
        Map<String, FindingsLedger.ExternalHeads> stale = h.heads();
        h.attest();
        FrbOperator.Outcome outcome = FrbOperator.run(sources(h, notes(h.setup().frb().provenance()), stale,
                costLedgers()));
        assertCompleted(outcome, h.run());
        assertThat(outcome.classifications().get(ROUTE).classification())
                .isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
        assertThat(outcome.built().notes().get(ROUTE).composed()).isTrue();
        assertThat(outcome.decisions().get(ROUTE).reasons()).contains(ProductionSwitchEligibility.LEDGER_CHECK_FAILED,
                ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
        assertThat(outcome.decisions().get(ROUTE).eligible()).isFalse();
    }

    @Test void seamAnInvalidLedgerInsideTheRealCheckIsAReasonNeverAnException() throws Exception {
        Harness h = harness(false);
        Path review = h.classA().reviewFile("no_veto_note");
        FrbOperator.SwitchChecker corrupting = (route, report, input, run, frb, ledgers, fresh, pending, options) -> {
            try {
                Files.writeString(review, "flipped");
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
            return ProductionSwitchEligibility.checkWithOptions(route, report, input, run, frb, ledgers, fresh,
                    pending, options);
        };
        FrbOperator.Outcome outcome = FrbOperator.run(sources(h), REAL_RUNNER, corrupting);
        assertCompleted(outcome, h.run());
        assertThat(outcome.decisions().get(ROUTE).reasons()).contains(ProductionSwitchEligibility.LEDGER_CHECK_FAILED);
    }

    // ---- Reachable and unreachable switch reasons on the real flow ----

    @Test void reachableCostReviewRequiredOnTheExpensiveFixtureWithTheDerivedCostFlagsClassified() throws Exception {
        Harness h = harness(setup(false, true), true);
        FrbOperator.Outcome outcome = FrbOperator.run(sources(h));
        assertCompleted(outcome, h.run());
        var expected = OperationalCostReview.flagsByRoute(ResultsPipeline.operationalCostReview(h.run()),
                FrozenGateParameters.load(h.run().parameters()));
        assertThat(expected.get(ROUTE)).isNotEmpty();
        assertThat(outcome.built().derivedCostFlags()).isEqualTo(expected);
        assertThat(outcome.classifications().get(ROUTE).flags()).containsAll(expected.get(ROUTE));
        assertThat(outcome.decisions().get(ROUTE).reasons()).contains(ProductionSwitchEligibility.COST_REVIEW_REQUIRED);
    }

    @Test void reachableShapeBRouteCompletesWithEvaluationFailedNotCompositionFailed() throws Exception {
        Harness h = harness(setup(false), false);
        h.classA().complete("no_veto_note", new FindingsLedger.ReviewSubject(ReviewRecord.Kind.NO_VETO, STEM, ROUTE));
        FrbOperator.Outcome outcome = FrbOperator.run(sources(h));
        assertCompleted(outcome, h.run());
        assertThat(outcome.built().inputs().get(ROUTE).reviewCheck()).isNull();
        assertThat(outcome.classifications().get(ROUTE).classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(outcome.decisions().get(ROUTE).reasons()).contains(ProductionSwitchEligibility.EVALUATION_FAILED)
                .doesNotContain(ProductionSwitchEligibility.COMPOSITION_FAILED);
        JsonNode report = IntegritySupport.JSON.readTree(h.run().report().toFile());
        assertThat(report.has(HEADS_FIELD)).isTrue();
        assertThat(report.get(HEADS_FIELD).isNull()).isTrue();
        ObjectNode objectHeads = report.deepCopy();
        objectHeads.putObject(HEADS_FIELD);
        var result = new ResultsPipeline.Result(outcome.built().reports(), h.run().report(), null, Map.of(), Map.of());
        FrbOperator.requireNoDrift(outcome.classifications(), report, result, outcome.built().reports(),
                outcome.built().inputs());
        assertDrift(outcome.classifications(), objectHeads, result, outcome.built().reports(), outcome.built().inputs(),
                "ledger heads differ");
    }

    @Test void unreachableEndToEndReportDiffersAndCompositionFailedAreAbsentFromACleanRun() throws Exception {
        // Not a claim of reachability: both reasons need a change between build and the check, which
        // one run cannot produce (fact 5 of the brief).
        Harness h = harness(true);
        FrbOperator.Outcome outcome = FrbOperator.run(sources(h));
        assertCompleted(outcome, h.run());
        assertThat(outcome.decisions().get(ROUTE).reasons()).doesNotContain(
                ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION,
                ProductionSwitchEligibility.COMPOSITION_FAILED, ProductionSwitchEligibility.REPORT_RECOMPUTATION_FAILED);
    }

    // ---- Preconditions ----

    @Test void preconditionsThrowBeforeAnyStepAndWriteNothing() throws Exception {
        Harness h = harness(false);
        FrbOperator.Sources base = sources(h);
        var bundle = base.bundleSources();
        var run = base.run();
        var frb = base.frb();
        var notes = base.notedAnchorTextByAccessLog();
        var ledgers = base.ledgers();
        var fresh = base.freshNotedHeadsByLockSha256();
        Map<Path, String> none = Map.of();
        Map<String, FindingsLedger.ExternalHeads> fewer = new HashMap<>(fresh);
        fewer.remove(h.classA().hash());
        Map<Path, String> before = snapshot();
        assertThatThrownBy(() -> new FrbOperator.Sources(null, run, frb, notes, ledgers, fresh, OWNER_FACTS, null))
                .isInstanceOf(FrbOperator.InvalidOperatorInputs.class);
        assertThatThrownBy(() -> new FrbOperator.Sources(bundle, null, frb, notes, ledgers, fresh, OWNER_FACTS, null))
                .isInstanceOf(FrbOperator.InvalidOperatorInputs.class);
        assertThatThrownBy(() -> new FrbOperator.Sources(bundle, run, null, notes, ledgers, fresh, OWNER_FACTS, null))
                .isInstanceOf(FrbOperator.InvalidOperatorInputs.class);
        assertThatThrownBy(() -> new FrbOperator.Sources(bundle, run, frb, null, ledgers, fresh, OWNER_FACTS, null))
                .isInstanceOf(FrbOperator.InvalidOperatorInputs.class);
        assertThatThrownBy(() -> new FrbOperator.Sources(bundle, run, frb, notes, null, fresh, OWNER_FACTS, null))
                .isInstanceOf(FrbOperator.InvalidOperatorInputs.class);
        assertThatThrownBy(() -> new FrbOperator.Sources(bundle, run, frb, notes, ledgers, null, OWNER_FACTS, null))
                .isInstanceOf(FrbOperator.InvalidOperatorInputs.class);
        assertThatThrownBy(() -> new FrbOperator.Sources(bundle, run, frb, notes, ledgers, fresh, null, null))
                .isInstanceOf(FrbOperator.InvalidOperatorInputs.class);
        assertThatThrownBy(() -> new FrbOperator.Sources(bundle, run, frb, none, ledgers, fresh, OWNER_FACTS, null))
                .isInstanceOf(FrbOperator.InvalidOperatorInputs.class).hasMessageContaining("noted anchor");
        assertThatThrownBy(() -> new FrbOperator.Sources(bundle, run, frb, notes, ledgers, fewer, OWNER_FACTS, null))
                .isInstanceOf(FrbOperator.InvalidOperatorInputs.class).hasMessageContaining("ledger locks");
        assertThatThrownBy(() -> FrbOperator.run(null)).isInstanceOf(FrbOperator.InvalidOperatorInputs.class);
        assertThatThrownBy(() -> FrbOperator.run(base, null, REAL_CHECKER))
                .isInstanceOf(FrbOperator.InvalidOperatorInputs.class);
        assertThatThrownBy(() -> FrbOperator.run(base, REAL_RUNNER, null))
                .isInstanceOf(FrbOperator.InvalidOperatorInputs.class);
        assertThat(snapshot()).isEqualTo(before);
        Files.writeString(run.report(), "{}");
        Map<Path, String> withReport = snapshot();
        AtomicInteger calls = new AtomicInteger();
        FrbOperator.FrbRunner counting = (r, f) -> {
            calls.incrementAndGet();
            return ResultsPipeline.runFrb(r, f);
        };
        assertThatThrownBy(() -> FrbOperator.run(base, counting, REAL_CHECKER))
                .isInstanceOf(FrbOperator.InvalidOperatorInputs.class).hasMessageContaining("already exists");
        assertThat(calls).hasValue(0);
        assertThat(snapshot()).isEqualTo(withReport);
        assertThat(Files.readString(run.report())).isEqualTo("{}");
    }

    // ---- Plumbing ----

    private Map<Path, String> snapshot() throws Exception {
        Map<Path, String> result = new TreeMap<>();
        try (Stream<Path> files = Files.walk(temp)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) result.put(file, FreezeRecord.sha256(file));
        }
        return result;
    }

    private static Set<String> systemTemp() throws Exception {
        try (Stream<Path> paths = Files.list(Path.of(System.getProperty(TEMP_ROOT)))) {
            return paths.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    @Test void plumbingTheFlowWritesOnlyTheReportLeavesNoScratchAndOpensNoKey() throws Exception {
        Harness h = harness(true);
        FrbOperator.Sources sources = sources(h, notes(h.setup().frb().provenance()), h.heads(), costLedgers());
        Map<Path, String> before = snapshot();
        Set<String> scratch = systemTemp();
        FrbOperator.Outcome outcome = FrbOperator.run(sources);
        assertCompleted(outcome, h.run());
        Map<Path, String> after = snapshot();
        assertThat(after).containsKey(h.run().report());
        after.remove(h.run().report());
        assertThat(after).isEqualTo(before);
        assertThat(systemTemp()).isEqualTo(scratch);
        for (Path logFile : expectedAnchors(h.setup().frb().provenance()).keySet())
            assertThat(before).containsKey(logFile);
        assertThat(outcome.completedSteps()).isUnmodifiable();
        assertThat(outcome.decisions()).isUnmodifiable();
        assertThat(outcome.classifications()).isUnmodifiable();
        assertThat(sources.notedAnchorTextByAccessLog()).isUnmodifiable();
        assertThat(sources.freshNotedHeadsByLockSha256()).isUnmodifiable();
        assertThat(sources.costLedgers()).isUnmodifiable();
    }

    // ---- Audit addition (release session): an extra classification route and same-size different causes ----

    @Test void directRequireNoDriftRefusesAnExtraClassificationRouteAndSameSizeDifferentCauses() throws Exception {
        Harness h = harness(false);
        List<ResultsPipeline.Result> results = new ArrayList<>();
        FrbOperator.FrbRunner capturing = (run, frb) -> {
            ResultsPipeline.Result result = ResultsPipeline.runFrb(run, frb);
            results.add(result);
            return result;
        };
        FrbOperator.Outcome outcome = FrbOperator.run(sources(h), capturing, REAL_CHECKER);
        assertCompleted(outcome, h.run());
        JsonNode report = IntegritySupport.JSON.readTree(h.run().report().toFile());
        ResultsPipeline.Result result = results.getFirst();
        Map<String, FrbClassification> expected = outcome.classifications();
        var reports = outcome.built().reports();
        var inputs = outcome.built().inputs();
        ObjectNode extraRoute = report.deepCopy();
        ((ObjectNode) extraRoute.get(CLASSIFICATION_FIELD)).set("CRITIQUE",
                extraRoute.get(CLASSIFICATION_FIELD).get(ROUTE));
        assertDrift(expected, extraRoute, result, reports, inputs, "route sets differ");
        FrbClassification want = expected.get(ROUTE);
        Map<String, FrbClassification> other = new TreeMap<>(expected);
        other.put(ROUTE, new FrbClassification(want.classification(),
                new java.util.TreeSet<>(java.util.Set.of("CAUSE_A")), want.flags(), want.blocksProductionSwitch()));
        ObjectNode differentCause = report.deepCopy();
        ObjectNode node = (ObjectNode) differentCause.get(CLASSIFICATION_FIELD).get(ROUTE);
        node.putArray("causes").add("CAUSE_B");
        assertDrift(other, differentCause, result, reports, inputs, "causes differ");
    }
}
