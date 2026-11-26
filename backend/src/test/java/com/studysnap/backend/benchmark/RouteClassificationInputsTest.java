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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Combined fixture copied from FrbBundleAssemblerTest (fixture with humanOnSelected and no other
 * option, plus setupFrom) with FrbPipelineTest's receipt re-stamp; ledger helpers copied from
 * OwnerAttestationTest and built from the COMBINED provenance; the conditional report row builder is
 * copied from OperationalCausesTest. Tests whose name starts with "seam" do not go through build.
 */
class RouteClassificationInputsTest {
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String OTHER_ROUTE = "CRITIQUE";
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
    private static final String RESOLUTION = "b".repeat(64);
    private static final String FINDING = "finding_one";
    private static final String VETO = "veto_one";
    private static final String REVIEW_RECORD_MISSING = "REVIEW_RECORD_MISSING";
    private static final String LEDGER_INTEGRITY_FAILURE = "LEDGER_INTEGRITY_FAILURE";
    private static final String LEDGER_NOT_SUPPLIED = "LEDGER_NOT_SUPPLIED";
    private static final String MISSING_RUN_DATA = "MISSING_RUN_DATA";
    private static final String NOT_TRIGGERED = "NOT_TRIGGERED";
    private static final String JSON_SUFFIX = ".json";
    private static final String CLASSIFICATION_FIELD = "classification";
    private static final String CAUSES_FIELD = "causes";
    private static final String FLAGS_FIELD = "flags";
    private static final String TEMP_ROOT = "java.io.tmpdir";
    private static final String COST_CAPS = "{\"schema\":1,\"caps\":{\"STAGE_1\":\"1\"}}";
    private static final KeyDefectWorkload.Parameters WORKLOAD = new KeyDefectWorkload.Parameters(
            BigDecimal.valueOf(30), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(4));
    private static final Map<String, ClassBScoreFiles.ScorerKind> SCORERS =
            Map.of(HUMAN, ClassBScoreFiles.ScorerKind.HUMAN);
    private static final RouteClassificationInputs.OwnerFacts OWNER_FACTS = new RouteClassificationInputs.OwnerFacts(
            WORKLOAD, Set.of(LUNA), Set.of(MINI, PAID), false);
    @TempDir Path temp;

    private record Setup(FrbBundleAssembler.Assembled assembled, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb, ClassASources aSources, ClassBSources b, PairMap pairs, String selected) {}

