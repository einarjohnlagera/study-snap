package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PacketMetadataScanTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> TOKENS = Set.of("LUNA", "BASELINE_MINI");
    @TempDir Path temp;
    private Path packet() {
        BlindReviewFixtures.classA(temp, 2);
        return temp.resolve("packets/NOTE_ADAPTIVE.jsonl");
    }
    private ObjectNode first(Path file) throws Exception {
        return (ObjectNode) JSON.readTree(Files.readAllLines(file).getFirst());
    }
    private void first(Path file, ObjectNode row) throws Exception {
        List<String> lines = Files.readAllLines(file);
        Files.write(file, List.of(row.toString(), lines.get(1)));
    }
    private void leaking() {
        Path directory = temp.resolve("packets");
        assertThatThrownBy(() -> PacketMetadataScan.scan(directory, TOKENS))
                .isInstanceOf(PacketMetadataScan.MetadataLeak.class);
    }
    @Test void realClassAAndBExporterPacketsPass() {
        packet();
        PacketMetadataScan.scan(temp.resolve("packets"), TOKENS);
        BlindReviewFixtures.classBAll(temp.resolve("b"), false);
        PacketMetadataScan.scan(temp.resolve("b/packets"), TOKENS);
    }
    @Test void realExporterTurnTimestampFailsAndNamedNestedFieldsAreKnown() throws Exception {
        BlindReviewFixtures.classBAll(temp, true);
        assertThat(PacketMetadataScan.B_NESTED_METADATA_NAMES).containsExactlyInAnyOrder("id", "createdAt");
        assertThat(Files.readString(temp.resolve("packets/ASK_COMPANION.jsonl"))).contains("\"createdAt\":");
        leaking();
    }
    @Test void fileNameAndIdMustBeNeutral() throws Exception {
        Path file = packet();
        Files.move(file, file.resolveSibling("LUNA.jsonl"));
        leaking();
        Files.move(file.resolveSibling("LUNA.jsonl"), file);
        ObjectNode row = first(file);
        ((ObjectNode) row.path("outputs").get(0).path("items").get(0)).put("itemId", "case-1");
        first(file, row);
        leaking();
    }
    @Test void structuralKeyFails() throws Exception {
        Path file = packet();
        ObjectNode row = first(file);
        ((ObjectNode) row.path("outputs").get(0)).put("unknown", "value");
        first(file, row);
        leaking();
    }
    @Test void structuralTimestampFails() throws Exception {
        BlindReviewFixtures.classB(temp, 2);
        Path file = temp.resolve("packets/STUDY_TIP.jsonl");
        ObjectNode row = first(file);
        ((ObjectNode) row.path("outputs").get(0)).put("output", BlindReviewFixtures.CLOCK);
        first(file, row);
        leaking();
    }
    @Test void itemIdsMustIncreaseWithinFile() throws Exception {
        Path file = packet();
        ObjectNode row = first(file);
        ((ObjectNode) row.path("outputs").get(1).path("items").get(0)).put("itemId", "blind-00000");
        first(file, row);
        leaking();
    }
    @Test void nestedQuestionTwelveDigitIntegerFails() throws Exception {
        Path file = packet();
        ObjectNode row = first(file);
        ObjectNode question = (ObjectNode) row.path("outputs").get(0).path("items").get(0).path("question");
        question.put("workingSolution", 123456789012L);
        first(file, row);
        leaking();
    }
    @Test void nestedQuestionTwelveDigitTextFails() throws Exception {
        Path file = packet();
        ObjectNode row = first(file);
        ObjectNode question = (ObjectNode) row.path("outputs").get(0).path("items").get(0).path("question");
        question.put("workingSolution", "123456789012");
        first(file, row);
        leaking();
    }
    @Test void nestedQuestionIsoDateTimeFails() throws Exception {
        Path file = packet();
        ObjectNode row = first(file);
        ObjectNode question = (ObjectNode) row.path("outputs").get(0).path("items").get(0).path("question");
        question.put("workingSolution", BlindReviewFixtures.CLOCK);
        first(file, row);
        leaking();
    }
    @Test void nestedQuestionIsoDateFails() throws Exception {
        Path file = packet();
        ObjectNode row = first(file);
        ObjectNode question = (ObjectNode) row.path("outputs").get(0).path("items").get(0).path("question");
        question.put("workingSolution", "2026-11-16");
        first(file, row);
        leaking();
    }
    @Test void nestedQuestionUnlistedAtAndIdFail() throws Exception {
        Path file = packet();
        ObjectNode row = first(file);
        ObjectNode question = (ObjectNode) row.path("outputs").get(0).path("items").get(0).path("question");
        question.put("createdAt", "ordinary");
        first(file, row);
        leaking();
        question.remove("createdAt");
        question.put("sourceId", "ordinary");
        first(file, row);
        leaking();
    }
    @Test void nestedClassBDateTimeFails() throws Exception {
        BlindReviewFixtures.classB(temp, 2);
        Path file = temp.resolve("packets/STUDY_TIP.jsonl");
        ObjectNode row = first(file);
        ObjectNode fixture = (ObjectNode) row.path("fixture");
        ObjectNode incorrect = (ObjectNode) fixture.path("incorrectQuestions").get(0);
        incorrect.put("createdAt", BlindReviewFixtures.CLOCK);
        first(file, row);
        leaking();
    }
    @Test void nestedClassBUnlistedIdFails() throws Exception {
        BlindReviewFixtures.classB(temp, 2);
        Path file = temp.resolve("packets/STUDY_TIP.jsonl");
        ObjectNode row = first(file);
        ObjectNode fixture = (ObjectNode) row.path("fixture");
        ObjectNode incorrect = (ObjectNode) fixture.path("incorrectQuestions").get(0);
        incorrect.put("extraId", "ordinary");
        first(file, row);
        leaking();
    }
    @Test void emptyDirectoryAndShortForbiddenTokenFail() throws Exception {
        Path empty = temp.resolve("empty");
        Files.createDirectories(empty);
        Set<String> shortToken = Set.of("abc");
        assertThatThrownBy(() -> PacketMetadataScan.scan(empty, TOKENS))
                .isInstanceOf(PacketMetadataScan.MetadataLeak.class);
        assertThatThrownBy(() -> PacketMetadataScan.scan(empty, shortToken))
                .isInstanceOf(PacketMetadataScan.InvalidScan.class);
        assertThat(Files.list(empty).count()).isZero();
    }
}
