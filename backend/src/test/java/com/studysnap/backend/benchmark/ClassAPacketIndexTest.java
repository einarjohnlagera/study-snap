package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.entity.LearnerLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassAPacketIndexTest {
    private static final String CASE = "synthetic-case";
    private static final String FIRST_ARM = "BASELINE_MINI";
    private static final String SECOND_ARM = "LUNA";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    static ClassABlindPacketExport.Export export(Path temp) {
        var source = new ClassACaseManifest.Source("Cells", ClassAScoreSchemaV2Test.SOURCE, "Boundary",
                List.of("membrane"), List.of("cells"), "Biology", "Biology", null, LearnerLevel.COLLEGE);
        var fixture = new ClassACaseManifest.Case(CASE, ClassACaseManifest.Workflow.NOTE_ADAPTIVE,
                "synthetic", LearnerLevel.COLLEGE, null, "Biology", false, List.of(source), 1, null, false, null);
        ObjectNode question = ClassAScoreSchemaV2Test.packet("MCQ").question().deepCopy();
        var questions = JSON.createArrayNode().add(question);
        var outputs = List.of(new ClassABlindPacketExport.Output(CASE, FIRST_ARM, questions),
                new ClassABlindPacketExport.Output(CASE, SECOND_ARM, questions));
        return ClassABlindPacketExport.export(List.of(fixture), outputs, FIRST_ARM, SECOND_ARM,
                temp.resolve("packets"), temp.resolve("arm-key.jsonl"), 17L);
    }

    @Test void indexesRealBlindPacketAndKeepsOriginalAnswerFields() {
        var exported = export(temp);
        ClassAPacketIndex index = ClassAPacketIndex.load(temp.resolve("packets"));
        assertThat(index.size()).isEqualTo(2);
        String id = "blind-00001";
        var packet = index.get(id);
        assertThat(packet.question().path("correctIndex").asInt()).isZero();
        assertThat(packet.question().path("question").asText()).isEqualTo(ClassAScoreSchemaV2Test.QUESTION);
        assertThat(packet.sourceNoteTexts()).containsExactly(ClassAScoreSchemaV2Test.SOURCE);
        assertThat(Files.exists(exported.armKey())).isTrue();
    }

    @Test void duplicateItemIdAndMissingKeyArePacketFailures() throws Exception {
        var exported = export(temp);
        Path file = exported.packets().getFirst();
        String body = Files.readString(file);
        Files.writeString(file, body.replace("blind-00002", "blind-00001"));
        assertThatThrownBy(() -> ClassAPacketIndex.load(temp.resolve("packets")))
                .isInstanceOf(ClassAPacketIndex.InvalidPacket.class).hasMessageContaining("Duplicate packet item id");
        Files.writeString(file, body.replace("\"correctIndex\":0,", ""));
        assertThatThrownBy(() -> ClassAPacketIndex.load(temp.resolve("packets")))
                .isInstanceOf(ClassAPacketIndex.InvalidPacket.class).hasMessageContaining("packet key index");
    }

    @Test void malformedAndDuplicateJsonPacketFieldsAreRejected() throws Exception {
        var exported = export(temp);
        Path file = exported.packets().getFirst();
        Files.writeString(file, "{\"outputs\":[],\"outputs\":[]}");
        assertThatThrownBy(() -> ClassAPacketIndex.load(temp.resolve("packets")))
                .isInstanceOf(ClassAPacketIndex.InvalidPacket.class);
    }
}
