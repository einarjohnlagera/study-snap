package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelFamilyRenameTest {
    private static final String OLD_FIELD = "authoringSource";
    private static final String NEW_FIELD = "authoringFamily";
    private static final String OLD_OPENAI = "CODEX";
    private static final String OLD_ANTHROPIC = "CLAUDE_OR_OWNER";
    private static final String OPENAI = "OPENAI";
    private static final String ANTHROPIC = "ANTHROPIC";
    private static final String SOURCE = "The cell membrane surrounds the cell.";
    private static final int[] SIZES = {7, 7, 10, 24, 30, 48};
    private static final Pattern LEFTOVERS = Pattern.compile("\\b(?:AuthoringSource|authoringSource|CLAUDE_OR_OWNER|CODEX)\\b");
    private static final String FIXTURES = "fixtures";
    private static final String ALPHA = "alpha";
    private static final String LABELS = "labels.jsonl";
    private static final String PACKET_ID = "packetId";
    private static final String QUESTIONS = "questions";
    private static final String PLANTED = "planted";
    private static final String QUESTION = "question";
    private static final String INDEX = "index";
    private static final String VERIFIER = "verifier";
    private static final String FIXTURE_SHA = "fixtureSha256";
    private static final String VERDICT = "verdict";
    private static final String CONFIRMED = "LABEL_CONFIRMED";
    private static final String CODE = "code";
    private static final String NONE = "NONE";
    private static final String COMPOSITION_BY_SOURCE = "compositionBySource";
    private static final String AUTHORING_CRITICAL_TOTALS = "authoringCriticalTotals";
    private static final String AUTHORING_PLANTED_TOTALS = "authoringPlantedTotals";
    private static final String CONFIRMED_BY_VERIFIER = "confirmedByVerifier";
    private static final String LOWER_OPENAI = "openai";
    private static final String LOWER_ANTHROPIC = "anthropic";
    private static final String NEWLINE = "\n";
    private static final String TEXT_BLOCK_DELIMITER = "\"\"\"";
    private static final String EMBED_PREFIX = "x";
    private static final String EMBED_SUFFIX = "y";
    @TempDir Path temp;

    @Test void closedEnumAndStrictFixtureAndKeyFormats() throws Exception {
        assertThat(ModelFamily.values()).containsExactly(ModelFamily.OPENAI, ModelFamily.ANTHROPIC);
        Path fixture = PilotPacketExportTest.fixture(temp.resolve(FIXTURES), ALPHA, 1);
        String valid = Files.readString(fixture);
        assertThat(PilotFixtureSource.load(fixture).questions().getFirst().authoringFamily()).isEqualTo(ModelFamily.OPENAI);
        Files.writeString(fixture, valid.replace(NEW_FIELD, OLD_FIELD));
        assertThatThrownBy(() -> PilotFixtureSource.load(fixture))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class).hasMessageContaining("Malformed planted item");
        Files.writeString(fixture, valid.replace(OPENAI, ANTHROPIC));
        assertThat(PilotFixtureSource.load(fixture).questions().getFirst().authoringFamily()).isEqualTo(ModelFamily.ANTHROPIC);

        List<ObjectNode> rows = PilotKeyTest.revision4();
        Path keyPath = writeKey(rows);
        PilotKey key = PilotKey.load(keyPath);
        assertThat(key.rows().getFirst().authoringFamily()).isEqualTo(ModelFamily.OPENAI);
        assertThat(key.byAuthor(ModelFamily.ANTHROPIC)).hasSize(10);
        assertThat(key.rows().stream().filter(row -> row.severity() == PlantedType.Severity.CRITICAL
                && row.authoringFamily() == ModelFamily.ANTHROPIC)).hasSize(10);
        assertThat(key.rows().stream().filter(row -> row.severity() == PlantedType.Severity.CRITICAL
                && row.authoringFamily() == ModelFamily.OPENAI)).hasSize(11);
        rows.getFirst().set(OLD_FIELD, rows.getFirst().remove(NEW_FIELD));
        writeKey(rows);
        assertThatThrownBy(() -> PilotKey.load(keyPath))
                .isInstanceOf(PilotKey.InvalidPilotKey.class).hasMessageContaining("Malformed pilot key row");
    }

    @Test void labelValuesAreStrictAndSameSourceRuleIsReached() throws Exception {
        Path fixtureFile = PilotPacketExportTest.fixture(temp.resolve(FIXTURES), ALPHA, 1);
        var fixture = PilotFixtureSource.load(fixtureFile);
        String hash = FreezeRecord.sha256(fixtureFile);
        Path labels = temp.resolve(LABELS);
        for (String bad : List.of(OLD_OPENAI, OLD_ANTHROPIC, "GOOGLE")) {
            Files.writeString(labels, label(ALPHA, bad, hash));
            assertThatThrownBy(() -> PilotLabelVerification.load(labels))
                    .isInstanceOf(PilotLabelVerification.InvalidLabelVerification.class)
                    .hasMessageContaining("Malformed verification record");
        }
        Files.writeString(labels, label(ALPHA, ANTHROPIC, hash));
        assertThat(PilotLabelVerification.load(labels).getFirst().verifier()).isEqualTo(ModelFamily.ANTHROPIC);
        Files.writeString(labels, label(ALPHA, OPENAI, hash));
        var same = PilotLabelVerification.load(labels);
        assertThatThrownBy(() -> PilotLabelVerification.requireConfirmed(same,
                Map.of(ALPHA, fixture), Map.of(ALPHA, hash)))
                .isInstanceOf(PilotLabelVerification.InvalidLabelVerification.class)
                .hasMessageContaining("Same-source verifier recorded");
    }

    @Test void assemblyPreservesReportShapeAndRejectsMixedFamilies() throws Exception {
        Path fixtures = synthetic();
        var checked = PilotFixtureAssembly.check(fixtures);
        assertThat(checked.critical()).containsEntry(ModelFamily.OPENAI, 11).containsEntry(ModelFamily.ANTHROPIC, 10);
        var assembled = PilotFixtureAssembly.assemble(fixtures, temp.resolve(LABELS),
                temp.resolve("packets"), temp.resolve("sealed"), 41L, temp.resolve("report.json"));
        PilotKey sealed = PilotKey.load(assembled.sealed());
        PilotBars bars = PilotTestKeys.bars(temp);
        var scores = PilotTestKeys.scores(sealed);
        var evaluated = PilotBarsEvaluator.evaluate(sealed, scores, ClassAScoreSchemaV2Test.MAPPING,
                bars, List.of(), PilotTestKeys.ratings(sealed, scores, bars));
        assertThat(evaluated.scorers().get("P").bars().get(2).observed()).contains("cross=10/10");
        assertThat(evaluated.scorers().get("S").bars().get(2).observed()).contains("cross=11/11");
        var report = IntegritySupport.JSON.readTree(Files.readString(assembled.report()));
        Set<String> fields = new HashSet<>();
        report.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("packetSizes", "compositionByType", COMPOSITION_BY_SOURCE,
                AUTHORING_CRITICAL_TOTALS, AUTHORING_PLANTED_TOTALS, FIXTURE_SHA, "packetSha256",
                "sealedSha256", "labelVerificationSha256", "seed", CONFIRMED_BY_VERIFIER, "disputesRemaining");
        for (String map : List.of(COMPOSITION_BY_SOURCE, AUTHORING_CRITICAL_TOTALS,
                AUTHORING_PLANTED_TOTALS, CONFIRMED_BY_VERIFIER)) {
            Set<String> keys = new HashSet<>();
            report.path(map).fieldNames().forEachRemaining(keys::add);
            assertThat(keys).containsExactlyInAnyOrder(OPENAI, ANTHROPIC);
        }
        assertThat(Files.readString(assembled.packets().getFirst())).doesNotContain(OLD_FIELD, NEW_FIELD);
        Path first = fixtures.resolve("pilot-01.json");
        ObjectNode changed = (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(first));
        ((ObjectNode) changed.path(QUESTIONS).get(0).path(PLANTED)).put(NEW_FIELD, ANTHROPIC);
        Files.writeString(first, IntegritySupport.JSON.writeValueAsString(changed));
        assertThatThrownBy(() -> PilotFixtureAssembly.check(fixtures))
                .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("mixed authors");
    }

    @Test void exportAndScanKeepTheirDifferentMatchingModes() throws Exception {
        for (String needle : List.of(OPENAI, ANTHROPIC, OLD_OPENAI, OLD_ANTHROPIC, LOWER_OPENAI,
                EMBED_PREFIX + OPENAI + EMBED_SUFFIX, EMBED_PREFIX + ANTHROPIC + EMBED_SUFFIX,
                EMBED_PREFIX + OLD_OPENAI + EMBED_SUFFIX, EMBED_PREFIX + OLD_ANTHROPIC + EMBED_SUFFIX)) {
            Path fixture = PilotPacketExportTest.fixture(temp.resolve("fixture-" + needle), ALPHA, 1);
            Files.writeString(fixture, Files.readString(fixture).replace(SOURCE, SOURCE + " " + needle));
            assertThatThrownBy(() -> PilotPacketExport.export(List.of(fixture), temp.resolve("packets-" + needle),
                    temp.resolve("key-" + needle + ".jsonl"), PilotPacketExportTest.CANARY, 1L))
                    .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class).hasMessageContaining("leaks metadata");
        }
        Path lowercase = PilotPacketExportTest.fixture(temp.resolve("fixture-lower"), ALPHA, 1);
        Files.writeString(lowercase, Files.readString(lowercase).replace(SOURCE, SOURCE + " anthropic"));
        PilotPacketExport.Export cleanExport = PilotPacketExport.export(List.of(lowercase), temp.resolve("packets-lower"),
                temp.resolve("key-lower.jsonl"), PilotPacketExportTest.CANARY, 1L);
        assertThat(Files.readString(cleanExport.packets().getFirst())).contains(LOWER_ANTHROPIC);
        PilotKey key = PilotKey.load(cleanExport.keyPath());
        Path prompt = temp.resolve("prompt.txt"), packet = temp.resolve("packet.txt"), output = temp.resolve("output.txt");
        Map<PilotLeakCheck.ArtifactClass, List<Path>> artifacts = Map.of(
                PilotLeakCheck.ArtifactClass.PROMPT, List.of(prompt),
                PilotLeakCheck.ArtifactClass.PACKET, List.of(packet),
                PilotLeakCheck.ArtifactClass.OUTPUT, List.of(output));
        Files.writeString(prompt, "clean prompt"); Files.writeString(output, "clean output");
        for (String needle : List.of(OPENAI, ANTHROPIC, OLD_OPENAI, OLD_ANTHROPIC,
                LOWER_OPENAI, LOWER_ANTHROPIC, "philanthropic", "xCoDeXy")) {
            Files.writeString(packet, needle);
            assertThat(PilotLeakCheck.scan(artifacts, key, cleanExport.keyPath(), Set.of()))
                    .as(needle).anySatisfy(hit -> assertThat(hit.kind()).isEqualTo("AUTHOR"));
        }
    }

    @Test void noRetiredIdentifiersOutsideExactAllowlist() throws Exception {
        Path dir = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize())
                .resolve("backend/src/test/java/com/studysnap/backend/benchmark");
        Map<String, Integer> actual = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = LEFTOVERS.matcher(stripComments(Files.readString(file)));
                int count = 0;
                while (matcher.find()) count++;
                if (count > 0) actual.put(file.getFileName().toString(), count);
            }
        }
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(Map.of(
                "PilotPacketExport.java", 2, "PilotLeakCheck.java", 2, "NoArmFieldsTest.java", 1,
                "PilotLeakCheckTest.java", 1, "ModelFamilyRenameTest.java", 7));
    }

    private Path writeKey(List<ObjectNode> rows) throws Exception {
        Path file = temp.resolve("key.jsonl");
        List<String> lines = new ArrayList<>();
        for (ObjectNode row : rows) lines.add(IntegritySupport.JSON.writeValueAsString(row));
        Files.writeString(file, String.join(NEWLINE, lines) + NEWLINE);
        Files.writeString(file.resolveSibling("key.jsonl.sha256"), FreezeRecord.sha256(Files.readAllBytes(file)));
        return file;
    }

    private static String label(String packet, String verifier, String hash) throws Exception {
        ObjectNode row = IntegritySupport.JSON.createObjectNode();
        row.put(PACKET_ID, packet).put(INDEX, 0).put(VERIFIER, verifier)
                .put(FIXTURE_SHA, hash).put(VERDICT, CONFIRMED).put(CODE, NONE);
        return IntegritySupport.JSON.writeValueAsString(row) + NEWLINE;
    }

    private Path synthetic() throws Exception {
        Path dir = Files.createDirectory(temp.resolve(FIXTURES));
        Map<ModelFamily, List<PlantedType>> pools = new EnumMap<>(ModelFamily.class);
        pools.put(ModelFamily.OPENAI, pool(true)); pools.put(ModelFamily.ANTHROPIC, pool(false));
        Map<ModelFamily, Integer> cursor = new EnumMap<>(ModelFamily.class);
        cursor.put(ModelFamily.OPENAI, 0); cursor.put(ModelFamily.ANTHROPIC, 0);
        List<String> labels = new ArrayList<>();
        boolean rootSet = false;
        int serial = 0;
        for (int p = 0; p < SIZES.length; p++) {
            String id = "pilot-%02d".formatted(p + 1);
            ModelFamily family = p == 0 || p == 1 || p == 5 ? ModelFamily.OPENAI : ModelFamily.ANTHROPIC;
            ObjectNode root = IntegritySupport.JSON.createObjectNode();
            root.put(PACKET_ID, id); root.putArray("sourceNoteTexts").add("Synthetic source " + id);
            ArrayNode questions = root.putArray(QUESTIONS);
            for (int i = 0; i < SIZES[p]; i++) {
                PlantedType type = pools.get(family).get(cursor.get(family));
                cursor.put(family, cursor.get(family) + 1);
                ObjectNode item = questions.addObject();
                ObjectNode question = ClassAScoreSchemaV2Test.packet("MCQ").question().deepCopy();
                question.put(QUESTION, "Synthetic question " + ++serial);
                question.put("explanation", "Synthetic explanation " + serial);
                item.set(QUESTION, question);
                boolean input = !rootSet && family == ModelFamily.OPENAI && type == PlantedType.WRONG_KEY;
                if (input) rootSet = true;
                item.putObject(PLANTED).put("plantedType", type.name()).put(NEW_FIELD, family.name())
                        .put("rootCauseInput", input);
            }
            Path file = dir.resolve(id + ".json");
            Files.writeString(file, IntegritySupport.JSON.writeValueAsString(root));
            String hash = FreezeRecord.sha256(file);
            String verifier = family == ModelFamily.OPENAI ? ANTHROPIC : OPENAI;
            for (int i = 0; i < SIZES[p]; i++) {
                ObjectNode row = IntegritySupport.JSON.createObjectNode();
                row.put(PACKET_ID, id).put(INDEX, i).put(VERIFIER, verifier)
                        .put(FIXTURE_SHA, hash).put(VERDICT, CONFIRMED).put(CODE, NONE);
                labels.add(IntegritySupport.JSON.writeValueAsString(row));
            }
        }
        Files.writeString(temp.resolve(LABELS), String.join(NEWLINE, labels) + NEWLINE);
        return dir;
    }

    private static List<PlantedType> pool(boolean openai) {
        Map<PlantedType, Integer> counts = new EnumMap<>(PlantedType.class);
        counts.put(PlantedType.WRONG_KEY, 3);
        counts.put(PlantedType.EXPLANATION_CONTRADICTS_KEY, openai ? 2 : 1);
        counts.put(PlantedType.SECOND_DEFENSIBLE_ANSWER, openai ? 2 : 3);
        counts.put(PlantedType.MATERIAL_AMBIGUITY_MULTI, 1);
        counts.put(PlantedType.MATERIAL_AMBIGUITY_ZERO, 1);
        counts.put(PlantedType.SOURCE_CONTRADICTION, openai ? 2 : 1);
        counts.put(PlantedType.MINOR_WORDING, openai ? 2 : 1);
        counts.put(PlantedType.NUMERIC_INCONSISTENCY, 1);
        counts.put(PlantedType.WEAK_DISTRACTOR, 2);
        counts.put(PlantedType.UNHELPFUL_EXPLANATION, openai ? 1 : 2);
        counts.put(PlantedType.CLEAN, openai ? 45 : 48);
        List<PlantedType> values = new ArrayList<>();
        counts.forEach((type, amount) -> { for (int i = 0; i < amount; i++) values.add(type); });
        return values;
    }

    // Copied from ArmKeyReaderGuardTest: comments go, while literals and text blocks remain.
    private static String stripComments(String source) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        int state = 0; // code, line comment, block comment, string, character, text block
        while (i < source.length()) {
            if (state == 0 && source.startsWith(TEXT_BLOCK_DELIMITER, i)) {
                result.append(TEXT_BLOCK_DELIMITER); i += 3; state = 5;
            } else if (state == 0 && source.startsWith("//", i)) { i += 2; state = 1;
            } else if (state == 0 && source.startsWith("/*", i)) { i += 2; state = 2;
            } else if (state == 2 && source.startsWith("*/", i)) { i += 2; state = 0;
            } else if (state == 5 && source.startsWith(TEXT_BLOCK_DELIMITER, i)) {
                result.append(TEXT_BLOCK_DELIMITER); i += 3; state = 0;
            } else if ((state == 3 || state == 4) && source.charAt(i) == '\\' && i + 1 < source.length()) {
                result.append(source, i, i + 2); i += 2;
            } else {
                char c = source.charAt(i++);
                if (state == 1 || state == 2) { if (c == '\n') result.append(c); if (state == 1 && c == '\n') state = 0; }
                else { result.append(c); if (state == 0 && c == '"') state = 3;
                    else if (state == 0 && c == '\'') state = 4;
                    else if (state == 3 && c == '"' || state == 4 && c == '\'') state = 0; }
            }
        }
        return result.toString();
    }
}
