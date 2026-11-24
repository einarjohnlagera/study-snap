package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic files use FrbPipelineTest's freeze/manifest helpers and the real class-A exporter and gate. */
class ClassAEvidenceAssemblerTest {
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
    private static final String PENDING = "PENDING_REVIEW";
    @TempDir Path temp;

    private record Fixture(ClassASources sources, PairMap pairs, String selected, Set<String> selections,
            Path log, Path registry, Path lock, Path seal, Path root, Path[] findings) {}

    private Fixture fixture(boolean humanOnSelected, boolean mandatory) throws Exception {
        return fixture(humanOnSelected, mandatory, false, false);
    }

    private Fixture fixture(boolean humanOnSelected, boolean mandatory, boolean excluded) throws Exception {
        return fixture(humanOnSelected, mandatory, excluded, false);
    }

    private Fixture fixture(boolean humanOnSelected, boolean mandatory, boolean excluded,
            boolean secondStem) throws Exception {
        Path base = Files.createTempDirectory(temp, "fixture-");
        FrbPipelineTest pipeline = new FrbPipelineTest();
        pipeline.temp = base;
        ResultsPipeline.FrbRunInput run = pipeline.fixture(false, 0, 2, false).run();
        if (mandatory || excluded) {
            Path manifest = run.manifests().get(PRIMARY_MANIFEST);
            String first = Files.readAllLines(manifest).getFirst();
            ObjectNode additional = (ObjectNode) IntegritySupport.JSON.readTree(first);
            additional.put("caseId", CASE_THREE);
            Files.writeString(manifest, Files.readString(manifest) + additional + "\n");
        }
        if (secondStem) {
            Path manifest = run.manifests().get(PRIMARY_MANIFEST);
            List<String> lines = Files.readAllLines(manifest);
            ObjectNode second = (ObjectNode) IntegritySupport.JSON.readTree(lines.get(1));
            second.put("workflow", PLAN_STEM);
            Files.writeString(manifest, lines.getFirst() + "\n" + second + "\n");
        }
        Path plan = base.resolve("spot-plan.json");
        Files.writeString(plan, "{\"stems\":[{\"stem\":\"" + STEM + "\",\"count\":1}"
                + (secondStem ? ", {\"stem\":\"" + PLAN_STEM + "\",\"count\":1}" : "") + "]}");
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.freeze()));
        ((ObjectNode) freeze.get("manifests")).put(plan.toAbsolutePath().toString(), FreezeRecord.sha256(plan));
        if (mandatory || excluded || secondStem) {
            Path manifest = run.manifests().get(PRIMARY_MANIFEST);
            ((ObjectNode) freeze.get("manifests")).put(manifest.toAbsolutePath().toString(),
                    FreezeRecord.sha256(manifest));
        }
        if (secondStem) {
            ((ArrayNode) freeze.path("arms").path(MINI).path("workflows")).add(PLAN_STEM);
            ((ObjectNode) freeze.path("costForecastUsdPerDeliveredCase")).put(PLAN_STEM, 1);
            ((ObjectNode) freeze.path("compatibilitySuites")).put("SyntheticSuite1", PLAN_STEM);
        }
        Files.writeString(run.freeze(), freeze.toString());
        detached(run.freeze());
        Map<String, Path> manifests = new HashMap<>(run.manifests());
        manifests.put(SPOT_PLAN, plan);
        Path root = base.resolve("assembled-a");
        Files.createDirectories(root);
        var source = new ClassACaseManifest.Source("Cells", ClassAScoreSchemaV2Test.SOURCE, "Boundary",
                List.of("membrane"), List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
        List<ClassACaseManifest.Case> cases = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        ArrayNode questions = ClassAScoreSchemaV2Test.JSON.createArrayNode();
        for (int i = 0; i < 2; i++) questions.add(ClassAScoreSchemaV2Test.packet("MCQ").question().deepCopy());
        for (String id : mandatory || excluded ? List.of(CASE_ONE, CASE_TWO, CASE_THREE)
                : List.of(CASE_ONE, CASE_TWO)) {
            cases.add(new ClassACaseManifest.Case(id, secondStem && id.equals(CASE_TWO)
                    ? ClassACaseManifest.Workflow.PLAN_ADAPTIVE : ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                    "synthetic", LearnerLevel.COLLEGE, null, "Biology", false,
                    List.of(source), 2, null, false, null));
            if (!excluded || !id.equals(CASE_THREE)) {
                outputs.add(new ClassABlindPacketExport.Output(id, MINI, questions));
                outputs.add(new ClassABlindPacketExport.Output(id, LUNA, questions));
            }
        }
        var exported = ClassABlindPacketExport.export(cases, outputs, MINI, LUNA,
                root.resolve("packets"), root.resolve("key.jsonl"), 17L);
        PairMap pairs = PairMap.fromPackets(root.resolve("packets"));
        String mandatoryPair = mandatory ? pairs.pairs().getLast().id() : null;
        Path directory = root.resolve("findings");
        Files.createDirectories(directory);
        Path registry = directory.resolve("seeds.json");
        Files.writeString(registry, "{\"seed\":\"synthetic-seed-123456789\",\"streams\":["
                + "{\"workflow\":\"" + STEM + "\",\"purpose\":\"SPOT_CHECK\",\"rounds\":1},"
                + "{\"workflow\":\"" + STEM + "\",\"purpose\":\"EXPANSION\",\"rounds\":3}"
                + (secondStem ? ", {\"workflow\":\"" + PLAN_STEM
                    + "\",\"purpose\":\"SPOT_CHECK\",\"rounds\":1}" : "") + "]}");
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
            pRows.add(ClassAAdjudicationFixture.scorer(id, "P", isMandatory ? "K" : "C", false).toString());
            sRows.add(ClassAAdjudicationFixture.scorer(id, "S", "C", false).toString());
            if (isMandatory || humanOnSelected && selections.contains(pairs.pairOf(id)))
                hRows.add(ClassAAdjudicationFixture.human(id, "C", isMandatory).toString());
        }
        Path p = directory.resolve("p.jsonl"), s = directory.resolve("s.jsonl"), h = directory.resolve("human.jsonl");
        Files.write(p, pRows); Files.write(s, sRows); Files.write(h, hRows);
        Path seal = ArmKeyGateFixtures.seal(root, exported.armKey());
        Path lock = root.resolve("lock.json");
        Path[] findings = {p, s, h, log, registry};
        relock(lock, seal, findings);
        ArmKeyAccess.OpenedKey opened = ArmKeyAccess.open(exported.armKey(), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, h)), Set.of(LockRecord.Kind.PRIMARY),
                root.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED, "class-A assembler test");
        ClassAExport a = new ClassAExport(root.resolve("packets"), p, s, h, opened, seal, lock,
                exported.exclusions(), FreezeRecord.sha256(exported.exclusions()));
        ClassASources sources = new ClassASources(run.freeze(), run.parameters(), run.mapping(),
                Map.copyOf(manifests), a, log, registry);
        return new Fixture(sources, pairs, selected, Set.copyOf(selections),
                log, registry, lock, seal, root, findings);
    }

    private static void detached(Path file) throws Exception {
        Files.writeString(file.resolveSibling(file.getFileName() + ".sha256"), FreezeRecord.sha256(file) + "\n");
    }

    private static void relock(Path lock, Path seal, Path[] findings) throws Exception {
        Files.deleteIfExists(lock);
        Files.deleteIfExists(lock.resolveSibling(lock.getFileName() + ".sha256"));
        ArmKeySeal.Seal sealed = ArmKeySeal.load(seal);
        LockRecord.create(lock, LockRecord.Kind.PRIMARY, sealed.workflowStems(), List.of(findings),
                sealed, seal, files -> {}, ArmKeyGateFixtures.LOCKED);
    }

    private static void repin(ClassASources sources, Path file) throws Exception {
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(sources.freezeFile()));
        ((ObjectNode) freeze.get("manifests")).put(file.toAbsolutePath().toString(), FreezeRecord.sha256(file));
        Files.writeString(sources.freezeFile(), freeze.toString());
        detached(sources.freezeFile());
    }

    @Test void assemblesDerivedReviewAndMatchesDirectConversion() throws Exception {
        Fixture fixture = fixture(true, false);
        byte[] before = Files.readAllBytes(fixture.log());
        byte[] registryBefore = Files.readAllBytes(fixture.registry());
        long size = Files.size(fixture.log());
        FileTime time = Files.getLastModifiedTime(fixture.log());
        FileTime registryTime = Files.getLastModifiedTime(fixture.registry());
        ClassAAssembly assembled = ClassAEvidenceAssembler.assemble(fixture.sources());
        Map<String, QuestionStatus> directStatuses = new HashMap<>();
        ClassAPacketIndex index = ClassAPacketIndex.load(fixture.sources().export().packetDirectory());
        var export = fixture.sources().export();
        var scores = ClassAScoreFilesV2.loadBlind(index, export.pScores(), export.sScores(), export.hScores(),
                ClassAScoreSchemaV2Test.MAPPING);
        for (var item : scores.values()) directStatuses.put(item.id(), FinalStatus.of(item,
                ClassAScoreSchemaV2Test.MAPPING, assembled.reviewRequiredPairs().contains(fixture.pairs().pairOf(item.id()))));
        AdjudicatedLoaded direct = AdjudicatedConverter.classA(export.key(), directStatuses, fixture.pairs(),
                Set.of(STEM), Set.of(CASE_ONE, CASE_TWO), export.exclusionsFileOrNull(),
                export.expectedExclusionsSha256OrNull(), Map.of(CASE_ONE, 2, CASE_TWO, 2));
        assertThat(assembled.facts()).isEqualTo(direct);
        assertThat(assembled.facts().excludedReasons()).isEmpty();
        assertThat(assembled.facts().requestedQuestionsByCaseId())
                .containsEntry(CASE_ONE, 2).containsEntry(CASE_TWO, 2);
        assertThat(assembled.statuses()).isEqualTo(directStatuses);
        assertThat(assembled.reviewRequiredPairs()).contains(fixture.selected());
        assertThat(assembled.digests().values().keySet()).contains("freeze", "parameters", "mapping",
                "manifest.classA.primary", "manifest.classA.spotCheckPlan", "classA.packets", "classA.seal",
                "classA.lock", "classA.scores.p", "classA.scores.s", "classA.scores.h", "classA.drawlog",
                "classA.registry", "classA.exclusions", "classA.lock.finding.0");
        assertThat(assembled.digests().values().toString()).doesNotContain(CASE_ONE, CASE_TWO, MINI, LUNA);
        assertThat(Files.readAllBytes(fixture.log())).isEqualTo(before);
        assertThat(Files.readAllBytes(fixture.registry())).isEqualTo(registryBefore);
        assertThat(Files.size(fixture.log())).isEqualTo(size);
        assertThat(Files.getLastModifiedTime(fixture.log())).isEqualTo(time);
        assertThat(Files.getLastModifiedTime(fixture.registry())).isEqualTo(registryTime);
    }

    @Test void selectedPairWithoutHumanIsPendingAndCannotBeAssembled() throws Exception {
        Fixture fixture = fixture(false, false);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining(PENDING);
    }

    @Test void mandatoryPairRequiresHumanOnBothPlacements() throws Exception {
        Fixture fixture = fixture(true, true);
        assertThat(ClassAEvidenceAssembler.assemble(fixture.sources()).sets().mandatory()).hasSize(1);
        Path human = fixture.sources().export().hScores();
        List<String> retained = new ArrayList<>();
        for (String line : Files.readAllLines(human)) {
            String id = IntegritySupport.JSON.readTree(line).path("id").asText();
            if (fixture.selected().equals(fixture.pairs().pairOf(id))) retained.add(line);
        }
        Files.write(human, retained);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining(PENDING);
    }

    @Test void lockAnchorsLastLineAndPlanRequiresDraw() throws Exception {
        Fixture fixture = fixture(true, false);
        Files.writeString(fixture.log(), "");
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(LockRecord.LockBroken.class);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(DrawLog.InvalidDrawLog.class);
    }

    @Test void scoresPacketsAndPinsAreBound() throws Exception {
        Fixture fixture = fixture(true, false);
        Files.writeString(fixture.sources().export().pScores(), "changed", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(LockRecord.LockBroken.class);
        Fixture second = fixture(true, false);
        Path packet = second.sources().export().packetDirectory().resolve(STEM + ".jsonl");
        Files.writeString(packet, Files.readString(packet) + " ");
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(second.sources()))
                .isInstanceOf(ArmKeySeal.SealBroken.class);
    }

    @Test void absentScoreFindingAndDifferentSealLockAreRefused() throws Exception {
        Fixture fixture = fixture(true, false);
        Path[] withoutP = {fixture.findings()[1], fixture.findings()[2], fixture.findings()[3],
                fixture.findings()[4]};
        relock(fixture.lock(), fixture.seal(), withoutP);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(LockRecord.LockBroken.class);
        Fixture second = fixture(true, false);
        ObjectNode lock = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(second.lock()));
        lock.put("sealSha256", "0".repeat(64));
        Files.writeString(second.lock(), lock.toString());
        detached(second.lock());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(second.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("lock differs");
    }

    @Test void changedSelectionWithNewLockStillFailsReplay() throws Exception {
        Fixture fixture = fixture(true, false);
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(fixture.log()));
        String other = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(fixture.selected())).findFirst().orElseThrow();
        ((ArrayNode) row.get("selectedPairIds")).removeAll().add(other);
        Files.writeString(fixture.log(), row.toString() + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("replay");
    }

    @Test void changedDrawCountAndFrameHashAreRefused() throws Exception {
        Fixture fixture = fixture(true, false);
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(fixture.log()));
        row.put("count", 2);
        Files.writeString(fixture.log(), row + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("plan");
        row.put("count", 1).put("frameSha256", "0".repeat(64));
        Files.writeString(fixture.log(), row + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("replay");
        Files.writeString(fixture.log(), Files.readString(fixture.log()) + " ");
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(LockRecord.LockBroken.class);
    }

    @Test void planCountAndManifestPinsAreEnforced() throws Exception {
        Fixture fixture = fixture(true, false);
        Path plan = fixture.sources().manifests().get(SPOT_PLAN);
        Files.writeString(plan, Files.readString(plan).replace("\"count\":1", "\"count\":2"));
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("Unpinned");
        repin(fixture.sources(), plan);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("plan");
        Path manifest = fixture.sources().manifests().get(PRIMARY_MANIFEST);
        Files.writeString(manifest, Files.readString(manifest) + "\n");
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("Unpinned");
    }

    @Test void spotPlanCannotIncludeAnExtraStem() throws Exception {
        Fixture fixture = fixture(true, false);
        Path plan = fixture.sources().manifests().get(SPOT_PLAN);
        Files.writeString(plan, Files.readString(plan).replace("]}",
                ", {\"stem\":\"PLAN_ADAPTIVE\",\"count\":1}]}"));
        repin(fixture.sources(), plan);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("plan");
    }

    @Test void zeroSpotChecksAreNotAValidFrozenPlan() throws Exception {
        Fixture fixture = fixture(true, false);
        Path plan = fixture.sources().manifests().get(SPOT_PLAN);
        Files.writeString(plan, Files.readString(plan).replace("\"count\":1", "\"count\":0"));
        repin(fixture.sources(), plan);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("plan stem");
    }

    @Test void registryChangedUnderNewLockStillDiffersFromDraw() throws Exception {
        Fixture fixture = fixture(true, false);
        Files.writeString(fixture.registry(), Files.readString(fixture.registry())
                .replace("synthetic-seed-123456789", "synthetic-seed-987654321"));
        detached(fixture.registry());
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("registry");
    }

    @Test void expansionSelectionIsReviewRequiredButFrameAndStreamAreNotReproducible() throws Exception {
        Fixture fixture = fixture(true, false);
        Path human = fixture.sources().export().hScores();
        List<String> rows = new ArrayList<>(Files.readAllLines(human));
        for (String id : fixture.pairs().blindItemIds())
            if (!fixture.selected().equals(fixture.pairs().pairOf(id)))
                rows.add(ClassAAdjudicationFixture.human(id, "C", false).toString());
        Files.write(human, rows);
        SeedStreamRegistry registry = SeedStreamRegistry.load(fixture.registry());
        String remaining = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(fixture.selected())).findFirst().orElseThrow();
        PairSampler.draw(registry, new DrawLog(fixture.log()), fixture.pairs(), STEM,
                SeedStreamRegistry.EXPANSION, 1, List.of(remaining), 1, CLOCK);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        ClassAAssembly assembly = ClassAEvidenceAssembler.assemble(fixture.sources());
        assertThat(assembly.reviewRequiredPairs()).contains(remaining);
        // Expansion.draw accepts caller-supplied prior reviews, so its true frame and stream output
        // cannot be reconstructed from these files; the lock anchors the logged selection instead.
        PairSampler.draw(registry, new DrawLog(fixture.log()), fixture.pairs(), STEM,
                SeedStreamRegistry.EXPANSION, 2, List.of(remaining), 1, CLOCK);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThat(ClassAEvidenceAssembler.assemble(fixture.sources()).drawEntries()).hasSize(3);
    }

    @Test void expansionCannotSelectSpotPairOrClaimOversizedFrame() throws Exception {
        Fixture fixture = fixture(true, false);
        String remaining = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(fixture.selected())).findFirst().orElseThrow();
        PairSampler.draw(SeedStreamRegistry.load(fixture.registry()), new DrawLog(fixture.log()),
                fixture.pairs(), STEM, SeedStreamRegistry.EXPANSION, 1, List.of(remaining), 1, CLOCK);
        List<String> lines = Files.readAllLines(fixture.log());
        ObjectNode second = (ObjectNode) IntegritySupport.JSON.readTree(lines.get(1));
        ((ArrayNode) second.get("selectedPairIds")).removeAll().add(fixture.selected());
        Files.writeString(fixture.log(), lines.getFirst() + "\n" + second + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class);
        ((ArrayNode) second.get("selectedPairIds")).removeAll().add(remaining);
        second.put("frameSize", 2);
        Files.writeString(fixture.log(), lines.getFirst() + "\n" + second + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class);
    }

    @Test void expansionRejectsOutsideFrameAndDescendingRounds() throws Exception {
        Fixture fixture = fixture(true, false, false, true);
        PairSampler.draw(SeedStreamRegistry.load(fixture.registry()), new DrawLog(fixture.log()),
                fixture.pairs(), STEM, SeedStreamRegistry.EXPANSION, 2,
                List.of(fixture.selected()), 1, CLOCK);
        List<String> lines = Files.readAllLines(fixture.log());
        ObjectNode last = (ObjectNode) IntegritySupport.JSON.readTree(lines.getLast());
        String otherWorkflowPair = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> PLAN_STEM.equals(fixture.pairs().workflowOf(id))).findFirst().orElseThrow();
        ((ArrayNode) last.get("selectedPairIds")).removeAll().add(otherWorkflowPair);
        Files.writeString(fixture.log(), lines.get(0) + "\n" + lines.get(1) + "\n" + last + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class);

        Fixture second = fixture(true, false);
        String remaining = second.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(second.selected())).findFirst().orElseThrow();
        SeedStreamRegistry registry = SeedStreamRegistry.load(second.registry());
        PairSampler.draw(registry, new DrawLog(second.log()), second.pairs(), STEM,
                SeedStreamRegistry.EXPANSION, 3, List.of(remaining), 1, CLOCK);
        PairSampler.draw(registry, new DrawLog(second.log()), second.pairs(), STEM,
                SeedStreamRegistry.EXPANSION, 2, List.of(remaining), 1, CLOCK);
        relock(second.lock(), second.seal(), second.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(second.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("round order");
    }

    @Test void drawLogMustBeNamedInLock() throws Exception {
        Fixture fixture = fixture(true, false);
        Path[] withoutLog = {fixture.findings()[0], fixture.findings()[1], fixture.findings()[2],
                fixture.findings()[4]};
        relock(fixture.lock(), fixture.seal(), withoutLog);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(LockRecord.LockBroken.class);
    }

    @Test void requiredStemCannotDisappearBehindAValidLogForAnotherStem() throws Exception {
        Fixture fixture = fixture(true, false);
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(fixture.log()));
        row.put("workflow", "UNRELATED");
        ((ArrayNode) row.get("selectedPairIds")).removeAll();
        Files.writeString(fixture.log(), row + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("Missing frozen");
    }

    @Test void everyStemOfOneExportNeedsItsOwnPlannedDraw() throws Exception {
        Fixture fixture = fixture(true, false, false, true);
        assertThat(ClassAEvidenceAssembler.assemble(fixture.sources()).drawEntries()).hasSize(2);
        String first = Files.readAllLines(fixture.log()).getFirst();
        Files.writeString(fixture.log(), first + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("Missing frozen");
    }

    @Test void middleLineRemovalAndReorderingBreakTheDrawChain() throws Exception {
        Fixture fixture = fixture(true, false, false, true);
        String remaining = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !fixture.selections().contains(id)).findFirst().orElse(null);
        if (remaining == null) {
            // Both one-pair stems are fully selected; draw a second round against a known frame.
            remaining = fixture.selected();
        }
        PairSampler.draw(SeedStreamRegistry.load(fixture.registry()), new DrawLog(fixture.log()),
                fixture.pairs(), STEM, SeedStreamRegistry.EXPANSION, 1, List.of(remaining), 1, CLOCK);
        List<String> lines = Files.readAllLines(fixture.log());
        Files.writeString(fixture.log(), lines.getFirst() + "\n" + lines.getLast() + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(DrawLog.InvalidDrawLog.class);
        Files.writeString(fixture.log(), lines.get(1) + "\n" + lines.getFirst() + "\n"
                + lines.getLast() + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(DrawLog.InvalidDrawLog.class);
    }

    @Test void caseOfOtherBaselineIsRefusedDespiteCompleteArmOutputs() throws Exception {
        Fixture fixture = fixture(true, false, false, true);
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(fixture.sources().freezeFile()));
        ArrayNode mini = (ArrayNode) freeze.path("arms").path(MINI).path("workflows");
        mini.remove(1);
        ((ArrayNode) freeze.path("arms").path("BASELINE_41").path("workflows")).add(PLAN_STEM);
        Files.writeString(fixture.sources().freezeFile(), freeze.toString());
        detached(fixture.sources().freezeFile());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("baseline scope");
    }

    @Test void spotCheckCountMayExhaustARealFrame() throws Exception {
        Fixture fixture = fixture(true, false);
        Path plan = fixture.sources().manifests().get(SPOT_PLAN);
        Files.writeString(plan, Files.readString(plan).replace("\"count\":1", "\"count\":3"));
        repin(fixture.sources(), plan);
        Files.delete(fixture.log());
        PairSampler.draw(SeedStreamRegistry.load(fixture.registry()), new DrawLog(fixture.log()),
                fixture.pairs(), STEM, SeedStreamRegistry.SPOT_CHECK, 1,
                fixture.pairs().pairs().stream().map(PairMap.Pair::id).toList(), 3, CLOCK);
        Path human = fixture.sources().export().hScores();
        List<String> rows = new ArrayList<>(Files.readAllLines(human));
        for (String id : fixture.pairs().blindItemIds())
            if (!fixture.selected().equals(fixture.pairs().pairOf(id)))
                rows.add(ClassAAdjudicationFixture.human(id, "C", false).toString());
        Files.write(human, rows);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThat(ClassAEvidenceAssembler.assemble(fixture.sources()).drawEntries().getFirst().exhausted()).isTrue();
        ObjectNode changed = (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(fixture.log()));
        changed.put("exhausted", false);
        Files.writeString(fixture.log(), changed + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("replay");
    }

    @Test void laterSpotCheckRoundsAreAlsoReplayed() throws Exception {
        Fixture fixture = fixture(true, false);
        Path registryFile = fixture.registry();
        Files.writeString(registryFile, Files.readString(registryFile)
                .replace("\"purpose\":\"SPOT_CHECK\",\"rounds\":1",
                        "\"purpose\":\"SPOT_CHECK\",\"rounds\":2"));
        detached(registryFile);
        SeedStreamRegistry registry = SeedStreamRegistry.load(registryFile);
        Files.delete(fixture.log());
        List<String> frame = fixture.pairs().pairs().stream().map(PairMap.Pair::id).toList();
        PairSampler.draw(registry, new DrawLog(fixture.log()), fixture.pairs(), STEM,
                SeedStreamRegistry.SPOT_CHECK, 1, frame, 1, CLOCK);
        PairSampler.draw(registry, new DrawLog(fixture.log()), fixture.pairs(), STEM,
                SeedStreamRegistry.SPOT_CHECK, 2, frame, 1, CLOCK);
        Path human = fixture.sources().export().hScores();
        List<String> rows = new ArrayList<>(Files.readAllLines(human));
        for (String id : fixture.pairs().blindItemIds())
            if (!rows.stream().anyMatch(line -> line.contains("\"id\":\"" + id + "\"")))
                rows.add(ClassAAdjudicationFixture.human(id, "C", false).toString());
        Files.write(human, rows);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThat(ClassAEvidenceAssembler.assemble(fixture.sources()).drawEntries()).hasSize(2);
    }

    @Test void frozenCaseSetAndWidthsComeFromPinnedManifest() throws Exception {
        Fixture fixture = fixture(true, false);
        Path manifest = fixture.sources().manifests().get(PRIMARY_MANIFEST);
        String first = Files.readAllLines(manifest).getFirst();
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(first);
        row.put("requestedQuestionCount", 3);
        List<String> lines = Files.readAllLines(manifest);
        Files.writeString(manifest, row + "\n" + lines.get(1) + "\n");
        repin(fixture.sources(), manifest);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining("shorter");
        row.put("requestedQuestionCount", 2).put("caseId", "outside");
        Files.writeString(manifest, row + "\n" + lines.get(1) + "\n");
        repin(fixture.sources(), manifest);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining("frozen");
    }

    @Test void exclusionsExpectedHashAndParameterMappingPinsAreEnforced() throws Exception {
        Fixture fixture = fixture(true, false);
        ClassAExport export = fixture.sources().export();
        ClassAExport wrong = new ClassAExport(export.packetDirectory(), export.pScores(), export.sScores(),
                export.hScores(), export.key(), export.sealFile(), export.lockFile(),
                export.exclusionsFileOrNull(), "0".repeat(64));
        ClassASources wrongSources = new ClassASources(fixture.sources().freezeFile(),
                fixture.sources().parametersFile(), fixture.sources().mappingFile(),
                fixture.sources().manifests(), wrong, fixture.log(), fixture.registry());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(wrongSources))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining("Exclusions SHA-256");
        Files.writeString(fixture.sources().parametersFile(), Files.readString(fixture.sources().parametersFile()) + " ");
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(FrozenGateParameters.InvalidParameters.class);
        Fixture second = fixture(true, false);
        Files.writeString(second.sources().mappingFile(), Files.readString(second.sources().mappingFile()) + " ");
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(second.sources()))
                .isInstanceOf(EvidenceMapping.InvalidMapping.class);
    }

    @Test void excludedFrozenCaseKeepsItsReason() throws Exception {
        Fixture fixture = fixture(true, false, true);
        ClassAAssembly assembly = ClassAEvidenceAssembler.assemble(fixture.sources());
        assertThat(assembly.facts().excludedReasons()).containsEntry(CASE_THREE, "MISSING_ARM_OUTPUT");
        assertThat(assembly.facts().requestedQuestionsByCaseId()).containsEntry(CASE_THREE, 2);
        assertThat(assembly.digests().values()).containsKey("classA.exclusions");
    }

    @Test void spotCheckPlanShapeIsStrict() throws Exception {
        String[] bad = {
                "{\"stems\":[{\"stem\":\"%s\",\"count\":1}],\"extra\":1}",
                "{\"stems\":[{\"stem\":\"%s\",\"count\":1,\"extra\":1}]}",
                "{\"stems\":[{\"stem\":\"%s\",\"count\":\"1\"}]}",
                "{\"stems\":[{\"stem\":\"%s\",\"count\":1.5}]}",
                "{\"stems\":[{\"stem\":\"%s\",\"count\":1},{\"stem\":\"%s\",\"count\":1}]}"};
        for (String template : bad) {
            Fixture fixture = fixture(true, false);
            Path plan = fixture.sources().manifests().get(SPOT_PLAN);
            Files.writeString(plan, template.replace("%s", STEM));
            repin(fixture.sources(), plan);
            assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                    .isInstanceOf(InvalidClassAAssembly.class);
        }
    }

    @Test void findingsNeedDistinctNamesAndTheHumanFileConvention() throws Exception {
        Fixture fixture = fixture(true, false);
        ClassAExport export = fixture.sources().export();
        ClassAExport sameFile = new ClassAExport(export.packetDirectory(), export.pScores(), export.pScores(),
                export.hScores(), export.key(), export.sealFile(), export.lockFile(),
                export.exclusionsFileOrNull(), export.expectedExclusionsSha256OrNull());
        ClassASources duplicate = new ClassASources(fixture.sources().freezeFile(), fixture.sources().parametersFile(),
                fixture.sources().mappingFile(), fixture.sources().manifests(), sameFile,
                fixture.sources().drawLogFile(), fixture.sources().seedRegistryFile());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(duplicate))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("distinct");
        Fixture renamed = fixture(true, false);
        Path human = renamed.sources().export().hScores();
        Path other = human.resolveSibling("reviewer.jsonl");
        Files.move(human, other);
        Path[] findings = renamed.findings().clone();
        for (int index = 0; index < findings.length; index++) if (findings[index].equals(human)) findings[index] = other;
        relock(renamed.lock(), renamed.seal(), findings);
        ClassAExport moved = renamed.sources().export();
        ClassAExport renamedExport = new ClassAExport(moved.packetDirectory(), moved.pScores(), moved.sScores(),
                other, moved.key(), moved.sealFile(), moved.lockFile(), moved.exclusionsFileOrNull(),
                moved.expectedExclusionsSha256OrNull());
        ClassASources changed = new ClassASources(renamed.sources().freezeFile(), renamed.sources().parametersFile(),
                renamed.sources().mappingFile(), renamed.sources().manifests(), renamedExport,
                renamed.sources().drawLogFile(), renamed.sources().seedRegistryFile());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(changed))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("convention");
    }

    @Test void aReserveKindLockIsNotAClassAPrimaryLock() throws Exception {
        Fixture fixture = fixture(true, false);
        Files.deleteIfExists(fixture.lock());
        Files.deleteIfExists(fixture.lock().resolveSibling(fixture.lock().getFileName() + ".sha256"));
        ArmKeySeal.Seal sealed = ArmKeySeal.load(fixture.seal());
        LockRecord.create(fixture.lock(), LockRecord.Kind.RESERVE, sealed.workflowStems(),
                List.of(fixture.findings()), sealed, fixture.seal(), files -> {}, ArmKeyGateFixtures.LOCKED);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class);
    }

    @Test void anExportArmAbsentFromTheFreezeIsRefused() throws Exception {
        Fixture fixture = fixture(true, false);
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(
                Files.readAllBytes(fixture.sources().freezeFile()));
        String candidate = fixture.sources().export().key().rows().stream().map(ArmKeyAccess.KeyRow::arm)
                .filter(arm -> !arm.startsWith("BASELINE")).findFirst().orElseThrow();
        ((ObjectNode) freeze.get("arms")).remove(candidate);
        Files.writeString(fixture.sources().freezeFile(), freeze.toString());
        detached(fixture.sources().freezeFile());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void expansionEntriesNeedADeclaredStreamAndAnInFrameSelection() throws Exception {
        Fixture fixture = fixture(true, false, false, true);
        PairSampler.draw(SeedStreamRegistry.load(fixture.registry()), new DrawLog(fixture.log()),
                fixture.pairs(), STEM, SeedStreamRegistry.EXPANSION, 2,
                List.of(fixture.selected()), 1, CLOCK);
        List<String> lines = Files.readAllLines(fixture.log());
        ObjectNode last = (ObjectNode) IntegritySupport.JSON.readTree(lines.getLast());
        last.put("round", 99);
        Files.writeString(fixture.log(), String.join("\n", lines.subList(0, lines.size() - 1)) + "\n" + last + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("not declared");
        // One stem with two or more critical-clean pairs, so the oversized-frame rule cannot fire first.
        Fixture outside = fixture(true, false);
        PairSampler.draw(SeedStreamRegistry.load(outside.registry()), new DrawLog(outside.log()),
                outside.pairs(), STEM, SeedStreamRegistry.EXPANSION, 2,
                List.of(outside.selected()), 1, CLOCK);
        List<String> rows = Files.readAllLines(outside.log());
        ObjectNode tail = (ObjectNode) IntegritySupport.JSON.readTree(rows.getLast());
        // A pair id that exists nowhere: not in the frame and not a spot selection, so ONLY the subset rule can refuse it.
        ((ArrayNode) tail.get("selectedPairIds")).removeAll().add("NO_SUCH_STEM#1");
        Files.writeString(outside.log(), String.join("\n", rows.subList(0, rows.size() - 1)) + "\n" + tail + "\n");
        relock(outside.lock(), outside.seal(), outside.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(outside.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("provable frame");
    }
}
