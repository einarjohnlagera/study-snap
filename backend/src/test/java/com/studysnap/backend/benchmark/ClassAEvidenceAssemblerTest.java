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
    private static final String LONG_WORKFLOW = "LONG_EXAM";
    private static final String LONG_DIRECT = "LONG_EXAM_DIRECT";
    private static final String LONG_POOL = "LONG_EXAM_LONG_EXAM_POOL";
    private static final String BOARD_POOL = "LONG_EXAM_BOARD_EXAM_POOL";
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
        return fixture(humanOnSelected, mandatory, excluded, secondStem, 0);
    }

    private Fixture fixture(boolean humanOnSelected, boolean mandatory, boolean excluded,
            boolean secondStem, int extraCases) throws Exception {
        Path base = Files.createTempDirectory(temp, "fixture-");
        FrbPipelineTest pipeline = new FrbPipelineTest();
        pipeline.temp = base;
        ResultsPipeline.FrbRunInput run = pipeline.fixture(false, 0, 2, false).run();
        if (mandatory || excluded || extraCases > 0) {
            Path manifest = run.manifests().get(PRIMARY_MANIFEST);
            String first = Files.readAllLines(manifest).getFirst();
            StringBuilder additions = new StringBuilder(Files.readString(manifest));
            if (mandatory || excluded) {
                ObjectNode additional = (ObjectNode) IntegritySupport.JSON.readTree(first);
                additional.put("caseId", CASE_THREE);
                additions.append(additional).append('\n');
            }
            for (int i = 0; i < extraCases; i++) {
                ObjectNode additional = (ObjectNode) IntegritySupport.JSON.readTree(first);
                additional.put("caseId", "extra-" + i);
                additions.append(additional).append('\n');
            }
            Files.writeString(manifest, additions.toString());
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
                + (secondStem ? ", {\"stem\":\"" + PLAN_STEM + "\",\"count\":1}" : "")
                + "],\"expansion\":[{\"workflow\":\"" + STEM + "\",\"stemPriority\":[\"" + STEM
                + "\"],\"initialAllocation\":{\"" + STEM + "\":3}}"
                + (secondStem ? ",{\"workflow\":\"" + PLAN_STEM + "\",\"stemPriority\":[\""
                    + PLAN_STEM + "\"],\"initialAllocation\":{\"" + PLAN_STEM + "\":3}}" : "") + "]}");
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.freeze()));
        ((ObjectNode) freeze.get("manifests")).put(plan.toAbsolutePath().toString(), FreezeRecord.sha256(plan));
        if (mandatory || excluded || secondStem || extraCases > 0) {
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
        List<String> caseIds = new ArrayList<>(List.of(CASE_ONE, CASE_TWO));
        if (mandatory || excluded) caseIds.add(CASE_THREE);
        for (int i = 0; i < extraCases; i++) caseIds.add("extra-" + i);
        for (String id : caseIds) {
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

    /** Copies the ordinary fixture's freeze, packet export, score, seal, lock and key-opening helpers. */
    private Fixture longFixture() throws Exception {
        Path base = Files.createTempDirectory(temp, "long-assembly-");
        FrbPipelineTest pipeline = new FrbPipelineTest();
        pipeline.temp = base;
        ResultsPipeline.FrbRunInput run = pipeline.fixture(false, 0, 2, false).run();
        List<String> priority = List.of(LONG_DIRECT, LONG_POOL, BOARD_POOL);
        Path manifest = run.manifests().get(PRIMARY_MANIFEST);
        ObjectNode template = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllLines(manifest).getFirst());
        StringBuilder manifestRows = new StringBuilder();
        var source = new ClassACaseManifest.Source("Cells", ClassAScoreSchemaV2Test.SOURCE, "Boundary",
                List.of("membrane"), List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
        List<ClassACaseManifest.Case> cases = new ArrayList<>();
        List<ClassABlindPacketExport.Output> outputs = new ArrayList<>();
        ArrayNode questions = ClassAScoreSchemaV2Test.JSON.createArrayNode();
        for (int i = 0; i < 2; i++) questions.add(ClassAScoreSchemaV2Test.packet("MCQ").question().deepCopy());
        for (ClassACaseManifest.Subgroup subgroup : ClassACaseManifest.Subgroup.values())
            for (int i = 0; i < 3; i++) {
                String id = "long-" + subgroup + "-" + i;
                ObjectNode row = template.deepCopy();
                row.put("caseId", id).put("workflow", LONG_WORKFLOW).put("subgroup", subgroup.name());
                manifestRows.append(row).append('\n');
                cases.add(new ClassACaseManifest.Case(id, ClassACaseManifest.Workflow.LONG_EXAM,
                        "synthetic", LearnerLevel.COLLEGE, null, "Biology", false,
                        List.of(source), 2, null, false, subgroup));
                outputs.add(new ClassABlindPacketExport.Output(id, MINI, questions));
                outputs.add(new ClassABlindPacketExport.Output(id, LUNA, questions));
            }
        Files.writeString(manifest, manifestRows.toString());
        Path plan = base.resolve("spot-plan.json");
        Files.writeString(plan, "{\"stems\":[{\"stem\":\"" + LONG_DIRECT + "\",\"count\":1},"
                + "{\"stem\":\"" + LONG_POOL + "\",\"count\":1},"
                + "{\"stem\":\"" + BOARD_POOL + "\",\"count\":3}],"
                + "\"expansion\":[{\"workflow\":\"" + LONG_WORKFLOW + "\",\"stemPriority\":[\""
                + String.join("\",\"", priority) + "\"],\"initialAllocation\":{\"" + LONG_DIRECT
                + "\":1,\"" + LONG_POOL + "\":1,\"" + BOARD_POOL + "\":1}}]}");
        ObjectNode freeze = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(run.freeze()));
        ((ObjectNode) freeze.get("manifests")).put(plan.toAbsolutePath().toString(), FreezeRecord.sha256(plan));
        ((ObjectNode) freeze.get("manifests")).put(manifest.toAbsolutePath().toString(), FreezeRecord.sha256(manifest));
        ((ArrayNode) freeze.path("arms").path(MINI).path("workflows")).add(LONG_WORKFLOW);
        ((ObjectNode) freeze.path("costForecastUsdPerDeliveredCase")).put(LONG_WORKFLOW, 1);
        ((ObjectNode) freeze.path("compatibilitySuites")).put("SyntheticSuite1", LONG_WORKFLOW);
        Files.writeString(run.freeze(), freeze.toString());
        detached(run.freeze());
        Map<String, Path> manifests = new HashMap<>(run.manifests());
        manifests.put(SPOT_PLAN, plan);
        Path root = base.resolve("assembled-a");
        Files.createDirectories(root);
        var exported = ClassABlindPacketExport.export(cases, outputs, MINI, LUNA,
                root.resolve("packets"), root.resolve("key.jsonl"), 17L);
        PairMap pairs = PairMap.fromPackets(root.resolve("packets"));
        Path directory = root.resolve("findings");
        Files.createDirectories(directory);
        Path registry = directory.resolve("seeds.json");
        StringBuilder streams = new StringBuilder("{\"seed\":\"synthetic-seed-123456789\",\"streams\":[");
        for (String stem : priority) streams.append("{\"workflow\":\"").append(stem)
                .append("\",\"purpose\":\"SPOT_CHECK\",\"rounds\":1},")
                .append("{\"workflow\":\"").append(stem)
                .append("\",\"purpose\":\"EXPANSION\",\"rounds\":1},");
        streams.setLength(streams.length() - 1);
        Files.writeString(registry, streams.append("]}").toString());
        detached(registry);
        Path log = directory.resolve("draws.jsonl");
        Set<String> selections = new java.util.LinkedHashSet<>();
        String selected = null;
        for (String stem : priority) {
            DrawLog.Entry receipt = PairSampler.draw(SeedStreamRegistry.load(registry), new DrawLog(log), pairs,
                    stem, SeedStreamRegistry.SPOT_CHECK, 1,
                    pairs.pairs().stream().map(PairMap.Pair::id)
                            .filter(id -> stem.equals(pairs.workflowOf(id))).toList(),
                    stem.equals(BOARD_POOL) ? 3 : 1, CLOCK);
            if (stem.equals(LONG_DIRECT)) selected = receipt.selectedPairIds().getFirst();
            selections.addAll(receipt.selectedPairIds());
        }
        List<String> pRows = new ArrayList<>(), sRows = new ArrayList<>(), hRows = new ArrayList<>();
        for (String id : pairs.blindItemIds().stream().sorted().toList()) {
            pRows.add(ClassAAdjudicationFixture.scorer(id, "P", "C", false).toString());
            sRows.add(ClassAAdjudicationFixture.scorer(id, "S", "C", false).toString());
            if (selections.contains(pairs.pairOf(id)))
                hRows.add(ClassAAdjudicationFixture.human(id, "C", false).toString());
        }
        Path p = directory.resolve("p.jsonl"), s = directory.resolve("s.jsonl"), h = directory.resolve("human.jsonl");
        Files.write(p, pRows); Files.write(s, sRows); Files.write(h, hRows);
        Path seal = ArmKeyGateFixtures.seal(root, exported.armKey());
        Path lock = root.resolve("lock.json");
        Path[] findings = {p, s, h, log, registry};
        relock(lock, seal, findings);
        ArmKeyAccess.OpenedKey opened = ArmKeyAccess.open(exported.armKey(), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, h)), Set.of(LockRecord.Kind.PRIMARY),
                root.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED, "long assembler test");
        ClassAExport export = new ClassAExport(root.resolve("packets"), p, s, h, opened, seal, lock,
                exported.exclusions(), FreezeRecord.sha256(exported.exclusions()));
        ClassASources sources = new ClassASources(run.freeze(), run.parameters(), run.mapping(),
                Map.copyOf(manifests), export, log, registry);
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

    private static void trigger(Fixture fixture, String pairId) throws Exception {
        Path human = fixture.sources().export().hScores();
        List<String> rows = new ArrayList<>();
        for (String line : Files.readAllLines(human)) {
            String id = IntegritySupport.JSON.readTree(line).path("id").asText();
            rows.add(pairId.equals(fixture.pairs().pairOf(id))
                    ? ClassAAdjudicationFixture.human(id, "K", false).toString() : line);
        }
        Files.write(human, rows);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
    }

    private static void drawExpansion(Fixture fixture, String stem, int count) throws Exception {
        String remaining = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> stem.equals(fixture.pairs().workflowOf(id)))
                .filter(id -> !fixture.selections().contains(id)).findFirst().orElseThrow();
        PairSampler.draw(SeedStreamRegistry.load(fixture.registry()), new DrawLog(fixture.log()),
                fixture.pairs(), stem, SeedStreamRegistry.EXPANSION, 1,
                List.of(remaining), count, CLOCK);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
    }

    private static void mandatoryDoubleMiss(Fixture fixture) throws Exception {
        String mandatory = fixture.pairs().pairs().getLast().id();
        Path p = fixture.sources().export().pScores();
        List<String> scorers = new ArrayList<>();
        for (String line : Files.readAllLines(p)) {
            String id = IntegritySupport.JSON.readTree(line).path("id").asText();
            scorers.add(mandatory.equals(fixture.pairs().pairOf(id))
                    ? ClassAAdjudicationFixture.scorer(id, "P", "C", true).toString() : line);
        }
        Files.write(p, scorers);
        Path human = fixture.sources().export().hScores();
        List<String> reviews = new ArrayList<>();
        for (String line : Files.readAllLines(human)) {
            String id = IntegritySupport.JSON.readTree(line).path("id").asText();
            reviews.add(mandatory.equals(fixture.pairs().pairOf(id))
                    ? ClassAAdjudicationFixture.human(id, "K", false).toString() : line);
        }
        Files.write(human, reviews);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
    }

    private static List<String> expansionDraw(Fixture fixture, int count) throws Exception {
        var export = fixture.sources().export();
        PairMap pairs = fixture.pairs();
        var scores = ClassAScoreFilesV2.loadBlind(ClassAPacketIndex.load(export.packetDirectory()),
                export.pScores(), export.sScores(), export.hScores(), ClassAAdjudicationFixture.MAPPING);
        ReviewSets sets = ReviewSets.compute(pairs, scores, ClassAAdjudicationFixture.MAPPING);
        List<String> drawn = Expansion.draw(SeedStreamRegistry.load(fixture.registry()), new DrawLog(fixture.log()),
                pairs, sets, List.of(), new Expansion.WorkflowScope(STEM, Map.of(STEM, count)), 1, CLOCK);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        return drawn;
    }

    private static void reviewPairs(Fixture fixture, List<String> pairIds) throws Exception {
        Path human = fixture.sources().export().hScores();
        List<String> reviews = new ArrayList<>(Files.readAllLines(human));
        for (String id : fixture.pairs().blindItemIds()) if (pairIds.contains(fixture.pairs().pairOf(id)))
            reviews.add(ClassAAdjudicationFixture.human(id, "C", false).toString());
        Files.write(human, reviews);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
    }

    @Test void drawToolAndVerifierAgreeAcrossInsufficientAndCompleteFrames() throws Exception {
        for (int extra : List.of(1, 2, 3)) {
            Fixture fixture = fixture(true, false, false, false, extra);
            trigger(fixture, fixture.selected());
            int expected = Math.min(3, extra + 1);
            List<String> drawn = expansionDraw(fixture, expected);
            assertThat(drawn).hasSize(expected).doesNotContain(fixture.selected());
            reviewPairs(fixture, drawn);
            byte[] logBefore = Files.readAllBytes(fixture.log());
            byte[] registryBefore = Files.readAllBytes(fixture.registry());
            Path planFile = fixture.sources().manifests().get(SPOT_PLAN);
            byte[] planBefore = Files.readAllBytes(planFile);
            var disclosure = ClassAEvidenceAssembler.assemble(fixture.sources()).expansionObligations().get(STEM);
            assertThat(disclosure.drawnPairs()).containsExactlyElementsOf(drawn);
            assertThat(disclosure.allocationByStem()).containsEntry(STEM, expected);
            assertThat(disclosure.requiredPairs()).isEqualTo(expected);
            assertThat(disclosure.frameExhausted()).isEqualTo(extra < 2);
            assertThat(Files.readAllBytes(fixture.log())).isEqualTo(logBefore);
            assertThat(Files.readAllBytes(fixture.registry())).isEqualTo(registryBefore);
            assertThat(Files.readAllBytes(planFile)).isEqualTo(planBefore);
        }
    }

    @Test void expansionCountSelectionFrameAndRoundTamperingAreRefused() throws Exception {
        Fixture fixture = fixture(true, false, false, false, 3);
        trigger(fixture, fixture.selected());
        List<String> selected = expansionDraw(fixture, 3);
        reviewPairs(fixture, selected);
        List<String> original = Files.readAllLines(fixture.log());
        ObjectNode row = (ObjectNode) IntegritySupport.JSON.readTree(original.get(1));
        row.put("frameSha256", "0".repeat(64));
        Files.writeString(fixture.log(), original.getFirst() + "\n" + row + "\n");
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(LockRecord.LockBroken.class);
        row = (ObjectNode) IntegritySupport.JSON.readTree(original.get(1));
        row.put("count", 4);
        Files.writeString(fixture.log(), original.getFirst() + "\n" + row + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("count");
        row.put("count", 3);
        String other = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !fixture.selections().contains(id) && !selected.contains(id)).findFirst().orElseThrow();
        reviewPairs(fixture, List.of(other));
        ((ArrayNode) row.get("selectedPairIds")).remove(0);
        ((ArrayNode) row.get("selectedPairIds")).add(other);
        Files.writeString(fixture.log(), original.getFirst() + "\n" + row + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("replay");
        row = (ObjectNode) IntegritySupport.JSON.readTree(original.get(1));
        row.put("frameSha256", "0".repeat(64));
        Files.writeString(fixture.log(), original.getFirst() + "\n" + row + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("replay");
        row = (ObjectNode) IntegritySupport.JSON.readTree(original.get(1));
        row.put("round", 2);
        Files.writeString(fixture.log(), original.getFirst() + "\n" + row + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("round");
    }

    @Test void wrongCountCannotHideBehindAnExactlyFullFrame() throws Exception {
        Fixture fixture = fixture(true, false, false, false, 2);
        trigger(fixture, fixture.selected());
        List<String> selected = expansionDraw(fixture, 3);
        reviewPairs(fixture, selected);
        List<String> rows = Files.readAllLines(fixture.log());
        ObjectNode entry = (ObjectNode) IntegritySupport.JSON.readTree(rows.get(1));
        entry.put("count", 4);
        Files.writeString(fixture.log(), rows.getFirst() + "\n" + entry + "\n");
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("count");
    }

    @Test void triggeredEmptyAndShrunkDrawsAreRefused() throws Exception {
        Fixture empty = fixture(true, false);
        trigger(empty, empty.selected());
        PairSampler.draw(SeedStreamRegistry.load(empty.registry()), new DrawLog(empty.log()),
                empty.pairs(), STEM, SeedStreamRegistry.EXPANSION, 1, List.of(), 1, CLOCK);
        relock(empty.lock(), empty.seal(), empty.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(empty.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("count");

        Fixture shrunk = fixture(true, false, false, false, 2);
        trigger(shrunk, shrunk.selected());
        expansionDraw(shrunk, 2);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(shrunk.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("count");
    }

    @Test void expansionReviewCannotCreateItsOwnTrigger() throws Exception {
        Fixture fixture = fixture(true, false);
        List<String> selected = expansionDraw(fixture, 1);
        Path human = fixture.sources().export().hScores();
        List<String> reviews = new ArrayList<>(Files.readAllLines(human));
        for (String id : fixture.pairs().blindItemIds()) if (selected.contains(fixture.pairs().pairOf(id)))
            reviews.add(ClassAAdjudicationFixture.human(id, "K", false).toString());
        Files.write(human, reviews);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("without trigger");
    }

    @Test void aNonFinalPairCannotTriggerExpansion() throws Exception {
        Fixture fixture = fixture(true, false);
        Path human = fixture.sources().export().hScores();
        List<String> previous = Files.readAllLines(human);
        String first = IntegritySupport.JSON.readTree(previous.getFirst()).path("id").asText();
        List<String> partial = new ArrayList<>();
        partial.add(ClassAAdjudicationFixture.human(first, "K", false).toString());
        partial.addAll(previous.subList(2, previous.size()));
        Files.write(human, partial);
        var export = fixture.sources().export();
        var scores = ClassAScoreFilesV2.loadBlind(ClassAPacketIndex.load(export.packetDirectory()),
                export.pScores(), export.sScores(), human, ClassAAdjudicationFixture.MAPPING);
        ReviewSets sets = ReviewSets.compute(fixture.pairs(), scores, ClassAAdjudicationFixture.MAPPING);
        Map<String, QuestionStatus> statuses = new HashMap<>();
        for (var item : scores.values()) statuses.put(item.id(), FinalStatus.of(item,
                ClassAAdjudicationFixture.MAPPING, fixture.selected().equals(fixture.pairs().pairOf(item.id()))));
        var plan = ClassAEvidenceAssembler.spotPlan(fixture.sources().manifests().get(SPOT_PLAN));
        var obligation = ExpansionObligation.verify(plan, Map.of(STEM, Set.of(STEM)), fixture.pairs(), sets,
                statuses, new DrawLog(fixture.log()).entries(), SeedStreamRegistry.load(fixture.registry()));
        assertThat(obligation.get(STEM).triggerPairs()).isEmpty();
    }

    @Test void zeroFrameNeedsNoDrawAndDisclosesVacuousCompletion() throws Exception {
        Fixture fixture = fixture(true, false);
        Path plan = fixture.sources().manifests().get(SPOT_PLAN);
        Files.writeString(plan, Files.readString(plan).replace("\"count\":1", "\"count\":3"));
        repin(fixture.sources(), plan);
        Files.delete(fixture.log());
        PairSampler.draw(SeedStreamRegistry.load(fixture.registry()), new DrawLog(fixture.log()),
                fixture.pairs(), STEM, SeedStreamRegistry.SPOT_CHECK, 1,
                fixture.pairs().pairs().stream().map(PairMap.Pair::id).toList(), 3, CLOCK);
        String unreviewed = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(fixture.selected())).findFirst().orElseThrow();
        reviewPairs(fixture, List.of(unreviewed));
        trigger(fixture, fixture.selected());
        var disclosure = ClassAEvidenceAssembler.assemble(fixture.sources()).expansionObligations().get(STEM);
        assertThat(disclosure.triggerPairs()).hasSize(1);
        assertThat(disclosure.drawnPairs()).isEmpty();
        assertThat(disclosure.frameExhausted()).isTrue();
        assertThat(disclosure.requiredPairs()).isZero();
        var planForOutcome = Expansion.plan(fixture.pairs(), disclosure.drawnPairs(),
                ExpansionObligation.K_REQ, 120, 1.25, null);
        assertThat(Expansion.outcome(planForOutcome, disclosure.triggerPairs(), List.of(), false, false).outcome())
                .isEqualTo(Expansion.ExpansionOutcome.COMPLETE_NO_SECOND_DOUBLE_MISS);
    }

    @Test void longExamEmptyStemRedistributesAndRequiresPriorityLogOrder() throws Exception {
        Path root = Files.createTempDirectory(temp, "long-fixture-");
        PairMap pairs = ClassAAdjudicationFixture.exportLong(root, 3);
        var clean = ReviewSetsTest.clean(pairs);
        ReviewSets sets = ReviewSets.compute(pairs, ClassAAdjudicationFixture.load(root, pairs,
                clean, clean, Map.of(), Map.of()), ClassAAdjudicationFixture.MAPPING);
        List<String> priority = List.of(LONG_DIRECT, LONG_POOL, BOARD_POOL);
        Path registryFile = root.resolve("registry.json");
        StringBuilder body = new StringBuilder("{\"seed\":\"synthetic-seed-123456789\",\"streams\":[");
        for (String stem : priority) {
            body.append("{\"workflow\":\"").append(stem)
                    .append("\",\"purpose\":\"SPOT_CHECK\",\"rounds\":1},")
                    .append("{\"workflow\":\"").append(stem)
                    .append("\",\"purpose\":\"EXPANSION\",\"rounds\":1},");
        }
        body.setLength(body.length() - 1);
        Files.writeString(registryFile, body.append("]}").toString());
        detached(registryFile);
        SeedStreamRegistry registry = SeedStreamRegistry.load(registryFile);
        DrawLog log = new DrawLog(root.resolve("draws.jsonl"));
        Set<String> spot = new java.util.LinkedHashSet<>();
        for (String stem : priority) spot.addAll(ReviewSets.spotCheck(sets, registry, log, pairs,
                stem, 1, stem.equals(BOARD_POOL) ? 3 : 1, CLOCK));
        String trigger = log.entries().getFirst().selectedPairIds().getFirst();
        Map<String, Integer> initial = new java.util.LinkedHashMap<>();
        priority.forEach(stem -> initial.put(stem, 1));
        Map<String, Integer> allocated = ExpansionAllocation.allocate(initial, priority,
                Map.of(LONG_DIRECT, 2, LONG_POOL, 2, BOARD_POOL, 0), 3);
        assertThat(allocated).containsEntry(LONG_DIRECT, 2).containsEntry(LONG_POOL, 1)
                .containsEntry(BOARD_POOL, 0);
        Map<String, Integer> positive = new java.util.LinkedHashMap<>();
        priority.stream().filter(stem -> allocated.get(stem) > 0)
                .forEach(stem -> positive.put(stem, allocated.get(stem)));
        List<String> drawn = Expansion.draw(registry, log, pairs, sets, List.of(),
                new Expansion.WorkflowScope(LONG_WORKFLOW, positive), 1, CLOCK);
        Map<String, QuestionStatus> statuses = new HashMap<>();
        for (String id : pairs.blindItemIds()) {
            String pair = pairs.pairOf(id);
            String human = pair.equals(trigger) ? "K" : spot.contains(pair) || drawn.contains(pair) ? "C" : null;
            statuses.put(id, FinalStatus.of(ClassAAdjudicationFixture.direct(id, "C", "C", 0, human),
                    ClassAAdjudicationFixture.MAPPING, spot.contains(pair) || drawn.contains(pair)));
        }
        var plan = new ClassAEvidenceAssembler.SpotPlan(
                Map.of(LONG_DIRECT, 1, LONG_POOL, 1, BOARD_POOL, 3),
                Map.of(LONG_WORKFLOW, new ClassAEvidenceAssembler.ExpansionPlan(priority, initial)));
        var verified = ExpansionObligation.verify(plan, Map.of(LONG_WORKFLOW, Set.copyOf(priority)),
                pairs, sets, statuses, log.entries(), registry);
        assertThat(verified.get(LONG_WORKFLOW).drawnPairs()).containsExactlyElementsOf(drawn);
        assertThat(verified.get(LONG_WORKFLOW).allocationByStem()).isEqualTo(allocated);
        assertThat(verified.get(LONG_WORKFLOW).frameExhausted()).isFalse();
        List<DrawLog.Entry> reordered = new ArrayList<>(log.entries());
        java.util.Collections.swap(reordered, 3, 4);
        assertThatThrownBy(() -> ExpansionObligation.verify(plan, Map.of(LONG_WORKFLOW, Set.copyOf(priority)),
                pairs, sets, statuses, reordered, registry))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("priority");
    }

    @Test void longExamAssemblyRedistributesAcrossOneWorkflow() throws Exception {
        Fixture fixture = longFixture();
        trigger(fixture, fixture.selected());
        var export = fixture.sources().export();
        var scores = ClassAScoreFilesV2.loadBlind(ClassAPacketIndex.load(export.packetDirectory()),
                export.pScores(), export.sScores(), export.hScores(), ClassAAdjudicationFixture.MAPPING);
        ReviewSets sets = ReviewSets.compute(fixture.pairs(), scores, ClassAAdjudicationFixture.MAPPING);
        Map<String, Integer> allocation = new java.util.LinkedHashMap<>();
        allocation.put(LONG_DIRECT, 2);
        allocation.put(LONG_POOL, 1);
        List<String> selected = Expansion.draw(SeedStreamRegistry.load(fixture.registry()),
                new DrawLog(fixture.log()), fixture.pairs(), sets, List.of(),
                new Expansion.WorkflowScope(LONG_WORKFLOW, allocation), 1, CLOCK);
        reviewPairs(fixture, selected);
        var assembly = ClassAEvidenceAssembler.assemble(fixture.sources());
        var obligation = assembly.expansionObligations().get(LONG_WORKFLOW);
        assertThat(obligation.triggerPairs()).containsExactly(fixture.selected());
        assertThat(obligation.allocationByStem()).containsEntry(LONG_DIRECT, 2)
                .containsEntry(LONG_POOL, 1).containsEntry(BOARD_POOL, 0);
        assertThat(obligation.drawnPairs()).containsExactlyElementsOf(selected);
        assertThat(obligation.frameExhausted()).isFalse();
        assertThat(obligation.requiredPairs()).isEqualTo(3);
        assertThat(assembly.drawEntries().stream().filter(e -> e.purpose().equals(SeedStreamRegistry.EXPANSION))
                .map(DrawLog.Entry::workflow).toList()).containsExactly(LONG_DIRECT, LONG_POOL);
        PairSampler.draw(SeedStreamRegistry.load(fixture.registry()), new DrawLog(fixture.log()),
                fixture.pairs(), BOARD_POOL, SeedStreamRegistry.EXPANSION, 1, List.of(), 1, CLOCK);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("priority allocation");
    }

    @Test void triggerRequiresTheCompleteExpansionAndDisclosesInsufficientFrame() throws Exception {
        Fixture fixture = fixture(true, false);
        trigger(fixture, fixture.selected());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("Expansion entries");
        drawExpansion(fixture, STEM, 1);
        Path human = fixture.sources().export().hScores();
        List<String> reviews = new ArrayList<>(Files.readAllLines(human));
        for (String id : fixture.pairs().blindItemIds()) if (!fixture.selected().equals(fixture.pairs().pairOf(id)))
            reviews.add(ClassAAdjudicationFixture.human(id, "C", false).toString());
        Files.write(human, reviews);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        var disclosure = ClassAEvidenceAssembler.assemble(fixture.sources()).expansionObligations().get(STEM);
        assertThat(disclosure.triggerPairs()).containsExactly(fixture.selected());
        assertThat(disclosure.allocationByStem()).containsEntry(STEM, 1);
        assertThat(disclosure.drawnPairs()).hasSize(1);
        assertThat(disclosure.frameExhausted()).isTrue();
        assertThat(disclosure.requiredPairs()).isEqualTo(1);
    }

    @Test void noTriggerForbidsExpansionAndTwoInitialTriggersStopBeforeIt() throws Exception {
        Fixture noTrigger = fixture(true, false);
        drawExpansion(noTrigger, STEM, 1);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(noTrigger.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("without trigger");

        Fixture two = fixture(true, true);
        trigger(two, two.selected());
        mandatoryDoubleMiss(two);
        var disclosure = ClassAEvidenceAssembler.assemble(two.sources()).expansionObligations().get(STEM);
        assertThat(disclosure.triggerPairs()).hasSize(2);
        assertThat(disclosure.drawnPairs()).isEmpty();
        drawExpansion(two, STEM, 1);
        // The key-free obligation accepts this stop state; the existing converter rejects PENDING_REVIEW.
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(two.sources()))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining(PENDING);
        Path human = two.sources().export().hScores();
        List<String> reviews = new ArrayList<>(Files.readAllLines(human));
        for (String id : two.pairs().blindItemIds()) if (!two.selections().contains(two.pairs().pairOf(id))
                && !two.pairs().pairs().getLast().id().equals(two.pairs().pairOf(id)))
            reviews.add(ClassAAdjudicationFixture.human(id, "C", false).toString());
        Files.write(human, reviews);
        relock(two.lock(), two.seal(), two.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(two.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("second double miss");
    }

    @Test void expansionPlanRequiresEveryWorkflowAndExactAllocation() throws Exception {
        String[] invalid = {
                "{\"stems\":[{\"stem\":\"NOTE_ADAPTIVE\",\"count\":1}]}",
                "{\"stems\":[{\"stem\":\"NOTE_ADAPTIVE\",\"count\":1}],\"expansion\":[]}",
                "{\"stems\":[{\"stem\":\"NOTE_ADAPTIVE\",\"count\":1}],\"expansion\":[{\"workflow\":\"NOTE_ADAPTIVE\",\"stemPriority\":[\"NOTE_ADAPTIVE\"],\"initialAllocation\":{\"NOTE_ADAPTIVE\":2}}]}",
                "{\"stems\":[{\"stem\":\"NOTE_ADAPTIVE\",\"count\":1}],\"expansion\":[{\"workflow\":\"NOTE_ADAPTIVE\",\"stemPriority\":[\"NOTE_ADAPTIVE\"],\"initialAllocation\":{\"OTHER\":3}}]}",
                "{\"stems\":[{\"stem\":\"NOTE_ADAPTIVE\",\"count\":1}],\"expansion\":[{\"workflow\":\"NOTE_ADAPTIVE\",\"stemPriority\":[\"OTHER\"],\"initialAllocation\":{\"OTHER\":3}}]}",
                "{\"stems\":[{\"stem\":\"NOTE_ADAPTIVE\",\"count\":1}],\"expansion\":[{\"workflow\":\"NOTE_ADAPTIVE\",\"stemPriority\":[\"NOTE_ADAPTIVE\"],\"initialAllocation\":{\"NOTE_ADAPTIVE\":3},\"extra\":1}]}"
        };
        for (String body : invalid) {
            Fixture fixture = fixture(true, false);
            Path plan = fixture.sources().manifests().get(SPOT_PLAN);
            Files.writeString(plan, body);
            repin(fixture.sources(), plan);
            assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                    .isInstanceOf(InvalidClassAAssembly.class);
        }
        Fixture duplicate = fixture(true, false);
        Path plan = duplicate.sources().manifests().get(SPOT_PLAN);
        Files.writeString(plan, Files.readString(plan).replace("\"stemPriority\":[\"NOTE_ADAPTIVE\"]",
                "\"stemPriority\":[\"NOTE_ADAPTIVE\",\"NOTE_ADAPTIVE\"]"));
        assertThatThrownBy(() -> ClassAEvidenceAssembler.spotPlan(plan))
                .isInstanceOf(InvalidClassAAssembly.class);
        Fixture omitted = fixture(true, false, false, true);
        Path omittedPlan = omitted.sources().manifests().get(SPOT_PLAN);
        ObjectNode parsed = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(omittedPlan));
        ((ArrayNode) parsed.path("expansion")).remove(1);
        Files.writeString(omittedPlan, parsed.toString());
        repin(omitted.sources(), omittedPlan);
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(omitted.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("workflows");
        Files.writeString(plan, Files.readString(plan)
                .replace("\"stemPriority\":[\"NOTE_ADAPTIVE\",\"NOTE_ADAPTIVE\"]",
                        "\"stemPriority\":[\"NOTE_ADAPTIVE\"]")
                .replace("\"NOTE_ADAPTIVE\":3", "\"NOTE_ADAPTIVE\":2"));
        assertThatThrownBy(() -> ClassAEvidenceAssembler.spotPlan(plan))
                .isInstanceOf(InvalidClassAAssembly.class);
    }

    @Test void lockValidatorChecksExpansionBeforeWritingLock() throws Exception {
        Fixture fixture = fixture(true, false);
        trigger(fixture, fixture.selected());
        var export = fixture.sources().export();
        var context = new LockReadiness.ExpansionObligationContext(export.packetDirectory(),
                fixture.sources().freezeFile(),
                fixture.sources().manifests().get(PRIMARY_MANIFEST), fixture.sources().manifests().get(SPOT_PLAN),
                fixture.registry(), fixture.log(), export.pScores(), export.sScores(), export.hScores(),
                ClassAAdjudicationFixture.MAPPING, Set.of(STEM));
        var validator = LockReadiness.asValidator(Map::of, Set::of, context);
        Path prospective = fixture.root().resolve("prospective-lock.json");
        var sealed = ArmKeySeal.load(fixture.seal());
        assertThatThrownBy(() -> LockRecord.create(prospective, LockRecord.Kind.PRIMARY, Set.of(STEM),
                List.of(fixture.findings()), sealed, fixture.seal(), validator, ArmKeyGateFixtures.LOCKED))
                .isInstanceOf(LockRecord.InvalidLock.class).hasCauseInstanceOf(InvalidClassAAssembly.class);
        assertThat(Files.exists(prospective)).isFalse();
        List<String> drawn = expansionDraw(fixture, 1);
        reviewPairs(fixture, drawn);
        LockRecord.create(prospective, LockRecord.Kind.PRIMARY, Set.of(STEM),
                List.of(fixture.findings()), sealed, fixture.seal(), validator, ArmKeyGateFixtures.LOCKED);
        assertThat(Files.exists(prospective)).isTrue();
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
        Files.writeString(plan, Files.readString(plan).replace("\"count\":1}]",
                "\"count\":1}, {\"stem\":\"PLAN_ADAPTIVE\",\"count\":1}]"));
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

    @Test void expansionSelectionIsReviewRequiredAndReplayExact() throws Exception {
        Fixture fixture = fixture(true, false);
        trigger(fixture, fixture.selected());
        Path human = fixture.sources().export().hScores();
        List<String> rows = new ArrayList<>(Files.readAllLines(human));
        for (String id : fixture.pairs().blindItemIds())
            if (!fixture.selected().equals(fixture.pairs().pairOf(id)))
                rows.add(ClassAAdjudicationFixture.human(id, "C", false).toString());
        Files.write(human, rows);
        SeedStreamRegistry registry = SeedStreamRegistry.load(fixture.registry());
        String remaining = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(fixture.selected())).findFirst().orElseThrow();
        drawExpansion(fixture, STEM, 1);
        ClassAAssembly assembly = ClassAEvidenceAssembler.assemble(fixture.sources());
        assertThat(assembly.reviewRequiredPairs()).contains(remaining);
        assertThat(assembly.expansionObligations().get(STEM).drawnPairs()).containsExactly(remaining);
        PairSampler.draw(registry, new DrawLog(fixture.log()), fixture.pairs(), STEM,
                SeedStreamRegistry.EXPANSION, 2, List.of(remaining), 1, CLOCK);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class);
    }

    @Test void expansionCannotSelectSpotPairOrClaimOversizedFrame() throws Exception {
        Fixture fixture = fixture(true, false);
        trigger(fixture, fixture.selected());
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
        trigger(fixture, fixture.selected());
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
        trigger(second, second.selected());
        String remaining = second.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(second.selected())).findFirst().orElseThrow();
        SeedStreamRegistry registry = SeedStreamRegistry.load(second.registry());
        PairSampler.draw(registry, new DrawLog(second.log()), second.pairs(), STEM,
                SeedStreamRegistry.EXPANSION, 3, List.of(remaining), 1, CLOCK);
        PairSampler.draw(registry, new DrawLog(second.log()), second.pairs(), STEM,
                SeedStreamRegistry.EXPANSION, 2, List.of(remaining), 1, CLOCK);
        relock(second.lock(), second.seal(), second.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(second.sources()))
                .isInstanceOf(InvalidClassAAssembly.class);
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
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("Extra draw entry");
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
        Fixture fixture = fixture(true, false, false, true, 1);
        trigger(fixture, fixture.selected());
        drawExpansion(fixture, STEM, 1);
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

    @Test void laterSpotCheckRoundsAreRefused() throws Exception {
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
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("Spot-check draw");
    }

    @Test void aLoneSecondSpotCheckRoundIsRefused() throws Exception {
        Fixture fixture = fixture(true, false);
        Path registryFile = fixture.registry();
        Files.writeString(registryFile, Files.readString(registryFile)
                .replace("\"purpose\":\"SPOT_CHECK\",\"rounds\":1",
                        "\"purpose\":\"SPOT_CHECK\",\"rounds\":2"));
        detached(registryFile);
        Files.delete(fixture.log());
        PairSampler.draw(SeedStreamRegistry.load(registryFile), new DrawLog(fixture.log()),
                fixture.pairs(), STEM, SeedStreamRegistry.SPOT_CHECK, 2,
                fixture.pairs().pairs().stream().map(PairMap.Pair::id).toList(), 1, CLOCK);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("Spot-check draw");
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
            String invalidStemPlan = template.replace("%s", STEM);
            Files.writeString(plan, invalidStemPlan.substring(0, invalidStemPlan.length() - 1)
                    + ",\"expansion\":[{\"workflow\":\"" + STEM + "\",\"stemPriority\":[\"" + STEM
                    + "\"],\"initialAllocation\":{\"" + STEM + "\":3}}]}");
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
        trigger(fixture, fixture.selected());
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
        trigger(outside, outside.selected());
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

    private LockReadiness.ExpansionObligationContext lockContext(Fixture fixture) {
        var export = fixture.sources().export();
        return new LockReadiness.ExpansionObligationContext(export.packetDirectory(),
                fixture.sources().freezeFile(), fixture.sources().manifests().get(PRIMARY_MANIFEST),
                fixture.sources().manifests().get(SPOT_PLAN), fixture.registry(), fixture.log(),
                export.pScores(), export.sScores(), export.hScores(),
                ClassAAdjudicationFixture.MAPPING, Set.of(STEM));
    }

    private void lockRefused(Fixture fixture, List<Path> findings,
            java.util.function.Supplier<Map<String, QuestionStatus>> statuses) throws Exception {
        var validator = LockReadiness.asValidator(statuses, Set::of, lockContext(fixture));
        Path prospective = fixture.root().resolve("refused-lock-" + java.util.UUID.randomUUID() + ".json");
        var sealed = ArmKeySeal.load(fixture.seal());
        assertThatThrownBy(() -> LockRecord.create(prospective, LockRecord.Kind.PRIMARY, Set.of(STEM),
                findings, sealed, fixture.seal(), validator, ArmKeyGateFixtures.LOCKED))
                .isInstanceOf(LockRecord.InvalidLock.class);
        assertThat(Files.exists(prospective)).isFalse();
    }

    @Test void lockValidatorRefusesMissingFindingsUnpinnedPlansTamperedDrawsAndStemMismatches() throws Exception {
        Fixture missing = fixture(true, false);
        List<Path> withoutLog = new ArrayList<>(List.of(missing.findings()));
        withoutLog.remove(missing.log());
        lockRefused(missing, withoutLog, Map::of);
        Fixture unpinned = fixture(true, false);
        Path plan = unpinned.sources().manifests().get(SPOT_PLAN);
        Files.writeString(plan, Files.readString(plan) + " ");
        lockRefused(unpinned, List.of(unpinned.findings()), Map::of);
        Fixture tampered = fixture(true, false);
        List<String> lines = Files.readAllLines(tampered.log());
        ObjectNode first = (ObjectNode) IntegritySupport.JSON.readTree(lines.getFirst());
        String other = tampered.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> !id.equals(tampered.selected())).findFirst().orElseThrow();
        ((ArrayNode) first.get("selectedPairIds")).removeAll().add(other);
        lines.set(0, first.toString());
        Files.write(tampered.log(), lines);
        lockRefused(tampered, List.of(tampered.findings()), Map::of);
        Fixture extraStem = fixture(true, false);
        Path extraPlan = extraStem.sources().manifests().get(SPOT_PLAN);
        Files.writeString(extraPlan, Files.readString(extraPlan).replace("\"stems\":[",
                "\"stems\":[{\"stem\":\"" + PLAN_STEM + "\",\"count\":1},"));
        repin(extraStem.sources(), extraPlan);
        lockRefused(extraStem, List.of(extraStem.findings()), Map::of);
        Fixture pending = fixture(true, false);
        var pendingStatus = FinalStatus.of(ClassAAdjudicationFixture.direct("blind-x", "C", "C", 0, null),
                ClassAAdjudicationFixture.MAPPING, true);
        lockRefused(pending, List.of(pending.findings()), () -> Map.of("blind-x", pendingStatus));
    }

    @Test void aForgedExhaustedFlagInAnExpansionEntryIsRefused() throws Exception {
        Fixture fixture = fixture(true, false, false, false, 2);
        trigger(fixture, fixture.selected());
        List<String> drawn = expansionDraw(fixture, 3);
        reviewPairs(fixture, drawn);
        List<String> lines = Files.readAllLines(fixture.log());
        ObjectNode last = (ObjectNode) IntegritySupport.JSON.readTree(lines.getLast());
        last.put("exhausted", true);
        lines.set(lines.size() - 1, last.toString());
        Files.write(fixture.log(), lines);
        relock(fixture.lock(), fixture.seal(), fixture.findings());
        assertThatThrownBy(() -> ClassAEvidenceAssembler.assemble(fixture.sources()))
                .isInstanceOf(InvalidClassAAssembly.class);
    }

    private static Map<String, ExpansionObligation.Disclosure> obligation(Fixture fixture) throws Exception {
        var export = fixture.sources().export();
        var scores = ClassAScoreFilesV2.loadBlind(ClassAPacketIndex.load(export.packetDirectory()),
                export.pScores(), export.sScores(), export.hScores(), ClassAAdjudicationFixture.MAPPING);
        ReviewSets sets = ReviewSets.compute(fixture.pairs(), scores, ClassAAdjudicationFixture.MAPPING);
        var registry = SeedStreamRegistry.load(fixture.registry());
        var entries = new DrawLog(fixture.log()).entries();
        Set<String> selected = new java.util.HashSet<>();
        entries.forEach(entry -> selected.addAll(entry.selectedPairIds()));
        Set<String> required = ReviewSets.reviewRequiredPairs(sets, selected);
        Map<String, QuestionStatus> statuses = new HashMap<>();
        for (var item : scores.values()) statuses.put(item.id(), FinalStatus.of(item,
                ClassAAdjudicationFixture.MAPPING, required.contains(fixture.pairs().pairOf(item.id()))));
        var plan = ClassAEvidenceAssembler.spotPlan(fixture.sources().manifests().get(SPOT_PLAN));
        return ExpansionObligation.verify(plan, Map.of(STEM, Set.of(STEM), PLAN_STEM, Set.of(PLAN_STEM)),
                fixture.pairs(), sets, statuses, entries, registry);
    }

    @Test void twoWorkflowsInOneExportAreJudgedIndependently() throws Exception {
        Fixture fixture = fixture(true, false, false, true, 2);
        trigger(fixture, fixture.selected());
        var exported = fixture.sources().export();
        var scored = ClassAScoreFilesV2.loadBlind(ClassAPacketIndex.load(exported.packetDirectory()),
                exported.pScores(), exported.sScores(), exported.hScores(), ClassAAdjudicationFixture.MAPPING);
        ReviewSets frames = ReviewSets.compute(fixture.pairs(), scored, ClassAAdjudicationFixture.MAPPING);
        int capacity = (int) frames.frameByStem().get(STEM).stream()
                .filter(id -> !fixture.selections().contains(id)).count();
        List<String> drawn = expansionDraw(fixture, Math.min(3, capacity));
        reviewPairs(fixture, drawn);
        var verified = obligation(fixture);
        assertThat(verified.get(STEM).triggerPairs()).hasSize(1);
        assertThat(verified.get(PLAN_STEM).triggerPairs()).isEmpty();
        assertThat(verified.get(PLAN_STEM).drawnPairs()).isEmpty();
        String planPair = fixture.pairs().pairs().stream().map(PairMap.Pair::id)
                .filter(id -> PLAN_STEM.equals(fixture.pairs().workflowOf(id))).findFirst().orElseThrow();
        ObjectNode forged = IntegritySupport.JSON.createObjectNode();
        forged.put("workflow", PLAN_STEM).put("purpose", SeedStreamRegistry.EXPANSION).put("round", 1)
                .put("frameSha256", "a".repeat(64)).put("frameSize", 1).put("count", 1)
                .put("exhausted", false).put("registrySha256", SeedStreamRegistry.load(fixture.registry()).sha256())
                .put("clockUtc", CLOCK);
        forged.set("selectedPairIds", IntegritySupport.JSON.createArrayNode().add(planPair));
        new DrawLog(fixture.log()).append(forged);
        assertThatThrownBy(() -> obligation(fixture))
                .isInstanceOf(InvalidClassAAssembly.class).hasMessageContaining("without trigger");
    }
}
