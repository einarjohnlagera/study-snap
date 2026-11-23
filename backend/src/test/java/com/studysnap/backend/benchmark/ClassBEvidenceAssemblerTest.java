package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.entity.LearnerLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.studysnap.backend.benchmark.ArmKeyAccess.open;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic files copied from FrbPipelineTest.Fixture and ReserveDecisionRecordTest's lock/record pattern. */
class ClassBEvidenceAssemblerTest {
    private static final String TIP = "STUDY_TIP";
    private static final String LUNA = "LUNA";
    private static final String PAID = "BASELINE_41";
    private static final String HUMAN = "HUMAN_OWNER";
    private static final String ASK_PACKET = "ASK_COMPANION.jsonl";
    private static final String TIP_PACKET = "STUDY_TIP.jsonl";
    private static final String OPENED = "2026-11-16T00:07:00Z";
    private static final String PRIMARY_MANIFEST = "classB.primary";
    private static final String RESERVE_MANIFEST = "classB.reserve";
    private static final Map<String, ClassBScoreFiles.ScorerKind> SCORERS =
            Map.of(HUMAN, ClassBScoreFiles.ScorerKind.HUMAN);
    @TempDir Path temp;

    private record Setup(ResultsPipeline.FrbRunInput run, ClassBSources sources) {}

    private Setup setup(boolean drawn) throws Exception {
        FrbPipelineTest pipeline = new FrbPipelineTest();
        pipeline.temp = temp;
        FrbPipelineTest.Fixture fixture = pipeline.fixture(drawn, 0);
        ResultsPipeline.FrbRunInput run = fixture.run();
        ResultsPipeline.FrbInput frb = fixture.frb();
        ResultsPipeline.ReserveVerification verification = frb.verification();
        Path primaryRoot = verification.sealFile().getParent();
        ArmKeyAccess.OpenedKey primaryKey = open(primaryRoot.resolve("arm-key.jsonl"),
                verification.sealFile(), List.of(ArmKeyGateFixtures.binding(verification.primaryLockFile(),
                        verification.sealFile(), verification.scoresFile())), Set.of(LockRecord.Kind.PRIMARY),
                primaryRoot.resolve("assembler-access.jsonl"), OPENED, "assembler test primary");
        ClassBExport primary = new ClassBExport(verification.packetDirectory(), verification.scoresFile(),
                SCORERS, primaryKey);
        Map<String, ClassBExport> reserves = new HashMap<>();
        if (drawn) {
            ResultsPipeline.ReserveExport reserve = frb.provenance().reserves().get(TIP);
            Path root = reserve.sealFile().getParent();
            Path scores = root.resolve("findings/scores.jsonl");
            ArmKeyAccess.OpenedKey reserveKey = open(root.resolve("arm-key.jsonl"), reserve.sealFile(),
                    List.of(ArmKeyGateFixtures.binding(reserve.lockForReserveOpening(), reserve.sealFile(), scores)),
                    Set.of(LockRecord.Kind.PRIMARY), root.resolve("assembler-access.jsonl"),
                    OPENED, "assembler test reserve");
            reserves.put(TIP, new ClassBExport(root.resolve("packets"), scores, SCORERS, reserveKey));
        }
        ClassBSources sources = new ClassBSources(run.freeze(), run.parameters(), run.mapping(),
                run.candidate().receipt(), run.manifests(), primary, reserves, frb.reserveRecordFile(),
                verification.sealFile(), verification.primaryLockFile());
        return new Setup(run, sources);
    }

    private static ClassBSources copy(ClassBSources old, Map<String, Path> manifests,
            ClassBExport primary, Map<String, ClassBExport> reserves, Path record) {
        return new ClassBSources(old.freezeFile(), old.parametersFile(), old.mappingFile(),
                old.candidateReceiptFile(), manifests, primary, reserves, record,
                old.primarySealFile(), old.primaryLockFile());
    }

    private static Set<String> reserveIds() {
        Set<String> ids = new java.util.LinkedHashSet<>();
        for (int index = 0; index < 30; index++) ids.add("reserve-" + index);
        return ids;
    }

