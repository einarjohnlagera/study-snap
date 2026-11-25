package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic fixture copies ClassAEvidenceAssemblerTest's export and FrbPipelineTest's run. */
class FrbBundleAssemblerTest {
    private static final String CASE_ID_FIELD = "caseId";
    private static final String EXTRA_CASE_PREFIX = "extra-";
    private static final String WORKFLOW_FIELD = "workflow";
    private static final String NEWLINE = "\n";
    private static final String SPOT_COUNT_SUFFIX = "\",\"count\":1}";
    private static final String EMPTY_TEXT = "";
    private static final String EXPANSION_PRIORITY_FRAGMENT = "\",\"stemPriority\":[\"";
    private static final String EXPANSION_ALLOCATION_FRAGMENT = "\"],\"initialAllocation\":{\"";
    private static final String EXPANSION_COUNT_END = "\":3}}";
    private static final String JSON_ARRAY_END = "]}";
    private static final String MANIFESTS_FIELD = "manifests";
    private static final String ARMS_FIELD = "arms";
    private static final String WORKFLOWS_FIELD = "workflows";
    private static final String COST_FORECAST_FIELD = "costForecastUsdPerDeliveredCase";
    private static final String COMPATIBILITY_FIELD = "compatibilitySuites";
    private static final String SYNTHETIC_SUITE_ONE = "SyntheticSuite1";
    private static final String BIOLOGY_LABEL = "Biology";
    private static final String PACKETS_NAME = "packets";
    private static final String FINDINGS_NAME = "findings";
    private static final String WORKFLOW_JSON_PREFIX = "{\"workflow\":\"";
    private static final String CLEAN_GRADE = "C";
    private static final String ACCESS_LOG_NAME = "access.jsonl";
    private static final String SHA_SUFFIX = ".sha256";
    private static final String ARM_KEY_FILE = "arm-key.jsonl";
    private static final String ASSEMBLER_ACCESS_LOG = "bundle-assembler-access.jsonl";
    private static final String TIP_CASE_ID = "tip";
    private static final String FREEZE_ROLE = "freeze";
    private static final String PARAMETERS_ROLE = "parameters";
    private static final String MAPPING_ROLE = "mapping";
    private static final String PRIMARY_SEAL_ROLE = "primary.seal";
    private static final String SHA_ROLE_REGEX = "\\.sha256$";
    private static final String CLASS_A_PRIMARY_DIGEST_ROLE = "manifest.classA.primary";
    private static final String CLASS_A_PLAN_DIGEST_ROLE = "manifest.classA.spotCheckPlan";
    private static final String CLASS_A_EXCLUSIONS_ROLE = "classA.exclusions";
    private static final String RESERVE_RECORD_ROLE = "reserve.record";
    private static final String ZERO_DIGIT = "0";
    private static final String FREEZE_SIDECAR_ROLE = "freeze.sha256";
    private static final String PARAMETERS_SIDECAR_ROLE = "parameters.sha256";
    private static final String MAPPING_SIDECAR_ROLE = "mapping.sha256";
    private static final String DIGEST_DIFF_MESSAGE = "Digest differs: ";
    private static final String OTHER_CASE_ID = "other";
    private static final String RESERVE_ID_MISMATCH_MESSAGE = "Reserve frozen ids differ";
    private static final String ASK_COMPANION_WORKFLOW = "ASK_COMPANION";
    private static final String FROZEN_PARTITION_MESSAGE = "Frozen workflow partition";
    private static final String RESERVE_ID_PREFIX = "reserve-";
    private static final String OTHER_ID_PREFIX = "other-";
    private static final String CLASS_B_PRIMARY_MANIFEST = "classB.primary";
    private static final String CLASS_A_RESERVE_MANIFEST = "classA.reserve";
    private static final String BOARD_EXAM_WORKFLOW = "BOARD_EXAM";
    private static final String ROUTES_FIELD = "routes";
    private static final String INTERACTIVE_QUIZ_ROUTE = "INTERACTIVE_QUIZ";
    private static final String PARAMETERS_SHA_FIELD = "parametersSha256";
    private static final String INVALID_WORKFLOW_MESSAGE = "Invalid frozen case or workflow";
    private static final String CASE_ARM_SEPARATOR = "\u0000";
    private static final String CLASS_B_RESERVE_MANIFEST = "classB.reserve";
    private static final String DOT = ".";
    private static final String MANIFEST_PREFIX = "manifest.";
    private static final String SCORES_NAME = "scores";
    private static final String SEAL_NAME = "seal";
    private static final String LOCK_NAME = "lock";
    private static final String EXCLUSIONS_NAME = "exclusions";
    private static final String UNASSEMBLED_PRIMARY_MESSAGE = "Unassembled primary export";
    private static final String PRIMARY_SIZE_FIELD = "primarySize";
    private static final String MISSING_CLASS_A_WORKFLOW_MESSAGE = "Class-A workflows lack";
    private static final String CLASS_A_P_SCORE_ROLE = "classA.scores.p";
    private static final String CLASS_A_KEY_FILE = "key.jsonl";
    private static final String CRITICAL_GRADE = "K";
    private static final String EXPANSION_DECISION_ROLE = "classA.expansionDecision";
    private static final String STEM = "NOTE_ADAPTIVE";
    private static final String MINI = "BASELINE_MINI";
    private static final String LUNA = "LUNA";
    private static final String CLOCK = "2026-11-16T00:00:30Z";
    private static final String CASE_ONE = "a-one";
    private static final String CASE_TWO = "a-two";
    private static final String CASE_THREE = "a-three";
    private static final String PLAN_STEM = "PLAN_ADAPTIVE";
    private static final String PRIMARY_MANIFEST = "classA.primary";
    private static final String SPOT_PLAN = "classA.spotCheckPlan";
    private static final String TIP = "STUDY_TIP";
    private static final String HUMAN = "HUMAN_OWNER";
    private static final String OPENED = "2026-11-16T00:07:00Z";
    private static final Map<String, ClassBScoreFiles.ScorerKind> SCORERS =
            Map.of(HUMAN, ClassBScoreFiles.ScorerKind.HUMAN);
    @TempDir Path temp;
    private boolean drawn;

    private record AFixture(ClassASources sources, PairMap pairs, String selected, Set<String> selections,
            Path log, Path registry, Path lock, Path seal, Path root, Path[] findings,
            FrbPipelineTest.Fixture pipelineFixture) {}
    private record Setup(AFixture a, ClassBSources b, FrbBundleAssembler.Assembled assembled,
            ResultsPipeline.FrbRunInput run, ResultsPipeline.FrbInput frb,
            Map<Path, String> beforeAssembly) {}

    private static org.assertj.core.api.AbstractThrowableAssert<?, ?> boundFailure(
            FrbBundleAssembler.Assembled assembled, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb) {
        return assertThatThrownBy(() -> FrbBundleAssembler.requireBound(assembled, run, frb));
    }

    private static org.assertj.core.api.AbstractThrowableAssert<?, ?> rowsFailure(Path manifestFile,
            String role, FreezeRecord freeze, FrozenGateParameters parameters) {
        return assertThatThrownBy(() -> FrbBundleAssembler.rows(manifestFile, role, freeze, parameters));
    }

    private static org.assertj.core.api.AbstractThrowableAssert<?, ?> partsFailure(Setup setup,
            ClassAAssembly part, ClassBAssembly classB) {
        return assertThatThrownBy(() -> parts(setup, part, classB));
    }

    private static org.assertj.core.api.AbstractThrowableAssert<?, ?> twoPartsFailure(TwoExportSeam seam,
            List<ClassAAssembly> parts) {
        return assertThatThrownBy(() -> twoParts(seam, parts));
    }

    private static org.assertj.core.api.AbstractThrowableAssert<?, ?> assemblyFailure(
            List<ClassAAssembly> parts, List<ClassASources> sources, ClassBAssembly classB, ClassBSources classBSources) {
        return assertThatThrownBy(() -> FrbBundleAssembler.assembleParts(parts, sources, classB, classBSources));
    }

    private AFixture fixture(boolean humanOnSelected, boolean mandatory) throws Exception {
        return fixture(humanOnSelected, mandatory, false, false);
    }

    private AFixture fixture(boolean humanOnSelected, boolean mandatory, boolean excluded) throws Exception {
        return fixture(humanOnSelected, mandatory, excluded, false);
    }

    private AFixture fixture(boolean humanOnSelected, boolean mandatory, boolean excluded,
            boolean secondStem) throws Exception {
        return fixture(humanOnSelected, mandatory, excluded, secondStem, 0);
    }

