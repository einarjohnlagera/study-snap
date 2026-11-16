package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PairMapTest {
    @TempDir Path temp;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PACKET = "packets";
    private static final String STUDY_TIP_FILE = "STUDY_TIP.jsonl";
    private Path classBFile() {
        BlindReviewFixtures.classB(temp, 2);
        return temp.resolve(PACKET).resolve(STUDY_TIP_FILE);
    }
    private ObjectNode first(Path file) throws Exception {
        return (ObjectNode) JSON.readTree(Files.readAllLines(file).getFirst());
    }
    private void replaceFirst(Path file, ObjectNode row) throws Exception {
        List<String> lines = Files.readAllLines(file);
        Files.write(file, List.of(row.toString(), lines.get(1)));
    }
    @Test void realClassAAndClassBShapesMapWholePairs() {
        BlindReviewFixtures.classA(temp.resolve("a"), 2);
        PairMap a = PairMap.fromPackets(temp.resolve("a/packets"));
        assertThat(a.pairs()).hasSize(2);
        assertThat(a.blindItemIds()).hasSize(4);
        assertThat(a.itemsOf(a.pairs().getFirst().id(), "A")).hasSize(1);
        assertThat(a.workflowOf(a.pairs().getFirst().id())).isEqualTo(BlindReviewFixtures.WORKFLOW_A);
        ClassBBlindPacketExport.Export exported = BlindReviewFixtures.classB(temp.resolve("b"), 2);
        PairMap b = PairMap.fromPackets(temp.resolve("b/packets"));
        assertThat(b.pairs()).hasSize(2);
        assertThat(b.blindItemIds()).containsExactlyInAnyOrderElementsOf(
                exported.manifest().stream().map(ClassBScoreFiles.BlindItem::itemId).toList());
        String item = b.itemsOf(b.pairs().getFirst().id(), "B").getFirst();
        assertThat(b.pairOf(item)).isEqualTo(b.pairs().getFirst().id());
        assertThat(b.placementOf(item)).isEqualTo("B");
    }
    @Test void pilotSingleOutputAndThreeOutputsFail() throws Exception {
        Path file = classBFile();
        Path directory = temp.resolve(PACKET);
        ObjectNode row = first(file);
        ArrayNode outputs = (ArrayNode) row.path("outputs");
        outputs.remove(1);
        replaceFirst(file, row);
        assertThatThrownBy(() -> PairMap.fromPackets(directory))
                .isInstanceOf(PairMap.InvalidPairMap.class);
        outputs.add(outputs.get(0).deepCopy());
        outputs.add(outputs.get(0).deepCopy());
        replaceFirst(file, row);
        assertThatThrownBy(() -> PairMap.fromPackets(directory))
                .isInstanceOf(PairMap.InvalidPairMap.class);
    }
    @Test void wrongPlacementsAndDuplicateIdsFail() throws Exception {
        Path file = classBFile();
        Path directory = temp.resolve(PACKET);
        ObjectNode row = first(file);
        ((ObjectNode) row.path("outputs").get(1)).put("placement", "A");
        replaceFirst(file, row);
        assertThatThrownBy(() -> PairMap.fromPackets(directory))
                .isInstanceOf(PairMap.InvalidPairMap.class);
        ((ObjectNode) row.path("outputs").get(1)).put("placement", "B")
                .put("itemId", row.path("outputs").get(0).path("itemId").asText());
        replaceFirst(file, row);
        assertThatThrownBy(() -> PairMap.fromPackets(directory))
                .isInstanceOf(PairMap.InvalidPairMap.class);
    }
    @Test void blankAndLeakLineFail() throws Exception {
        Path file = classBFile();
        Path directory = temp.resolve(PACKET);
        Files.writeString(file, Files.readString(file) + "\n");
        assertThatThrownBy(() -> PairMap.fromPackets(directory))
                .isInstanceOf(PairMap.InvalidPairMap.class);
        ObjectNode row = first(file);
        ((ObjectNode) row.path("outputs").get(0)).put("output", "baseline");
        replaceFirst(file, row);
        assertThatThrownBy(() -> PairMap.fromPackets(directory))
                .isInstanceOf(PairMap.InvalidPairMap.class);
    }
    @Test void idOnlyKeyFeedsUnchangedClassAScoreLoaderAndCannotOverwrite() throws Exception {
        BlindReviewFixtures.classA(temp, 2);
        PairMap pairs = PairMap.fromPackets(temp.resolve(PACKET));
        Path key = temp.resolve("id-only.jsonl");
        pairs.idOnlyKey(key);
        NoArmFields.check(key);
        Path p = temp.resolve("p.jsonl"), s = temp.resolve("s.jsonl"), h = temp.resolve("h.jsonl");
        Files.writeString(p, ""); Files.writeString(s, ""); Files.writeString(h, "");
        assertThat(ClassAScoreFilesV2.load(key, ClassAPacketIndex.load(temp.resolve(PACKET)),
                p, s, h, ClassAScoreSchemaV2Test.MAPPING)).hasSize(4);
        assertThat(Files.readString(key)).doesNotContain("arm");
        assertThatThrownBy(() -> pairs.idOnlyKey(key)).isInstanceOf(PairMap.InvalidPairMap.class);
    }
    @Test void unreadableDirectoryFails() {
        Path missing = temp.resolve("missing");
        assertThatThrownBy(() -> PairMap.fromPackets(missing)).isInstanceOf(PairMap.InvalidPairMap.class);
    }
}