    private static void resign(Path file) throws Exception {
        Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(file));
    }

    private static void repin(Setup fixture, String key) throws Exception {
        Path file = fixture.sources().manifests().get(key);
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(fixture.sources().freezeFile().toFile());
        ((ObjectNode) freeze.get("manifests")).put(file.toAbsolutePath().toString(), FreezeRecord.sha256(file));
        Files.writeString(fixture.sources().freezeFile(), freeze.toString());
        resign(fixture.sources().freezeFile());
    }

    private static void changeRecord(Path file, String field, String value) throws Exception {
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(file.toFile());
        row.put(field, value);
        Files.writeString(file, row.toString());
        resign(file);
    }

    private static String packetHash(Path directory) throws Exception {
        SourceDigests.Builder builder = new SourceDigests.Builder();
        builder.directory("packets", directory, () -> "parsed");
        return builder.build().values().get("packets");
    }

    private static Map<String, String> rawDigests(Map<String, Path> files, Path primaryPackets,
            Path reservePackets) throws Exception {
        SourceDigests.Builder builder = new SourceDigests.Builder();
        for (var entry : files.entrySet()) builder.file(entry.getKey(), entry.getValue(), () -> null);
        builder.directory("primary.packets", primaryPackets, () -> null);
        builder.directory("reserve.STUDY_TIP.packets", reservePackets, () -> null);
        return builder.build().values();
    }

    /** Copies ReserveDecisionRecordTest's synthetic export, score, seal, lock and gate sequence. */
    private ClassBExport reserveExport(Path root, Set<String> ids) throws Exception {
        List<ClassBCaseManifest.Case> cases = new ArrayList<>();
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (String id : ids) {
            cases.add(new ClassBCaseManifest.Case(id, ClassBCaseManifest.Workflow.STUDY_TIP,
                    "synthetic", true, LearnerLevel.COLLEGE,
                    List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                    null, null, null, null, null, null, null, null));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.BASELINE, "Answer one."));
            outputs.add(new ClassBBlindPacketExport.Output(id, ClassBScoreAggregator.Arm.CANDIDATE, "Answer two."));
        }
        Path packets = root.resolve("packets");
        Path key = root.resolve("arm-key.jsonl");
        ClassBBlindPacketExport.Export export = ClassBBlindPacketExport.export(cases, outputs, packets, key, 17L);
        Path scores = root.resolve("findings/scores.jsonl");
        Files.createDirectories(scores.getParent());
        List<String> rows = new ArrayList<>();
        for (ClassBScoreFiles.BlindItem item : export.manifest()) {
            ObjectNode row = IntegritySupport.JSON.createObjectNode();
            row.put("itemId", item.itemId()).put("workflow", TIP).put("round", 1)
                    .put("scorerId", HUMAN).put("scorerKind", "HUMAN")
                    .put("taskFulfilled", true).put("grounded", true).put("actionable", true)
                    .put("noCriticalMisleading", true).put("critical", false)
                    .put("borderline", false).put("adjudication", false).putNull("reasonCode");
            rows.add(row.toString());
        }
        Files.write(scores, rows);
        Path seal = ArmKeyGateFixtures.seal(root, key);
        Path lock = ArmKeyGateFixtures.lock(root, seal, scores, LockRecord.Kind.PRIMARY);
        ArmKeyAccess.OpenedKey opened = open(key, seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, scores)), Set.of(LockRecord.Kind.PRIMARY),
                root.resolve("access.jsonl"), OPENED, "synthetic reserve source");
        return new ClassBExport(packets, scores, SCORERS, opened);
    }

    private ClassBSources twoWorkflowSources() throws Exception {
        Setup fixture = setup(true);
        ClassBSources old = fixture.sources();
        Path parametersFile = old.parametersFile();
        ObjectNode parameters = (ObjectNode) IntegritySupport.JSON.readTree(parametersFile.toFile());
        ObjectNode workflows = (ObjectNode) parameters.get("workflows");
        workflows.set("ASK_COMPANION", workflows.get(TIP).deepCopy());
        ((ObjectNode) parameters.path("routes").path("INTERACTIVE_QUIZ")).set("workflows",
                IntegritySupport.JSON.valueToTree(List.of("NOTE_ADAPTIVE", TIP, "ASK_COMPANION")));
        ((ObjectNode) parameters.get("frb")).set("classBWorkflows",
                IntegritySupport.JSON.valueToTree(List.of(TIP, "ASK_COMPANION")));
        Files.writeString(parametersFile, parameters.toString());
        resign(parametersFile);
        Path primaryManifest = old.manifests().get(PRIMARY_MANIFEST);
        Files.writeString(primaryManifest, Files.readString(primaryManifest)
                + "{\"fixtureId\":\"ask-case\",\"workflow\":\"ASK_COMPANION\"}\n");
        Path reserveManifest = old.manifests().get(RESERVE_MANIFEST);
        Files.writeString(reserveManifest, Files.readString(reserveManifest)
                + "{\"fixtureId\":\"ask-reserve\",\"workflow\":\"ASK_COMPANION\"}\n");
        Path freezeFile = old.freezeFile();
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(freezeFile.toFile());
        freeze.put("parametersSha256", FreezeRecord.sha256(parametersFile));
        ObjectNode pinned = (ObjectNode) freeze.get("manifests");
        pinned.put(primaryManifest.toAbsolutePath().toString(), FreezeRecord.sha256(primaryManifest));
        pinned.put(reserveManifest.toAbsolutePath().toString(), FreezeRecord.sha256(reserveManifest));
        ((ObjectNode) freeze.path("arms").path("BASELINE_MINI")).set("workflows",
                IntegritySupport.JSON.valueToTree(List.of("NOTE_ADAPTIVE", "ASK_COMPANION")));
        ((ObjectNode) freeze.path("arms").path(LUNA)).set("workflows",
                IntegritySupport.JSON.valueToTree(List.of("NOTE_ADAPTIVE", TIP, "ASK_COMPANION")));
        ((ObjectNode) freeze.get("costForecastUsdPerDeliveredCase")).put("ASK_COMPANION", BigDecimal.ONE);
        ObjectNode suites = (ObjectNode) freeze.get("compatibilitySuites");
        String firstSuite = suites.fieldNames().next();
        suites.put(firstSuite, "ASK_COMPANION");
        Files.writeString(freezeFile, freeze.toString());
        resign(freezeFile);

        Path root = temp.resolve("two-workflow-primary");
        var tip = new ClassBCaseManifest.Case("tip", ClassBCaseManifest.Workflow.STUDY_TIP,
                "synthetic", false, LearnerLevel.COLLEGE,
                List.of(new QuickReviewIncorrectQuestionRequest("Question?", "Answer", "Explanation.")),
                null, null, null, null, null, null, null, null);
        var companion = new CompanionContent("Overview", "Strategy", "Mistakes", "Resources", List.of(), List.of());
        var ask = new ClassBCaseManifest.Case("ask-case", ClassBCaseManifest.Workflow.ASK_COMPANION,
                "synthetic", false, null, null, null, null, companion, List.of(),
                "Current question?", true, null, null);
        Path packets = root.resolve("packets");
        Path key = root.resolve("arm-key.jsonl");
        var export = ClassBBlindPacketExport.export(List.of(tip, ask), List.of(
                new ClassBBlindPacketExport.Output("tip", ClassBScoreAggregator.Arm.BASELINE, "Answer one."),
                new ClassBBlindPacketExport.Output("tip", ClassBScoreAggregator.Arm.CANDIDATE, "Answer two."),
                new ClassBBlindPacketExport.Output("ask-case", ClassBScoreAggregator.Arm.BASELINE, "Answer three."),
                new ClassBBlindPacketExport.Output("ask-case", ClassBScoreAggregator.Arm.CANDIDATE, "Answer four.")),
                packets, key, 17L);
        Path scores = root.resolve("findings/scores.jsonl");
        Files.createDirectories(scores.getParent());
        List<String> rows = new ArrayList<>();
        for (ClassBScoreAggregator.Key row : export.armKey()) {
            boolean critical = row.caseId().equals("tip") && row.arm() == ClassBScoreAggregator.Arm.CANDIDATE;
            ObjectNode score = IntegritySupport.JSON.createObjectNode();
            score.put("itemId", row.itemId()).put("workflow", row.workflow().name()).put("round", 1)
                    .put("scorerId", HUMAN).put("scorerKind", "HUMAN")
                    .put("taskFulfilled", true).put("grounded", true).put("actionable", true)
                    .put("noCriticalMisleading", !critical).put("critical", critical)
                    .put("borderline", false).put("adjudication", false);
            if (critical) score.put("reasonCode", "CRITICAL_MISLEADING");
            else score.putNull("reasonCode");
            rows.add(score.toString());
        }
        Files.write(scores, rows);
        Path seal = ArmKeyGateFixtures.seal(root, key);
        Path lockFile = ArmKeyGateFixtures.lock(root, seal, scores, LockRecord.Kind.PRIMARY);
        LockRecord lock = LockRecord.load(lockFile);
        var validated = ClassBScoreFiles.validate(scores,
                ReserveDecisionRecord.manifestFromPackets(packets), SCORERS);
        var decisions = ReserveDecisionRecord.compute(packets, validated,
                EvidenceMapping.load(old.mappingFile()), Set.of(TIP, "ASK_COMPANION"));
        Path record = temp.resolve("two-workflow-decision.json");
        ReserveDecisionRecord.create(record, decisions, lock, lockFile, scores, packets, seal,
                "2026-11-16T00:02:00Z");
        ArmKeyAccess.OpenedKey opened = open(key, seal,
                List.of(ArmKeyGateFixtures.binding(lockFile, seal, scores)), Set.of(LockRecord.Kind.PRIMARY),
                root.resolve("access.jsonl"), OPENED, "two-workflow primary");
        return new ClassBSources(freezeFile, parametersFile, old.mappingFile(), old.candidateReceiptFile(),
                old.manifests(), new ClassBExport(packets, scores, SCORERS, opened),
                old.reservesByWorkflow(), record, seal, lockFile);
    }

    @Test void realFilesYieldDirectConverterFactsAndEveryRequiredDigest() throws Exception {
        Setup fixture = setup(true);
        ClassBAssembly assembled = ClassBEvidenceAssembler.assemble(fixture.sources());
        ClassBExport primary = fixture.sources().primary();
        EvidenceMapping mapping = EvidenceMapping.load(fixture.sources().mappingFile());
        var labels = new ClassBAdjudicatedConverter.ArmLabels(LUNA, ignored -> PAID);
        var primaryScores = ClassBScoreFiles.validate(primary.scoresFile(),
                ReserveDecisionRecord.manifestFromPackets(primary.packetDirectory()), SCORERS);
        AdjudicatedLoaded expectedPrimary = ClassBAdjudicatedConverter.classB(primary.key(),
                ClassBAdjudicatedConverter.ExportKind.PRIMARY, primaryScores, mapping,
                PairMap.fromPackets(primary.packetDirectory()), Set.of(TIP), Set.of("tip"), labels);
        assertThat(assembled.primary().byCaseArm()).isEqualTo(expectedPrimary.byCaseArm());
        ClassBExport reserve = fixture.sources().reservesByWorkflow().get(TIP);
        var reserveScores = ClassBScoreFiles.validate(reserve.scoresFile(),
                ReserveDecisionRecord.manifestFromPackets(reserve.packetDirectory()), SCORERS);
        AdjudicatedLoaded expectedReserve = ClassBAdjudicatedConverter.classB(reserve.key(),
                ClassBAdjudicatedConverter.ExportKind.RESERVE, reserveScores, mapping,
                PairMap.fromPackets(reserve.packetDirectory()), Set.of(TIP), reserveIds(), labels);
        assertThat(assembled.reserveParts()).hasSize(1);
        assertThat(assembled.reserveParts().getFirst().facts().byCaseArm()).isEqualTo(expectedReserve.byCaseArm());
        assertThat(assembled.reserveParts().getFirst().frozenReserveCaseIds()).isEqualTo(reserveIds());
        assertThat(assembled.digests().values().keySet()).contains("freeze", "parameters", "mapping",
                "candidate.receipt", "manifest.classB.primary", "manifest.classB.reserve",
                "primary.packets", "primary.scores", "reserve.record", "reserve.STUDY_TIP.packets",
                "reserve.STUDY_TIP.scores");
        assertThat(assembled.digests().values()).containsEntry("freeze", FreezeRecord.sha256(fixture.sources().freezeFile()))
                .containsEntry("parameters", FreezeRecord.sha256(fixture.sources().parametersFile()))
                .containsEntry("mapping", FreezeRecord.sha256(fixture.sources().mappingFile()))
                .containsEntry("candidate.receipt", FreezeRecord.sha256(fixture.sources().candidateReceiptFile()))
                .containsEntry("manifest.classB.primary",
                        FreezeRecord.sha256(fixture.sources().manifests().get(PRIMARY_MANIFEST)))
                .containsEntry("manifest.classB.reserve",
                        FreezeRecord.sha256(fixture.sources().manifests().get(RESERVE_MANIFEST)))
                .containsEntry("primary.scores", FreezeRecord.sha256(primary.scoresFile()))
                .containsEntry("reserve.record", FreezeRecord.sha256(fixture.sources().reserveRecordFile()))
                .containsEntry("reserve.STUDY_TIP.scores", FreezeRecord.sha256(reserve.scoresFile()))
                .containsEntry("primary.packets", packetHash(primary.packetDirectory()))
                .containsEntry("reserve.STUDY_TIP.packets", packetHash(reserve.packetDirectory()));
        assertThat(assembled.digests().values().toString())
                .doesNotContain("tip", "reserve-0", LUNA, PAID, "Answer one.", temp.toString());
    }

    @Test void frozenIdsAgreeWithPipelineAndRejectMissingOrExtraPrimaryCase() throws Exception {
        Setup fixture = setup(true);
        ClassBAssembly assembled = ClassBEvidenceAssembler.assemble(fixture.sources());
        FreezeRecord freeze = FreezeRecord.load(fixture.run().freeze());
        assertThat(ResultsPipeline.loadCases(fixture.run().manifests(), freeze).keySet())
                .containsExactlyInAnyOrder("a-one", "a-two", "tip");
        assertThat(ResultsPipeline.loadReserveCases(fixture.run().manifests(), freeze).keySet())
                .containsExactlyInAnyOrderElementsOf(reserveIds());
        assertThat(assembled.primary().requestedQuestionsByCaseId().keySet()).containsExactly("tip");
        assertThat(assembled.reserveParts().getFirst().frozenReserveCaseIds()).isEqualTo(reserveIds());

        Path manifest = fixture.sources().manifests().get(PRIMARY_MANIFEST);
        Files.writeString(manifest, Files.readString(manifest)
                + "{\"fixtureId\":\"tip-added\",\"workflow\":\"STUDY_TIP\"}\n");
        repin(fixture, PRIMARY_MANIFEST);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidEvidence.class);
    }

    @Test void extraPrimaryExportCaseIsRejectedAgainstSmallerPinnedManifest() throws Exception {
        FrbPipelineTest pipeline = new FrbPipelineTest();
        pipeline.temp = temp;
        FrbPipelineTest.Fixture fixture = pipeline.fixture(false, 0, 1, false, false, false, 2);
        ResultsPipeline.FrbInput frb = fixture.frb();
        var verification = frb.verification();
        Path root = verification.sealFile().getParent();
        ArmKeyAccess.OpenedKey key = open(root.resolve("arm-key.jsonl"), verification.sealFile(),
                List.of(ArmKeyGateFixtures.binding(verification.primaryLockFile(), verification.sealFile(),
                        verification.scoresFile())), Set.of(LockRecord.Kind.PRIMARY),
                root.resolve("assembler-access.jsonl"), OPENED, "extra primary test");
        ClassBSources sources = new ClassBSources(fixture.run().freeze(), fixture.run().parameters(),
                fixture.run().mapping(), fixture.run().candidate().receipt(), fixture.run().manifests(),
                new ClassBExport(verification.packetDirectory(), verification.scoresFile(), SCORERS, key),
                Map.of(), frb.reserveRecordFile(), verification.sealFile(), verification.primaryLockFile());
        Setup setup = new Setup(fixture.run(), sources);
        Path manifest = sources.manifests().get(PRIMARY_MANIFEST);
        Files.writeString(manifest, Files.readAllLines(manifest).getFirst() + "\n");
        repin(setup, PRIMARY_MANIFEST);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(sources)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void manifestPinAndRequiredKeyFailClosed() throws Exception {
        Setup fixture = setup(false);
        Path primary = fixture.run().manifests().get(PRIMARY_MANIFEST);
        String original = Files.readString(primary);
        Files.writeString(primary, original + "\n");
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassBAssembly.class);
        Files.writeString(primary, original);
        Map<String, Path> missing = new HashMap<>(fixture.sources().manifests());
        missing.remove(RESERVE_MANIFEST);
        ClassBSources without = copy(fixture.sources(), missing, fixture.sources().primary(), Map.of(),
                fixture.sources().reserveRecordFile());
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(without))
                .isInstanceOf(InvalidClassBAssembly.class);
        Files.writeString(primary, original + "\n");
        repin(fixture, PRIMARY_MANIFEST);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassBAssembly.class);
    }

    @Test void unpinnedManifestPathAndExtraReserveCaseAreRejected() throws Exception {
        Setup fixture = setup(true);
        Path original = fixture.sources().manifests().get(PRIMARY_MANIFEST);
        Path unpinned = temp.resolve("unregistered-primary.jsonl");
        Files.copy(original, unpinned);
        Map<String, Path> paths = new HashMap<>(fixture.sources().manifests());
        paths.put(PRIMARY_MANIFEST, unpinned);
        ClassBSources changed = copy(fixture.sources(), paths, fixture.sources().primary(),
                fixture.sources().reservesByWorkflow(), fixture.sources().reserveRecordFile());
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(changed))
                .isInstanceOf(InvalidClassBAssembly.class);
        Path reserve = fixture.sources().manifests().get(RESERVE_MANIFEST);
        Files.writeString(reserve, Files.readString(reserve)
                + "{\"fixtureId\":\"reserve-added\",\"workflow\":\"STUDY_TIP\"}\n");
        repin(fixture, RESERVE_MANIFEST);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidEvidence.class);
    }

    @Test void decisionAndMissingReserveHandoffAreVerified() throws Exception {
        Setup fixture = setup(true);
        ClassBSources noReserve = copy(fixture.sources(), fixture.sources().manifests(), fixture.sources().primary(),
                Map.of(), fixture.sources().reserveRecordFile());
        ClassBAssembly assembled = ClassBEvidenceAssembler.assemble(noReserve);
        assertThat(assembled.reserveParts()).isEmpty();
        AdjudicatedLoaded emptyA = new AdjudicatedLoaded(Map.of(), Set.of(), Map.of());
        FrbEvidenceBundle bundle = FrbEvidenceBundle.assemble(List.of(emptyA), assembled.primary(),
                assembled.reserveParts(), Set.of(), Set.of("tip"), assembled.decision(),
                assembled.classBWorkflowNames());
        assertThat(bundle.workflowsAwaitingReserve()).containsExactly(TIP);

        Path record = fixture.sources().reserveRecordFile();
        Files.delete(record);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(noReserve))
                .isInstanceOf(InvalidClassBAssembly.class);
    }

    @Test void sourceForUndrawnWorkflowIsRejected() throws Exception {
        Setup undrawn = setup(false);
        ClassBExport reserve = reserveExport(temp.resolve("other-reserve"), reserveIds());
        ClassBSources withSource = copy(undrawn.sources(), undrawn.sources().manifests(),
                undrawn.sources().primary(), Map.of(TIP, reserve),
                undrawn.sources().reserveRecordFile());
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(withSource))
                .isInstanceOf(InvalidClassBAssembly.class);
    }

    @Test void drawnReserveSourceMustCoverEveryFrozenReserveCase() throws Exception {
        Setup fixture = setup(true);
        Set<String> fewer = new java.util.LinkedHashSet<>(reserveIds());
        fewer.remove("reserve-29");
        ClassBExport shortened = reserveExport(temp.resolve("short-reserve"), fewer);
        ClassBSources sources = copy(fixture.sources(), fixture.sources().manifests(),
                fixture.sources().primary(), Map.of(TIP, shortened), fixture.sources().reserveRecordFile());
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(sources)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void twoWorkflowsUseSeparateFrozenBaselinesAndOnlyTheDrawnReserve() throws Exception {
        ClassBAssembly assembly = ClassBEvidenceAssembler.assemble(twoWorkflowSources());
        assertThat(assembly.classBWorkflowNames()).containsExactlyInAnyOrder(TIP, "ASK_COMPANION");
        assertThat(assembly.primary().get("tip", PAID)).isNotNull();
        assertThat(assembly.primary().get("ask-case", "BASELINE_MINI")).isNotNull();
        assertThat(assembly.reserveParts()).extracting(FrbEvidenceBundle.ReservePart::workflow)
                .containsExactly(TIP);
        assertThat(assembly.decision().reserveDrawnByWorkflow()).containsEntry(TIP, true)
                .containsEntry("ASK_COMPANION", false);
    }

    @Test void wrongReserveKindUnknownWorkflowAndDuplicateSourceAreRejected() throws Exception {
        Setup drawn = setup(true);
        ClassBExport real = drawn.sources().reservesByWorkflow().get(TIP);
        ClassBExport wrongKind = new ClassBExport(real.packetDirectory(), real.scoresFile(), SCORERS,
                drawn.sources().primary().key());
        ClassBSources wrong = copy(drawn.sources(), drawn.sources().manifests(), drawn.sources().primary(),
                Map.of(TIP, wrongKind), drawn.sources().reserveRecordFile());
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(wrong)).isInstanceOf(InvalidEvidence.class);
        ClassBSources unknown = copy(drawn.sources(), drawn.sources().manifests(), drawn.sources().primary(),
                Map.of("OTHER_WORKFLOW", real), drawn.sources().reserveRecordFile());
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(unknown))
                .isInstanceOf(InvalidClassBAssembly.class);
        ClassBSources duplicate = copy(drawn.sources(), drawn.sources().manifests(), drawn.sources().primary(),
                Map.of(TIP, drawn.sources().primary()), drawn.sources().reserveRecordFile());
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(duplicate))
                .isInstanceOf(InvalidClassBAssembly.class);
    }

    @Test void digestsAreStableAndContentFree() throws Exception {
        Setup fixture = setup(true);
        SourceDigests first = ClassBEvidenceAssembler.assemble(fixture.sources()).digests();
        SourceDigests second = ClassBEvidenceAssembler.assemble(fixture.sources()).digests();
        assertThat(second).isEqualTo(first);
        assertThat(first.values().values()).allMatch(value -> value.matches("[0-9a-f]{64}"));
    }

    @Test void reserveDecisionRejectsChangedChoiceHashesAndTime() throws Exception {
        Setup fixture = setup(true);
        Path record = fixture.sources().reserveRecordFile();
        byte[] original = Files.readAllBytes(record);
        ObjectNode flipped = (ObjectNode) IntegritySupport.JSON.readTree(original);
        ((ObjectNode) flipped.withArray("decisions").get(0)).put("reserveDrawn", false);
        Files.writeString(record, flipped.toString());
        resign(record);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidReserveRecord.class);
        Files.write(record, original);
        resign(record);
        changeRecord(record, "lockSha256", "0".repeat(64));
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidReserveRecord.class);
        Files.write(record, original);
        resign(record);
        changeRecord(record, "findingsSha256", "0".repeat(64));
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidReserveRecord.class);
        Files.write(record, original);
        resign(record);
        changeRecord(record, "createdAtUtc", ArmKeyGateFixtures.SEALED);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidReserveRecord.class);
    }

    @Test void parameterAndMappingPinsCannotDrift() throws Exception {
        Setup fixture = setup(false);
        Path file = fixture.sources().freezeFile();
        byte[] original = Files.readAllBytes(file);
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(original);
        freeze.put("parametersSha256", "0".repeat(64));
        Files.writeString(file, freeze.toString());
        resign(file);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassBAssembly.class);
        Files.write(file, original);
        resign(file);
        freeze = (ObjectNode) IntegritySupport.JSON.readTree(original);
        freeze.put("evidenceMappingSha256", "0".repeat(64));
        Files.writeString(file, freeze.toString());
        resign(file);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassBAssembly.class);
    }

    @Test void baselineAndCandidateLabelsComeFromFreezeAndReceipt() throws Exception {
        Setup fixture = setup(false);
        assertThat(ClassBEvidenceAssembler.assemble(fixture.sources()).primary().get("tip", PAID)).isNotNull();
        Path freezeFile = fixture.sources().freezeFile();
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(freezeFile.toFile());
        ((ObjectNode) freeze.path("arms").path(PAID)).set("workflows",
                IntegritySupport.JSON.valueToTree(List.of("NOTE_ADAPTIVE")));
        ((ObjectNode) freeze.path("arms").path("BASELINE_MINI")).set("workflows",
                IntegritySupport.JSON.valueToTree(List.of("NOTE_ADAPTIVE", TIP)));
        Files.writeString(freezeFile, freeze.toString());
        resign(freezeFile);
        assertThat(ClassBEvidenceAssembler.assemble(fixture.sources()).primary().get("tip", "BASELINE_MINI"))
                .isNotNull();
        ((ObjectNode) freeze.path("arms").path(PAID)).set("workflows",
                IntegritySupport.JSON.valueToTree(List.of(TIP)));
        ((ObjectNode) freeze.path("arms").path("BASELINE_MINI")).set("workflows",
                IntegritySupport.JSON.valueToTree(List.of("NOTE_ADAPTIVE")));
        ((ObjectNode) freeze.path("arms")).set("SOL",
                IntegritySupport.JSON.valueToTree(new FreezeRecord.Arm("synthetic-model", List.of(TIP))));
        Files.writeString(freezeFile, freeze.toString());
        resign(freezeFile);
        Path receipt = fixture.sources().candidateReceiptFile();
        Path solReceipt = temp.resolve("sol-receipt.json");
        Files.writeString(solReceipt, Files.readString(receipt).replace("\"arm\":\"LUNA\"",
                "\"arm\":\"SOL\""));
        ClassBSources changed = new ClassBSources(fixture.sources().freezeFile(), fixture.sources().parametersFile(),
                fixture.sources().mappingFile(), solReceipt, fixture.sources().manifests(),
                fixture.sources().primary(), fixture.sources().reservesByWorkflow(),
                fixture.sources().reserveRecordFile(), fixture.sources().primarySealFile(),
                fixture.sources().primaryLockFile());
        assertThat(ClassBEvidenceAssembler.assemble(changed).primary().get("tip", "SOL")).isNotNull();
    }

    @Test void scoreValidationFailuresPropagateAsNamedFailures() throws Exception {
        Setup fixture = setup(false);
        Path file = fixture.sources().primary().scoresFile();
        List<String> rows = Files.readAllLines(file);
        Files.writeString(file, rows.getFirst() + "\n" + rows.getFirst() + "\n");
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(ClassBScoreFiles.InvalidScore.class);
        Files.writeString(file, rows.getFirst().replace("blind-00001", "blind-99999") + "\n");
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(ClassBScoreFiles.InvalidScore.class);
        Files.writeString(file, "[" + rows.getFirst().substring(1) + "\n");
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(ClassBScoreFiles.InvalidScore.class);
        Files.writeString(file, "");
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(ClassBScoreFiles.MissingHumanCoverage.class);
        Files.write(file, rows);
        ClassBExport original = fixture.sources().primary();
        ClassBExport wrongRegistry = new ClassBExport(original.packetDirectory(), original.scoresFile(),
                Map.of("MODEL_OWNER", ClassBScoreFiles.ScorerKind.MODEL), original.key());
        ClassBSources changed = copy(fixture.sources(), fixture.sources().manifests(), wrongRegistry,
                Map.of(), fixture.sources().reserveRecordFile());
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(changed))
                .isInstanceOf(ClassBScoreFiles.InvalidScore.class);
    }

    @Test void packetDigestIncludesSortedNamesAndEveryFile() throws Exception {
        Path first = temp.resolve("first");
        Path second = temp.resolve("second");
        Files.createDirectories(first);
        Files.createDirectories(second);
        Files.writeString(first.resolve(TIP_PACKET), "b");
        Files.writeString(first.resolve(ASK_PACKET), "a");
        Files.writeString(second.resolve(ASK_PACKET), "a");
        Files.writeString(second.resolve(TIP_PACKET), "b");
        String baseline = packetHash(first);
        assertThat(packetHash(second)).isEqualTo(baseline);
        List<Path> forward = List.of(first, first.resolve(ASK_PACKET), first.resolve(TIP_PACKET));
        List<Path> reversed = List.of(first.resolve(TIP_PACKET), first.resolve(ASK_PACKET), first);
        assertThat(SourceDigests.Builder.directoryHash(first, reversed))
                .isEqualTo(SourceDigests.Builder.directoryHash(first, forward));
        Files.writeString(first.resolve("INTERVIEW_CRITIQUE.jsonl"), "c");
        assertThat(packetHash(first)).isNotEqualTo(baseline);
        Files.delete(first.resolve("INTERVIEW_CRITIQUE.jsonl"));
        assertThat(packetHash(first)).isEqualTo(baseline);
        Files.move(first.resolve(TIP_PACKET), first.resolve("COLLECTION_COMPANION.jsonl"));
        assertThat(packetHash(first)).isNotEqualTo(baseline);
        Files.move(first.resolve("COLLECTION_COMPANION.jsonl"), first.resolve(TIP_PACKET));
        Files.writeString(first.resolve(TIP_PACKET), "B");
        assertThat(packetHash(first)).isNotEqualTo(baseline);
        Files.writeString(first.resolve("unexpected.txt"), "private bytes must remain unread");
        assertThatThrownBy(() -> packetHash(first)).isInstanceOf(InvalidClassBAssembly.class);
    }

    @Test void singleFileByteChangeChangesOnlyItsDigestRole() throws Exception {
        Path a = temp.resolve("digest-a.txt");
        Path b = temp.resolve("digest-b.txt");
        Files.writeString(a, "alpha");
        Files.writeString(b, "bravo");
        SourceDigests.Builder first = new SourceDigests.Builder();
        first.file("a", a, () -> Files.readString(a));
        first.file("b", b, () -> Files.readString(b));
        Map<String, String> before = first.build().values();
        Files.writeString(b, "bravO");
        SourceDigests.Builder second = new SourceDigests.Builder();
        second.file("a", a, () -> Files.readString(a));
        second.file("b", b, () -> Files.readString(b));
        Map<String, String> after = second.build().values();
        assertThat(after.get("a")).isEqualTo(before.get("a"));
        assertThat(after.get("b")).isNotEqualTo(before.get("b"));
    }

    @Test void everySourceRoleChangesOnlyWithItsOwnBytes() throws Exception {
        Setup fixture = setup(true);
        ClassBSources sources = fixture.sources();
        ClassBExport reserve = sources.reservesByWorkflow().get(TIP);
        Map<String, Path> files = Map.ofEntries(
                Map.entry("freeze", sources.freezeFile()),
                Map.entry("parameters", sources.parametersFile()),
                Map.entry("mapping", sources.mappingFile()),
                Map.entry("candidate.receipt", sources.candidateReceiptFile()),
                Map.entry("manifest.classB.primary", sources.manifests().get(PRIMARY_MANIFEST)),
                Map.entry("manifest.classB.reserve", sources.manifests().get(RESERVE_MANIFEST)),
                Map.entry("primary.scores", sources.primary().scoresFile()),
                Map.entry("reserve.record", sources.reserveRecordFile()),
                Map.entry("reserve.STUDY_TIP.scores", reserve.scoresFile()));
        Path primaryPackets = sources.primary().packetDirectory();
        Path reservePackets = reserve.packetDirectory();
        Map<String, String> baseline = rawDigests(files, primaryPackets, reservePackets);
        Map<String, Path> changed = new HashMap<>(files);
        changed.put("primary.packets", primaryPackets.resolve(TIP_PACKET));
        changed.put("reserve.STUDY_TIP.packets", reservePackets.resolve(TIP_PACKET));
        for (var entry : changed.entrySet()) {
            Path file = entry.getValue();
            byte[] original = Files.readAllBytes(file);
            byte[] altered = original.clone();
            altered[0] ^= 1;
            Files.write(file, altered);
            Map<String, String> after = rawDigests(files, primaryPackets, reservePackets);
            Set<String> difference = baseline.keySet().stream()
                    .filter(role -> !baseline.get(role).equals(after.get(role)))
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(difference).containsExactly(entry.getKey());
            Files.write(file, original);
        }
    }

    private static void repinSources(ClassBSources sources, String key) throws Exception {
        Path file = sources.manifests().get(key);
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(sources.freezeFile().toFile());
        ((ObjectNode) freeze.get("manifests")).put(file.toAbsolutePath().toString(), FreezeRecord.sha256(file));
        Files.writeString(sources.freezeFile(), freeze.toString());
        resign(sources.freezeFile());
    }

    @Test void digestBuilderRefusesChangesDuringAndBetweenLoadsAndRecordsDetachedDigests() throws Exception {
        Path file = temp.resolve("digest-source.txt");
        Files.writeString(file, "one");
        SourceDigests.Builder during = new SourceDigests.Builder();
        assertThatThrownBy(() -> during.file("x", file, () -> {
            Files.writeString(file, "two");
            return null;
        })).isInstanceOf(InvalidClassBAssembly.class);
        Path signed = temp.resolve("digest-signed.txt");
        Files.writeString(signed, "signed");
        resign(signed);
        SourceDigests.Builder recorded = new SourceDigests.Builder();
        recorded.file("signed", signed, () -> null);
        assertThat(recorded.build().values()).containsEntry("signed", FreezeRecord.sha256(signed))
                .containsEntry("signed.sha256", FreezeRecord.sha256(IntegritySupport.detached(signed)));
        SourceDigests.Builder detachedChange = new SourceDigests.Builder();
        assertThatThrownBy(() -> detachedChange.file("signed", signed, () -> {
            Files.writeString(IntegritySupport.detached(signed), "0".repeat(64));
            return null;
        })).isInstanceOf(InvalidClassBAssembly.class);
        Path again = temp.resolve("digest-again.txt");
        Files.writeString(again, "first");
        SourceDigests.Builder between = new SourceDigests.Builder();
        between.file("again", again, () -> null);
        Files.writeString(again, "second");
        assertThatThrownBy(() -> between.file("again", again, () -> null))
                .isInstanceOf(InvalidClassBAssembly.class);
        Path packets = temp.resolve("digest-packets");
        Files.createDirectories(packets);
        Path packet = packets.resolve("STUDY_TIP.jsonl");
        Files.writeString(packet, "a");
        SourceDigests.Builder directory = new SourceDigests.Builder();
        assertThatThrownBy(() -> directory.directory("packets", packets, () -> {
            Files.writeString(packet, "b");
            return null;
        })).isInstanceOf(InvalidClassBAssembly.class);
        Path link = packets.resolve("ASK_COMPANION.jsonl");
        Files.createSymbolicLink(link, packet);
        assertThatThrownBy(() -> SourceDigests.Builder.directoryHash(packets, List.of(packet, link)))
                .isInstanceOf(InvalidClassBAssembly.class);
        assertThatThrownBy(() -> SourceDigests.Builder.directoryHash(packets, List.of(packets)))
                .isInstanceOf(InvalidClassBAssembly.class);
    }

    @Test void reserveScoresNeedFullHumanCoverageAndEachDuplicateSourceKindIsRefused() throws Exception {
        Setup drawn = setup(true);
        ClassBExport real = drawn.sources().reservesByWorkflow().get(TIP);
        Path original = real.scoresFile();
        List<String> rows = Files.readAllLines(original);
        Files.writeString(original, "");
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(drawn.sources()))
                .isInstanceOf(InvalidEvidence.class);
        Files.write(original, rows);
        ClassBExport packetsReused = new ClassBExport(drawn.sources().primary().packetDirectory(),
                real.scoresFile(), SCORERS, real.key());
        ClassBSources first = copy(drawn.sources(), drawn.sources().manifests(), drawn.sources().primary(),
                Map.of(TIP, packetsReused), drawn.sources().reserveRecordFile());
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(first))
                .isInstanceOf(InvalidClassBAssembly.class).hasMessageContaining("Duplicate");
        ClassBExport scoresReused = new ClassBExport(real.packetDirectory(),
                drawn.sources().primary().scoresFile(), SCORERS, real.key());
        ClassBSources second = copy(drawn.sources(), drawn.sources().manifests(), drawn.sources().primary(),
                Map.of(TIP, scoresReused), drawn.sources().reserveRecordFile());
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(second))
                .isInstanceOf(InvalidClassBAssembly.class).hasMessageContaining("Duplicate");
    }

    @Test void manifestsMustCoverFrozenWorkflowsWithoutDuplicateOrUnknownRows() throws Exception {
        ClassBSources two = twoWorkflowSources();
        Path primary = two.manifests().get(PRIMARY_MANIFEST);
        String original = Files.readString(primary);
        Files.writeString(primary, original.lines().filter(line -> !line.contains("ASK_COMPANION"))
                .collect(java.util.stream.Collectors.joining("\n")) + "\n");
        repinSources(two, PRIMARY_MANIFEST);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(two))
                .isInstanceOf(InvalidClassBAssembly.class).hasMessageContaining("differ");
        Files.writeString(primary, original + original.lines().findFirst().orElseThrow() + "\n");
        repinSources(two, PRIMARY_MANIFEST);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(two))
                .isInstanceOf(InvalidClassBAssembly.class).hasMessageContaining("duplicate");
    }

    @Test void aReserveManifestNamingAnUnfrozenWorkflowIsRefused() throws Exception {
        Setup single = setup(false);
        Path reserve = single.sources().manifests().get(RESERVE_MANIFEST);
        Files.writeString(reserve, Files.readString(reserve)
                + "{\"fixtureId\":\"stray-reserve\",\"workflow\":\"COLLECTION_COMPANION\"}\n");
        repin(single, RESERVE_MANIFEST);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(single.sources()))
                .isInstanceOf(InvalidClassBAssembly.class);
    }

    @Test void candidateAndBothBaselineArmsMustExistInTheFreeze() throws Exception {
        Setup fixture = setup(false);
        Path ghostReceipt = temp.resolve("ghost-receipt.json");
        Files.writeString(ghostReceipt, Files.readString(fixture.sources().candidateReceiptFile())
                .replace("\"arm\":\"LUNA\"", "\"arm\":\"GHOST\""));
        ClassBSources ghost = new ClassBSources(fixture.sources().freezeFile(), fixture.sources().parametersFile(),
                fixture.sources().mappingFile(), ghostReceipt, fixture.sources().manifests(),
                fixture.sources().primary(), fixture.sources().reservesByWorkflow(),
                fixture.sources().reserveRecordFile(), fixture.sources().primarySealFile(),
                fixture.sources().primaryLockFile());
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(ghost))
                .isInstanceOf(InvalidClassBAssembly.class).hasMessageContaining("absent from freeze");
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(fixture.sources().freezeFile().toFile());
        ((ObjectNode) freeze.get("arms")).remove("BASELINE_MINI");
        Files.writeString(fixture.sources().freezeFile(), freeze.toString());
        resign(fixture.sources().freezeFile());
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void aCaseInBothThePrimaryAndTheReserveManifestIsRefused() throws Exception {
        Setup single = setup(false);
        Path primary = single.sources().manifests().get(PRIMARY_MANIFEST);
        String firstId = IntegritySupport.JSON.readTree(Files.readAllLines(primary).getFirst())
                .path("fixtureId").asText();
        Path reserve = single.sources().manifests().get(RESERVE_MANIFEST);
        Files.writeString(reserve, Files.readString(reserve)
                + "{\"fixtureId\":\"" + firstId + "\",\"workflow\":\"" + TIP + "\"}\n");
        repin(single, RESERVE_MANIFEST);
        assertThatThrownBy(() -> ClassBEvidenceAssembler.assemble(single.sources()))
                .isInstanceOf(InvalidClassBAssembly.class).hasMessageContaining("primary and reserve");
    }
}