    private AFixture fixture(boolean humanOnSelected, boolean mandatory, boolean excluded,
            boolean secondStem, int extraCases) throws Exception {
        Path base = Files.createTempDirectory(temp, "fixture-");
        FrbPipelineTest pipeline = new FrbPipelineTest();
        pipeline.temp = base;
        FrbPipelineTest.Fixture pipelineFixture = pipeline.fixture(drawn, 0, 2, false);
        ResultsPipeline.FrbRunInput run = pipelineFixture.run();
        if (mandatory || excluded || extraCases > 0) {
            Path manifest = run.manifests().get(PRIMARY_MANIFEST);
            String first = Files.readAllLines(manifest).getFirst();
            StringBuilder additions = new StringBuilder(Files.readString(manifest));
            if (mandatory || excluded) {
                ObjectNode additional = (ObjectNode) IntegritySupport.JSON.readTree(first);
                additional.put(CASE_ID_FIELD, CASE_THREE);
                additions.append(additional).append('\n');
            }
            for (int i = 0; i < extraCases; i++) {
                ObjectNode additional = (ObjectNode) IntegritySupport.JSON.readTree(first);
                additional.put(CASE_ID_FIELD, EXTRA_CASE_PREFIX + i);
                additions.append(additional).append('\n');
            }
            Files.writeString(manifest, additions.toString());
        }
        if (secondStem) {
            Path manifest = run.manifests().get(PRIMARY_MANIFEST);
            List<String> lines = Files.readAllLines(manifest);
            ObjectNode second = (ObjectNode) IntegritySupport.JSON.readTree(lines.get(1));
            second.put(WORKFLOW_FIELD, PLAN_STEM);
            Files.writeString(manifest, lines.getFirst() + NEWLINE + second + NEWLINE);
        }
        Path plan = base.resolve("spot-plan.json");
        Files.writeString(plan, "{\"stems\":[{\"stem\":\"" + STEM + SPOT_COUNT_SUFFIX
                + (secondStem ? ", {\"stem\":\"" + PLAN_STEM + SPOT_COUNT_SUFFIX : EMPTY_TEXT)
                + "],\"expansion\":[{\"workflow\":\"" + STEM + EXPANSION_PRIORITY_FRAGMENT + STEM
                + EXPANSION_ALLOCATION_FRAGMENT + STEM + EXPANSION_COUNT_END
                + (secondStem ? ",{\"workflow\":\"" + PLAN_STEM + EXPANSION_PRIORITY_FRAGMENT
                    + PLAN_STEM + EXPANSION_ALLOCATION_FRAGMENT + PLAN_STEM + EXPANSION_COUNT_END : EMPTY_TEXT) + JSON_ARRAY_END);
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.freeze()));
        ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(plan.toAbsolutePath().toString(), FreezeRecord.sha256(plan));
        if (mandatory || excluded || secondStem || extraCases > 0) {
            Path manifest = run.manifests().get(PRIMARY_MANIFEST);
            ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(manifest.toAbsolutePath().toString(),
                    FreezeRecord.sha256(manifest));
        }
        if (secondStem) {
            ((ArrayNode) freeze.path(ARMS_FIELD).path(MINI).path(WORKFLOWS_FIELD)).add(PLAN_STEM);
            ((ObjectNode) freeze.path(COST_FORECAST_FIELD)).put(PLAN_STEM, 1);
            ((ObjectNode) freeze.path(COMPATIBILITY_FIELD)).put(SYNTHETIC_SUITE_ONE, PLAN_STEM);
        }
        Files.writeString(run.freeze(), freeze.toString());
        detached(run.freeze());
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
        List<String> caseIds = new ArrayList<>(List.of(CASE_ONE, CASE_TWO));
        if (mandatory || excluded) caseIds.add(CASE_THREE);
        for (int i = 0; i < extraCases; i++) caseIds.add(EXTRA_CASE_PREFIX + i);
        for (String id : caseIds) {
            cases.add(new ClassACaseManifest.Case(id, secondStem && id.equals(CASE_TWO)
                    ? ClassACaseManifest.Workflow.PLAN_ADAPTIVE : ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                    "synthetic", LearnerLevel.COLLEGE, null, BIOLOGY_LABEL, false,
                    List.of(source), 2, null, false, null));
            if (!excluded || !id.equals(CASE_THREE)) {
                outputs.add(new ClassABlindPacketExport.Output(id, MINI, questions));
                outputs.add(new ClassABlindPacketExport.Output(id, LUNA, questions));
            }
        }
        var exported = ClassABlindPacketExport.export(cases, outputs, MINI, LUNA,
                root.resolve(PACKETS_NAME), root.resolve(CLASS_A_KEY_FILE), 17L);
        PairMap pairs = PairMap.fromPackets(root.resolve(PACKETS_NAME));
        String mandatoryPair = mandatory ? pairs.pairs().getLast().id() : null;
        Path directory = root.resolve(FINDINGS_NAME);
        Files.createDirectories(directory);
        Path registry = directory.resolve("seeds.json");
        Files.writeString(registry, "{\"seed\":\"synthetic-seed-123456789\",\"streams\":["
                + WORKFLOW_JSON_PREFIX + STEM + "\",\"purpose\":\"SPOT_CHECK\",\"rounds\":1},"
                + WORKFLOW_JSON_PREFIX + STEM + "\",\"purpose\":\"EXPANSION\",\"rounds\":3}"
                + (secondStem ? ", {\"workflow\":\"" + PLAN_STEM
                    + "\",\"purpose\":\"SPOT_CHECK\",\"rounds\":1}" : EMPTY_TEXT) + JSON_ARRAY_END);
        detached(registry);
        Path log = directory.resolve("draws.jsonl");
        DrawLog.Entry draw = PairSampler.draw(SeedStreamRegistry.load(registry), new DrawLog(log), pairs,
                STEM, SeedStreamRegistry.SPOT_CHECK, 1,
                pairs.pairs().stream().map(PairMap.Pair::id).filter(id -> !id.equals(mandatoryPair))
                        .filter(id -> STEM.equals(pairs.workflowOf(id))).toList(),
                1, CLOCK);
        String selected = draw.selectedPairIds().getFirst();
        Set<String> selections = new java.util.HashSet<>(draw.selectedPairIds());
        if (secondStem) {
            DrawLog.Entry other = PairSampler.draw(SeedStreamRegistry.load(registry), new DrawLog(log), pairs,
                    PLAN_STEM, SeedStreamRegistry.SPOT_CHECK, 1,
                    pairs.pairs().stream().map(PairMap.Pair::id)
                            .filter(id -> PLAN_STEM.equals(pairs.workflowOf(id))).toList(), 1, CLOCK);
            selections.addAll(other.selectedPairIds());
        }
        List<String> pRows = new ArrayList<>(), sRows = new ArrayList<>(), hRows = new ArrayList<>();
        for (String id : pairs.blindItemIds().stream().sorted().toList()) {
            boolean isMandatory = mandatory && mandatoryPair.equals(pairs.pairOf(id));
            pRows.add(ClassAAdjudicationFixture.scorer(id, "P", isMandatory ? CRITICAL_GRADE : CLEAN_GRADE, false).toString());
            sRows.add(ClassAAdjudicationFixture.scorer(id, "S", CLEAN_GRADE, false).toString());
            if (isMandatory || humanOnSelected && selections.contains(pairs.pairOf(id)))
                hRows.add(ClassAAdjudicationFixture.human(id, CLEAN_GRADE, isMandatory).toString());
        }
        Path p = directory.resolve("p.jsonl"), s = directory.resolve("s.jsonl"), h = directory.resolve("human.jsonl");
        Files.write(p, pRows); Files.write(s, sRows); Files.write(h, hRows);
        Path seal = ArmKeyGateFixtures.seal(root, exported.armKey());
        Path lock = root.resolve("lock.json");
        Path[] findings = {p, s, h, log, registry};
        relock(lock, seal, findings);
        ArmKeyAccess.OpenedKey opened = ArmKeyAccess.open(exported.armKey(), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, h)), Set.of(LockRecord.Kind.PRIMARY),
                root.resolve(ACCESS_LOG_NAME), ArmKeyGateFixtures.OPENED, "class-A assembler test");
        ClassAExport a = new ClassAExport(root.resolve(PACKETS_NAME), p, s, h, opened, seal, lock,
                exported.exclusions(), FreezeRecord.sha256(exported.exclusions()));
        ClassASources sources = new ClassASources(run.freeze(), run.parameters(), run.mapping(),
                Map.copyOf(manifests), a, log, registry);
        return new AFixture(sources, pairs, selected, Set.copyOf(selections),
                log, registry, lock, seal, root, findings, pipelineFixture);
    }

    private static void detached(Path file) throws Exception {
        Files.writeString(file.resolveSibling(file.getFileName() + SHA_SUFFIX), FreezeRecord.sha256(file) + NEWLINE);
    }

    private static void relock(Path lock, Path seal, Path[] findings) throws Exception {
        Files.deleteIfExists(lock);
        Files.deleteIfExists(lock.resolveSibling(lock.getFileName() + SHA_SUFFIX));
        ArmKeySeal.Seal sealed = ArmKeySeal.load(seal);
        LockRecord.create(lock, LockRecord.Kind.PRIMARY, sealed.workflowStems(), List.of(findings),
                sealed, seal, files -> {}, ArmKeyGateFixtures.LOCKED);
    }


    private Setup setup(boolean withReserve) throws Exception {
        return setup(withReserve, false);
    }

    private Setup setup(boolean withReserve, boolean excluded) throws Exception {
        drawn = withReserve;
        AFixture a = fixture(true, false, excluded);
        return setupFrom(a, withReserve);
    }

    private Setup setupFrom(AFixture a, boolean withReserve) throws Exception {
        ResultsPipeline.FrbRunInput run = a.pipelineFixture().run();
        ResultsPipeline.FrbInput original = a.pipelineFixture().frb();
        ResultsPipeline.ReserveVerification verification = original.verification();
        Path primaryRoot = verification.sealFile().getParent();
        ArmKeyAccess.OpenedKey primaryOpened = ArmKeyAccess.open(primaryRoot.resolve(ARM_KEY_FILE),
                verification.sealFile(), List.of(ArmKeyGateFixtures.binding(verification.primaryLockFile(),
                        verification.sealFile(), verification.scoresFile())), Set.of(LockRecord.Kind.PRIMARY),
                primaryRoot.resolve(ASSEMBLER_ACCESS_LOG), OPENED, "bundle assembler test primary");
        ClassBExport primary = new ClassBExport(verification.packetDirectory(), verification.scoresFile(),
                SCORERS, primaryOpened);
        Map<String, ClassBExport> reserves = new HashMap<>();
        Map<String, ResultsPipeline.ReserveExport> reserveLogs = new HashMap<>();
        if (withReserve) {
            ResultsPipeline.ReserveExport reserve = original.provenance().reserves().get(TIP);
            Path root = reserve.sealFile().getParent();
            Path scores = root.resolve("findings/scores.jsonl");
            ArmKeyAccess.OpenedKey opened = ArmKeyAccess.open(root.resolve(ARM_KEY_FILE), reserve.sealFile(),
                    List.of(ArmKeyGateFixtures.binding(reserve.lockForReserveOpening(), reserve.sealFile(), scores)),
                    Set.of(LockRecord.Kind.PRIMARY), root.resolve(ASSEMBLER_ACCESS_LOG),
                    OPENED, "bundle assembler test reserve");
            reserves.put(TIP, new ClassBExport(root.resolve(PACKETS_NAME), scores, SCORERS, opened));
            reserveLogs.put(TIP, new ResultsPipeline.ReserveExport(TIP, reserve.reserveKeyAccessLog(),
                    reserve.sealFile(), reserve.lockForPrimaryOpening(), reserve.lockForReserveOpening(),
                    reserve.findingsDirectory(), root.resolve(PACKETS_NAME), scores, SCORERS));
        }
        ClassBSources b = new ClassBSources(run.freeze(), run.parameters(), run.mapping(),
                run.candidate().receipt(), run.manifests(), primary, reserves, original.reserveRecordFile(),
                verification.sealFile(), verification.primaryLockFile());
        Map<Path, String> beforeAssembly = snapshot();
        FrbBundleAssembler.Assembled assembled = FrbBundleAssembler.assemble(
                new FrbBundleAssembler.Sources(List.of(a.sources()), b));
        ResultsPipeline.PrimaryExport aPrimary = new ResultsPipeline.PrimaryExport(
                a.root().resolve(ACCESS_LOG_NAME), a.seal(), a.lock(),
                a.sources().export().pScores().getParent(), a.sources().export().exclusionsFileOrNull(),
                a.sources().export().expectedExclusionsSha256OrNull());
        ResultsPipeline.PrimaryExport bPrimary = original.provenance().primaries().getLast();
        ResultsPipeline.FrbInput frb = new ResultsPipeline.FrbInput(assembled.bundle(), original.reserveRecordFile(),
                verification, new ResultsPipeline.ProvenanceLogs(List.of(aPrimary, bPrimary), reserveLogs));
        return new Setup(a, b, assembled, run, frb, beforeAssembly);
    }

    @Test void realFilesAssembleDrawnReserveAndBindToRun() throws Exception {
        Setup setup = setup(true);
        var assembled = setup.assembled();
        var a = assembled.classAAssemblies().getFirst();
        var b = assembled.classB();
        assertThat(assembled.bundle()).isEqualTo(FrbEvidenceBundle.assemble(List.of(a.facts()), b.primary(),
                b.reserveParts(), Set.of(CASE_ONE, CASE_TWO), Set.of(TIP_CASE_ID), b.decision(), b.classBWorkflowNames()));
        assertThat(assembled.expansionOutcomeByWorkflow()).isEqualTo(ExpansionOutcomes.derive(a).outcomeByWorkflow());
        assertThat(assembled.expansionOutcomeByWorkflow())
                .isEqualTo(ExpansionOutcomes.derive(setup.a().sources()).outcomeByWorkflow());
        assertThat(assembled.expansionFindings()).isEqualTo(ExpansionOutcomes.derive(a).findings());
        assertThat(assembled.digests()).containsKeys(FREEZE_ROLE, PARAMETERS_ROLE, MAPPING_ROLE,
                "classA[NOTE_ADAPTIVE].classA.seal", PRIMARY_SEAL_ROLE, "reserve.STUDY_TIP.scores");
        FrbBundleAssembler.requireBound(assembled, setup.run(), setup.frb());
    }

    @Test void realFilesUndrawnReserveIsAllowed() throws Exception {
        Setup setup = setup(false);
        assertThat(setup.assembled().classB().reserveParts()).isEmpty();
        FrbBundleAssembler.requireBound(setup.assembled(), setup.run(), setup.frb());
    }

    @Test void sourceAndAssembledCollectionsAreDefensivelyImmutable() throws Exception {
        Setup setup = setup(false);
        List<ClassASources> inputs = new ArrayList<>(List.of(setup.a().sources()));
        FrbBundleAssembler.Sources sources = new FrbBundleAssembler.Sources(inputs, setup.b());
        inputs.clear();
        assertThat(sources.classAExports()).hasSize(1);
        List<ClassAAssembly> assemblies = setup.assembled().classAAssemblies();
        Map<String, String> outcomes = setup.assembled().expansionOutcomeByWorkflow();
        Map<String, ExpansionOutcomes.Finding> findings = setup.assembled().expansionFindings();
        SortedMap<String, String> digests = setup.assembled().digests();
        assertThatThrownBy(assemblies::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(outcomes::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(findings::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(digests::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void realAssemblyPreservesLocksSealsLogsAndDecisionBytes() throws Exception {
        Setup setup = setup(true);
        assertThat(snapshot()).isEqualTo(setup.beforeAssembly());
    }

    private static ClassAAssembly copyA(ClassAAssembly old, AdjudicatedLoaded facts,
            Map<String, ExpansionObligation.Disclosure> obligations, SourceDigests digests) {
        return new ClassAAssembly(facts, old.statuses(), old.pairs(), old.sets(), old.reviewRequiredPairs(),
                old.drawEntries(), digests, obligations, old.declared());
    }

    private static ClassBAssembly copyB(ClassBAssembly old, AdjudicatedLoaded primary,
            List<FrbEvidenceBundle.ReservePart> reserves, Set<String> workflows, SourceDigests digests) {
        return new ClassBAssembly(primary, reserves, old.decision(), workflows, digests);
    }

    private static SourceDigests digestChanged(SourceDigests original, String role, String value) {
        SortedMap<String, String> values = new TreeMap<>(original.values());
        values.put(role, value);
        return new SourceDigests(values);
    }

    private static FrbBundleAssembler.Assembled parts(Setup setup, ClassAAssembly a, ClassBAssembly b) {
        return FrbBundleAssembler.assembleParts(List.of(a), List.of(setup.a().sources()), b, setup.b());
    }

    private static ResultsPipeline.FrbRunInput runWith(ResultsPipeline.FrbRunInput old,
            Path freeze, Path parameters, Path mapping, Map<String, Path> manifests) {
        return new ResultsPipeline.FrbRunInput(old.repoRoot(), freeze, parameters, mapping, manifests,
                old.baselineMini(), old.baseline41(), old.candidate(), old.reserves(),
                old.compatibilityReports(), old.headCommitTime(), old.ownerDispositions(), old.report(), old.synthetic());
    }

    private static ResultsPipeline.FrbInput frbWith(ResultsPipeline.FrbInput old, FrbEvidenceBundle bundle,
            Path record, ResultsPipeline.ReserveVerification verification, ResultsPipeline.ProvenanceLogs provenance) {
        return new ResultsPipeline.FrbInput(bundle, record, verification, provenance,
                old.routeClassifications(), old.operationalCostReview());
    }

    private static Path changedFile(Path original, Path destination) throws Exception {
        Files.writeString(destination, Files.readString(original) + NEWLINE);
        detached(destination);
        return destination;
    }

    @Test void realDigestRolesAreClassifiedAndUnknownRoleIsRefused() throws Exception {
        Setup setup = setup(true);
        ClassAAssembly a = setup.assembled().classAAssemblies().getFirst();
        ClassBAssembly b = setup.assembled().classB();
        Set<String> common = Set.of(FREEZE_ROLE, PARAMETERS_ROLE, MAPPING_ROLE);
        for (String role : common) {
            assertThat(a.digests().values()).containsEntry(role, b.digests().values().get(role));
            assertThat(a.digests().values()).containsEntry(role + SHA_SUFFIX,
                    b.digests().values().get(role + SHA_SUFFIX));
        }
        for (String role : a.digests().values().keySet()) {
            String base = role.replaceFirst(SHA_ROLE_REGEX, EMPTY_TEXT);
            assertThat(common.contains(base) || base.equals(CLASS_A_PRIMARY_DIGEST_ROLE)
                    || base.equals(CLASS_A_PLAN_DIGEST_ROLE)
                    || Set.of("classA.packets", "classA.seal", "classA.lock", CLASS_A_P_SCORE_ROLE,
                            "classA.scores.s", "classA.scores.h", "classA.drawlog", "classA.registry",
                            CLASS_A_EXCLUSIONS_ROLE, EXPANSION_DECISION_ROLE).contains(base)
                    || base.matches("classA\\.lock\\.finding\\.[0-9]+"))
                    .as(role).isTrue();
        }
        for (String role : b.digests().values().keySet()) {
            String base = role.replaceFirst(SHA_ROLE_REGEX, EMPTY_TEXT);
            assertThat(common.contains(base) || base.equals("manifest.classB.primary")
                    || base.equals("manifest.classB.reserve") || base.equals("candidate.receipt")
                    || base.equals(RESERVE_RECORD_ROLE)
                    || Set.of("primary.packets", "primary.scores", PRIMARY_SEAL_ROLE, "primary.lock").contains(base)
                    || base.matches("primary\\.finding\\.[0-9]+")
                    || base.matches("reserve\\.[A-Z][A-Z0-9_]*\\.(packets|scores)"))
                    .as(role).isTrue();
        }
        ClassAAssembly rogue = copyA(a, a.facts(), a.expansionObligations(),
                digestChanged(a.digests(), "new.unclassified.role", ZERO_DIGIT.repeat(64)));
        partsFailure(setup, rogue, b)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("Unclassified digest role");
    }

    @Test void commonDigestDifferencesAreRefusedOneRoleAtATime() throws Exception {
        Setup setup = setup(false);
        ClassAAssembly a = setup.assembled().classAAssemblies().getFirst();
        ClassBAssembly b = setup.assembled().classB();
        for (String role : List.of(FREEZE_ROLE, FREEZE_SIDECAR_ROLE, PARAMETERS_ROLE, PARAMETERS_SIDECAR_ROLE,
                MAPPING_ROLE, MAPPING_SIDECAR_ROLE, CLASS_A_PRIMARY_DIGEST_ROLE)) {
            ClassAAssembly changed = copyA(a, a.facts(), a.expansionObligations(),
                    digestChanged(a.digests(), role, ZERO_DIGIT.repeat(64)));
            if (role.startsWith(CLASS_A_PRIMARY_DIGEST_ROLE)) {
                ClassAAssembly second = copyA(a, a.facts(), a.expansionObligations(), a.digests());
                // A second export needs a distinct workflow and frozen parameters; covered by the seam fixture below.
                assertThat(second.digests().values().get(role)).isNotEqualTo(changed.digests().values().get(role));
            } else {
                partsFailure(setup, changed, b)
                        .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(DIGEST_DIFF_MESSAGE + role);
            }
        }
    }

    @Test void selfConsistentClassBPartStillMustMatchIndependentManifest() throws Exception {
        Setup setup = setup(false);
        ClassBAssembly b = setup.assembled().classB();
        Map<String, AdjudicatedFacts> renamed = new LinkedHashMap<>();
        b.primary().byCaseArm().forEach((id, fact) -> renamed.put(id.replace(TIP_CASE_ID, OTHER_CASE_ID), fact));
        AdjudicatedLoaded replacement = new AdjudicatedLoaded(renamed, Set.of(), Map.of(OTHER_CASE_ID, 1));
        ClassBAssembly changed = copyB(b, replacement, b.reserveParts(), b.classBWorkflowNames(), b.digests());
        partsFailure(setup, setup.assembled().classAAssemblies().getFirst(), changed)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("Class-B primary differs from manifest");
    }

    @Test void reserveFrozenIdsAndWorkflowNamesMustMatchIndependentManifest() throws Exception {
        Setup setup = setup(true);
        ClassBAssembly b = setup.assembled().classB();
        FrbEvidenceBundle.ReservePart old = b.reserveParts().getFirst();
        FrbEvidenceBundle.ReservePart wrong = new FrbEvidenceBundle.ReservePart(TIP, old.facts(), Set.of(OTHER_CASE_ID));
        ClassBAssembly changed = copyB(b, b.primary(), List.of(wrong), b.classBWorkflowNames(), b.digests());
        partsFailure(setup, setup.assembled().classAAssemblies().getFirst(), changed)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(RESERVE_ID_MISMATCH_MESSAGE);
        ClassBAssembly wrongWorkflows = copyB(b, b.primary(), b.reserveParts(), Set.of(ASK_COMPANION_WORKFLOW), b.digests());
        partsFailure(setup, setup.assembled().classAAssemblies().getFirst(), wrongWorkflows)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(FROZEN_PARTITION_MESSAGE);
        Setup undrawn = setup(false);
        ClassBAssembly original = undrawn.assembled().classB();
        ReserveDecisionRecord oldRecord = original.decision();
        ReserveDecisionRecord.WorkflowDecision oldDecision = oldRecord.decisions().getFirst();
        ReserveDecisionRecord wrongRecord = new ReserveDecisionRecord(oldRecord.schemaVersion(),
                List.of(new ReserveDecisionRecord.WorkflowDecision(ASK_COMPANION_WORKFLOW, oldDecision.primaryPairs(),
                        false, oldDecision.primaryUnresolved(), oldDecision.pairDigestSha256())),
                oldRecord.findingsSha256(), oldRecord.lockSha256(), oldRecord.createdAtUtc());
        ClassBAssembly internallyConsistent = new ClassBAssembly(original.primary(), original.reserveParts(),
                wrongRecord, Set.of(ASK_COMPANION_WORKFLOW), original.digests());
        partsFailure(undrawn, undrawn.assembled().classAAssemblies().getFirst(),
                internallyConsistent)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(FROZEN_PARTITION_MESSAGE);
    }

    @Test void selfConsistentReservePartStillMustMatchIndependentReserveManifest() throws Exception {
        Setup setup = setup(true);
        ClassBAssembly b = setup.assembled().classB();
        FrbEvidenceBundle.ReservePart old = b.reserveParts().getFirst();
        Map<String, AdjudicatedFacts> facts = new LinkedHashMap<>();
        old.facts().byCaseArm().forEach((id, value) -> facts.put(id.replace(RESERVE_ID_PREFIX, OTHER_ID_PREFIX), value));
        Map<String, Integer> widths = new LinkedHashMap<>();
        old.facts().requestedQuestionsByCaseId().forEach((id, value) ->
                widths.put(id.replace(RESERVE_ID_PREFIX, OTHER_ID_PREFIX), value));
        AdjudicatedLoaded replacement = new AdjudicatedLoaded(facts, Set.of(), widths);
        FrbEvidenceBundle.ReservePart changed = new FrbEvidenceBundle.ReservePart(TIP, replacement, widths.keySet());
        ClassBAssembly updated = copyB(b, b.primary(), List.of(changed), b.classBWorkflowNames(), b.digests());
        partsFailure(setup, setup.assembled().classAAssemblies().getFirst(), updated)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(RESERVE_ID_MISMATCH_MESSAGE);
    }

    @Test void incompleteReservePartFailsWithNamedBundleException() throws Exception {
        Setup setup = setup(true);
        ClassBAssembly original = setup.assembled().classB();
        FrbEvidenceBundle.ReservePart old = original.reserveParts().getFirst();
        FrbEvidenceBundle.ReservePart incomplete = new FrbEvidenceBundle.ReservePart(
                old.workflow(), old.facts(), null);
        ClassBAssembly changed = copyB(original, original.primary(), List.of(incomplete),
                original.classBWorkflowNames(), original.digests());
        partsFailure(setup, setup.assembled().classAAssemblies().getFirst(), changed)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("reserve part");
    }

    @Test void excludedFrozenClassACaseRemainsInManifestDerivedSet() throws Exception {
        Setup setup = setup(false, true);
        assertThat(setup.assembled().classAAssemblies().getFirst().facts().excludedCaseIds())
                .contains(CASE_THREE);
        assertThat(setup.assembled().bundle().requestedQuestionsByCaseId()).containsKey(CASE_THREE);
        FrbBundleAssembler.requireBound(setup.assembled(), setup.run(), setup.frb());
    }

    private static void repinManifest(ResultsPipeline.FrbRunInput run, Path manifest) throws Exception {
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.freeze()));
        ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(manifest.toAbsolutePath().toString(),
                FreezeRecord.sha256(manifest));
        Files.writeString(run.freeze(), freeze.toString());
        detached(run.freeze());
    }

    @Test void independentReadersUseCaseIdAndFixtureIdRatherThanOneSharedField() throws Exception {
        Setup setup = setup(false);
        var run = setup.run();
        var aRow = IntegritySupport.JSON.readTree(Files.readAllLines(run.manifests().get(PRIMARY_MANIFEST)).getFirst());
        var bRow = IntegritySupport.JSON.readTree(Files.readAllLines(run.manifests().get(CLASS_B_PRIMARY_MANIFEST)).getFirst());
        assertThat(aRow.path(CASE_ID_FIELD).asText()).isEqualTo(CASE_ONE);
        assertThat(bRow.path(CASE_ID_FIELD).asText()).isBlank();
        assertThat(bRow.path("fixtureId").asText()).isEqualTo(TIP_CASE_ID);
        assertThat(setup.assembled().bundle().requestedQuestionsByCaseId()).containsKeys(CASE_ONE, CASE_TWO, TIP_CASE_ID);
    }

    @Test void pipelinePrimaryCrossCheckRejectsMalformedWidthEvenWhenPartsAreSelfConsistent() throws Exception {
        Setup setup = setup(false);
        Path manifest = setup.run().manifests().get(PRIMARY_MANIFEST);
        List<String> lines = Files.readAllLines(manifest);
        ObjectNode first = (ObjectNode) IntegritySupport.JSON.readTree(lines.getFirst());
        first.remove("requestedQuestionCount");
        Files.writeString(manifest, first + NEWLINE + lines.get(1) + NEWLINE);
        repinManifest(setup.run(), manifest);
        partsFailure(setup, setup.assembled().classAAssemblies().getFirst(),
                setup.assembled().classB())
                .isInstanceOf(InvalidFrbBundle.class).hasCauseInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void pipelineReserveCrossCheckRejectsMalformedClassAReserve() throws Exception {
        Setup setup = setup(false);
        Path manifest = setup.run().manifests().get(CLASS_A_RESERVE_MANIFEST);
        Files.writeString(manifest, "{\"caseId\":\"\",\"workflow\":\"NOTE_ADAPTIVE\","
                + "\"requestedQuestionCount\":1}\n");
        repinManifest(setup.run(), manifest);
        partsFailure(setup, setup.assembled().classAAssemblies().getFirst(),
                setup.assembled().classB())
                .isInstanceOf(InvalidFrbBundle.class).hasCauseInstanceOf(ResultsPipeline.InvalidInput.class);
    }

    @Test void freezeValidationErrorIsWrappedWithItsCauseAtTheSeam() throws Exception {
        Setup setup = setup(false);
        Files.writeString(IntegritySupport.detached(setup.run().freeze()), ZERO_DIGIT.repeat(64));
        partsFailure(setup, setup.assembled().classAAssemblies().getFirst(), setup.assembled().classB())
                .isInstanceOf(InvalidFrbBundle.class).hasCauseInstanceOf(FreezeRecord.InvalidFreeze.class);
    }

    @Test void readerSeamMapsBoardExamDirectAndRefusesUnknownFrozenWorkflow() throws Exception {
        Setup setup = setup(false);
        ResultsPipeline.FrbRunInput run = setup.run();
        ObjectNode parameters = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.parameters()));
        ObjectNode workflows = (ObjectNode) parameters.get(WORKFLOWS_FIELD);
        ObjectNode board = workflows.get(TIP).deepCopy();
        workflows.remove(TIP);
        workflows.set(BOARD_EXAM_WORKFLOW, board);
        ((ArrayNode) parameters.path(ROUTES_FIELD).path(INTERACTIVE_QUIZ_ROUTE).path(WORKFLOWS_FIELD))
                .removeAll().add(STEM).add(BOARD_EXAM_WORKFLOW);
        ((ObjectNode) parameters.get("frb")).set("classBWorkflows",
                IntegritySupport.JSON.valueToTree(List.of(BOARD_EXAM_WORKFLOW)));
        Files.writeString(run.parameters(), parameters.toString());
        detached(run.parameters());
        Path manifest = temp.resolve("board-direct.jsonl");
        Files.writeString(manifest, "{\"fixtureId\":\"board-case\",\"workflow\":\"BOARD_EXAM_DIRECT\"}\n");
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.freeze()));
        freeze.put(PARAMETERS_SHA_FIELD, FreezeRecord.sha256(run.parameters()));
        ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(manifest.toAbsolutePath().toString(), FreezeRecord.sha256(manifest));
        ((ArrayNode) freeze.path(ARMS_FIELD).path("BASELINE_41").path(WORKFLOWS_FIELD)).removeAll().add(BOARD_EXAM_WORKFLOW);
        ((ObjectNode) freeze.path(COST_FORECAST_FIELD)).put(BOARD_EXAM_WORKFLOW, 1);
        ((ObjectNode) freeze.path(COMPATIBILITY_FIELD)).put(SYNTHETIC_SUITE_ONE, BOARD_EXAM_WORKFLOW);
        Files.writeString(run.freeze(), freeze.toString());
        detached(run.freeze());
        var rows = FrbBundleAssembler.rows(manifest, CLASS_B_PRIMARY_MANIFEST, FreezeRecord.load(run.freeze()),
                FrozenGateParameters.load(run.parameters()));
        assertThat(rows).containsEntry(BOARD_EXAM_WORKFLOW, Set.of("board-case"));
        Files.writeString(manifest, "{\"fixtureId\":\"board-case\",\"workflow\":\"OTHER\"}\n");
        repinManifest(run, manifest);
        rowsFailure(manifest, CLASS_B_PRIMARY_MANIFEST, FreezeRecord.load(run.freeze()),
                FrozenGateParameters.load(run.parameters()))
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(INVALID_WORKFLOW_MESSAGE);
    }

    @Test void readerSeamRefusesClassBWorkflowInClassAPrimary() throws Exception {
        Setup setup = setup(false);
        Path manifest = temp.resolve("wrong-class-a.jsonl");
        Files.writeString(manifest, "{\"caseId\":\"wrong\",\"workflow\":\"STUDY_TIP\","
                + "\"requestedQuestionCount\":2}\n");
        repinManifest(setup.run(), manifest);
        rowsFailure(manifest, PRIMARY_MANIFEST,
                FreezeRecord.load(setup.run().freeze()), FrozenGateParameters.load(setup.run().parameters()))
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(INVALID_WORKFLOW_MESSAGE);
    }

    @Test void aCaseMissingFromAnExportIsRefusedByTheExistingMerge() throws Exception {
        Setup setup = setup(false);
        ClassAAssembly a = setup.assembled().classAAssemblies().getFirst();
        Map<String, AdjudicatedFacts> facts = new LinkedHashMap<>(a.facts().byCaseArm());
        facts.keySet().removeIf(id -> id.startsWith(CASE_TWO + CASE_ARM_SEPARATOR));
        AdjudicatedLoaded missing = new AdjudicatedLoaded(facts, Set.of(), Map.of(CASE_ONE, 2));
        ClassAAssembly changed = copyA(a, missing, a.expansionObligations(), a.digests());
        partsFailure(setup, changed, setup.assembled().classB())
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining("requested width: a-two");
    }

    @Test void bindingRefusesDifferentBundleAndEveryRunFileRole() throws Exception {
        Setup setup = setup(true);
        ResultsPipeline.FrbInput frb = setup.frb();
        ResultsPipeline.FrbRunInput run = setup.run();
        FrbEvidenceBundle different = new FrbEvidenceBundle(frb.bundle().evidence(), Map.of(),
                frb.bundle().reserveDrawnByWorkflow(), frb.bundle().workflowsAwaitingReserve());
        ResultsPipeline.FrbInput wrongBundle = frbWith(frb, different, frb.reserveRecordFile(),
                frb.verification(), frb.provenance());
        boundFailure(setup.assembled(), run, wrongBundle)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("bundle differs");
        List<String> roles = List.of(FREEZE_ROLE, PARAMETERS_ROLE, MAPPING_ROLE, PRIMARY_MANIFEST,
                CLASS_B_PRIMARY_MANIFEST, CLASS_B_RESERVE_MANIFEST);
        for (String role : roles) {
            Path original = switch (role) {
                case FREEZE_ROLE -> run.freeze();
                case PARAMETERS_ROLE -> run.parameters();
                case MAPPING_ROLE -> run.mapping();
                default -> run.manifests().get(role);
            };
            Path changed = changedFile(original, temp.resolve("changed-" + role.replace('.', '-') + ".jsonl"));
            Map<String, Path> manifests = new HashMap<>(run.manifests());
            if (role.contains(DOT)) manifests.put(role, changed);
            ResultsPipeline.FrbRunInput changedRun = runWith(run,
                    role.equals(FREEZE_ROLE) ? changed : run.freeze(),
                    role.equals(PARAMETERS_ROLE) ? changed : run.parameters(),
                    role.equals(MAPPING_ROLE) ? changed : run.mapping(), manifests);
            String digestRole = role.contains(DOT) ? MANIFEST_PREFIX + role : role;
            boundFailure(setup.assembled(), changedRun, frb)
                    .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("Binding differs: " + digestRole);
        }
    }

    @Test void bindingRequiresThreePrimaryAndReserveManifestKeysButAllowsClassAReserve() throws Exception {
        Setup setup = setup(false);
        assertThat(setup.run().manifests()).containsKey(CLASS_A_RESERVE_MANIFEST);
        FrbBundleAssembler.requireBound(setup.assembled(), setup.run(), setup.frb());
        for (String role : List.of(PRIMARY_MANIFEST, CLASS_B_PRIMARY_MANIFEST, CLASS_B_RESERVE_MANIFEST)) {
            Map<String, Path> manifests = new HashMap<>(setup.run().manifests());
            manifests.remove(role);
            ResultsPipeline.FrbRunInput changed = runWith(setup.run(), setup.run().freeze(),
                    setup.run().parameters(), setup.run().mapping(), manifests);
            boundFailure(setup.assembled(), changed, setup.frb())
                    .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(MANIFEST_PREFIX + role);
        }
    }

    @Test void bindingChecksReserveRecordAndAllVerificationFields() throws Exception {
        Setup setup = setup(true);
        ResultsPipeline.FrbInput frb = setup.frb();
        Path changedRecord = changedFile(frb.reserveRecordFile(), temp.resolve("changed-record.json"));
        ResultsPipeline.FrbInput wrongRecord = frbWith(frb, frb.bundle(), changedRecord,
                frb.verification(), frb.provenance());
        boundFailure(setup.assembled(), setup.run(), wrongRecord)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(RESERVE_RECORD_ROLE);
        ResultsPipeline.ReserveVerification original = frb.verification();
        for (String role : List.of(PACKETS_NAME, SCORES_NAME, SEAL_NAME, LOCK_NAME, WORKFLOWS_FIELD)) {
            Path packets = original.packetDirectory();
            Path scores = original.scoresFile();
            Path seal = original.sealFile();
            Path lock = original.primaryLockFile();
            Set<String> workflows = original.workflowNames();
            if (role.equals(PACKETS_NAME)) packets = changedPackets(packets, temp.resolve("other-primary-packets"));
            if (role.equals(SCORES_NAME)) scores = changedFile(scores, temp.resolve("other-primary-scores.jsonl"));
            if (role.equals(SEAL_NAME)) seal = changedFile(seal, temp.resolve("other-primary-seal.json"));
            if (role.equals(LOCK_NAME)) lock = changedFile(lock, temp.resolve("other-primary-lock.json"));
            if (role.equals(WORKFLOWS_FIELD)) workflows = Set.of(ASK_COMPANION_WORKFLOW);
            ResultsPipeline.ReserveVerification changed = new ResultsPipeline.ReserveVerification(
                    packets, seal, scores, original.scorers(), workflows, lock);
            ResultsPipeline.FrbInput wrong = frbWith(frb, frb.bundle(), frb.reserveRecordFile(),
                    changed, frb.provenance());
            boundFailure(setup.assembled(), setup.run(), wrong)
                    .isInstanceOf(InvalidFrbBundle.class)
                    .hasMessageContaining(role.equals(WORKFLOWS_FIELD) ? "Verification workflows" : "primary.");
        }
    }

    private static Path changedPackets(Path original, Path destination) throws Exception {
        Files.createDirectories(destination);
        try (var files = Files.list(original)) {
            for (Path file : files.toList())
                Files.writeString(destination.resolve(file.getFileName()), Files.readString(file) + NEWLINE);
        }
        return destination;
    }

    @Test void bindingRequiresExactlyTheAssembledPrimariesAndTheirFindings() throws Exception {
        Setup setup = setup(false);
        ResultsPipeline.FrbInput frb = setup.frb();
        List<ResultsPipeline.PrimaryExport> original = frb.provenance().primaries();
        for (List<ResultsPipeline.PrimaryExport> primaries : List.of(
                List.of(original.getLast()), List.of(original.getFirst()),
                List.of(original.getFirst(), original.getLast(), original.getFirst()))) {
            ResultsPipeline.ProvenanceLogs logs = new ResultsPipeline.ProvenanceLogs(primaries, Map.of());
            ResultsPipeline.FrbInput wrong = frbWith(frb, frb.bundle(), frb.reserveRecordFile(),
                    frb.verification(), logs);
            boundFailure(setup.assembled(), setup.run(), wrong)
                    .isInstanceOf(InvalidFrbBundle.class);
        }
        ResultsPipeline.PrimaryExport a = original.getFirst();
        for (String role : List.of(SEAL_NAME, LOCK_NAME, FINDINGS_NAME, EXCLUSIONS_NAME)) {
            Path seal = a.sealFile(), lock = a.lockFile(), directory = a.findingsDirectory();
            Path exclusions = a.exclusionsFileOrNull();
            if (role.equals(SEAL_NAME)) seal = changedFile(seal, temp.resolve("other-a-seal.json"));
            if (role.equals(LOCK_NAME)) lock = changedFile(lock, temp.resolve("other-a-lock.json"));
            if (role.equals(FINDINGS_NAME)) {
                directory = Files.createDirectories(temp.resolve("copied-findings"));
                Path pScores = setup.a().sources().export().pScores();
                Files.copy(pScores, directory.resolve(pScores.getFileName()));
            }
            if (role.equals(EXCLUSIONS_NAME)) exclusions = changedFile(exclusions, temp.resolve("other-a-exclusions.jsonl"));
            ResultsPipeline.PrimaryExport changed = new ResultsPipeline.PrimaryExport(a.accessLog(), seal, lock,
                    directory, exclusions, a.expectedExclusionsSha256OrNull());
            ResultsPipeline.ProvenanceLogs logs = new ResultsPipeline.ProvenanceLogs(
                    List.of(changed, original.getLast()), Map.of());
            ResultsPipeline.FrbInput wrong = frbWith(frb, frb.bundle(), frb.reserveRecordFile(),
                    frb.verification(), logs);
            boundFailure(setup.assembled(), setup.run(), wrong)
                    .isInstanceOf(InvalidFrbBundle.class);
        }
        ResultsPipeline.PrimaryExport b = original.getLast();
        ResultsPipeline.PrimaryExport secondLock = new ResultsPipeline.PrimaryExport(b.accessLog(), b.sealFile(),
                changedFile(b.lockFile(), temp.resolve("second-b-lock.json")), b.findingsDirectory(), null, null);
        ResultsPipeline.FrbInput wrongLock = frbWith(frb, frb.bundle(), frb.reserveRecordFile(),
                frb.verification(), new ResultsPipeline.ProvenanceLogs(List.of(a, secondLock), Map.of()));
        boundFailure(setup.assembled(), setup.run(), wrongLock)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(UNASSEMBLED_PRIMARY_MESSAGE);
        ResultsPipeline.PrimaryExport extra = new ResultsPipeline.PrimaryExport(b.accessLog(),
                changedFile(b.sealFile(), temp.resolve("extra-primary-seal.json")),
                changedFile(b.lockFile(), temp.resolve("extra-primary-lock.json")),
                b.findingsDirectory(), null, null);
        ResultsPipeline.FrbInput wrongExtra = frbWith(frb, frb.bundle(), frb.reserveRecordFile(),
                frb.verification(), new ResultsPipeline.ProvenanceLogs(List.of(a, b, extra), Map.of()));
        boundFailure(setup.assembled(), setup.run(), wrongExtra)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(UNASSEMBLED_PRIMARY_MESSAGE);
    }

    @Test void bindingRefusesExclusionsWhenAssemblyRecordedNone() throws Exception {
        Setup setup = setup(false);
        ClassAAssembly a = setup.assembled().classAAssemblies().getFirst();
        SortedMap<String, String> values = new TreeMap<>(a.digests().values());
        values.remove(CLASS_A_EXCLUSIONS_ROLE);
        values.remove("classA.exclusions.sha256");
        ClassAAssembly without = copyA(a, a.facts(), a.expansionObligations(), new SourceDigests(values));
        FrbBundleAssembler.Assembled assembled = parts(setup, without, setup.assembled().classB());
        ResultsPipeline.PrimaryExport original = setup.frb().provenance().primaries().getFirst();
        ResultsPipeline.PrimaryExport omitted = new ResultsPipeline.PrimaryExport(original.accessLog(),
                original.sealFile(), original.lockFile(), original.findingsDirectory(), null, null);
        ResultsPipeline.ProvenanceLogs clean = new ResultsPipeline.ProvenanceLogs(
                List.of(omitted, setup.frb().provenance().primaries().getLast()), Map.of());
        ResultsPipeline.FrbInput pass = frbWith(setup.frb(), assembled.bundle(), setup.frb().reserveRecordFile(),
                setup.frb().verification(), clean);
        FrbBundleAssembler.requireBound(assembled, setup.run(), pass);
        ResultsPipeline.ProvenanceLogs present = new ResultsPipeline.ProvenanceLogs(
                setup.frb().provenance().primaries(), Map.of());
        ResultsPipeline.FrbInput wrong = frbWith(pass, assembled.bundle(), pass.reserveRecordFile(),
                pass.verification(), present);
        boundFailure(assembled, setup.run(), wrong)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("Class-A exclusions differ");
    }

    private Map<Path, String> snapshot() throws Exception {
        Map<Path, String> result = new TreeMap<>();
        try (var files = Files.walk(temp)) {
            for (Path file : files.filter(Files::isRegularFile).toList())
                result.put(file, FreezeRecord.sha256(file));
        }
        return result;
    }

    @Test void bindingDoesNotCreateModifyOrLeaveAnyFile() throws Exception {
        Setup setup = setup(true);
        Map<Path, String> before = snapshot();
        FrbBundleAssembler.requireBound(setup.assembled(), setup.run(), setup.frb());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void bindingRefusesChangedOriginalClassAPScores() throws Exception {
        Setup setup = setup(false);
        Path pScores = setup.a().sources().export().pScores();
        Files.writeString(pScores, Files.readString(pScores) + NEWLINE);
        detached(pScores);
        boundFailure(setup.assembled(), setup.run(), setup.frb())
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(CLASS_A_P_SCORE_ROLE);
    }

    @Test void bindingRequiresDrawnReservePacketsAndScores() throws Exception {
        Setup setup = setup(true);
        ResultsPipeline.FrbInput frb = setup.frb();
        ResultsPipeline.ReserveExport reserve = frb.provenance().reserves().get(TIP);
        ResultsPipeline.ReserveExport six = new ResultsPipeline.ReserveExport(TIP,
                reserve.reserveKeyAccessLog(), reserve.sealFile(), reserve.lockForPrimaryOpening(),
                reserve.lockForReserveOpening(), reserve.findingsDirectory());
        for (ResultsPipeline.ReserveExport changed : List.of(six,
                new ResultsPipeline.ReserveExport(TIP, reserve.reserveKeyAccessLog(), reserve.sealFile(),
                        reserve.lockForPrimaryOpening(), reserve.lockForReserveOpening(),
                        reserve.findingsDirectory(), changedPackets(reserve.reservePacketDirectory(),
                                temp.resolve("other-reserve-packets")), reserve.scoresFile(), reserve.scorers()),
                new ResultsPipeline.ReserveExport(TIP, reserve.reserveKeyAccessLog(), reserve.sealFile(),
                        reserve.lockForPrimaryOpening(), reserve.lockForReserveOpening(),
                        reserve.findingsDirectory(), reserve.reservePacketDirectory(),
                        changedFile(reserve.scoresFile(), temp.resolve("other-reserve-scores.jsonl")),
                        reserve.scorers()))) {
            ResultsPipeline.ProvenanceLogs logs = new ResultsPipeline.ProvenanceLogs(
                    frb.provenance().primaries(), Map.of(TIP, changed));
            ResultsPipeline.FrbInput wrong = frbWith(frb, frb.bundle(), frb.reserveRecordFile(),
                    frb.verification(), logs);
            boundFailure(setup.assembled(), setup.run(), wrong)
                    .isInstanceOf(InvalidFrbBundle.class);
        }
        ResultsPipeline.ProvenanceLogs extra = new ResultsPipeline.ProvenanceLogs(
                frb.provenance().primaries(), Map.of(ASK_COMPANION_WORKFLOW, reserve));
        ResultsPipeline.FrbInput wrong = frbWith(frb, frb.bundle(), frb.reserveRecordFile(),
                frb.verification(), extra);
        boundFailure(setup.assembled(), setup.run(), wrong)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("Reserve exports differ");
    }

    private record TwoExportSeam(Setup setup, ClassAAssembly note, ClassAAssembly plan,
            ClassBAssembly classB) {}

    /** Copies the single-export fixture, then changes frozen files and hand-builds two assembly records. */
    private TwoExportSeam twoExportSeam() throws Exception {
        Setup setup = setup(false);
        ResultsPipeline.FrbRunInput run = setup.run();
        ObjectNode parameters = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.parameters()));
        ObjectNode workflows = (ObjectNode) parameters.get(WORKFLOWS_FIELD);
        ObjectNode plan = workflows.get(STEM).deepCopy();
        plan.put(PRIMARY_SIZE_FIELD, 1);
        workflows.set(PLAN_STEM, plan);
        ((ObjectNode) workflows.get(STEM)).put(PRIMARY_SIZE_FIELD, 1);
        ((ArrayNode) parameters.path(ROUTES_FIELD).path(INTERACTIVE_QUIZ_ROUTE).path(WORKFLOWS_FIELD)).add(PLAN_STEM);
        Files.writeString(run.parameters(), parameters.toString());
        detached(run.parameters());
        Path primary = run.manifests().get(PRIMARY_MANIFEST);
        List<String> lines = Files.readAllLines(primary);
        ObjectNode second = (ObjectNode) IntegritySupport.JSON.readTree(lines.get(1));
        second.put(WORKFLOW_FIELD, PLAN_STEM);
        Files.writeString(primary, lines.getFirst() + NEWLINE + second + NEWLINE);
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.freeze()));
        freeze.put(PARAMETERS_SHA_FIELD, FreezeRecord.sha256(run.parameters()));
        ((ObjectNode) freeze.get(MANIFESTS_FIELD)).put(primary.toAbsolutePath().toString(), FreezeRecord.sha256(primary));
        ((ArrayNode) freeze.path(ARMS_FIELD).path(MINI).path(WORKFLOWS_FIELD)).add(PLAN_STEM);
        ((ObjectNode) freeze.path(COST_FORECAST_FIELD)).put(PLAN_STEM, 1);
        ((ObjectNode) freeze.path(COMPATIBILITY_FIELD)).put(SYNTHETIC_SUITE_ONE, PLAN_STEM);
        Files.writeString(run.freeze(), freeze.toString());
        detached(run.freeze());
        ClassAAssembly original = setup.assembled().classAAssemblies().getFirst();
        ClassBAssembly b = setup.assembled().classB();
        SourceDigests aDigest = updatedFrozenDigests(original.digests(), run);
        SourceDigests bDigest = updatedFrozenDigests(b.digests(), run);
        Map<String, AdjudicatedFacts> noteFacts = new LinkedHashMap<>();
        Map<String, AdjudicatedFacts> planFacts = new LinkedHashMap<>();
        original.facts().byCaseArm().forEach((identity, fact) ->
                (identity.startsWith(CASE_ONE + CASE_ARM_SEPARATOR) ? noteFacts : planFacts).put(identity, fact));
        AdjudicatedLoaded notePart = new AdjudicatedLoaded(noteFacts, Set.of(), Map.of(CASE_ONE, 2));
        AdjudicatedLoaded planPart = new AdjudicatedLoaded(planFacts, Set.of(), Map.of(CASE_TWO, 2));
        ExpansionObligation.Disclosure originalObligation = original.expansionObligations().get(STEM);
        ExpansionObligation.Disclosure planObligation = new ExpansionObligation.Disclosure(PLAN_STEM,
                Set.of(), Map.of(PLAN_STEM, 3), List.of(), false, 0);
        ClassAAssembly note = copyA(original, notePart, Map.of(STEM, originalObligation), aDigest);
        ClassAAssembly secondPart = copyA(original, planPart, Map.of(PLAN_STEM, planObligation),
                digestChanged(aDigest, CLASS_A_PLAN_DIGEST_ROLE, ZERO_DIGIT.repeat(64)));
        ClassBAssembly classB = copyB(b, b.primary(), b.reserveParts(), b.classBWorkflowNames(), bDigest);
        return new TwoExportSeam(setup, note, secondPart, classB);
    }

    private static SourceDigests updatedFrozenDigests(SourceDigests original, ResultsPipeline.FrbRunInput run)
            throws Exception {
        SortedMap<String, String> values = new TreeMap<>(original.values());
        for (Map.Entry<String, Path> item : Map.of(FREEZE_ROLE, run.freeze(), PARAMETERS_ROLE, run.parameters(),
                CLASS_A_PRIMARY_DIGEST_ROLE, run.manifests().get(PRIMARY_MANIFEST)).entrySet()) {
            if (!values.containsKey(item.getKey())) continue;
            values.put(item.getKey(), FreezeRecord.sha256(item.getValue()));
            if (values.containsKey(item.getKey() + SHA_SUFFIX))
                values.put(item.getKey() + SHA_SUFFIX, FreezeRecord.sha256(IntegritySupport.detached(item.getValue())));
        }
        return new SourceDigests(values);
    }

    private static FrbBundleAssembler.Assembled twoParts(TwoExportSeam seam, List<ClassAAssembly> parts) {
        return FrbBundleAssembler.assembleParts(parts,
                List.of(seam.setup().a().sources(), seam.setup().a().sources()),
                seam.classB(), seam.setup().b());
    }

    @Test void seamTwoExportsUseContentOrderAndAcceptDifferentSpotPlanDigests() throws Exception {
        TwoExportSeam seam = twoExportSeam();
        var first = twoParts(seam, List.of(seam.note(), seam.plan()));
        var reversed = twoParts(seam, List.of(seam.plan(), seam.note()));
        assertThat(first.bundle()).isEqualTo(reversed.bundle());
        assertThat(first.classAAssemblies()).containsExactly(seam.note(), seam.plan());
        assertThat(reversed.classAAssemblies()).containsExactly(seam.note(), seam.plan());
        assertThat(new ArrayList<>(first.expansionFindings().keySet())).containsExactly(STEM, PLAN_STEM);
        assertThat(new ArrayList<>(reversed.expansionFindings().keySet())).containsExactly(STEM, PLAN_STEM);
        assertThat(new ArrayList<>(first.expansionOutcomeByWorkflow().keySet()))
                .isEqualTo(new ArrayList<>(reversed.expansionOutcomeByWorkflow().keySet()));
        assertThat(first.digests()).isEqualTo(reversed.digests());
        assertThat(first.digests()).containsKeys("classA[NOTE_ADAPTIVE].manifest.classA.spotCheckPlan",
                "classA[PLAN_ADAPTIVE].manifest.classA.spotCheckPlan");
    }

    @Test void seamEqualAcrossClassAExportRolesRejectDifferentPrimaryAndCommonDigests() throws Exception {
        TwoExportSeam seam = twoExportSeam();
        for (String role : List.of(CLASS_A_PRIMARY_DIGEST_ROLE, "manifest.classA.primary.sha256",
                FREEZE_ROLE, FREEZE_SIDECAR_ROLE, PARAMETERS_ROLE, PARAMETERS_SIDECAR_ROLE, MAPPING_ROLE, MAPPING_SIDECAR_ROLE)) {
            ClassAAssembly first = seam.note();
            if (!first.digests().values().containsKey(role))
                first = copyA(first, first.facts(), first.expansionObligations(),
                        digestChanged(first.digests(), role, ZERO_DIGIT.repeat(64)));
            ClassAAssembly changed = copyA(seam.plan(), seam.plan().facts(), seam.plan().expansionObligations(),
                    digestChanged(seam.plan().digests(), role, "f".repeat(64)));
            ClassAAssembly left = first;
            twoPartsFailure(seam, List.of(left, changed))
                    .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(DIGEST_DIFF_MESSAGE + role);
        }
    }

    @Test void seamDuplicateOrMissingWorkflowIsRefused() throws Exception {
        TwoExportSeam seam = twoExportSeam();
        ClassAAssembly duplicate = copyA(seam.plan(), seam.plan().facts(), seam.note().expansionObligations(),
                seam.plan().digests());
        twoPartsFailure(seam, List.of(seam.note(), duplicate))
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("Workflow appears in two");
        assemblyFailure(List.of(seam.note()),
                List.of(seam.setup().a().sources()), seam.classB(), seam.setup().b())
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(MISSING_CLASS_A_WORKFLOW_MESSAGE);
        ClassAAssembly completeCases = copyA(seam.note(), seam.setup().assembled().classAAssemblies().getFirst().facts(),
                seam.note().expansionObligations(), seam.note().digests());
        assemblyFailure(List.of(completeCases),
                List.of(seam.setup().a().sources()), seam.classB(), seam.setup().b())
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining(MISSING_CLASS_A_WORKFLOW_MESSAGE);
    }

    @Test void seamOverlappingCasesAreRefusedByExistingMergeAfterDisjointWorkflowCheck() throws Exception {
        TwoExportSeam seam = twoExportSeam();
        ClassAAssembly overlap = copyA(seam.plan(), seam.note().facts(), seam.plan().expansionObligations(),
                seam.plan().digests());
        twoPartsFailure(seam, List.of(seam.note(), overlap))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining("Case appears in two exports");
    }

    @Test void seamDeclaredPendingFactRemainsPendingAndUnresolvedInBundle() throws Exception {
        Setup setup = setup(false);
        ClassAAssembly original = setup.assembled().classAAssemblies().getFirst();
        Map<String, AdjudicatedFacts> facts = new LinkedHashMap<>(original.facts().byCaseArm());
        String identity = facts.keySet().iterator().next();
        AdjudicatedFacts old = facts.get(identity);
        AdjudicatedFacts declared = new AdjudicatedFacts(old.scoredQuestions(), old.criticalQuestions(),
                old.qualityFailQuestions(), old.unresolvedQuestions() + 1, old.pendingQuestions() + 1,
                old.doubleMisses(), old.scorerFailureCriticals(), old.humanCleared(), old.subgroup());
        facts.put(identity, declared);
        AdjudicatedLoaded loaded = new AdjudicatedLoaded(facts, original.facts().excludedCaseIds(),
                original.facts().requestedQuestionsByCaseId(), original.facts().excludedReasons());
        ClassAAssembly changed = copyA(original, loaded, original.expansionObligations(), original.digests());
        var bundle = parts(setup, changed, setup.assembled().classB()).bundle();
        assertThat(bundle.evidence().byCaseArm()).containsEntry(identity, declared);
        assertThat(bundle.evidence().byCaseArm().get(identity).pendingQuestions()).isEqualTo(old.pendingQuestions() + 1);
        assertThat(bundle.evidence().byCaseArm().get(identity).unresolvedQuestions())
                .isEqualTo(old.unresolvedQuestions() + 1);
        facts.remove(identity);
        facts.forEach((id, fact) -> assertThat(bundle.evidence().byCaseArm()).containsEntry(id, fact));
    }

    /** Extends the copied class-A fixture with a real lock-listed Brief 5t declaration. */
    private AFixture declaredPendingFixture() throws Exception {
        drawn = false;
        AFixture a = fixture(true, false);
        Path human = a.sources().export().hScores();
        List<String> criticalHuman = new ArrayList<>();
        for (String line : Files.readAllLines(human)) {
            String id = IntegritySupport.JSON.readTree(line).path("id").asText();
            criticalHuman.add(ClassAAdjudicationFixture.human(id, CRITICAL_GRADE, false).toString());
        }
        Files.write(human, criticalHuman);
        String remaining = a.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(a.selected())).findFirst().orElseThrow();
        PairSampler.draw(SeedStreamRegistry.load(a.registry()), new DrawLog(a.log()), a.pairs(), STEM,
                SeedStreamRegistry.EXPANSION, 1, List.of(remaining), 1, CLOCK);
        Path decisionFile = human.getParent().resolve(ExpansionDecision.FILE_NAME);
        ObjectNode row = IntegritySupport.JSON.createObjectNode();
        row.put(WORKFLOW_FIELD, STEM);
        row.put("freezeSha256", FreezeRecord.sha256(a.sources().freezeFile()));
        row.put(PARAMETERS_SHA_FIELD, FreezeRecord.sha256(a.sources().parametersFile()));
        row.put("sealSha256", FreezeRecord.sha256(a.seal()));
        row.put("drawLogSha256", FreezeRecord.sha256(a.log()));
        row.put("capMinutes", ExpansionDecision.PREREGISTERED_EXPANSION_CAP_MINUTES);
        row.put("approvedLargerCap", false);
        row.put("declinedOrExhausted", true);
        row.putNull("stopReason");
        row.putObject("unreviewedPairsByStem").putArray(STEM).add(remaining);
        ObjectNode record = IntegritySupport.JSON.createObjectNode();
        record.put("schema", 1);
        record.putArray("decisions").add(row);
        Files.writeString(decisionFile, record.toString());
        Path[] findings = {a.sources().export().pScores(), a.sources().export().sScores(), human,
                a.log(), a.registry(), decisionFile};
        relock(a.lock(), a.seal(), findings);
        ArmKeyAccess.OpenedKey opened = ArmKeyAccess.open(a.root().resolve(CLASS_A_KEY_FILE), a.seal(),
                List.of(ArmKeyGateFixtures.binding(a.lock(), a.seal(), human)), Set.of(LockRecord.Kind.PRIMARY),
                a.root().resolve("decision-access.jsonl"), ArmKeyGateFixtures.OPENED,
                "bundle assembler declared-pending fixture");
        ClassAExport old = a.sources().export();
        ClassAExport export = new ClassAExport(old.packetDirectory(), old.pScores(), old.sScores(),
                old.hScores(), opened, old.sealFile(), old.lockFile(), old.exclusionsFileOrNull(),
                old.expectedExclusionsSha256OrNull());
        ClassASources sources = new ClassASources(a.sources().freezeFile(), a.sources().parametersFile(),
                a.sources().mappingFile(), a.sources().manifests(), export, a.log(), a.registry());
        return new AFixture(sources, a.pairs(), a.selected(), a.selections(), a.log(), a.registry(),
                a.lock(), a.seal(), a.root(), findings, a.pipelineFixture());
    }

    @Test void realDeclaredPendingDecisionSurvivesBundleAssembly() throws Exception {
        AFixture a = declaredPendingFixture();
        Setup setup = setupFrom(a, false);
        ClassAAssembly assembly = setup.assembled().classAAssemblies().getFirst();
        assertThat(assembly.declared().itemIds()).isNotEmpty();
        assertThat(assembly.digests().values()).containsKey(EXPANSION_DECISION_ROLE);
        for (var entry : assembly.facts().byCaseArm().entrySet()) {
            AdjudicatedFacts bundled = setup.assembled().bundle().evidence().byCaseArm().get(entry.getKey());
            assertThat(bundled).isEqualTo(entry.getValue());
            if (bundled.pendingQuestions() > 0)
                assertThat(bundled.unresolvedQuestions()).isEqualTo(bundled.pendingQuestions());
        }
        assertThat(setup.assembled().bundle().evidence().byCaseArm().values())
                .anySatisfy(fact -> assertThat(fact.pendingQuestions()).isPositive());
        setup.assembled().classB().primary().byCaseArm().forEach((id, fact) ->
                assertThat(setup.assembled().bundle().evidence().byCaseArm()).containsEntry(id, fact));
    }

    @Test void seamTriggeredWithoutCompletionIsReportedAsNotSupplied() throws Exception {
        Setup setup = setup(false);
        ClassAAssembly original = setup.assembled().classAAssemblies().getFirst();
        String remaining = setup.a().pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(setup.a().selected())).findFirst().orElseThrow();
        ExpansionObligation.Disclosure obligation = new ExpansionObligation.Disclosure(STEM,
                Set.of(setup.a().selected()), Map.of(STEM, 1), List.of(remaining), true, 1);
        ClassAAssembly changed = copyA(original, original.facts(), Map.of(STEM, obligation), original.digests());
        var assembled = parts(setup, changed, setup.assembled().classB());
        assertThat(assembled.expansionOutcomeByWorkflow()).doesNotContainKey(STEM);
        assertThat(assembled.expansionFindings()).containsKey(STEM);
        assertThat(assembled.expansionFindings().get(STEM).supplied()).isFalse();
    }


    // ---- Audit additions (release session): guards a mutant removed without any test failing ----

    private static SourceDigests digestRemoved(SourceDigests original, String role) {
        SortedMap<String, String> values = new TreeMap<>(original.values());
        values.remove(role);
        return new SourceDigests(values);
    }

    @Test void requiredCommonAndClassAManifestRolesCannotBeMissingAndPacketSidecarsAreUnclassified()
            throws Exception {
        Setup setup = setup(false);
        ClassAAssembly a = setup.assembled().classAAssemblies().getFirst();
        ClassBAssembly b = setup.assembled().classB();
        partsFailure(setup, copyA(a, a.facts(), a.expansionObligations(),
                digestRemoved(a.digests(), FREEZE_ROLE)), b)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("Missing common digest role");
        for (String role : List.of(CLASS_A_PRIMARY_DIGEST_ROLE, CLASS_A_PLAN_DIGEST_ROLE))
            partsFailure(setup, copyA(a, a.facts(), a.expansionObligations(),
                    digestRemoved(a.digests(), role)), b)
                    .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("Missing class-A manifest digest role");
        partsFailure(setup, copyA(a, a.facts(), a.expansionObligations(),
                digestChanged(a.digests(), "classA.packets.sha256", ZERO_DIGIT.repeat(64))), b)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("Unclassified digest role");
    }

    @Test void aTamperedSidecarAloneIsRefusedByTheBindingCheck() throws Exception {
        Setup setup = setup(false);
        Path sidecar = IntegritySupport.detached(setup.run().freeze());
        Files.writeString(sidecar, Files.readString(sidecar) + "tampered");
        assertThatThrownBy(() -> FrbBundleAssembler.requireBound(setup.assembled(), setup.run(), setup.frb()))
                .isInstanceOf(InvalidFrbBundle.class);
    }

    @Test void theClassBPrimaryCannotCarryAnExclusionsFile() throws Exception {
        Setup setup = setup(false);
        ResultsPipeline.FrbInput frb = setup.frb();
        String classBSeal = setup.assembled().digests().get(PRIMARY_SEAL_ROLE);
        List<ResultsPipeline.PrimaryExport> primaries = new java.util.ArrayList<>();
        for (ResultsPipeline.PrimaryExport export : frb.provenance().primaries()) {
            if (FreezeRecord.sha256(export.sealFile()).equals(classBSeal))
                primaries.add(new ResultsPipeline.PrimaryExport(export.accessLog(), export.sealFile(),
                        export.lockFile(), export.findingsDirectory(), export.lockFile(),
                        FreezeRecord.sha256(export.lockFile())));
            else primaries.add(export);
        }
        ResultsPipeline.FrbInput wrong = frbWith(frb, frb.bundle(), frb.reserveRecordFile(),
                frb.verification(), new ResultsPipeline.ProvenanceLogs(primaries, frb.provenance().reserves()));
        boundFailure(setup.assembled(), setup.run(), wrong)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("Class-B exclusions differ");
    }

    @Test void aDrawnReserveWithoutAnAssembledPartIsRefusedByTheBindingCheck() throws Exception {
        Setup setup = setup(true);
        ClassBAssembly b = setup.assembled().classB();
        ClassBAssembly noParts = copyB(b, b.primary(), List.of(), b.classBWorkflowNames(), b.digests());
        FrbBundleAssembler.Assembled assembled = parts(setup, setup.assembled().classAAssemblies().getFirst(), noParts);
        ResultsPipeline.FrbInput frb = frbWith(setup.frb(), assembled.bundle(), setup.frb().reserveRecordFile(),
                setup.frb().verification(), setup.frb().provenance());
        boundFailure(assembled, setup.run(), frb)
                .isInstanceOf(InvalidFrbBundle.class).hasMessageContaining("Drawn reserves lack assembled parts");
    }
}