    // ---- Copied from FrbBundleAssemblerTest.fixture(true, false) and setupFrom(a, withReserve) ----

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
                    "synthetic", LearnerLevel.COLLEGE, null, BIOLOGY_LABEL, false,
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
        // Changed from the copied fixture: the class-A key opens after the reserve decision and,
        // when a reserve is drawn, also names the reserve lock, as FrbProvenance requires.
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
        return new Setup(assembled, run, frb, aSources, b, pairs, draw.selectedPairIds().getFirst());
    }

    private static void detached(Path file) throws Exception {
        Files.writeString(file.resolveSibling(file.getFileName() + SHA_SUFFIX), FreezeRecord.sha256(file) + NEWLINE);
    }

    /**
     * Copied from FrbPipelineTest.legacyReportJsonHasNoFrbKey: re-stamps every receipt (five with a
     * drawn reserve) with the new freeze hash; optionally makes the candidate cost trigger the cost review.
     */
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

    // ---- Ledger helpers copied from OwnerAttestationTest.Ledger and ledger(...) ----

    private record Ledger(Path root, PostLockContext context, SafetyFindingStore safety,
            EvidenceDisputeStore disputes, ReviewRecordStore reviews, FindingsLedger ledger, boolean classA) {
        String hash() { return context.verify().lockSha256(); }
        FindingsLedger.ExternalHeads heads() {
            return new FindingsLedger.ExternalHeads(external(safety.headSha256()),
                    external(disputes.headSha256()), external(reviews.headSha256()));
        }
        ReviewRecord record(String id, ReviewRecord.Kind kind, String workflow, String subject,
                ReviewRecord.Outcome outcome, ReviewRecord.SignerRole role) {
            return new ReviewRecord(id, CREATED, hash(), kind, workflow, subject, outcome, OWNER, role, EVIDENCE);
        }
        ReviewRecord complete(String id, FindingsLedger.ReviewSubject subject) {
            return reviews.create(record(id, subject.kind(), subject.workflow(), subject.subjectId(),
                    ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER));
        }
        String firstItem() {
            return PairMap.fromPackets(context.packetDirectory()).blindItemIds().stream()
                    .sorted().findFirst().orElseThrow();
        }
        void finding(String id) {
            safety.create(new SafetyFinding(id, CREATED, hash(), firstItem(), STEM, null,
                    List.of(ROUTE), "Incorrect answer", "reviewer", SafetyFinding.Attribution.GENERATOR,
                    false), null);
        }
        void veto(String id, String subject) {
            reviews.create(record(id, ReviewRecord.Kind.OWNER_VETO, STEM, subject,
                    ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER));
        }
        void attest(String id, String workflow, String caseId) {
            reviews.create(record(id, ReviewRecord.Kind.OPERATIONAL_CAUSE, workflow, OperationalCauses.subjectId(
                    OperationalCauses.Category.PROVIDER_OUTAGE, workflow, caseId),
                    ReviewRecord.Outcome.COMPLETE, ReviewRecord.SignerRole.OWNER));
        }
        Path reviewFile(String id) { return root.resolve(REVIEWS).resolve(id + JSON_SUFFIX); }
    }

    private record Harness(Setup setup, List<Ledger> ledgers) {
        Ledger classA() { return ledgers.stream().filter(Ledger::classA).findFirst().orElseThrow(); }
        Ledger classB() {
            return ledgers.stream().filter(item -> !item.classA()).findFirst().orElseThrow();
        }
        /** Sorted by lock hash, the order the builder uses. */
        List<Ledger> sorted() {
            return ledgers.stream().sorted(java.util.Comparator.comparing(Ledger::hash)).toList();
        }
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
        RouteClassificationInputs.Built build(RouteClassificationInputs.OwnerFacts owner,
                List<ProductionSwitchEligibility.CostLedgerInput> cost) {
            return RouteClassificationInputs.build(setup.assembled(), setup.run(), setup.frb(), routeLedgers(),
                    owner, cost);
        }
        RouteClassificationInputs.Built build() { return build(OWNER_FACTS, null); }
        void attest() {
            classA().complete("no_veto_note", new FindingsLedger.ReviewSubject(ReviewRecord.Kind.NO_VETO, STEM, ROUTE));
            classB().complete("no_veto_tip", new FindingsLedger.ReviewSubject(ReviewRecord.Kind.NO_VETO, TIP, ROUTE));
        }
    }

    private static FindingsLedger.ExternalHead external(PostLockStoreIndex.Head head) {
        return new FindingsLedger.ExternalHead(head.seq(), head.sha256());
    }

    private Harness harness(boolean withReserve, boolean attest) throws Exception {
        return harness(setup(withReserve), attest, true);
    }

    private Harness harness(Setup setup, boolean attest, boolean classBComplete) throws Exception {
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
        if (classBComplete) h.classB().complete("class_b_complete",
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

    private static Set<FindingsLedger.ReviewSubject> expectedExtras() {
        return Set.of(new FindingsLedger.ReviewSubject(ReviewRecord.Kind.NO_VETO, STEM, ROUTE),
                new FindingsLedger.ReviewSubject(ReviewRecord.Kind.NO_VETO, TIP, ROUTE));
    }

    private Path costLedger(String name) throws Exception {
        Path ledger = temp.resolve(name + ".jsonl");
        try (StageCapLedger ignored = StageCapLedger.open(ledger, Map.of(StageCapLedger.Stage.STAGE_1, BigDecimal.ONE), true)) {
            // synthetic empty ledger
        }
        return ledger;
    }

    private ApprovedCaps approved(String caps) throws Exception {
        Path file = Files.createTempFile(temp, "approved-", JSON_SUFFIX);
        Files.writeString(file, caps);
        return ApprovedCaps.load(file);
    }

    private static FrbClassification classification(RouteClassificationInputs.Built built) {
        return RouteClassificationInputs.classify(built).get(ROUTE);
    }

    /** Every shape (b) field, plus the owner return on a PASS-verdict report. */
    private static void assertShapeB(RouteClassificationInputs.Built built) {
        FrbClassificationInput input = built.inputs().get(ROUTE);
        assertThat(built.reports().get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        assertThat(input.missingRequiredReviews()).isNull();
        assertThat(input.reviewCheck()).isNull();
        assertThat(input.ledgerHeadHashes()).isNull();
        assertThat(built.notes().get(ROUTE).composed()).isFalse();
        assertThat(built.notes().get(ROUTE).compositionFailure()).isNotBlank();
        FrbClassification result = classification(built);
        assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(result.causes()).contains(REVIEW_RECORD_MISSING);
        assertThat(result.blocksProductionSwitch()).isTrue();
    }

    private static void assertLedgerFailure(RouteClassificationInputs.Built built) {
        FrbClassificationInput input = built.inputs().get(ROUTE);
        assertThat(input.ledgerFailure()).isTrue();
        assertThat(input.missingRequiredReviews()).isNull();
        assertThat(input.reviewCheck()).isNull();
        assertThat(input.ledgerHeadHashes()).isNull();
        FrbClassification result = classification(built);
        assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(result.causes()).contains(LEDGER_INTEGRITY_FAILURE, REVIEW_RECORD_MISSING);
    }

    // ---- First step: the combined setup recomputes ----

    @Test void smokeRecomputationSucceedsOnTheCombinedAssemblerSetup() throws Exception {
        for (boolean withReserve : List.of(false, true)) {
            Setup setup = setup(withReserve);
            FrbBundleAssembler.requireBound(setup.assembled(), setup.run(), setup.frb());
            var reports = ReportRecomputation.recompute(setup.run(), setup.frb());
            assertThat(reports).containsOnlyKeys(ROUTE);
            assertThat(reports.get(ROUTE).verdict()).isEqualTo(RouteVerdictCalculator.Verdict.PASS);
        }
    }

    // ---- Shape (a) ----

    @Test void cleanRouteBuildsShapeAWithLiteralEmptySetsAndClassifiesLikeDeadlineRule() throws Exception {
        Harness h = harness(false, true);
        Path ledger = costLedger("clean-cost");
        var built = h.build(OWNER_FACTS, List.of(new ProductionSwitchEligibility.CostLedgerInput(ledger,
                approved(COST_CAPS))));
        FrbClassificationInput input = built.inputs().get(ROUTE);
        RouteVerdictCalculator.RouteReport report = built.reports().get(ROUTE);
        assertThat(built.reports()).isEqualTo(ReportRecomputation.recompute(h.setup().run(), h.setup().frb()));
        List<FindingsLedger.ReviewCheck> tokens = new ArrayList<>();
        for (Ledger item : h.sorted()) tokens.add(item.ledger().checkLedger(ROUTE, expectedExtras(), item.heads()));
        var composed = ReviewComposition.compose(ROUTE, h.setup().run(), h.setup().frb(), report, tokens, WORKLOAD,
                Set.of(LUNA), Set.of(MINI, PAID), true);
        assertThat(input.openSafetyFindingIds()).isNotNull().isEmpty();
        assertThat(input.openDisputeIds()).isNotNull().isEmpty();
        assertThat(input.missingRequiredReviews()).isNotNull().isEmpty();
        assertThat(input.ownerVetoFindingIds()).isNotNull().isEmpty();
        assertThat(input.reviewCheck()).isEqualTo(composed);
        assertThat(input.ledgerHeadHashes()).isEqualTo(composed.heads()).isNotEmpty();
        assertThat(input.ledgerFailure()).isFalse();
        assertThat(input.solRecommended()).isFalse();
        assertThat(input.expansionOutcomeByWorkflow()).isEqualTo(h.setup().assembled().expansionOutcomeByWorkflow())
                .containsEntry(STEM, NOT_TRIGGERED);
        assertThat(input.establishedOperationalCaseIds()).isNotNull().isEqualTo(OperationalCauses.derive(
                h.sorted().stream().map(Ledger::ledger).toList(), ROUTE, report).establishedCaseIds());
        assertThat(input.costFlags()).isNotNull().isEmpty();
        // The tokens' missing union is NOT empty (each lock lacks the other's completions), yet the input is.
        assertThat(built.notes().get(ROUTE).missingFromTokens()).isNotEmpty();
        assertThat(built.notes().get(ROUTE).composed()).isTrue();
        assertThat(built.notes().get(ROUTE).compositionFailure()).isNull();
        assertThat(built.notes().get(ROUTE).unmetOwnerFacts()).contains("WORKLOAD_PARAMETERS", "SOL_RECOMMENDED");
        assertThat(built.pendingRoutes()).isEmpty();
        assertThat(built.derivedCostFlags()).isEmpty();
        FrbClassification result = classification(built);
        assertThat(result).isEqualTo(DeadlineRule.classifyFrb(report, input));
        assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
        assertThat(result.flags()).isEmpty();
        assertThat(result.blocksProductionSwitch()).isFalse();
        var sol = h.build(new RouteClassificationInputs.OwnerFacts(WORKLOAD, Set.of(LUNA), Set.of(MINI, PAID), true),
                null);
        assertThat(sol.inputs().get(ROUTE).solRecommended()).isTrue();
        assertThat(classification(sol).flags()).contains("SOL_RECOMMENDED", "COST_REVIEW_UNDETERMINED");
    }

    @Test void subjectMissingFromOneTokenButCompletedByAnotherCoveringLockStaysLiteralEmpty() throws Exception {
        Harness h = harness(true, true);
        assertThat(h.ledgers()).hasSize(4);
        var built = h.build();
        FrbClassificationInput input = built.inputs().get(ROUTE);
        assertThat(built.notes().get(ROUTE).composed()).isTrue();
        assertThat(built.notes().get(ROUTE).missingFromTokens()).contains("NO_VETO:STUDY_TIP:INTERACTIVE_QUIZ");
        assertThat(input.missingRequiredReviews()).isNotNull().isEmpty();
        assertThat(input.reviewCheck().lockSha256s()).hasSize(4);
        assertThat(input.reviewCheck().extrasByLock().values()).allSatisfy(extras ->
                assertThat(extras).isEqualTo(expectedExtras()));
        assertThat(classification(built)).isEqualTo(DeadlineRule.classifyFrb(built.reports().get(ROUTE), input));
    }

    // ---- Shape (b): compose throws, each reason separately ----

    @Test void openSafetyFindingIsShapeBAndAppearsInItsField() throws Exception {
        Harness h = harness(false, true);
        assertThat(h.sorted().getLast()).as("class-A lock sorts last").isEqualTo(h.classA());
        h.classA().finding(FINDING);
        var built = h.build();
        assertShapeB(built);
        FrbClassificationInput input = built.inputs().get(ROUTE);
        assertThat(input.openSafetyFindingIds()).containsExactly(FINDING);
        assertThat(input.openDisputeIds()).isNotNull().isEmpty();
        assertThat(input.ownerVetoFindingIds()).isNotNull().isEmpty();
        assertThat(input.ledgerFailure()).isFalse();
        assertThat(classification(built).causes()).contains("OPEN_SAFETY_FINDING").doesNotContain(LEDGER_INTEGRITY_FAILURE);
    }

    @Test void openDisputeOnTheSortedLastLockIsShapeBAndAppearsInItsField() throws Exception {
        Harness h = harness(true, true);
        Path scores = h.setup().frb().verification().scoresFile();
        String item = null;
        for (String line : Files.readAllLines(scores)) {
            JsonNode row = IntegritySupport.JSON.readTree(line);
            if (row.path("critical").asBoolean()) item = row.path("itemId").asText();
        }
        assertThat(item).isNotNull();
        Ledger b = h.classB();
        b.disputes().create(new EvidenceDispute("dispute_one", CREATED, b.hash(), TIP, item, null,
                "reviewer", "I disagree with the critical", List.of(ROUTE)),
                new ConfirmedCriticalSource.ClassB(SCORERS), null);
        var built = h.build();
        assertShapeB(built);
        FrbClassificationInput input = built.inputs().get(ROUTE);
        assertThat(input.openDisputeIds()).containsExactly("dispute_one");
        assertThat(input.openSafetyFindingIds()).isNotNull().isEmpty();
        assertThat(input.ledgerFailure()).isFalse();
        assertThat(classification(built).causes()).contains("OPEN_EVIDENCE_DISPUTE");
    }

    @Test void missingRequiredClassBReviewIsShapeB() throws Exception {
        Harness h = harness(setup(false), true, false);
        var built = h.build();
        assertShapeB(built);
        assertThat(built.notes().get(ROUTE).missingFromTokens()).contains("CLASS_B_WORKFLOW:STUDY_TIP:STUDY_TIP");
        assertThat(built.notes().get(ROUTE).compositionFailure()).contains("Required review lacks a covering completion");
        assertThat(built.inputs().get(ROUTE).ledgerFailure()).isFalse();
        assertThat(built.inputs().get(ROUTE).openSafetyFindingIds()).isNotNull().isEmpty();
    }

    @Test void missingNoVetoAttestationIsShapeBBecauseOwnerAttestationsAreAlwaysRequired() throws Exception {
        Harness h = harness(false, false);
        h.classA().complete("no_veto_note", new FindingsLedger.ReviewSubject(ReviewRecord.Kind.NO_VETO, STEM, ROUTE));
        var built = h.build();
        assertShapeB(built);
        assertThat(built.notes().get(ROUTE).compositionFailure()).contains("NO_VETO:STUDY_TIP:INTERACTIVE_QUIZ");
    }

    @Test void openOperationalCauseConcernOnARouteWithNoConditionalCauseReachesTheOwner() throws Exception {
        Harness h = harness(false, true);
        Ledger a = h.classA();
        a.reviews().create(a.record("operational_concern", ReviewRecord.Kind.OPERATIONAL_CAUSE, STEM,
                OperationalCauses.subjectId(OperationalCauses.Category.PROVIDER_OUTAGE, STEM, CASE_ONE),
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER));
        var built = h.build();
        assertShapeB(built);
        assertThat(built.notes().get(ROUTE).openConcernIds()).containsExactly("operational_concern");
        assertThat(built.inputs().get(ROUTE).establishedOperationalCaseIds()).isNotNull().isEmpty();
        assertThat(built.inputs().get(ROUTE).ledgerFailure()).isFalse();
        // LEDGER_NOT_SUPPLIED is expected: shape (b) never carries ledger heads.
        assertThat(classification(built).causes()).doesNotContain(LEDGER_INTEGRITY_FAILURE)
                .contains(LEDGER_NOT_SUPPLIED);
    }

    @Test void openAbsoluteQualityConcernOnTheSortedLastLockReachesTheOwner() throws Exception {
        Harness h = harness(false, true);
        Ledger last = h.sorted().getLast();
        String workflow = last.classA() ? STEM : TIP;
        last.reviews().create(last.record("quality_concern", ReviewRecord.Kind.ABSOLUTE_QUALITY, workflow,
                "quality_one", ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.PRODUCT_UX));
        var built = h.build();
        assertShapeB(built);
        assertThat(built.notes().get(ROUTE).openConcernIds()).containsExactly("quality_concern");
        assertThat(built.inputs().get(ROUTE).ownerVetoFindingIds()).isNotNull().isEmpty();
        assertThat(classification(built).causes()).doesNotContain("OWNER_VETO", LEDGER_INTEGRITY_FAILURE);
    }

    @Test void openOwnerVetoIsShapeBAndTheVetoIdComesFromTheLedger() throws Exception {
        Harness h = harness(false, true);
        assertThat(h.sorted().getLast()).as("class-A lock sorts last").isEqualTo(h.classA());
        h.classA().finding(FINDING);
        h.classA().veto(VETO, FINDING);
        var built = h.build();
        assertShapeB(built);
        FrbClassificationInput input = built.inputs().get(ROUTE);
        assertThat(input.ownerVetoFindingIds()).containsExactly(FINDING);
        assertThat(built.notes().get(ROUTE).openConcernIds()).contains(VETO);
        assertThat(input.ledgerFailure()).isFalse();
        assertThat(classification(built).causes()).contains("OWNER_VETO");
    }

    @Test void armLabelOverlapIsAWrappedWorkloadFailureAndShapeB() throws Exception {
        Harness h = harness(false, true);
        var built = h.build(new RouteClassificationInputs.OwnerFacts(WORKLOAD, Set.of(LUNA), Set.of(LUNA, MINI),
                false), null);
        assertShapeB(built);
        assertThat(built.notes().get(ROUTE).compositionFailure()).contains("Cannot verify review composition");
        assertThat(built.inputs().get(ROUTE).openSafetyFindingIds()).isNotNull().isEmpty();
    }

    @Test void seamOmittedExtrasAndTokenCountMismatchMakeComposeThrow() throws Exception {
        Harness h = harness(false, true);
        RouteVerdictCalculator.RouteReport report = ReportRecomputation.recompute(h.setup().run(), h.setup().frb())
                .get(ROUTE);
        List<FindingsLedger.ReviewCheck> omitted = new ArrayList<>();
        List<FindingsLedger.ReviewCheck> partial = new ArrayList<>();
        for (Ledger item : h.sorted()) {
            omitted.add(item.ledger().checkLedger(ROUTE, Set.of(), item.heads()));
            partial.add(item.ledger().checkLedger(ROUTE, expectedExtras(), item.heads()));
        }
        partial.removeLast();
        ResultsPipeline.FrbRunInput run = h.setup().run();
        ResultsPipeline.FrbInput frb = h.setup().frb();
        Set<String> candidate = Set.of(LUNA);
        Set<String> baseline = Set.of(MINI, PAID);
        assertThatThrownBy(() -> ReviewComposition.compose(ROUTE, run, frb, report,
                omitted, WORKLOAD, candidate, baseline, true))
                .isInstanceOf(ReviewComposition.InvalidComposition.class).hasMessageContaining("omitted extras");
        assertThatThrownBy(() -> ReviewComposition.compose(ROUTE, run, frb, report,
                partial, WORKLOAD, candidate, baseline, true))
                .isInstanceOf(ReviewComposition.InvalidComposition.class).hasMessageContaining("Exactly one token");
    }

    // ---- Ledger failures: build does not throw ----

    @Test void tamperedReviewRecordIsALedgerFailureWithNoCleanDefault() throws Exception {
        Harness h = harness(false, true);
        var noted = h.routeLedgers();
        Files.writeString(h.classA().reviewFile("no_veto_note"), "flipped");
        var built = RouteClassificationInputs.build(h.setup().assembled(), h.setup().run(), h.setup().frb(), noted,
                OWNER_FACTS, null);
        assertLedgerFailure(built);
        FrbClassificationInput input = built.inputs().get(ROUTE);
        assertThat(input.openSafetyFindingIds()).isNull();
        assertThat(input.openDisputeIds()).isNull();
        assertThat(input.ownerVetoFindingIds()).isNull();
        assertThat(input.establishedOperationalCaseIds()).isNull();
        assertThat(built.notes().get(ROUTE).compositionFailure()).startsWith("Ledger failure: ");
        assertThat(built.notes().get(ROUTE).operationalCauseFailure()).isNotBlank();
        assertThat(classification(built).causes()).contains(LEDGER_NOT_SUPPLIED);
    }

    @Test void noteHeadDifferentFromCurrentIsALedgerFailureNotShapeB() throws Exception {
        Harness h = harness(false, true);
        Map<String, FindingsLedger.ExternalHeads> stale = h.heads();
        h.sorted().getLast().complete("late_review", new FindingsLedger.ReviewSubject(ReviewRecord.Kind.NO_VETO,
                h.sorted().getLast().classA() ? STEM : TIP, ROUTE));
        var built = RouteClassificationInputs.build(h.setup().assembled(), h.setup().run(), h.setup().frb(),
                new RouteClassificationInputs.RouteLedgers(h.ledgerMap(), stale), OWNER_FACTS, null);
        assertLedgerFailure(built);
        FrbClassificationInput input = built.inputs().get(ROUTE);
        assertThat(built.notes().get(ROUTE).compositionFailure()).contains("Externally noted head is not current");
        assertThat(input.ownerVetoFindingIds()).isNotNull().isEmpty();
        assertThat(input.establishedOperationalCaseIds()).isNotNull().isEmpty();
        assertThat(input.openSafetyFindingIds()).isNull();
    }

    @Test void corruptOperationalCauseStoreGivesNullEstablishedIdsAndANote() throws Exception {
        Harness h = harness(false, true);
        Ledger last = h.sorted().getLast();
        last.attest("cause_record", last.classA() ? STEM : TIP, last.classA() ? CASE_ONE : "tip");
        Path record = last.reviewFile("cause_record");
        var noted = h.routeLedgers();
        Files.writeString(record, Files.readString(record) + " ");
        var built = RouteClassificationInputs.build(h.setup().assembled(), h.setup().run(), h.setup().frb(), noted,
                OWNER_FACTS, null);
        assertLedgerFailure(built);
        assertThat(built.inputs().get(ROUTE).establishedOperationalCaseIds()).isNull();
        assertThat(built.notes().get(ROUTE).operationalCauses()).isNull();
        assertThat(built.notes().get(ROUTE).operationalCauseFailure()).isNotBlank();
    }

    @Test void badOwnerVetoReferenceBreaksTokensAndVetoesButNotOperationalCauses() throws Exception {
        Harness h = harness(false, true);
        h.classA().veto(VETO, "missing_finding");
        var built = h.build();
        assertLedgerFailure(built);
        FrbClassificationInput input = built.inputs().get(ROUTE);
        assertThat(input.ownerVetoFindingIds()).isNull();
        assertThat(input.establishedOperationalCaseIds()).isNotNull().isEmpty();
        assertThat(built.notes().get(ROUTE).operationalCauses()).isNotNull();
        assertThat(built.notes().get(ROUTE).operationalCauseFailure()).isNull();
    }

    // ---- Operational causes (seam: no real fixture yields a conditional cause through runFrb) ----

    private static RouteVerdictCalculator.WorkflowReport row(String sha, RouteVerdictCalculator.Verdict verdict,
            Map<String, SortedSet<String>> causes) {
        return new RouteVerdictCalculator.WorkflowReport(verdict, Set.of(), Set.of(),
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false,
                Map.of(), Map.of(), Map.of(), sha, 0, Set.of(), null,
                causes == null ? null : List.copyOf(causes.keySet()), null, null, causes,
                null, null, null);
    }

    private static RouteVerdictCalculator.RouteReport conditionalReport(Harness h, String workflow, String caseId) {
        String sha = FrozenGateParameters.load(h.setup().run().parameters()).sha256;
        Map<String, SortedSet<String>> causes = Map.of(MISSING_RUN_DATA, new TreeSet<>(Set.of(caseId)));
        var conditional = row(sha, RouteVerdictCalculator.Verdict.INCONCLUSIVE, causes);
        var clean = row(sha, RouteVerdictCalculator.Verdict.PASS, null);
        return new RouteVerdictCalculator.RouteReport(RouteVerdictCalculator.Verdict.INCONCLUSIVE, Set.of(), Set.of(),
                0, false, sha, STEM.equals(workflow) ? Map.of(STEM, conditional, TIP, clean)
                        : Map.of(STEM, clean, TIP, conditional));
    }

    private static RouteClassificationInputs.RouteInput seam(Harness h, RouteVerdictCalculator.RouteReport report,
            Map<String, String> expansion) throws Exception {
        return RouteClassificationInputs.routeInput(ROUTE, report, h.setup().run(), h.setup().frb(),
                h.routeLedgers(), OWNER_FACTS, expansion, new RouteClassificationInputs.CostFacts(Set.of(), null));
    }

    @Test void seamEstablishedCaseOnTheSortedLastLockGivesTheNarrowFallbackNeverPass() throws Exception {
        Harness h = harness(false, true);
        Ledger last = h.sorted().getLast();
        String workflow = last.classA() ? STEM : TIP;
        String caseId = last.classA() ? CASE_ONE : "tip";
        last.attest("cause_record", workflow, caseId);
        var report = conditionalReport(h, workflow, caseId);
        var result = seam(h, report, Map.of(STEM, NOT_TRIGGERED));
        assertThat(result.notes().composed()).isTrue();
        assertThat(result.input().establishedOperationalCaseIds()).containsExactly(caseId);
        FrbClassification classified = DeadlineRule.classifyFrb(report, result.input());
        assertThat(classified.classification()).isEqualTo(DeadlineRule.Classification.LUNA_FALLBACK_ELIGIBLE)
                .isNotEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
    }

    @Test void seamUnattestedOrWithdrawnCaseIsNotEstablished() throws Exception {
        Harness h = harness(false, true);
        Ledger a = h.classA();
        var report = conditionalReport(h, STEM, CASE_ONE);
        var unattested = seam(h, report, Map.of(STEM, NOT_TRIGGERED));
        assertThat(unattested.input().establishedOperationalCaseIds()).isNotNull().isEmpty();
        assertThat(DeadlineRule.classifyFrb(report, unattested.input()).classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        String subject = OperationalCauses.subjectId(OperationalCauses.Category.PROVIDER_OUTAGE, STEM, CASE_ONE);
        a.attest("cause_record", STEM, CASE_ONE);
        a.reviews().create(a.record("cause_concern", ReviewRecord.Kind.OPERATIONAL_CAUSE, STEM, subject,
                ReviewRecord.Outcome.BLOCKING_CONCERN, ReviewRecord.SignerRole.OWNER));
        a.reviews().create(new ReviewRecord("cause_withdrawn", LATER, a.hash(), ReviewRecord.Kind.OPERATIONAL_CAUSE,
                STEM, subject, ReviewRecord.Outcome.COMPLETE, OWNER, ReviewRecord.SignerRole.OWNER, RESOLUTION,
                "cause_concern", ReviewRecord.ResolutionCode.NOT_APPLICABLE));
        var withdrawn = seam(h, report, Map.of(STEM, NOT_TRIGGERED));
        assertThat(withdrawn.input().establishedOperationalCaseIds()).isNotNull().isEmpty();
        assertThat(withdrawn.notes().operationalCauses().findings()).anySatisfy(finding ->
                assertThat(finding.status()).isEqualTo(OperationalCauses.Status.WITHDRAWN_BY_OWNER));
        assertThat(DeadlineRule.classifyFrb(report, withdrawn.input()).classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
    }

    @Test void seamOperationalCauseFailureAfterASuccessfulCompositionIsStillShapeB() throws Exception {
        Harness h = harness(false, true);
        var report = conditionalReport(h, STEM, " ");
        var result = seam(h, report, Map.of(STEM, NOT_TRIGGERED));
        assertThat(result.notes().composed()).isTrue();
        assertThat(result.notes().operationalCauseFailure()).contains("case id are required");
        FrbClassificationInput input = result.input();
        assertThat(input.ledgerFailure()).isTrue();
        assertThat(input.establishedOperationalCaseIds()).isNull();
        assertThat(input.missingRequiredReviews()).isNull();
        assertThat(input.reviewCheck()).isNull();
        assertThat(input.ledgerHeadHashes()).isNull();
        assertThat(input.openSafetyFindingIds()).isNotNull().isEmpty();
        assertThat(input.ownerVetoFindingIds()).isNotNull().isEmpty();
        assertThat(DeadlineRule.classifyFrb(report, input).causes()).contains(LEDGER_INTEGRITY_FAILURE);
    }

    @Test void seamEstablishedCauseOnAShapeBRouteStillReturnsToTheOwner() throws Exception {
        Harness h = harness(false, true);
        h.classA().attest("cause_record", STEM, CASE_ONE);
        h.classA().finding(FINDING);
        var report = conditionalReport(h, STEM, CASE_ONE);
        var result = seam(h, report, Map.of(STEM, NOT_TRIGGERED));
        assertThat(result.input().establishedOperationalCaseIds()).containsExactly(CASE_ONE);
        assertThat(result.input().missingRequiredReviews()).isNull();
        assertThat(DeadlineRule.classifyFrb(report, result.input()).classification())
                .isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
    }

    // ---- Expansion ----

    @Test void triggeredWorkflowWithoutASuppliedOutcomeIsAbsentAndReturnsToTheOwner() throws Exception {
        Setup setup = setup(false);
        ClassAAssembly original = setup.assembled().classAAssemblies().getFirst();
        String remaining = setup.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(setup.selected())).findFirst().orElseThrow();
        ExpansionObligation.Disclosure obligation = new ExpansionObligation.Disclosure(STEM,
                Set.of(setup.selected()), Map.of(STEM, 1), List.of(remaining), true, 1);
        ClassAAssembly changed = new ClassAAssembly(original.facts(), original.statuses(), original.pairs(),
                original.sets(), original.reviewRequiredPairs(), original.drawEntries(), original.digests(),
                Map.of(STEM, obligation), original.declared());
        FrbBundleAssembler.Assembled assembled = FrbBundleAssembler.assembleParts(List.of(changed),
                List.of(setup.aSources()), setup.assembled().classB(), setup.b());
        Harness h = harness(new Setup(assembled, setup.run(), setup.frb(), setup.aSources(), setup.b(),
                setup.pairs(), setup.selected()), true, true);
        var built = h.build();
        assertThat(built.inputs().get(ROUTE).expansionOutcomeByWorkflow()).doesNotContainKey(STEM)
                .isEqualTo(assembled.expansionOutcomeByWorkflow());
        assertThat(built.notes().get(ROUTE).composed()).isTrue();
        FrbClassification result = classification(built);
        assertThat(result.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertThat(result.causes()).contains("EXPANSION_OUTCOME_NOT_SUPPLIED");
    }

    @Test void seamInconclusiveExpansionOutcomesReturnTheRouteToTheOwner() throws Exception {
        Harness h = harness(false, true);
        RouteVerdictCalculator.RouteReport report = ReportRecomputation.recompute(h.setup().run(), h.setup().frb())
                .get(ROUTE);
        for (String outcome : List.of("INCONCLUSIVE_WORKLOAD_EXHAUSTED", "INCONCLUSIVE_SCORER_UNRELIABLE")) {
            var result = seam(h, report, Map.of(STEM, outcome));
            assertThat(result.input().expansionOutcomeByWorkflow()).containsExactly(Map.entry(STEM, outcome));
            FrbClassification classified = DeadlineRule.classifyFrb(report, result.input());
            assertThat(classified.classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
            assertThat(classified.causes()).contains(outcome);
        }
    }

    // ---- Cost: one global union ----

    @Test void costLedgersProduceOneUnionAndEveryFailureIsUndetermined() throws Exception {
        Harness h = harness(false, true);
        Path clean = costLedger("cost-clean");
        Path second = costLedger("cost-second");
        ApprovedCaps matching = approved(COST_CAPS);
        ApprovedCaps differing = approved("{\"schema\":1,\"caps\":{\"STAGE_1\":\"2\"}}");
        var union = RouteClassificationInputs.costFacts(List.of(
                new ProductionSwitchEligibility.CostLedgerInput(clean, matching),
                new ProductionSwitchEligibility.CostLedgerInput(second, differing)));
        assertThat(union.flags()).containsExactlyElementsOf(new TreeSet<>(CostEvidenceProducer.produce(second,
                differing).flags())).isNotEmpty();
        var built = h.build(OWNER_FACTS, List.of(new ProductionSwitchEligibility.CostLedgerInput(clean, matching),
                new ProductionSwitchEligibility.CostLedgerInput(second, differing)));
        assertThat(built.inputs().get(ROUTE).costFlags()).isEqualTo(union.flags());
        assertThat(classification(built).flags()).containsAll(union.flags());
        var absent = h.build();
        assertThat(absent.inputs().get(ROUTE).costFlags()).isNull();
        assertThat(classification(absent).flags()).contains("COST_REVIEW_UNDETERMINED");
        assertThat(RouteClassificationInputs.costFacts(List.of()).flags()).isNull();
        var duplicate = RouteClassificationInputs.costFacts(List.of(
                new ProductionSwitchEligibility.CostLedgerInput(clean, matching),
                new ProductionSwitchEligibility.CostLedgerInput(clean, matching)));
        assertThat(duplicate.flags()).isNull();
        assertThat(duplicate.failure()).contains("Duplicate cost ledger file");
        Path corrupt = temp.resolve("cost-corrupt.jsonl");
        Files.writeString(corrupt, "{");
        var broken = h.build(OWNER_FACTS, List.of(new ProductionSwitchEligibility.CostLedgerInput(corrupt, matching)));
        assertThat(broken.inputs().get(ROUTE).costFlags()).isNull();
        assertThat(broken.notes().get(ROUTE).costFailure()).isNotBlank();
        assertThat(classification(broken).flags()).contains("COST_REVIEW_UNDETERMINED");
    }

    @Test void operationalCostReviewFillsDerivedCostFlagsExactlyAsRunFrb() throws Exception {
        Setup expensive = setup(false, true);
        ResultsPipeline.FrbInput reviewed = new ResultsPipeline.FrbInput(expensive.frb().bundle(),
                expensive.frb().reserveRecordFile(), expensive.frb().verification(), expensive.frb().provenance(),
                null, true);
        Harness h = harness(new Setup(expensive.assembled(), expensive.run(), reviewed, expensive.aSources(),
                expensive.b(), expensive.pairs(), expensive.selected()), true, true);
        var built = h.build();
        var expected = OperationalCostReview.flagsByRoute(ResultsPipeline.operationalCostReview(expensive.run()),
                FrozenGateParameters.load(expensive.run().parameters()));
        assertThat(built.derivedCostFlags()).isEqualTo(expected);
        assertThat(built.derivedCostFlags().get(ROUTE)).isNotEmpty();
        assertThat(classification(built).flags()).containsAll(expected.get(ROUTE));
        assertNoDrift(h, built);
        var unreviewed = harness(expensive, true, true).build();
        assertThat(unreviewed.derivedCostFlags()).isEmpty();
    }

    // ---- No drift: the report runFrb writes with built.inputs() matches classify(built) ----

    private void assertNoDrift(Harness h, RouteClassificationInputs.Built built) throws Exception {
        ResultsPipeline.FrbRunInput run = h.setup().run();
        Path report = Files.createTempDirectory(temp, "no-drift-").resolve("report.json");
        ResultsPipeline.FrbRunInput copy = new ResultsPipeline.FrbRunInput(run.repoRoot(), run.freeze(),
                run.parameters(), run.mapping(), run.manifests(), run.baselineMini(), run.baseline41(),
                run.candidate(), run.reserves(), run.compatibilityReports(), run.headCommitTime(),
                run.ownerDispositions(), report, run.synthetic());
        ResultsPipeline.FrbInput frb = h.setup().frb();
        ResultsPipeline.runFrb(copy, new ResultsPipeline.FrbInput(frb.bundle(), frb.reserveRecordFile(),
                frb.verification(), frb.provenance(), built.inputs(), frb.operationalCostReview()));
        JsonNode written = IntegritySupport.JSON.readTree(report.toFile());
        for (var entry : RouteClassificationInputs.classify(built).entrySet()) {
            JsonNode node = written.path(CLASSIFICATION_FIELD).path(entry.getKey());
            FrbClassification expected = entry.getValue();
            assertThat(node.path(CLASSIFICATION_FIELD).asText()).isEqualTo(expected.classification().name());
            List<String> causes = new ArrayList<>();
            node.path(CAUSES_FIELD).forEach(value -> causes.add(value.asText()));
            List<String> flags = new ArrayList<>();
            node.path(FLAGS_FIELD).forEach(value -> flags.add(value.asText()));
            assertThat(causes).containsExactlyElementsOf(expected.causes());
            assertThat(flags).containsExactlyElementsOf(expected.flags());
            assertThat(node.path("blocksProductionSwitch").asBoolean()).isEqualTo(expected.blocksProductionSwitch());
            assertThat(written.path("deadlineClassification").path(entry.getKey()).asText())
                    .isEqualTo(ResultsPipeline.renderFrbClassification(expected));
        }
    }

    @Test void noDriftOnACleanAndAnOwnerRoute() throws Exception {
        Harness clean = harness(false, true);
        assertNoDrift(clean, clean.build());
        Harness owner = harness(false, true);
        owner.classA().finding(FINDING);
        owner.classA().veto(VETO, FINDING);
        var built = owner.build();
        assertThat(classification(built).classification()).isEqualTo(DeadlineRule.Classification.RETURN_TO_OWNER);
        assertNoDrift(owner, built);
    }

    @Test void declaredPendingItemMakesItsRoutePendingInBothTheBuilderAndRunFrb() throws Exception {
        Setup setup = setup(false);
        ClassAAssembly original = setup.assembled().classAAssemblies().getFirst();
        Map<String, AdjudicatedFacts> facts = new LinkedHashMap<>(original.facts().byCaseArm());
        String identity = facts.keySet().iterator().next();
        AdjudicatedFacts old = facts.get(identity);
        facts.put(identity, new AdjudicatedFacts(old.scoredQuestions(), old.criticalQuestions(),
                old.qualityFailQuestions(), old.unresolvedQuestions() + 1, old.pendingQuestions() + 1,
                old.doubleMisses(), old.scorerFailureCriticals(), old.humanCleared(), old.subgroup()));
        AdjudicatedLoaded loaded = new AdjudicatedLoaded(facts, original.facts().excludedCaseIds(),
                original.facts().requestedQuestionsByCaseId(), original.facts().excludedReasons());
        ClassAAssembly changed = new ClassAAssembly(loaded, original.statuses(), original.pairs(), original.sets(),
                original.reviewRequiredPairs(), original.drawEntries(), original.digests(),
                original.expansionObligations(), original.declared());
        FrbBundleAssembler.Assembled assembled = FrbBundleAssembler.assembleParts(List.of(changed),
                List.of(setup.aSources()), setup.assembled().classB(), setup.b());
        ResultsPipeline.FrbInput frb = new ResultsPipeline.FrbInput(assembled.bundle(),
                setup.frb().reserveRecordFile(), setup.frb().verification(), setup.frb().provenance());
        Harness h = harness(new Setup(assembled, setup.run(), frb, setup.aSources(), setup.b(), setup.pairs(),
                setup.selected()), true, true);
        var built = h.build();
        assertThat(built.pendingRoutes()).containsExactly(ROUTE);
        assertThat(classification(built).causes()).contains("PENDING_REVIEW_ITEMS");
        assertNoDrift(h, built);
    }

    @Test void seamClassifyKeepsEachRoutesOwnInputPendingAndCostFlags() throws Exception {
        Harness h = harness(false, true);
        var built = h.build(OWNER_FACTS, List.of(new ProductionSwitchEligibility.CostLedgerInput(
                costLedger("seam-cost"), approved(COST_CAPS))));
        RouteVerdictCalculator.RouteReport report = built.reports().get(ROUTE);
        var twoRoutes = new RouteClassificationInputs.Built(Map.of(ROUTE, report, OTHER_ROUTE, report),
                Map.of(ROUTE, built.inputs().get(ROUTE)), Set.of(OTHER_ROUTE),
                Map.of(OTHER_ROUTE, new TreeSet<>(Set.of("COST_REVIEW_TRIGGERED:X"))), built.notes());
        var classified = RouteClassificationInputs.classify(twoRoutes);
        assertThat(classified.get(ROUTE).classification()).isEqualTo(DeadlineRule.Classification.BENCHMARK_PASS);
        assertThat(classified.get(ROUTE).flags()).isEmpty();
        assertThat(classified.get(OTHER_ROUTE).causes()).contains(LEDGER_NOT_SUPPLIED, "PENDING_REVIEW_ITEMS");
        assertThat(classified.get(OTHER_ROUTE).flags()).contains("COST_REVIEW_TRIGGERED:X");
    }

    // ---- Completeness and preconditions ----

    @Test void unboundInputsAreRefusedByTheBindingCheck() throws Exception {
        Harness h = harness(false, true);
        Setup other = setup(false);
        var ledgers = h.routeLedgers();
        FrbBundleAssembler.Assembled assembled = h.setup().assembled();
        FrbBundleAssembler.Assembled otherAssembled = other.assembled();
        ResultsPipeline.FrbRunInput run = h.setup().run();
        ResultsPipeline.FrbRunInput otherRun = other.run();
        ResultsPipeline.FrbInput frb = h.setup().frb();
        assertThatThrownBy(() -> RouteClassificationInputs.build(null, run, frb, ledgers, OWNER_FACTS, null))
                .isInstanceOf(InvalidFrbBundle.class);
        assertThatThrownBy(() -> RouteClassificationInputs.build(otherAssembled, run, frb, ledgers, OWNER_FACTS,
                null)).isInstanceOf(InvalidFrbBundle.class);
        assertThatThrownBy(() -> RouteClassificationInputs.build(assembled, otherRun, frb, ledgers, OWNER_FACTS,
                null)).isInstanceOf(InvalidFrbBundle.class);
    }

    @Test void ledgerKeysMustEqualTheProvenanceLockSetAndTokensTheirKeys() throws Exception {
        Harness h = harness(true, true);
        FrbBundleAssembler.Assembled assembled = h.setup().assembled();
        ResultsPipeline.FrbRunInput run = h.setup().run();
        ResultsPipeline.FrbInput frb = h.setup().frb();
        Map<String, FindingsLedger> missing = new HashMap<>(h.ledgerMap());
        Map<String, FindingsLedger.ExternalHeads> missingHeads = new HashMap<>(h.heads());
        String dropped = h.sorted().getLast().hash();
        missing.remove(dropped);
        missingHeads.remove(dropped);
        var lacking = new RouteClassificationInputs.RouteLedgers(missing, missingHeads);
        assertThatThrownBy(() -> RouteClassificationInputs.build(assembled, run, frb,
                lacking, OWNER_FACTS, null)).isInstanceOf(RouteClassificationInputs.InvalidRouteInputs.class)
                .hasMessageContaining("provenance locks");
        Map<String, FindingsLedger> extra = new HashMap<>(h.ledgerMap());
        Map<String, FindingsLedger.ExternalHeads> extraHeads = new HashMap<>(h.heads());
        extra.put("f".repeat(64), h.classA().ledger());
        extraHeads.put("f".repeat(64), h.classA().heads());
        var surplus = new RouteClassificationInputs.RouteLedgers(extra, extraHeads);
        assertThatThrownBy(() -> RouteClassificationInputs.build(assembled, run, frb,
                surplus, OWNER_FACTS, null)).isInstanceOf(RouteClassificationInputs.InvalidRouteInputs.class)
                .hasMessageContaining("provenance locks");
        List<Ledger> sorted = h.sorted();
        Map<String, FindingsLedger> swapped = new HashMap<>(h.ledgerMap());
        Map<String, FindingsLedger.ExternalHeads> swappedHeads = new HashMap<>(h.heads());
        swapped.put(sorted.get(0).hash(), sorted.get(1).ledger());
        swapped.put(sorted.get(1).hash(), sorted.get(0).ledger());
        swappedHeads.put(sorted.get(0).hash(), sorted.get(1).heads());
        swappedHeads.put(sorted.get(1).hash(), sorted.get(0).heads());
        var crossed = new RouteClassificationInputs.RouteLedgers(swapped, swappedHeads);
        assertThatThrownBy(() -> RouteClassificationInputs.build(assembled, run, frb,
                crossed, OWNER_FACTS, null)).isInstanceOf(RouteClassificationInputs.InvalidRouteInputs.class)
                .hasMessageContaining("token lock hash");
    }

    @Test void malformedLedgerAndOwnerInputsAreRefused() throws Exception {
        Harness h = harness(false, true);
        Map<String, FindingsLedger> ledgers = h.ledgerMap();
        Map<String, FindingsLedger.ExternalHeads> fewerHeads = new HashMap<>(h.heads());
        fewerHeads.remove(h.classA().hash());
        Map<String, FindingsLedger> empty = Map.of();
        Map<String, FindingsLedger.ExternalHeads> noHeads = Map.of();
        assertThatThrownBy(() -> new RouteClassificationInputs.RouteLedgers(ledgers, fewerHeads))
                .isInstanceOf(RouteClassificationInputs.InvalidRouteInputs.class);
        assertThatThrownBy(() -> new RouteClassificationInputs.RouteLedgers(empty, noHeads))
                .isInstanceOf(RouteClassificationInputs.InvalidRouteInputs.class);
        assertThatThrownBy(() -> new RouteClassificationInputs.RouteLedgers(null, noHeads))
                .isInstanceOf(RouteClassificationInputs.InvalidRouteInputs.class);
        Set<String> none = Set.of();
        Set<String> baseline = Set.of(MINI);
        assertThatThrownBy(() -> new RouteClassificationInputs.OwnerFacts(WORKLOAD, none, baseline, false))
                .isInstanceOf(RouteClassificationInputs.InvalidRouteInputs.class);
        assertThatThrownBy(() -> new RouteClassificationInputs.OwnerFacts(WORKLOAD, baseline, none, false))
                .isInstanceOf(RouteClassificationInputs.InvalidRouteInputs.class);
        assertThatThrownBy(() -> new RouteClassificationInputs.OwnerFacts(null, baseline, baseline, false))
                .isInstanceOf(RouteClassificationInputs.InvalidRouteInputs.class);
        FrbBundleAssembler.Assembled assembled = h.setup().assembled();
        ResultsPipeline.FrbRunInput run = h.setup().run();
        ResultsPipeline.FrbInput frb = h.setup().frb();
        var routeLedgers = h.routeLedgers();
        assertThatThrownBy(() -> RouteClassificationInputs.build(assembled, run, frb, routeLedgers, null, null))
                .isInstanceOf(RouteClassificationInputs.InvalidRouteInputs.class);
        assertThatThrownBy(() -> RouteClassificationInputs.build(assembled, run, frb, null, OWNER_FACTS, null))
                .isInstanceOf(RouteClassificationInputs.InvalidRouteInputs.class);
        var built = h.build();
        Map<String, FrbClassificationInput> inputs = built.inputs();
        Set<String> pending = built.pendingRoutes();
        assertThatThrownBy(inputs::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(pending::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- Plumbing ----

    private Map<Path, String> snapshot() throws Exception {
        Map<Path, String> result = new TreeMap<>();
        try (Stream<Path> files = Files.walk(temp)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) result.put(file, FreezeRecord.sha256(file));
        }
        return result;
    }

    private static Set<String> scratchDirectories() throws Exception {
        try (Stream<Path> paths = Files.list(Path.of(System.getProperty(TEMP_ROOT)))) {
            return paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("frb-report-recomputation-") || name.startsWith("cost-evidence-"))
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    @Test void buildCreatesModifiesAndLeavesNoFileAndOpensNoKey() throws Exception {
        Harness h = harness(true, true);
        Path cost = costLedger("plumbing-cost");
        ApprovedCaps caps = approved(COST_CAPS);
        Map<Path, String> before = snapshot();
        Set<String> scratch = scratchDirectories();
        var built = h.build(OWNER_FACTS, List.of(new ProductionSwitchEligibility.CostLedgerInput(cost, caps)));
        assertThat(built.notes().get(ROUTE).composed()).isTrue();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(scratchDirectories()).isEqualTo(scratch);
        assertThat(Files.exists(h.setup().run().report())).isFalse();
        Set<Path> logs = new HashSet<>();
        h.setup().frb().provenance().primaries().forEach(export -> logs.add(export.accessLog()));
        assertThat(logs).allSatisfy(log -> assertThat(before).containsKey(log));
    }

    // ---- Audit addition (release session): owner and expansion facts survive shape (b) ----

    @Test void solRecommendedAndTheExpansionOutcomesSurviveShapeBAndLedgerFailure() throws Exception {
        Harness h = harness(false, true);
        h.classA().finding(FINDING);
        var owner = new RouteClassificationInputs.OwnerFacts(WORKLOAD, Set.of(LUNA), Set.of(MINI, PAID), true);
        var built = h.build(owner, null);
        assertShapeB(built);
        FrbClassificationInput input = built.inputs().get(ROUTE);
        assertThat(input.solRecommended()).isTrue();
        assertThat(input.expansionOutcomeByWorkflow()).isEqualTo(h.setup().assembled().expansionOutcomeByWorkflow());
    }
}
