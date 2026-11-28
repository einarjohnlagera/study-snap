package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Switch-time assertions on the 5r-2a combined fixture. Copied privately, and named here:
 * FrbOperatorTest's operator Harness and RouteClassificationInputsTest.setup(withReserve,
 * expensiveCandidate) (itself copied from
 * FrbBundleAssemblerTest with FrbPipelineTest's receipt re-stamp; the class-A key opens after the
 * reserve decision and, with a drawn reserve, binds the reserve forPrimary lock), its detached and
 * restampReceipts helpers, its Ledger, Harness, harness, ledger, costLedger and approved helpers;
 * LockAnchorsTest.secondLock and rewriteLock. Tests whose name starts with "seam" drive the flow
 * through a test-only FrbRunner or SwitchChecker, or call requireNoDrift directly; every other test
 * runs the real ResultsPipeline.runFrb and ProductionSwitchEligibility.checkWithOptions.
 */
class SwitchTimeAssertionsTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String STEM = "NOTE_ADAPTIVE";
    private static final String TIP = "STUDY_TIP";
    private static final String MINI = "BASELINE_MINI";
    private static final String PAID = "BASELINE_41";
    private static final String LUNA = "LUNA";
    private static final String CLOCK = "2026-11-16T00:00:30Z";
    private static final String OPENED = "2026-11-16T00:07:00Z";
    private static final String A_OPENED = "2026-11-16T00:05:30Z";
    private static final String CREATED = OPENED;
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
    private static final String WORKFLOW_JSON_PREFIX = "{\"workflow\":\"";
    private static final String WORKFLOWS_FIELD = "workflows";
    private static final String ONE_DOLLAR = "1.00";
    private static final String INVENTED_CASE = "invented-case";
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
                + WORKFLOW_JSON_PREFIX + STEM + "\",\"purpose\":\"SPOT_CHECK\",\"rounds\":1},"
                + WORKFLOW_JSON_PREFIX + STEM + "\",\"purpose\":\"EXPANSION\",\"rounds\":3}]}");
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
                    ((ObjectNode) row).put("conservativeUsd", new BigDecimal(ONE_DOLLAR))
                            .put("billedUsd", new BigDecimal(ONE_DOLLAR));
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

    private record Call(String route, RouteVerdictCalculator.RouteReport report, FrbClassificationInput input,
            ResultsPipeline.FrbRunInput run, ResultsPipeline.FrbInput frb, Map<String, FindingsLedger> ledgers,
            Map<String, FindingsLedger.ExternalHeads> fresh, Set<String> pending,
            ProductionSwitchEligibility.SwitchOptions options) {
        ProductionSwitchEligibility.Decision check(FrbClassificationInput changed,
                ProductionSwitchEligibility.SwitchOptions chosen) {
            return ProductionSwitchEligibility.checkWithOptions(route, report, changed, run, frb, ledgers,
                    fresh, pending, chosen);
        }
        ProductionSwitchEligibility.Decision check(ProductionSwitchEligibility.SwitchOptions chosen) {
            return check(input, chosen);
        }
    }

    private record Checked(Harness harness, FrbOperator.Outcome outcome, Call call) {}

    private Checked checked(boolean reserve) throws Exception {
        return checked(reserve, false);
    }

    private Checked checked(boolean reserve, boolean operationalAttestation) throws Exception {
        return checked(harness(reserve), operationalAttestation);
    }

    private Checked checked(Harness h, boolean operationalAttestation) throws Exception {
        if (operationalAttestation)
            h.classA().complete("operational_attestation", new FindingsLedger.ReviewSubject(
                    ReviewRecord.Kind.OPERATIONAL_CAUSE, STEM,
                    OperationalCauses.subjectId(OperationalCauses.Category.PROVIDER_OUTAGE, STEM, CASE_ONE)));
        FrbOperator.Sources source = sources(h, notes(h.setup().frb().provenance()), h.heads(), costLedgers());
        List<Call> calls = new ArrayList<>();
        FrbOperator.SwitchChecker recording = (route, report, input, run, frb, ledgers, fresh, pending, options) -> {
            calls.add(new Call(route, report, input, run, frb, ledgers, fresh, pending, options));
            return ProductionSwitchEligibility.checkWithOptions(route, report, input, run, frb, ledgers,
                    fresh, pending, options);
        };
        FrbOperator.Outcome outcome = FrbOperator.run(source, REAL_RUNNER, recording);
        assertCompleted(outcome, h.run());
        assertThat(calls).hasSize(1);
        return new Checked(h, outcome, calls.getFirst());
    }

    private static ProductionSwitchEligibility.SwitchOptions options(Call call, boolean recompute,
            boolean owner, boolean operational, FrbBundleAssembler.Sources sources, boolean verify) {
        return new ProductionSwitchEligibility.SwitchOptions(call.options().costLedgers(), recompute,
                owner, operational, sources, verify);
    }

    private static FrbClassificationInput assertions(FrbClassificationInput original,
            Set<String> missing, Set<String> cases, Map<String, String> expansion) {
        return new FrbClassificationInput(original.openSafetyFindingIds(), original.openDisputeIds(),
                missing, original.costFlags(), cases, original.ledgerFailure(), original.solRecommended(),
                original.ownerVetoFindingIds(), expansion, original.ledgerHeadHashes(), original.reviewCheck());
    }

    private static void blockedBy(ProductionSwitchEligibility.Decision decision, String reason) {
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons()).contains(reason,
                ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
    }

    @Test void cleanOperatorRouteClearsTheAssertionReasonAndExistingConstructorsDoNot() throws Exception {
        Checked checked = checked(false);
        Call call = checked.call();
        assertThat(checked.outcome().decisions().get(ROUTE).eligible()).isTrue();
        assertThat(checked.outcome().decisions().get(ROUTE).reasons()).isEmpty();
        assertThat(checked.outcome().decisions().get(ROUTE).reasons())
                .doesNotContain(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
        assertThat(call.options().requireVerifiedAssertions()).isTrue();
        assertThat(call.options().reassemblySources()).isEqualTo(checked.harness().setup().bundleSources());
        for (ProductionSwitchEligibility.SwitchOptions legacy : List.of(
                new ProductionSwitchEligibility.SwitchOptions(call.options().costLedgers(), true, true),
                new ProductionSwitchEligibility.SwitchOptions(call.options().costLedgers(), true, true, true),
                options(call, true, true, true, call.options().reassemblySources(), false))) {
            ProductionSwitchEligibility.Decision decision = call.check(legacy);
            assertThat(decision.reasons()).contains(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
            assertThat(decision.eligible()).isFalse();
        }
    }

    @Test void eachRequiredOptionAndTheSourcesGateFailClosed() throws Exception {
        Call call = checked(false).call();
        FrbBundleAssembler.Sources sources = call.options().reassemblySources();
        blockedBy(call.check(options(call, false, true, true, sources, true)),
                ProductionSwitchEligibility.ASSERTION_OPTIONS_INCOMPLETE);
        blockedBy(call.check(options(call, true, false, true, sources, true)),
                ProductionSwitchEligibility.ASSERTION_OPTIONS_INCOMPLETE);
        blockedBy(call.check(options(call, true, true, false, sources, true)),
                ProductionSwitchEligibility.ASSERTION_OPTIONS_INCOMPLETE);
        blockedBy(call.check(options(call, true, true, true, null, true)),
                ProductionSwitchEligibility.ASSERTION_SOURCES_MISSING);
    }

    @Test void missingReviewAssertionMustBePresentAndEmpty() throws Exception {
        Call call = checked(false).call();
        FrbClassificationInput input = call.input();
        blockedBy(call.check(assertions(input, null, input.establishedOperationalCaseIds(),
                        input.expansionOutcomeByWorkflow()), call.options()),
                ProductionSwitchEligibility.MISSING_REVIEWS_ASSERTION_DIFFERS);
        blockedBy(call.check(assertions(input, Set.of("unreviewed"), input.establishedOperationalCaseIds(),
                        input.expansionOutcomeByWorkflow()), call.options()),
                ProductionSwitchEligibility.MISSING_REVIEWS_ASSERTION_DIFFERS);
    }

    @Test void failedFreshCompositionKeepsItsNamedAssertionReason() throws Exception {
        Call call = checked(false).call();
        ResultsPipeline.ProvenanceLogs incomplete = new ResultsPipeline.ProvenanceLogs(
                List.of(call.frb().provenance().primaries().getFirst()), Map.of());
        ResultsPipeline.FrbInput changed = new ResultsPipeline.FrbInput(call.frb().bundle(),
                call.frb().reserveRecordFile(), call.frb().verification(), incomplete,
                call.frb().routeClassifications(), true);
        ProductionSwitchEligibility.Decision decision = ProductionSwitchEligibility.checkWithOptions(
                call.route(), call.report(), call.input(), call.run(), changed, call.ledgers(),
                call.fresh(), call.pending(), call.options());
        blockedBy(decision, ProductionSwitchEligibility.MISSING_REVIEWS_ASSERTION_DIFFERS);
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.COMPOSITION_FAILED);
    }

    @Test void differingFreshCompositionKeepsItsNamedAssertionReason() throws Exception {
        Checked checked = checked(false);
        Call call = checked.call();
        Ledger ledger = checked.harness().classA();
        ledger.complete("late_absolute_quality", new FindingsLedger.ReviewSubject(
                ReviewRecord.Kind.ABSOLUTE_QUALITY, STEM, CASE_ONE));
        Map<String, FindingsLedger.ExternalHeads> fresh = new HashMap<>(call.fresh());
        fresh.put(ledger.hash(), ledger.heads());
        ProductionSwitchEligibility.Decision decision = ProductionSwitchEligibility.checkWithOptions(
                call.route(), call.report(), call.input(), call.run(), call.frb(), call.ledgers(),
                fresh, call.pending(), call.options());
        blockedBy(decision, ProductionSwitchEligibility.MISSING_REVIEWS_ASSERTION_DIFFERS);
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.COMPOSITION_DIFFERS);
    }

    @Test void operationalAssertionRequiresExactNonNullLedgerDerivedSet() throws Exception {
        Call call = checked(false).call();
        FrbClassificationInput input = call.input();
        blockedBy(call.check(assertions(input, input.missingRequiredReviews(), null,
                        input.expansionOutcomeByWorkflow()), call.options()),
                ProductionSwitchEligibility.OPERATIONAL_CASES_DIFFER_FROM_LEDGER);
        blockedBy(call.check(assertions(input, input.missingRequiredReviews(), Set.of(INVENTED_CASE),
                        input.expansionOutcomeByWorkflow()), call.options()),
                ProductionSwitchEligibility.OPERATIONAL_CASES_DIFFER_FROM_LEDGER);
    }

    @Test void conditionalReportDetectsBothOmittedAndInventedOperationalCases() throws Exception {
        Call call = checked(false, true).call();
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.valueToTree(call.report());
        ObjectNode causeIds = IntegritySupport.JSON.createObjectNode();
        causeIds.putArray("MISSING_RUN_DATA").add(CASE_ONE);
        ((ObjectNode) node.path(WORKFLOWS_FIELD).path(STEM)).set("unresolvedCaseIdsByCause", causeIds);
        RouteVerdictCalculator.RouteReport conditional = IntegritySupport.JSON.treeToValue(node,
                RouteVerdictCalculator.RouteReport.class);
        List<FindingsLedger> ordered = new ArrayList<>();
        for (String lock : call.input().reviewCheck().lockSha256s()) ordered.add(call.ledgers().get(lock));
        assertThat(OperationalCauses.derive(ordered, ROUTE, conditional).establishedCaseIds())
                .containsExactly(CASE_ONE);
        FrbClassificationInput input = call.input();
        for (Set<String> wrong : List.of(Set.<String>of(), Set.of(CASE_ONE, INVENTED_CASE))) {
            FrbClassificationInput changed = assertions(input, input.missingRequiredReviews(), wrong,
                    input.expansionOutcomeByWorkflow());
            ProductionSwitchEligibility.Decision decision = ProductionSwitchEligibility.checkWithOptions(
                    call.route(), conditional, changed, call.run(), call.frb(), call.ledgers(),
                    call.fresh(), call.pending(), call.options());
            blockedBy(decision, ProductionSwitchEligibility.OPERATIONAL_CASES_DIFFER_FROM_LEDGER);
        }
    }

    @Test void operationalDerivationFailureIsAReason() throws Exception {
        Call call = checked(false).call();
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.valueToTree(call.report());
        node.set(WORKFLOWS_FIELD, IntegritySupport.JSON.createObjectNode());
        RouteVerdictCalculator.RouteReport changed = IntegritySupport.JSON.treeToValue(node,
                RouteVerdictCalculator.RouteReport.class);
        ProductionSwitchEligibility.Decision decision = ProductionSwitchEligibility.checkWithOptions(
                call.route(), changed, call.input(), call.run(), call.frb(), call.ledgers(),
                call.fresh(), call.pending(), call.options());
        blockedBy(decision, ProductionSwitchEligibility.OPERATIONAL_CASES_RECOMPUTATION_FAILED);
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.COMPOSITION_FAILED,
                ProductionSwitchEligibility.MISSING_REVIEWS_ASSERTION_DIFFERS);
    }

    @Test void changedOperationalStoreStopsAtTheEarlierLedgerCheck() throws Exception {
        Checked checked = checked(false);
        Call call = checked.call();
        checked.harness().classA().complete("late_operational_attestation",
                new FindingsLedger.ReviewSubject(ReviewRecord.Kind.OPERATIONAL_CAUSE, STEM,
                        OperationalCauses.subjectId(OperationalCauses.Category.PROVIDER_OUTAGE, STEM, CASE_ONE)));
        ProductionSwitchEligibility.Decision decision = call.check(call.options());
        blockedBy(decision, ProductionSwitchEligibility.LEDGER_CHECK_FAILED);
        assertThat(decision.reasons()).doesNotContain(
                ProductionSwitchEligibility.OPERATIONAL_CASES_RECOMPUTATION_FAILED);
    }

    @Test void expansionAssertionRequiresExactNonNullReassembledMap() throws Exception {
        Call call = checked(false).call();
        FrbClassificationInput input = call.input();
        Map<String, String> original = input.expansionOutcomeByWorkflow();
        blockedBy(call.check(assertions(input, input.missingRequiredReviews(),
                        input.establishedOperationalCaseIds(), null), call.options()),
                ProductionSwitchEligibility.EXPANSION_OUTCOMES_DIFFER_FROM_REASSEMBLY);
        blockedBy(call.check(assertions(input, input.missingRequiredReviews(),
                        input.establishedOperationalCaseIds(), Map.of()), call.options()),
                ProductionSwitchEligibility.EXPANSION_OUTCOMES_DIFFER_FROM_REASSEMBLY);
        Map<String, String> extra = new HashMap<>(original);
        extra.put("added-workflow", "NOT_TRIGGERED");
        blockedBy(call.check(assertions(input, input.missingRequiredReviews(),
                        input.establishedOperationalCaseIds(), extra), call.options()),
                ProductionSwitchEligibility.EXPANSION_OUTCOMES_DIFFER_FROM_REASSEMBLY);
        Map<String, String> changed = new HashMap<>(original);
        changed.put(STEM, "TRIGGERED");
        blockedBy(call.check(assertions(input, input.missingRequiredReviews(),
                        input.establishedOperationalCaseIds(), changed), call.options()),
                ProductionSwitchEligibility.EXPANSION_OUTCOMES_DIFFER_FROM_REASSEMBLY);
    }

    @Test void reassemblyFailureAndChangedBundleKeepTheBlock() throws Exception {
        Checked first = checked(false);
        Call call = first.call();
        ClassASources source = call.options().reassemblySources().classAExports().getFirst();
        ClassASources unreadable = new ClassASources(source.freezeFile(), source.parametersFile(),
                source.mappingFile(), source.manifests(), source.export(),
                temp.resolve("missing-draw-log.jsonl"), source.seedRegistryFile());
        FrbBundleAssembler.Sources broken = new FrbBundleAssembler.Sources(List.of(unreadable),
                call.options().reassemblySources().classB());
        blockedBy(call.check(options(call, true, true, true, broken, true)),
                ProductionSwitchEligibility.EXPANSION_REASSEMBLY_FAILED);

        Checked second = checked(false);
        ProductionSwitchEligibility.Decision different = call.check(options(call, true, true, true,
                second.call().options().reassemblySources(), true));
        blockedBy(different, ProductionSwitchEligibility.BUNDLE_DIFFERS_FROM_REASSEMBLY);
    }

    @Test void equalBundleWithCopiedFindingsDirectoryFailsTheBindingGate() throws Exception {
        Call call = checked(false).call();
        ClassASources original = call.options().reassemblySources().classAExports().getFirst();
        Path copy = Files.createDirectory(temp.resolve("copied-findings"));
        try (Stream<Path> files = Files.list(original.export().pScores().getParent())) {
            for (Path file : files.toList()) Files.copy(file, copy.resolve(file.getFileName()));
        }
        ClassAExport export = original.export();
        ClassAExport copiedExport = new ClassAExport(export.packetDirectory(),
                copy.resolve(export.pScores().getFileName()), copy.resolve(export.sScores().getFileName()),
                copy.resolve(export.hScores().getFileName()), export.key(), export.sealFile(),
                export.lockFile(), export.exclusionsFileOrNull(), export.expectedExclusionsSha256OrNull());
        ClassASources copied = new ClassASources(original.freezeFile(), original.parametersFile(),
                original.mappingFile(), original.manifests(), copiedExport,
                copy.resolve(original.drawLogFile().getFileName()),
                copy.resolve(original.seedRegistryFile().getFileName()));
        FrbBundleAssembler.Sources sources = new FrbBundleAssembler.Sources(List.of(copied),
                call.options().reassemblySources().classB());
        assertThat(FrbBundleAssembler.assemble(sources).bundle()).isEqualTo(call.frb().bundle());
        ProductionSwitchEligibility.Decision decision = call.check(options(call, true, true, true, sources, true));
        blockedBy(decision, ProductionSwitchEligibility.BUNDLE_DIFFERS_FROM_REASSEMBLY);
        assertThat(decision.reasons()).doesNotContain(ProductionSwitchEligibility.EXPANSION_REASSEMBLY_FAILED);
    }

    @Test void successfulAssertionChecksDoNotEraseOtherReasons() throws Exception {
        Call call = checked(false).call();
        ProductionSwitchEligibility.Decision clean = call.check(call.options());
        assertThat(clean.eligible()).isTrue();
        assertThat(clean.reasons()).isEmpty();
        Checked reserveChecked = checked(true);
        Call reserve = reserveChecked.call();
        ProductionSwitchEligibility.Decision classification = reserve.check(reserve.options());
        assertThat(classification.eligible()).isFalse();
        assertThat(classification.reasons()).containsExactly(
                ProductionSwitchEligibility.CLASSIFICATION_BLOCKS_SWITCH);
        assertThat(DeadlineRule.uncoveredDispositionIds(reserveChecked.outcome().classifications().get(ROUTE).causes(),
                reserve.input().reviewCheck())).isNotEmpty();
        ProductionSwitchEligibility.SwitchOptions noCost = new ProductionSwitchEligibility.SwitchOptions(
                null, true, true, true, call.options().reassemblySources(), true);
        ProductionSwitchEligibility.Decision missingCost = call.check(noCost);
        assertThat(missingCost.reasons()).contains(ProductionSwitchEligibility.COST_EVIDENCE_NOT_LEDGER_BACKED)
                .doesNotContain(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
        ProductionSwitchEligibility.Decision pending = ProductionSwitchEligibility.checkWithOptions(call.route(),
                call.report(), call.input(), call.run(), call.frb(), call.ledgers(), call.fresh(),
                Set.of(ROUTE), call.options());
        assertThat(pending.reasons()).contains(ProductionSwitchEligibility.PENDING_REVIEW_ITEMS)
                .doesNotContain(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
        Map<String, FindingsLedger.ExternalHeads> stale = new HashMap<>(call.fresh());
        stale.remove(call.input().reviewCheck().lockSha256s().iterator().next());
        ProductionSwitchEligibility.Decision absentHead = ProductionSwitchEligibility.checkWithOptions(call.route(),
                call.report(), call.input(), call.run(), call.frb(), call.ledgers(), stale,
                call.pending(), call.options());
        blockedBy(absentHead, ProductionSwitchEligibility.EVALUATION_FAILED);

        ObjectNode changedReport = (ObjectNode) IntegritySupport.JSON.valueToTree(call.report());
        changedReport.put("d", call.report().d() + 1);
        RouteVerdictCalculator.RouteReport report = IntegritySupport.JSON.treeToValue(changedReport,
                RouteVerdictCalculator.RouteReport.class);
        ProductionSwitchEligibility.Decision reportMismatch = ProductionSwitchEligibility.checkWithOptions(
                call.route(), report, call.input(), call.run(), call.frb(), call.ledgers(),
                call.fresh(), call.pending(), call.options());
        assertThat(reportMismatch.reasons()).contains(
                ProductionSwitchEligibility.REPORT_DIFFERS_FROM_RECOMPUTATION)
                .doesNotContain(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
        FrbClassificationInput input = call.input();
        FrbClassificationInput claimedVeto = new FrbClassificationInput(input.openSafetyFindingIds(),
                input.openDisputeIds(), input.missingRequiredReviews(), input.costFlags(),
                input.establishedOperationalCaseIds(), input.ledgerFailure(), input.solRecommended(),
                Set.of(CASE_ONE), input.expansionOutcomeByWorkflow(), input.ledgerHeadHashes(),
                input.reviewCheck());
        ProductionSwitchEligibility.Decision vetoMismatch = call.check(claimedVeto, call.options());
        assertThat(vetoMismatch.eligible()).isFalse();
        assertThat(vetoMismatch.reasons()).contains(ProductionSwitchEligibility.OWNER_VETO_DIFFERS_FROM_LEDGER)
                .doesNotContain(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
    }

    @Test void triggeredOperationalCostRemainsBlockingAfterAssertionsMatch() throws Exception {
        Call call = checked(harness(setup(false, true), true), false).call();
        ProductionSwitchEligibility.Decision decision = call.check(call.options());
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons()).contains(ProductionSwitchEligibility.COST_REVIEW_REQUIRED)
                .doesNotContain(ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
    }

    @Test void missingCoreInputsStillYieldEvaluationFailure() throws Exception {
        Call call = checked(false).call();
        assertThat(ProductionSwitchEligibility.checkWithOptions(call.route(), null, call.input(), call.run(),
                call.frb(), call.ledgers(), call.fresh(), call.pending(), call.options()).reasons())
                .contains(ProductionSwitchEligibility.EVALUATION_FAILED,
                        ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
        assertThat(ProductionSwitchEligibility.checkWithOptions(call.route(), call.report(), null, call.run(),
                call.frb(), call.ledgers(), call.fresh(), call.pending(), call.options()).reasons())
                .contains(ProductionSwitchEligibility.EVALUATION_FAILED,
                        ProductionSwitchEligibility.SWITCH_ASSERTIONS_NOT_VERIFIED);
        blockedBy(ProductionSwitchEligibility.checkWithOptions(call.route(), call.report(), call.input(), null,
                call.frb(), call.ledgers(), call.fresh(), call.pending(), call.options()),
                ProductionSwitchEligibility.EVALUATION_FAILED);
        blockedBy(ProductionSwitchEligibility.checkWithOptions(call.route(), call.report(), call.input(), call.run(),
                null, call.ledgers(), call.fresh(), call.pending(), call.options()),
                ProductionSwitchEligibility.EVALUATION_FAILED);
        blockedBy(ProductionSwitchEligibility.checkWithOptions(call.route(), call.report(), call.input(), call.run(),
                call.frb(), null, call.fresh(), call.pending(), call.options()),
                ProductionSwitchEligibility.EVALUATION_FAILED);
        blockedBy(ProductionSwitchEligibility.checkWithOptions(call.route(), call.report(), call.input(), call.run(),
                call.frb(), call.ledgers(), null, call.pending(), call.options()),
                ProductionSwitchEligibility.EVALUATION_FAILED);
    }

    @Test void verificationLeavesFixtureFilesAndScratchNamesUnchanged() throws Exception {
        Call call = checked(false).call();
        Map<Path, byte[]> before = fileBytes(temp);
        Set<String> scratchBefore = scratchNames();
        assertThat(call.check(call.options()).eligible()).isTrue();
        Map<Path, byte[]> after = fileBytes(temp);
        assertThat(after.keySet()).isEqualTo(before.keySet());
        for (Path path : before.keySet()) assertThat(Arrays.equals(after.get(path), before.get(path))).isTrue();
        assertThat(scratchNames()).isEqualTo(scratchBefore);
    }

    private static Map<Path, byte[]> fileBytes(Path directory) throws Exception {
        Map<Path, byte[]> result = new TreeMap<>();
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path path : files.filter(Files::isRegularFile).toList())
                result.put(path, Files.readAllBytes(path));
        }
        return result;
    }

    private static Set<String> scratchNames() throws Exception {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty(TEMP_ROOT)))) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("class-a-draw-replay-")
                            || name.startsWith("expansion-replay-")
                            || name.startsWith("cost-evidence-"))
                    .collect(Collectors.toSet());
        }
    }
}
