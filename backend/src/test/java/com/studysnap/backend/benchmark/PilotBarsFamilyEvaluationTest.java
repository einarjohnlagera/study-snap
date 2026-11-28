package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PilotBarsFamilyEvaluationTest {
    private static final int[] SIZES = {7, 7, 10, 24, 30, 48};
    private static final String P = "P";
    private static final String S = "S";
    private static final String FAMILY_SHA = "familiesSha256";
    @TempDir Path temp;
    private record Setup(Path root, PilotKey key, PilotBars bars, PilotFamilies families, Path report,
                         Map<String, ClassAScoreFilesV2.ItemScores> scores, Path fixtures, Path labels, Path familiesFile) {}

    private static List<PlantedType> pool(boolean openai, int openCritical) {
        Map<PlantedType, Integer> counts = new EnumMap<>(PlantedType.class);
        int wrong = openai ? openCritical - 8 : 14 - openCritical;
        counts.put(PlantedType.WRONG_KEY, wrong);
        counts.put(PlantedType.EXPLANATION_CONTRADICTS_KEY, openai ? 2 : 1);
        counts.put(PlantedType.SECOND_DEFENSIBLE_ANSWER, openai ? 2 : 3);
        counts.put(PlantedType.MATERIAL_AMBIGUITY_MULTI, 1);
        counts.put(PlantedType.MATERIAL_AMBIGUITY_ZERO, 1);
        counts.put(PlantedType.SOURCE_CONTRADICTION, openai ? 2 : 1);
        counts.put(PlantedType.MINOR_WORDING, openai ? 2 : 1);
        counts.put(PlantedType.NUMERIC_INCONSISTENCY, 1);
        counts.put(PlantedType.WEAK_DISTRACTOR, 2);
        counts.put(PlantedType.UNHELPFUL_EXPLANATION, openai ? 1 : 2);
        counts.put(PlantedType.CLEAN, openai ? 62 - openCritical - 6 : 64 - (21 - openCritical) - 6);
        List<PlantedType> result = new ArrayList<>();
        counts.forEach((type, n) -> { for (int i = 0; i < n; i++) result.add(type); });
        result.sort(java.util.Comparator.comparingInt(Enum::ordinal));
        return result;
    }
    private static ObjectNode barsNode() throws Exception {
        return (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(Path.of(
                "src/test/java/com/studysnap/backend/benchmark/pilot-bars-test-only.json")));
    }
    private static Path writeJson(Path file, ObjectNode node) throws Exception {
        Files.writeString(file, node.toString());
        Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(Files.readAllBytes(file)));
        return file;
    }
    private Setup setup(int openCritical) throws Exception {
        Path run = Files.createTempDirectory(temp, "run-");
        Path fixtures = Files.createDirectory(run.resolve("fixtures"));
        List<PlantedType> open = pool(true, openCritical), other = pool(false, openCritical);
        List<String> keyLines = new ArrayList<>(), labels = new ArrayList<>();
        int a = 0, b = 0, serial = 0;
        boolean rootSet = false;
        for (int packetIndex = 0; packetIndex < SIZES.length; packetIndex++) {
            String id = "pilot-%02d".formatted(packetIndex + 1);
            boolean openai = packetIndex == 0 || packetIndex == 1 || packetIndex == 5;
            ObjectNode fixture = IntegritySupport.JSON.createObjectNode();
            fixture.put("packetId", id);
            fixture.putArray("sourceNoteTexts").add("Synthetic source " + id);
            ArrayNode questions = fixture.putArray("questions");
            for (int index = 0; index < SIZES[packetIndex]; index++) {
                PlantedType type = openai ? open.get(a++) : other.get(b++);
                String itemId = "pilot-%05d".formatted(++serial);
                boolean input = openai && type == PlantedType.WRONG_KEY && !rootSet;
                if (input) rootSet = true;
                ObjectNode question = ClassAScoreSchemaV2Test.packet("MCQ").question().deepCopy();
                question.put("question", "Synthetic question " + itemId);
                question.putArray("choices").add("A " + itemId).add("B " + itemId);
                question.put("correctIndex", 0).put("explanation", "Synthetic explanation " + itemId);
                ObjectNode item = questions.addObject();
                item.set("question", question);
                ObjectNode planted = item.putObject("planted");
                planted.put("plantedType", type.name()).put("authoringFamily", openai ? "OPENAI" : "ANTHROPIC")
                        .put("rootCauseInput", input);
                ObjectNode row = IntegritySupport.JSON.createObjectNode();
                row.put("id", itemId).put("packetId", id).put("plantedType", type.name())
                        .put("severity", type.severity().name())
                        .put("authoringFamily", openai ? "OPENAI" : "ANTHROPIC")
                        .put("rootCauseInput", input).put("canary", "CANARY-0123456789ABCDEF");
                keyLines.add(row.toString());
            }
            Path fixtureFile = fixtures.resolve(id + ".json");
            Files.writeString(fixtureFile, fixture.toString());
            String hash = FreezeRecord.sha256(fixtureFile);
            for (int i = 0; i < SIZES[packetIndex]; i++) {
                ObjectNode label = IntegritySupport.JSON.createObjectNode();
                label.put("packetId", id).put("index", i).put("verifier", openai ? "ANTHROPIC" : "OPENAI")
                        .put("fixtureSha256", hash).put("verdict", "LABEL_CONFIRMED").put("code", "NONE");
                labels.add(label.toString());
            }
        }
        Path labelsFile = run.resolve("labels.jsonl");
        Files.writeString(labelsFile, String.join("\n", labels) + "\n");
        Path keyFile = run.resolve("synthetic-key.jsonl");
        Files.writeString(keyFile, String.join("\n", keyLines) + "\n");
        Files.writeString(IntegritySupport.detached(keyFile), FreezeRecord.sha256(Files.readAllBytes(keyFile)));
        PilotKey key = PilotKey.load(keyFile);
        ObjectNode barsJson = barsNode();
        barsJson.putObject("scorerFamilies").put(P, "OPENAI").put(S, "ANTHROPIC");
        Path barsFile = writeJson(run.resolve("bars.json"), barsJson);
        PilotBars bars = PilotBars.load(barsFile);
        Path familiesFile = PilotFamiliesTest.write(run, PilotFamiliesTest.valid(bars.limits(P).model(), bars.limits(S).model()));
        PilotFamilies families = PilotFamilies.load(familiesFile);
        ObjectNode report = IntegritySupport.JSON.createObjectNode(); report.put(FAMILY_SHA, families.sha256OfBytes());
        Path reportFile = writeJson(run.resolve("report.json"), report);
        return new Setup(run, key, bars, families, reportFile, PilotTestKeys.scores(key), fixtures, labelsFile, familiesFile);
    }
    private PilotBarsEvaluator.PilotBarReport evaluate(Setup setup) {
        return PilotBarsEvaluator.evaluateByFamily(setup.key(), setup.scores(), ClassAScoreSchemaV2Test.MAPPING,
                setup.bars(), List.of(), PilotTestKeys.ratings(setup.key(), setup.scores(), setup.bars()),
                setup.families(), setup.report());
    }
    private void clean(Setup setup, PilotKey.Row row, String role) {
        var old = setup.scores().get(row.id());
        var slot = ClassAScoreFilesV2.Slot.ok(ClassAScoreSchemaV2Test.scorer(row.id(), role));
        setup.scores().put(row.id(), new ClassAScoreFilesV2.ItemScores(row.id(),
                P.equals(role) ? slot : old.p(), S.equals(role) ? slot : old.s(), null));
    }
    private List<PilotKey.Row> critical(Setup setup, ModelFamily family) {
        return setup.key().rows().stream().filter(row -> row.severity() == PlantedType.Severity.CRITICAL
                && row.authoringFamily() == family).toList();
    }
    @Test void exactCrossBoundariesAndPooledClause() throws Exception {
        Setup setup = setup(11);
        assertThat(critical(setup, ModelFamily.ANTHROPIC)).hasSize(10);
        assertThat(critical(setup, ModelFamily.OPENAI)).hasSize(11);
        clean(setup, critical(setup, ModelFamily.ANTHROPIC).getFirst(), P);
        assertThat(evaluate(setup).scorers().get(P).bars().get(2).passed()).isTrue();
        clean(setup, critical(setup, ModelFamily.ANTHROPIC).get(1), P);
        assertThat(evaluate(setup).scorers().get(P).bars().get(2).passed()).isFalse();
        setup = setup(11);
        clean(setup, critical(setup, ModelFamily.OPENAI).getFirst(), S);
        assertThat(evaluate(setup).scorers().get(S).bars().get(2).passed()).isTrue();
        clean(setup, critical(setup, ModelFamily.OPENAI).get(1), S);
        assertThat(evaluate(setup).scorers().get(S).bars().get(2).passed()).isFalse();
    }
    @Test void ownRecallCannotRescueCrossAndOtherClausesRemain() throws Exception {
        Setup setup = setup(11);
        for (int i = 0; i < 2; i++) clean(setup, critical(setup, ModelFamily.ANTHROPIC).get(i), P);
        var p = evaluate(setup).scorers().get(P);
        assertThat(p.ownFamilyCriticalRecall()).isEqualTo(1.0);
        assertThat(p.bars().get(2).observed()).contains("critical=19", "cross=8/10");
        assertThat(p.bars().get(2).passed()).isFalse();
        setup = setup(11);
        for (int i = 0; i < 3; i++) clean(setup, critical(setup, ModelFamily.OPENAI).get(i), P);
        assertThat(evaluate(setup).scorers().get(P).bars().get(2).observed()).contains("critical=18");
        assertThat(evaluate(setup).scorers().get(P).bars().get(2).passed()).isFalse();
    }
    @Test void materialAndZeroDefensibleClausesRemainRequired() throws Exception {
        Setup setup = setup(11);
        for (PilotKey.Row row : setup.key().rows()) {
            if (row.plantedType() == PlantedType.MATERIAL_AMBIGUITY_MULTI) {
                var old = setup.scores().get(row.id());
                ObjectNode changed = (ObjectNode) old.p().row().deepCopy();
                changed.put("ambiguityClass", "NONE");
                setup.scores().put(row.id(), new ClassAScoreFilesV2.ItemScores(row.id(),
                        ClassAScoreFilesV2.Slot.ok(changed), old.s(), null));
            }
        }
        assertThat(evaluate(setup).scorers().get(P).bars().get(2).passed()).isFalse();
        setup = setup(11);
        PilotKey.Row zero = setup.key().rows().stream()
                .filter(row -> row.plantedType() == PlantedType.MATERIAL_AMBIGUITY_ZERO).findFirst().orElseThrow();
        var old = setup.scores().get(zero.id());
        ObjectNode changed = (ObjectNode) old.p().row().deepCopy();
        changed.put("keyDefensible", true);
        setup.scores().put(zero.id(), new ClassAScoreFilesV2.ItemScores(zero.id(),
                ClassAScoreFilesV2.Slot.ok(changed), old.s(), null));
        assertThat(evaluate(setup).scorers().get(P).bars().get(2).passed()).isFalse();
    }
    @Test void familyAndHistoricalBarResultsCoincideAcrossAllLegalAllocations() throws Exception {
        for (int openCritical : List.of(9, 10, 11, 12)) {
            Setup setup = setup(openCritical);
            var family = evaluate(setup);
            PilotBars legacyBars = PilotTestKeys.bars(Files.createDirectory(temp.resolve("legacy-bars-" + openCritical)));
            var legacy = PilotBarsEvaluator.evaluate(setup.key(), setup.scores(), ClassAScoreSchemaV2Test.MAPPING,
                    legacyBars, List.of(), PilotTestKeys.ratings(setup.key(), setup.scores(), legacyBars));
            assertThat(family.scorers().get(P).bars()).isEqualTo(legacy.scorers().get(P).bars());
            assertThat(family.scorers().get(S).bars()).isEqualTo(legacy.scorers().get(S).bars());
        }
    }
    @Test void barsFieldAndLegacyBarrier() throws Exception {
        Setup setup = setup(11);
        assertThat(setup.bars().scorerFamilies()).containsEntry(P, ModelFamily.OPENAI).containsEntry(S, ModelFamily.ANTHROPIC);
        assertThatThrownBy(() -> evaluateLegacy(setup, setup.bars()))
                .isInstanceOf(PilotBars.InvalidPilotBars.class).hasMessageContaining("Family bars");
        Path source = Path.of("src/test/java/com/studysnap/backend/benchmark/pilot-bars-test-only.json");
        PilotBars old = PilotBars.load(source);
        assertThat(old.scorerFamilies()).isNull();
        assertThatThrownBy(() -> evaluateWithBars(setup, old)).isInstanceOf(PilotBars.InvalidPilotBars.class)
                .hasMessageContaining("scorerFamilies");
    }
    @Test void barsFamilySchemaIsStrict() throws Exception {
        ObjectNode root = barsNode();
        ((ObjectNode) root.path("bars")).putObject("scorerFamilies").put(P, "OPENAI").put(S, "ANTHROPIC");
        Path file = writeJson(temp.resolve("bars.json"), root);
        Path nested = file;
        assertThatThrownBy(() -> PilotBars.load(nested)).isInstanceOf(PilotBars.InvalidPilotBars.class);
        root = barsNode(); root.putObject("scorerFamilies").put(P, "OPENAI"); file = writeJson(file, root);
        Path missing = file;
        assertThatThrownBy(() -> PilotBars.load(missing)).isInstanceOf(PilotBars.InvalidPilotBars.class);
        root = barsNode(); root.putObject("scorerFamilies").put(P, "GOOGLE").put(S, "ANTHROPIC"); file = writeJson(file, root);
        Path unknown = file;
        assertThatThrownBy(() -> PilotBars.load(unknown)).isInstanceOf(PilotBars.InvalidPilotBars.class);
        root = barsNode(); root.putObject("scorerFamilies").put(P, "ANTHROPIC").put(S, "OPENAI"); file = writeJson(file, root);
        Path swapped = file;
        assertThatThrownBy(() -> PilotBars.load(swapped)).isInstanceOf(PilotBars.InvalidPilotBars.class);
    }
    @Test void keyAndReportIntegrityAreChecked() throws Exception {
        Setup setup = setup(11);
        ObjectNode rows = (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(setup.root().resolve("synthetic-key.jsonl")).lines().findFirst().orElseThrow());
        rows.put("packetId", "pilot-07");
        PilotKey outside = changedKey(setup, rows);
        assertThatThrownBy(() -> evaluateWithKey(setup, outside)).isInstanceOf(PilotBars.InvalidPilotBars.class)
                .hasMessageContaining("Unknown family packet");
        rows.put("packetId", "pilot-03");
        PilotKey mismatch = changedKey(setup, rows);
        assertThatThrownBy(() -> evaluateWithKey(setup, mismatch)).isInstanceOf(PilotBars.InvalidPilotBars.class)
                .hasMessageContaining("Key author family");
        ObjectNode report = (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(setup.report()));
        report.put(FAMILY_SHA, "0".repeat(64)); writeJson(setup.report(), report);
        assertThatThrownBy(() -> evaluate(setup)).isInstanceOf(PilotBars.InvalidPilotBars.class)
                .hasMessageContaining("families SHA-256");
        report.put(FAMILY_SHA, setup.families().sha256OfBytes()); writeJson(setup.report(), report);
        ObjectNode changedFamilies = PilotFamiliesTest.valid(setup.bars().limits(P).model(), setup.bars().limits(S).model());
        ((ObjectNode) changedFamilies.path("packets").path("pilot-01")).put("authorTool", "different-runner");
        PilotFamiliesTest.write(setup.root(), changedFamilies);
        PilotFamilies rewritten = PilotFamilies.load(setup.familiesFile());
        assertThatThrownBy(() -> evaluateWithFamilies(setup, rewritten))
                .isInstanceOf(PilotBars.InvalidPilotBars.class).hasMessageContaining("families SHA-256");
        Files.writeString(setup.report(), Files.readString(setup.report()) + " ");
        assertThatThrownBy(() -> evaluate(setup)).isInstanceOf(PilotBars.InvalidPilotBars.class)
                .hasMessageContaining("report digest");
        writeJson(setup.report(), report); Files.delete(IntegritySupport.detached(setup.report()));
        assertThatThrownBy(() -> evaluate(setup)).isInstanceOf(PilotBars.InvalidPilotBars.class)
                .hasMessageContaining("report digest");
    }
    private PilotKey changedKey(Setup setup, ObjectNode first) throws Exception {
        Path source = setup.root().resolve("synthetic-key.jsonl");
        List<String> lines = new ArrayList<>(Files.readAllLines(source));
        lines.set(0, first.toString());
        Path changed = setup.root().resolve("changed-key.jsonl");
        Files.writeString(changed, String.join("\n", lines) + "\n");
        Files.writeString(IntegritySupport.detached(changed), FreezeRecord.sha256(Files.readAllBytes(changed)));
        return PilotKey.load(changed);
    }
    private PilotBarsEvaluator.PilotBarReport evaluateWithKey(Setup setup, PilotKey key) {
        return PilotBarsEvaluator.evaluateByFamily(key, setup.scores(), ClassAScoreSchemaV2Test.MAPPING,
                setup.bars(), List.of(), PilotTestKeys.ratings(setup.key(), setup.scores(), setup.bars()),
                setup.families(), setup.report());
    }
    @Test void scorerModelAndFamilyMustMatchBars() throws Exception {
        Setup setup = setup(11);
        ObjectNode node = PilotFamiliesTest.valid("wrong-model", setup.bars().limits(S).model());
        PilotFamiliesTest.write(setup.root(), node);
        PilotFamilies wrongModel = PilotFamilies.load(setup.familiesFile());
        assertThatThrownBy(() -> evaluateWithFamilies(setup, wrongModel)).isInstanceOf(PilotBars.InvalidPilotBars.class)
                .hasMessageContaining("modelId");
        node = PilotFamiliesTest.valid(setup.bars().limits(P).model(), setup.bars().limits(S).model());
        ((ObjectNode) node.path("scorers").path(P)).put("family", "ANTHROPIC");
        PilotFamiliesTest.write(setup.root(), node);
        Path invalidFamilyFile = setup.familiesFile();
        assertThatThrownBy(() -> PilotFamilies.load(invalidFamilyFile))
                .isInstanceOf(PilotFamilies.InvalidPilotFamilies.class).hasMessageContaining("Scorer family");
    }
    private PilotBarsEvaluator.PilotBarReport evaluateWithFamilies(Setup setup, PilotFamilies families) {
        return PilotBarsEvaluator.evaluateByFamily(setup.key(), setup.scores(), ClassAScoreSchemaV2Test.MAPPING,
                setup.bars(), List.of(), PilotTestKeys.ratings(setup.key(), setup.scores(), setup.bars()),
                families, setup.report());
    }
    @Test void evaluatorCrossChecksFrozenScorerFamiliesIndependentlyOfLoader() throws Exception {
        Setup setup = setup(11);
        var constructor = PilotFamilies.class.getDeclaredConstructor(Map.class, Map.class, String.class);
        constructor.setAccessible(true);
        Map<String, PilotFamilies.Scorer> scorers = new HashMap<>(setup.families().scorers());
        PilotFamilies.Scorer p = scorers.get(P);
        scorers.put(P, new PilotFamilies.Scorer(ModelFamily.ANTHROPIC, p.tool(), p.modelId()));
        PilotFamilies inconsistent = constructor.newInstance(setup.families().packets(), scorers,
                setup.families().sha256OfBytes());
        assertThatThrownBy(() -> evaluateWithFamilies(setup, inconsistent))
                .isInstanceOf(PilotBars.InvalidPilotBars.class).hasMessageContaining("Scorer family differs");
    }
    @Test void assemblyBindsFamiliesAndReportsOnlyAllowedMetadata() throws Exception {
        Setup setup = setup(11);
        Path packetOut = setup.root().resolve("packets"), sealedOut = setup.root().resolve("sealed"), reportOut = setup.root().resolve("assembly-report.json");
        var assembled = assembleFamily(setup, packetOut, sealedOut, reportOut, setup.familiesFile());
        assertThat(assembled.packets()).hasSize(6);
        Path copied = sealedOut.resolve("pilot-families.json");
        assertThat(Files.readAllBytes(copied)).isEqualTo(Files.readAllBytes(setup.familiesFile()));
        assertThat(IntegritySupport.detachedHash(copied)).isEqualTo(setup.families().sha256OfBytes());
        ObjectNode report = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(reportOut));
        assertThat(report.path(FAMILY_SHA).asText()).isEqualTo(setup.families().sha256OfBytes());
        assertThat(report.path("requiredCrossRecall").path(P).asText()).isEqualTo("9/10");
        assertThat(report.path("requiredCrossRecall").path(S).asText()).isEqualTo("10/11");
        Set<String> reportFields = new java.util.HashSet<>();
        report.fieldNames().forEachRemaining(reportFields::add);
        assertThat(reportFields).containsExactlyInAnyOrder("packetSizes", "compositionByType", "compositionBySource",
                "authoringCriticalTotals", "authoringPlantedTotals", "fixtureSha256", "packetSha256",
                "sealedSha256", "labelVerificationSha256", "seed", "confirmedByVerifier", "disputesRemaining",
                FAMILY_SHA, "familyByPacket", "scorerFamilies", "requiredCrossRecall");
        String reportText = Files.readString(reportOut);
        for (PilotFamilies.Packet packet : setup.families().packets().values())
            assertThat(reportText).doesNotContain(packet.authorTool(), packet.authorModelId(), packet.authorSessionId(),
                    packet.verifierTool(), packet.verifierModelId(), packet.verifierSessionId());
        for (PilotFamilies.Scorer scorer : setup.families().scorers().values())
            assertThat(reportText).doesNotContain(scorer.tool(), scorer.modelId());
        var checked = PilotFixtureAssembly.check(setup.fixtures());
        assertThat(report.path("compositionByType")).isEqualTo(IntegritySupport.JSON.valueToTree(checked.counts()));
        assertThat(report.path("compositionBySource")).isEqualTo(IntegritySupport.JSON.valueToTree(checked.bySource()));
        assertThat(report.path("authoringCriticalTotals")).isEqualTo(IntegritySupport.JSON.valueToTree(checked.critical()));
        assertThat(report.path("authoringPlantedTotals")).isEqualTo(IntegritySupport.JSON.valueToTree(checked.planted()));
        assertThat(report.path("fixtureSha256")).isEqualTo(IntegritySupport.JSON.valueToTree(checked.hashes()));
        assertThat(report.path("labelVerificationSha256").asText()).isEqualTo(FreezeRecord.sha256(setup.labels()));
        assertThat(report.path("sealedSha256").asText()).isEqualTo(FreezeRecord.sha256(assembled.sealed()));
        assertThat(report.path("seed").asLong()).isEqualTo(41L);
        for (int n = 1; n <= SIZES.length; n++) {
            String id = "pilot-%02d".formatted(n);
            assertThat(report.path("packetSizes").path(id).asInt()).isEqualTo(SIZES[n - 1]);
            assertThat(report.path("packetSha256").path(id).asText())
                    .isEqualTo(FreezeRecord.sha256(assembled.packets().get(n - 1)));
        }
        Path familyFile = setup.familiesFile();
        assertThatThrownBy(() -> assembleFamily(setup, packetOut, sealedOut, reportOut, familyFile)).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class)
                .hasMessageContaining("already exists");
    }
    @Test void invalidFamilyAndLocationsLeaveNoOutput() throws Exception {
        Setup setup = setup(11);
        Path packetOut = setup.root().resolve("packets"), sealedOut = setup.root().resolve("sealed"), reportOut = setup.root().resolve("assembly-report.json");
        ObjectNode invalid = PilotFamiliesTest.valid("p-model", "s-model");
        ((ObjectNode) invalid.path("packets").path("pilot-01")).put("authorTool", "Codex");
        PilotFamiliesTest.write(setup.root(), invalid);
        Path familyFile = setup.familiesFile();
        assertThatThrownBy(() -> assembleFamily(setup, packetOut, sealedOut, reportOut, familyFile)).isInstanceOf(PilotFamilies.InvalidPilotFamilies.class);
        assertThat(Files.exists(packetOut)).isFalse(); assertThat(Files.exists(sealedOut)).isFalse();
        PilotFamiliesTest.write(setup.root(), PilotFamiliesTest.valid("p-model", "s-model"));
        for (Path inside : List.of(setup.fixtures().resolve("family.json"), packetOut.resolve("family.json"),
                sealedOut.resolve("family.json"))) {
            if (!inside.startsWith(setup.fixtures())) {
                Files.createDirectories(inside.getParent());
                Files.copy(setup.familiesFile(), inside);
                Files.copy(IntegritySupport.detached(setup.familiesFile()), IntegritySupport.detached(inside));
            }
            assertThatThrownBy(() -> assembleFamily(setup, packetOut, sealedOut, reportOut, inside)).isInstanceOf(PilotFamilies.InvalidPilotFamilies.class)
                    .hasMessageContaining("outside fixtures and outputs");
        }
    }
    @Test void assemblyRejectsOtherLegalCriticalSplits() throws Exception {
        for (int openCritical : List.of(9, 10, 12)) {
            Path base = Files.createDirectory(temp.resolve("split-" + openCritical));
            PilotBarsFamilyEvaluationTest helper = new PilotBarsFamilyEvaluationTest();
            helper.temp = base;
            Setup setup = helper.setup(openCritical);
            assertThat(PilotFixtureAssembly.check(setup.fixtures()).critical()).containsEntry(ModelFamily.OPENAI, openCritical)
                    .containsEntry(ModelFamily.ANTHROPIC, 21 - openCritical);
            Path packetOut = base.resolve("packets"), sealedOut = base.resolve("sealed");
            Path out = base.resolve("out.json"), familyFile = setup.familiesFile();
            assertThatThrownBy(() -> assembleFamily(setup, packetOut, sealedOut, out, familyFile))
                    .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("critical allocation");
            assertThat(Files.exists(packetOut)).isFalse();
        }
    }

    @Test void emptyCrossSetIsRefusedAtEvaluation() throws Exception {
        Setup setup = setup(11);
        List<ObjectNode> rows = new ArrayList<>();
        for (String line : Files.readAllLines(setup.root().resolve("synthetic-key.jsonl")))
            rows.add((ObjectNode) IntegritySupport.JSON.readTree(line));
        List<ObjectNode> otherCritical = rows.stream().filter(row -> row.path("authoringFamily").asText().equals("ANTHROPIC")
                && row.path("severity").asText().equals("CRITICAL")).toList();
        List<ObjectNode> openClean = rows.stream().filter(row -> row.path("authoringFamily").asText().equals("OPENAI")
                && row.path("severity").asText().equals("CLEAN")).limit(otherCritical.size()).toList();
        for (int i = 0; i < otherCritical.size(); i++) {
            ObjectNode critical = otherCritical.get(i), clean = openClean.get(i);
            String packet = critical.path("packetId").asText();
            critical.put("packetId", clean.path("packetId").asText()).put("authoringFamily", "OPENAI");
            clean.put("packetId", packet).put("authoringFamily", "ANTHROPIC");
        }
        Path file = setup.root().resolve("empty-cross.jsonl");
        Files.writeString(file, String.join("\n", rows.stream().map(ObjectNode::toString).toList()) + "\n");
        Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(Files.readAllBytes(file)));
        PilotKey emptyCross = PilotKey.load(file);
        assertThatThrownBy(() -> evaluateWithKey(setup, emptyCross)).isInstanceOf(PilotBars.InvalidPilotBars.class)
                .hasMessageContaining("Empty cross-family");
    }
    @Test void evaluationAcceptsOtherLegalAllocationButAssemblyOwnsTenElevenRule() throws Exception {
        Setup setup = setup(9);
        assertThat(critical(setup, ModelFamily.OPENAI)).hasSize(9);
        assertThat(critical(setup, ModelFamily.ANTHROPIC)).hasSize(12);
        assertThat(evaluate(setup).pilotPassed()).isTrue();
        Path packetOut = setup.root().resolve("packets"), sealedOut = setup.root().resolve("sealed");
        Path reportOut = setup.root().resolve("assembly.json"), familyFile = setup.familiesFile();
        assertThatThrownBy(() -> assembleFamily(setup, packetOut, sealedOut, reportOut, familyFile))
                .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("critical allocation");
    }
    @Test void familyFractionRequiresSmallExactDenominator() throws Exception {
        Setup setup = setup(11);
        ObjectNode root = barsNode();
        root.putObject("scorerFamilies").put(P, "OPENAI").put(S, "ANTHROPIC");
        ((ObjectNode) root.path("bars")).put("minCrossFamilyRecallFraction", 0.9000000000000001);
        PilotBars fine = PilotBars.load(writeJson(setup.root().resolve("fine-bars.json"), root));
        assertThatThrownBy(() -> evaluateWithBars(setup, fine))
                .isInstanceOf(PilotBars.InvalidPilotBars.class).hasMessageContaining("denominator");
    }


    @Test void operatorRequiresFamiliesPropertyAndAssemblesWhenPresent() throws Exception {
        Setup setup = setup(11);
        Map<String, String> props = Map.of("assemble", "true", "dir", setup.fixtures().toString(),
                "labels", setup.labels().toString(), "packets", setup.root().resolve("operator-packets").toString(),
                "sealed", setup.root().resolve("operator-sealed").toString(),
                "report", setup.root().resolve("operator-report.json").toString(), "seed", "41");
        PilotFixtureOperatorTest operator = new PilotFixtureOperatorTest();
        try {
            props.forEach((name, value) -> System.setProperty("pilot.fixtures." + name, value));
            System.clearProperty("pilot.fixtures.families");
            assertThatThrownBy(PilotFixtureOperatorTest::requiredFamilies)
                    .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("families");
            assertThatThrownBy(operator::operatorCheckOrAssemble).isInstanceOf(AssertionError.class);
            System.setProperty("pilot.fixtures.families", "relative.json");
            assertThatThrownBy(PilotFixtureOperatorTest::requiredFamilies)
                    .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("Absolute");
            System.setProperty("pilot.fixtures.families", setup.familiesFile().toString());
            operator.operatorCheckOrAssemble();
            assertThat(Files.exists(setup.root().resolve("operator-report.json"))).isTrue();
        } finally {
            props.keySet().forEach(name -> System.clearProperty("pilot.fixtures." + name));
            System.clearProperty("pilot.fixtures.families");
        }
    }


    private PilotBarsEvaluator.PilotBarReport evaluateLegacy(Setup setup, PilotBars bars) {
        return PilotBarsEvaluator.evaluate(setup.key(), setup.scores(), ClassAScoreSchemaV2Test.MAPPING,
                bars, List.of(), PilotTestKeys.ratings(setup.key(), setup.scores(), bars));
    }
    private PilotBarsEvaluator.PilotBarReport evaluateWithBars(Setup setup, PilotBars bars) {
        return PilotBarsEvaluator.evaluateByFamily(setup.key(), setup.scores(), ClassAScoreSchemaV2Test.MAPPING,
                bars, List.of(), PilotTestKeys.ratings(setup.key(), setup.scores(), bars),
                setup.families(), setup.report());
    }
    private PilotFixtureAssembly.Assembled assembleFamily(Setup setup, Path packetOut, Path sealedOut,
            Path reportOut, Path familiesFile) {
        return PilotFixtureAssembly.assembleWithFamilies(setup.fixtures(), setup.labels(), packetOut,
                sealedOut, 41L, reportOut, familiesFile);
    }

    @Test void historicalCallsHaveExactKnownCountsIncludingMergedRenameTest() throws Exception {
        Path dir = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize())
                .resolve("backend/src/test/java/com/studysnap/backend/benchmark");
        Map<String, Integer> counts = new HashMap<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path file : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\\n]*", "");
                int count = source.split("PilotBarsEvaluator\\.evaluate\\(|PilotFixtureAssembly\\.assemble\\(", -1).length - 1;
                if (count > 0) counts.put(file.getFileName().toString(), count);
            }
        }
        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of("PilotBarsEvaluatorTest.java", 4,
                "PilotFixtureAssemblyTest.java", 3, "ModelFamilyRenameTest.java", 2,
                "PilotBarsFamilyEvaluationTest.java", 2));
        assertThat(Files.readString(dir.resolve("PilotFixtureOperatorTest.java")))
                .doesNotContain("PilotFixtureAssembly." + "assemble(");
    }
}
