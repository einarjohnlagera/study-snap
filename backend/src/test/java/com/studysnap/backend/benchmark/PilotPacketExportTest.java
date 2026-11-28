package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PilotPacketExportTest {
    static final String CANARY = "CANARY-0123456789ABCDEF";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    static Path fixture(Path directory, String packetId, int count) throws Exception {
        Files.createDirectories(directory);
        ObjectNode root = JSON.createObjectNode();
        root.put("packetId", packetId);
        root.putArray("sourceNoteTexts").add("The cell membrane surrounds the cell.");
        ArrayNode entries = root.putArray("questions");
        for (int i = 0; i < count; i++) {
            ObjectNode entry = entries.addObject();
            ObjectNode question = ClassAScoreSchemaV2Test.packet("MCQ").question().deepCopy();
            question.put("question", "What surrounds cell number " + i + " in " + packetId + "?");
            question.put("sourceStudyPackId", 99);
            entry.set("question", question);
            entry.putObject("planted").put("plantedType", "CLEAN")
                    .put("authoringFamily", "OPENAI").put("rootCauseInput", false);
        }
        Path file = directory.resolve(packetId + ".json");
        Files.writeString(file, JSON.writeValueAsString(root));
        return file;
    }
    private List<Path> three() throws Exception {
        return List.of(fixture(temp.resolve("fixtures"), "alpha", 5),
                fixture(temp.resolve("fixtures"), "beta", 5),
                fixture(temp.resolve("fixtures"), "gamma", 5));
    }
    private PilotPacketExport.Export export(List<Path> files, String name, long seed) {
        return PilotPacketExport.export(files, temp.resolve(name + "-packets"), temp.resolve(name + "-key.jsonl"),
                CANARY, seed);
    }

    @Test void threePacketsAndSealedKeyUseProductionIndexAndUnchangedScoreLoader() throws Exception {
        PilotPacketExport.Export result = export(three(), "first", 17);
        ClassAPacketIndex index = ClassAPacketIndex.loadPilot(temp.resolve("first-packets"));
        PilotKey key = PilotKey.load(result.keyPath());
        assertThat(index.size()).isEqualTo(15);
        assertThat(index.get("pilot-00001").question().path("questionFormat").asText()).isEqualTo("MCQ");
        assertThat(index.get("pilot-00001").question().path("keyedAnswer").asText()).isEqualTo("membrane");
        assertThat(key.rows()).hasSize(15);
        assertThat(result.packets()).hasSize(3);
        assertThat(result.keySha256()).isEqualTo(FreezeRecord.sha256(Files.readAllBytes(result.keyPath())));
        assertThat(Files.readString(result.keyPath().resolveSibling(result.keyPath().getFileName() + ".sha256")).trim())
                .isEqualTo(result.keySha256());
        List<String> p = new ArrayList<>(), s = new ArrayList<>(), h = new ArrayList<>();
        for (PilotKey.Row row : key.rows()) {
            p.add(JSON.writeValueAsString(ClassAScoreSchemaV2Test.scorer(row.id(), "P")));
            s.add(JSON.writeValueAsString(ClassAScoreSchemaV2Test.scorer(row.id(), "S")));
            h.add(JSON.writeValueAsString(ClassAScoreSchemaV2Test.human(row.id())));
        }
        Path pFile = temp.resolve("p.jsonl"), sFile = temp.resolve("s.jsonl"), hFile = temp.resolve("h.jsonl");
        Files.writeString(pFile, String.join("\n", p) + "\n");
        Files.writeString(sFile, String.join("\n", s) + "\n");
        Files.writeString(hFile, String.join("\n", h) + "\n");
        Map<String, ClassAScoreFilesV2.ItemScores> scores = ClassAScoreFilesV2.load(
                result.keyPath(), index, pFile, sFile, hFile, ClassAScoreSchemaV2Test.MAPPING);
        assertThat(scores).hasSize(15);
        assertThat(scores.values()).allSatisfy(score -> {
            assertThat(score.p().status()).isEqualTo(ClassAScoreFilesV2.Status.OK);
            assertThat(score.s().status()).isEqualTo(ClassAScoreFilesV2.Status.OK);
            assertThat(score.h()).isNotNull();
        });
        for (Path packet : result.packets()) {
            String body = Files.readString(packet);
            assertThat(body).doesNotContain("planted", "authoringFamily", CANARY,
                    result.keyPath().toAbsolutePath().toString(), "sourceStudyPackId");
        }
    }

    @Test void sealedKeyIdIsReadableByUnchangedScoreLoaderWithoutPilotKeyReader() throws Exception {
        PilotPacketExport.Export result = export(three(), "direct", 17);
        ClassAPacketIndex index = ClassAPacketIndex.loadPilot(temp.resolve("direct-packets"));
        Path pFile = temp.resolve("empty-p.jsonl"), sFile = temp.resolve("empty-s.jsonl"),
                hFile = temp.resolve("empty-h.jsonl");
        Files.writeString(pFile, "");
        Files.writeString(sFile, "");
        Files.writeString(hFile, "");
        assertThat(ClassAScoreFilesV2.load(result.keyPath(), index, pFile, sFile, hFile,
                ClassAScoreSchemaV2Test.MAPPING)).hasSize(15);
    }

    @Test void shuffleIsSeededAndQuestionOrderChanges() throws Exception {
        List<Path> files = three();
        PilotPacketExport.Export first = export(files, "first", 17);
        PilotPacketExport.Export same = export(files, "same", 17);
        PilotPacketExport.Export different = export(files, "different", 123);
        for (int i = 0; i < 3; i++)
            assertThat(Files.readAllBytes(first.packets().get(i)))
                    .isEqualTo(Files.readAllBytes(same.packets().get(i)));
        assertThat(Files.readAllBytes(first.keyPath())).isEqualTo(Files.readAllBytes(same.keyPath()));
        assertThat(Files.readString(first.packets().getFirst()))
                .isNotEqualTo(Files.readString(different.packets().getFirst()));
    }

    @Test void invalidCanaryDuplicateIdAndLeakWordFailClosed() throws Exception {
        List<Path> files = three();
        assertThatThrownBy(() -> PilotPacketExport.export(files, temp.resolve("bad-packets"),
                temp.resolve("bad-key"), "bad", 1))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class);
        List<Path> duplicates = List.of(files.getFirst(), files.getFirst());
        assertThatThrownBy(() -> export(duplicates, "duplicate", 1))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class).hasMessageContaining("Duplicate packet id");
        Path first = files.getFirst();
        Files.writeString(first, Files.readString(first).replace("What surrounds", "Which token surrounds"));
        assertThatThrownBy(() -> export(files, "leak", 1))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class).hasMessageContaining("alpha");
    }

    @Test void keyAndPacketPathsCannotNestAndKeyTamperingFails() throws Exception {
        List<Path> files = three();
        Path packetDirectory = temp.resolve("nested");
        Path keyInside = packetDirectory.resolve("key.jsonl");
        assertThatThrownBy(() -> PilotPacketExport.export(files, packetDirectory, keyInside, CANARY, 1))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class);
        Path keyDirectory = temp.resolve("other");
        assertThatThrownBy(() -> PilotPacketExport.export(files, keyDirectory.resolve("packets"),
                keyDirectory, CANARY, 1))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class);
        PilotPacketExport.Export result = export(files, "good", 1);
        Files.writeString(result.keyPath(),
                Files.readString(result.keyPath()).replaceFirst("OPENAI", "ANTHROPIC"));
        assertThatThrownBy(() -> PilotKey.load(result.keyPath()))
                .isInstanceOf(PilotKey.InvalidPilotKey.class).hasMessageContaining("SHA-256");
    }

    @Test void privateEvaluationPathsAreRejectedWithoutAccessingThem() throws Exception {
        List<Path> files = three();
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        Path privatePath = repo.resolve(ClassBCaseManifest.PRIVATE_PATH).resolve("pilot");
        assertThatThrownBy(() -> PilotPacketExport.export(files, privatePath,
                temp.resolve("outside-key.jsonl"), CANARY, 1))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class);
        assertThatThrownBy(() -> PilotPacketExport.export(files, temp.resolve("outside-packets"),
                privatePath.resolve("key.jsonl"), CANARY, 1))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class);
    }
}
