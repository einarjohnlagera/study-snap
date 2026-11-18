package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PilotFixtureAssemblyTest {
    private static final String QUESTION = "Synthetic question ";
    private static final String SOURCE = "Synthetic source ";
    private static final String CHOICE = "Synthetic choice ";
    private static final String EXPLANATION = "Synthetic explanation ";
    private static final long SEED = 41L;
    private static final int[] SIZES = {7, 7, 10, 24, 30, 48};
    @TempDir Path temp;

    private record SetUp(Path dir, Path labels) {}
    private static List<PlantedType> pool(boolean codex) {
        Map<PlantedType, Integer> counts = new EnumMap<>(PlantedType.class);
        counts.put(PlantedType.WRONG_KEY, 3);
        counts.put(PlantedType.EXPLANATION_CONTRADICTS_KEY, codex ? 2 : 1);
        counts.put(PlantedType.SECOND_DEFENSIBLE_ANSWER, codex ? 2 : 3);
        counts.put(PlantedType.MATERIAL_AMBIGUITY_MULTI, 1);
        counts.put(PlantedType.MATERIAL_AMBIGUITY_ZERO, 1);
        counts.put(PlantedType.SOURCE_CONTRADICTION, codex ? 2 : 1);
        counts.put(PlantedType.MINOR_WORDING, codex ? 2 : 1);
        counts.put(PlantedType.NUMERIC_INCONSISTENCY, 1);
        counts.put(PlantedType.WEAK_DISTRACTOR, 2);
        counts.put(PlantedType.UNHELPFUL_EXPLANATION, codex ? 1 : 2);
        counts.put(PlantedType.CLEAN, codex ? 45 : 48);
        List<PlantedType> result = new ArrayList<>();
        counts.forEach((type, n) -> { for (int i = 0; i < n; i++) result.add(type); });
        Collections.shuffle(result, new java.util.Random(codex ? 101 : 202));
        return result;
    }
    private SetUp synthetic() throws Exception { return synthetic(temp); }
    private SetUp synthetic(Path base) throws Exception {
        Path dir = Files.createDirectory(base.resolve("fixtures"));
        List<PlantedType> codex = pool(true), other = pool(false);
        int c = 0, o = 0, serial = 0;
        boolean rootSet = false;
        for (int p = 0; p < SIZES.length; p++) {
            String id = "pilot-%02d".formatted(p + 1);
            boolean authorCodex = p == 0 || p == 1 || p == 5;
            ObjectNode root = IntegritySupport.JSON.createObjectNode();
            root.put("packetId", id);
            root.putArray("sourceNoteTexts").add(SOURCE + id);
            ArrayNode questions = root.putArray("questions");
            for (int i = 0; i < SIZES[p]; i++) {
                PlantedType type = authorCodex ? codex.get(c++) : other.get(o++);
                ObjectNode item = questions.addObject();
                ObjectNode q = ClassAScoreSchemaV2Test.packet("MCQ").question().deepCopy();
                String marker = String.valueOf(++serial);
                q.put("question", QUESTION + marker);
                q.putArray("choices").add(CHOICE + marker + " A").add(CHOICE + marker + " B");
                q.put("correctIndex", 0);
                q.put("explanation", EXPLANATION + marker);
                item.set("question", q);
                boolean input = authorCodex && type == PlantedType.WRONG_KEY && !rootSet;
                if (input) rootSet = true;
                item.putObject("planted").put("plantedType", type.name())
                        .put("authoringSource", authorCodex ? "CODEX" : "CLAUDE_OR_OWNER")
                        .put("rootCauseInput", input);
            }
            Files.writeString(dir.resolve(id + ".json"), IntegritySupport.JSON.writeValueAsString(root));
        }
        Path labels = base.resolve("labels.jsonl");
        writeLabels(dir, labels);
        return new SetUp(dir, labels);
    }
    private static void writeLabels(Path dir, Path labels) throws Exception {
        List<String> rows = new ArrayList<>();
        for (int p = 0; p < SIZES.length; p++) {
            String id = "pilot-%02d".formatted(p + 1);
            Path file = dir.resolve(id + ".json");
            String verifier = p == 0 || p == 1 || p == 5 ? "CLAUDE_OR_OWNER" : "CODEX";
            String hash = FreezeRecord.sha256(file);
            for (int i = 0; i < SIZES[p]; i++) rows.add(row(id, i, verifier, hash, "LABEL_CONFIRMED", "NONE"));
        }
        Files.writeString(labels, String.join("\n", rows) + "\n");
    }
    private static String row(String id, int index, String verifier, String hash, String verdict, String code) throws Exception {
        ObjectNode node = IntegritySupport.JSON.createObjectNode();
        node.put("packetId", id).put("index", index).put("verifier", verifier)
                .put("fixtureSha256", hash).put("verdict", verdict).put("code", code);
        return IntegritySupport.JSON.writeValueAsString(node);
    }
    private static ObjectNode root(Path dir, String id) throws Exception {
        return (ObjectNode) IntegritySupport.JSON.readTree(Files.readString(dir.resolve(id + ".json")));
    }
    private static void save(Path dir, String id, ObjectNode node) throws Exception {
        Files.writeString(dir.resolve(id + ".json"), IntegritySupport.JSON.writeValueAsString(node));
    }
    private static ObjectNode planted(ObjectNode root, int index) {
        return (ObjectNode) root.path("questions").get(index).path("planted");
    }
    private PilotFixtureAssembly.Assembled assemble(SetUp setup, String suffix) {
        return PilotFixtureAssembly.assemble(setup.dir(), setup.labels(), temp.resolve("packets-" + suffix),
                temp.resolve("sealed-" + suffix), SEED, temp.resolve("report-" + suffix + ".json"));
    }
    @Test void allocationAndPilotCompositionPassIncludingFinalGate() throws Exception {
        SetUp setup = synthetic();
        var checked = PilotFixtureAssembly.check(setup.dir());
        assertThat(checked.planted()).containsEntry(PilotFixtureSource.AuthoringSource.CODEX, 17)
                .containsEntry(PilotFixtureSource.AuthoringSource.CLAUDE_OR_OWNER, 16);
        assertThat(checked.critical()).containsEntry(PilotFixtureSource.AuthoringSource.CODEX, 11)
                .containsEntry(PilotFixtureSource.AuthoringSource.CLAUDE_OR_OWNER, 10);
        var result = assemble(setup, "a");
        PilotKey sealed = PilotKey.load(result.sealed());
        sealed.requireRevision4Composition();
        assertThat(sealed.countsByType()).containsAllEntriesOf(checked.counts().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().longValue())));
        assertThat(checked.counts()).containsAllEntriesOf(PilotFixtureAssembly.REVISION_4);
    }
    @Test void packetSizeAndFileSetFail() throws Exception {
        SetUp setup = synthetic();
        ObjectNode node = root(setup.dir(), "pilot-01");
        ((ArrayNode) node.path("questions")).remove(0); save(setup.dir(), "pilot-01", node);
        assertThatThrownBy(() -> PilotFixtureAssembly.check(setup.dir())).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("Packet size");
        Files.delete(setup.dir().resolve("pilot-06.json"));
        assertThatThrownBy(() -> PilotFixtureAssembly.check(setup.dir())).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("Fixture file count");
    }
    @Test void stemAndExtraFileFail() throws Exception {
        SetUp setup = synthetic();
        ObjectNode node = root(setup.dir(), "pilot-01"); node.put("packetId", "other"); save(setup.dir(), "pilot-01", node);
        assertThatThrownBy(() -> PilotFixtureAssembly.check(setup.dir())).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("stem");
        Files.writeString(setup.dir().resolve("extra.json"), "{}");
        assertThatThrownBy(() -> PilotFixtureAssembly.check(setup.dir())).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("file count");
    }
    @Test void eachTypeCountIsGated() throws Exception {
        for (PlantedType type : PlantedType.values()) {
            Path base = Files.createDirectory(temp.resolve(type.name()));
            SetUp setup = synthetic(base);
            boolean changed = false;
            for (int p = 0; p < SIZES.length && !changed; p++) {
                String id = "pilot-%02d".formatted(p + 1);
                ObjectNode node = root(setup.dir(), id);
                for (int i = 0; i < SIZES[p]; i++) {
                    ObjectNode item = planted(node, i);
                    if (item.path("plantedType").asText().equals(type.name()) && !item.path("rootCauseInput").asBoolean()) {
                        item.put("plantedType", type == PlantedType.CLEAN ? "WEAK_DISTRACTOR" : "CLEAN");
                        save(setup.dir(), id, node);
                        changed = true;
                        break;
                    }
                }
            }
            assertThat(changed).isTrue();
            assertThatThrownBy(() -> PilotFixtureAssembly.check(setup.dir()))
                    .as(type.name()).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("Composition count");
        }
    }
    @Test void rootCauseCountAndTypeFail() throws Exception {
        SetUp setup = synthetic(); ObjectNode node = root(setup.dir(), "pilot-06");
        for (int i = 0; i < 48; i++) if (planted(node, i).path("plantedType").asText().equals("WRONG_KEY")) planted(node, i).put("rootCauseInput", true);
        save(setup.dir(), "pilot-06", node);
        assertThatThrownBy(() -> PilotFixtureAssembly.check(setup.dir())).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("count");
        for (int i = 0; i < 48; i++) planted(node, i).put("rootCauseInput", false);
        for (int i = 0; i < 48; i++) if (planted(node, i).path("plantedType").asText().equals("SOURCE_CONTRADICTION")) { planted(node, i).put("rootCauseInput", true); break; }
        save(setup.dir(), "pilot-06", node);
        assertThatThrownBy(() -> PilotFixtureAssembly.check(setup.dir())).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("Input root cause has wrong category");
    }
    @Test void authorshipAllocationAndMixedPacketFail() throws Exception {
        SetUp setup = synthetic(); ObjectNode node = root(setup.dir(), "pilot-01");
        planted(node, 0).put("authoringSource", "CLAUDE_OR_OWNER"); save(setup.dir(), "pilot-01", node);
        assertThatThrownBy(() -> PilotFixtureAssembly.check(setup.dir())).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("authorship");
    }
    @Test void repositoryPathsFail() {
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        assertThatThrownBy(() -> PilotFixtureAssembly.check(repo)).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class)
                .hasMessageContaining("Cannot check fixture directory");
    }
    @Test void verificationLastRowAndMissingAndStaleFail() throws Exception {
        SetUp setup = synthetic();
        var checked = PilotFixtureAssembly.check(setup.dir());
        var rows = PilotLabelVerification.load(setup.labels());
        assertThat(PilotLabelVerification.requireConfirmed(rows, checked.fixtures(), checked.hashes()).confirmed().values())
                .containsExactlyInAnyOrder(62, 64);
        String original = Files.readString(setup.labels());
        Files.writeString(setup.labels(), original.substring(original.indexOf('\n') + 1));
        assertThatThrownBy(() -> assemble(setup, "missing")).isInstanceOf(PilotLabelVerification.InvalidLabelVerification.class).hasMessageContaining("Missing");
        Files.writeString(setup.labels(), original);
        String hash = FreezeRecord.sha256(setup.dir().resolve("pilot-01.json"));
        Files.writeString(setup.labels(), original + row("pilot-01", 0, "CLAUDE_OR_OWNER", hash,
                "LABEL_DISPUTED", "OTHER") + "\n");
        assertThatThrownBy(() -> assemble(setup, "disputed")).isInstanceOf(PilotLabelVerification.InvalidLabelVerification.class).hasMessageContaining("Disputed");
        Files.writeString(setup.labels(), Files.readString(setup.labels()) + row("pilot-01", 0, "CLAUDE_OR_OWNER", hash,
                "LABEL_CONFIRMED", "NONE") + "\n");
        assemble(setup, "reconfirmed");
        ObjectNode node = root(setup.dir(), "pilot-01"); node.putArray("sourceNoteTexts").add("Synthetic amended source"); save(setup.dir(), "pilot-01", node);
        assertThatThrownBy(() -> assemble(setup, "stale")).isInstanceOf(PilotLabelVerification.InvalidLabelVerification.class).hasMessageContaining("Stale");
    }
    @Test void invalidVerificationRowsFailWithoutExport() throws Exception {
        SetUp setup = synthetic(); String original = Files.readString(setup.labels());
        String hash = FreezeRecord.sha256(setup.dir().resolve("pilot-01.json"));
        String[] invalid = {row("pilot-01", 0, "CODEX", hash, "LABEL_CONFIRMED", "NONE"),
                row("pilot-01", 99, "CLAUDE_OR_OWNER", hash, "LABEL_CONFIRMED", "NONE"),
                "{\"packetId\":\"pilot-01\",\"packetId\":\"pilot-01\"}",
                row("pilot-01", 0, "CLAUDE_OR_OWNER", hash, "LABEL_DISPUTED", "free text")};
        for (int i = 0; i < invalid.length; i++) {
            Files.writeString(setup.labels(), original + invalid[i] + "\n");
            int suffix = i;
            assertThatThrownBy(() -> assemble(setup, "bad" + suffix)).isInstanceOf(PilotLabelVerification.InvalidLabelVerification.class);
            assertThat(Files.exists(temp.resolve("packets-bad" + suffix))).isFalse();
        }
    }
    private record Located(String id, ObjectNode root, int index) {}
    private static Located locate(Path dir, boolean codex, PlantedType type) throws Exception {
        for (int p = 0; p < SIZES.length; p++) {
            if ((p == 0 || p == 1 || p == 5) != codex) continue;
            String id = "pilot-%02d".formatted(p + 1);
            ObjectNode node = root(dir, id);
            for (int i = 0; i < SIZES[p]; i++) {
                ObjectNode item = planted(node, i);
                if (item.path("plantedType").asText().equals(type.name()) && !item.path("rootCauseInput").asBoolean())
                    return new Located(id, node, i);
            }
        }
        throw new AssertionError("Synthetic type missing");
    }
    private static void exchange(Path dir, PlantedType codexType, PlantedType otherType) throws Exception {
        Located c = locate(dir, true, codexType);
        Located o = locate(dir, false, otherType);
        planted(c.root(), c.index()).put("plantedType", otherType.name());
        planted(o.root(), o.index()).put("plantedType", codexType.name());
        save(dir, c.id(), c.root()); save(dir, o.id(), o.root());
    }
    @Test void authorshipPlantedAndCriticalBandsFailIndependently() throws Exception {
        SetUp plantedSet = synthetic();
        exchange(plantedSet.dir(), PlantedType.MINOR_WORDING, PlantedType.CLEAN);
        exchange(plantedSet.dir(), PlantedType.WEAK_DISTRACTOR, PlantedType.CLEAN);
        exchange(plantedSet.dir(), PlantedType.UNHELPFUL_EXPLANATION, PlantedType.CLEAN);
        assertThatThrownBy(() -> PilotFixtureAssembly.check(plantedSet.dir()))
                .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("planted below band");
        Path base = Files.createDirectory(temp.resolve("critical-band"));
        SetUp criticalSet = synthetic(base);
        exchange(criticalSet.dir(), PlantedType.WRONG_KEY, PlantedType.MINOR_WORDING);
        exchange(criticalSet.dir(), PlantedType.EXPLANATION_CONTRADICTS_KEY, PlantedType.WEAK_DISTRACTOR);
        exchange(criticalSet.dir(), PlantedType.SOURCE_CONTRADICTION, PlantedType.UNHELPFUL_EXPLANATION);
        assertThatThrownBy(() -> PilotFixtureAssembly.check(criticalSet.dir()))
                .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("critical below band");
    }
    @Test void authorshipUpperBoundsFailIndependently() throws Exception {
        SetUp plantedSet = synthetic();
        exchange(plantedSet.dir(), PlantedType.CLEAN, PlantedType.MINOR_WORDING);
        exchange(plantedSet.dir(), PlantedType.CLEAN, PlantedType.WEAK_DISTRACTOR);
        assertThatThrownBy(() -> PilotFixtureAssembly.check(plantedSet.dir()))
                .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("planted above band");
        Path base = Files.createDirectory(temp.resolve("critical-upper"));
        SetUp criticalSet = synthetic(base);
        exchange(criticalSet.dir(), PlantedType.MINOR_WORDING, PlantedType.WRONG_KEY);
        exchange(criticalSet.dir(), PlantedType.WEAK_DISTRACTOR, PlantedType.SOURCE_CONTRADICTION);
        assertThatThrownBy(() -> PilotFixtureAssembly.check(criticalSet.dir()))
                .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("critical above band");
    }
    @Test void materialAmbiguityAndCriticalPresenceRequireBothSources() throws Exception {
        SetUp material = synthetic();
        exchange(material.dir(), PlantedType.CLEAN, PlantedType.MATERIAL_AMBIGUITY_ZERO);
        assertThatThrownBy(() -> PilotFixtureAssembly.check(material.dir()))
                .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("Material ambiguity");
        Path base = Files.createDirectory(temp.resolve("presence"));
        SetUp presence = synthetic(base);
        exchange(presence.dir(), PlantedType.CLEAN, PlantedType.SOURCE_CONTRADICTION);
        assertThatThrownBy(() -> PilotFixtureAssembly.check(presence.dir()))
                .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("Critical category");
    }
    @Test void diagnosticsNeverPairPositionWithSensitiveLabel() throws Exception {
        SetUp setup = synthetic();
        ObjectNode node = root(setup.dir(), "pilot-01");
        planted(node, 0).put("authoringSource", "CLAUDE_OR_OWNER");
        save(setup.dir(), "pilot-01", node);
        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> PilotFixtureAssembly.check(setup.dir()));
        assertThat(failure).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class);
        for (PlantedType type : PlantedType.values()) assertThat(failure.getMessage()).doesNotContain(type.name());
        for (PilotLabelVerification.Code code : PilotLabelVerification.Code.values())
            if (code != PilotLabelVerification.Code.NONE) assertThat(failure.getMessage()).doesNotContain(code.name());
    }
    @Test void malformedInputFailuresCarryNoCauseThatCouldEchoFileContent() throws Exception {
        SetUp setup = synthetic();
        Files.writeString(setup.dir().resolve("pilot-01.json"), "{\"packetId\":\"pilot-01\",\"ZZ_SECRET_MARKER\": ");
        Throwable fixture = org.assertj.core.api.Assertions.catchThrowable(() -> PilotFixtureAssembly.check(setup.dir()));
        assertThat(fixture).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasNoCause();
        assertThat(fixture.getMessage()).doesNotContain("ZZ_SECRET_MARKER");
        Path badLabels = temp.resolve("bad-labels.jsonl");
        Files.writeString(badLabels, "{\"packetId\":\"pilot-02\",\"ZZ_SECRET_MARKER\": \n");
        Throwable labels = org.assertj.core.api.Assertions.catchThrowable(() -> PilotLabelVerification.load(badLabels));
        assertThat(labels).isInstanceOf(PilotLabelVerification.InvalidLabelVerification.class).hasNoCause();
        assertThat(labels.getMessage()).doesNotContain("ZZ_SECRET_MARKER");
    }
    @Test void reportIsContentFreeHasDetachedHashAndPacketsAreBlind() throws Exception {
        SetUp setup = synthetic(); var result = assemble(setup, "report");
        String report = Files.readString(result.report());
        assertThat(IntegritySupport.detachedHash(result.report())).isEqualTo(FreezeRecord.sha256(result.report()));
        assertThat(report).doesNotContain("CANARY-");
        java.util.Set<String> reportFields = new java.util.HashSet<>();
        IntegritySupport.JSON.readTree(report).fieldNames().forEachRemaining(reportFields::add);
        assertThat(reportFields).containsExactlyInAnyOrder("packetSizes", "compositionByType",
                "compositionBySource", "authoringCriticalTotals", "authoringPlantedTotals",
                "fixtureSha256", "packetSha256", "sealedSha256", "labelVerificationSha256",
                "seed", "confirmedByVerifier", "disputesRemaining");
        for (int i = 1; i <= 126; i++) assertThat(report).doesNotContain(QUESTION + i, CHOICE + i, EXPLANATION + i);
        for (int i = 1; i <= 6; i++) assertThat(report).doesNotContain(SOURCE + "pilot-%02d".formatted(i));
        PilotKey sealed = PilotKey.load(result.sealed());
        var artifacts = Map.of(PilotLeakCheck.ArtifactClass.PACKET, result.packets(),
                PilotLeakCheck.ArtifactClass.PROMPT, result.packets(), PilotLeakCheck.ArtifactClass.OUTPUT, result.packets());
        assertThat(PilotLeakCheck.scan(artifacts, sealed, result.sealed(), Set.of("synthetic-forbidden-needle"))).isEmpty();
        assertThatThrownBy(() -> assemble(setup, "report")).isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("already exists");
    }
    @Test void verificationDiagnosticsContainNoSensitiveCategory() throws Exception {
        SetUp setup = synthetic();
        String hash = FreezeRecord.sha256(setup.dir().resolve("pilot-01.json"));
        Files.writeString(setup.labels(), Files.readString(setup.labels())
                + row("pilot-01", 0, "CLAUDE_OR_OWNER", hash, "LABEL_DISPUTED", "KEY_NOT_ACTUALLY_WRONG") + "\n");
        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> assemble(setup, "diagnostic"));
        assertThat(failure).isInstanceOf(PilotLabelVerification.InvalidLabelVerification.class);
        for (PlantedType type : PlantedType.values()) assertThat(failure.getMessage()).doesNotContain(type.name());
        for (PilotLabelVerification.Code code : PilotLabelVerification.Code.values())
            if (code != PilotLabelVerification.Code.NONE) assertThat(failure.getMessage()).doesNotContain(code.name());
    }
    @Test void leakAndNestedOutputPathsFailBeforePublishing() throws Exception {
        SetUp setup = synthetic();
        Path packets = temp.resolve("nested-packets");
        assertThatThrownBy(() -> PilotFixtureAssembly.assemble(setup.dir(), setup.labels(), packets,
                packets.resolve("sealed"), SEED, temp.resolve("nested-report.json")))
                .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("separate");
        ObjectNode node = root(setup.dir(), "pilot-01");
        node.putArray("sourceNoteTexts").add("Synthetic model source");
        save(setup.dir(), "pilot-01", node);
        writeLabels(setup.dir(), setup.labels());
        assertThatThrownBy(() -> assemble(setup, "leak"))
                .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("Cannot assemble");
        assertThat(Files.exists(temp.resolve("packets-leak"))).isFalse();
    }
    @Test void outputPathsCannotOverwriteAndFailedLabelsWriteNothing() throws Exception {
        SetUp setup = synthetic();
        Path packets = Files.createDirectory(temp.resolve("occupied"));
        Files.writeString(packets.resolve("existing"), "synthetic existing file");
        assertThatThrownBy(() -> PilotFixtureAssembly.assemble(setup.dir(), setup.labels(), packets,
                temp.resolve("sealed-occupied"), SEED, temp.resolve("occupied-report.json")))
                .isInstanceOf(PilotFixtureAssembly.InvalidAssembly.class).hasMessageContaining("already exists");
        assertThat(Files.readString(packets.resolve("existing"))).isEqualTo("synthetic existing file");
        Files.writeString(setup.labels(), "");
        assertThatThrownBy(() -> assemble(setup, "invalid-labels"))
                .isInstanceOf(PilotLabelVerification.InvalidLabelVerification.class);
        assertThat(Files.exists(temp.resolve("packets-invalid-labels"))).isFalse();
    }
    @Test void packetHashesRepeatWithFixedSeedWhileRandomSealDiffers() throws Exception {
        SetUp setup = synthetic(); var a = assemble(setup, "first"); var b = assemble(setup, "second");
        for (int i = 0; i < 6; i++) assertThat(FreezeRecord.sha256(a.packets().get(i))).isEqualTo(FreezeRecord.sha256(b.packets().get(i)));
        assertThat(FreezeRecord.sha256(a.sealed())).isNotEqualTo(FreezeRecord.sha256(b.sealed()));
    }
}
