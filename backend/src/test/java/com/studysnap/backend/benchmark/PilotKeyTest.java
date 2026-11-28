package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PilotKeyTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int[] SIZES = {7, 7, 10, 24, 30, 48};
    @TempDir Path temp;

    static List<ObjectNode> revision4() {
        List<ObjectNode> rows = new ArrayList<>();
        int serial = 0;
        int critical = 0;
        for (PlantedType type : PlantedType.values()) {
            int count = switch (type) {
                case WRONG_KEY -> 6;
                case EXPLANATION_CONTRADICTS_KEY -> 3;
                case SECOND_DEFENSIBLE_ANSWER -> 5;
                case MATERIAL_AMBIGUITY_MULTI, MATERIAL_AMBIGUITY_ZERO, NUMERIC_INCONSISTENCY -> 2;
                case SOURCE_CONTRADICTION, MINOR_WORDING, UNHELPFUL_EXPLANATION -> 3;
                case WEAK_DISTRACTOR -> 4;
                case CLEAN -> 93;
            };
            for (int n = 0; n < count; n++) {
                int packet = 0;
                int offset = serial;
                while (offset >= SIZES[packet]) offset -= SIZES[packet++];
                ObjectNode row = JSON.createObjectNode();
                row.put("id", "pilot-%05d".formatted(++serial));
                row.put("packetId", "packet-" + packet);
                row.put("plantedType", type.name()).put("severity", type.severity().name());
                row.put("authoringFamily", type.severity() == PlantedType.Severity.CRITICAL && critical++ >= 11
                        ? "ANTHROPIC" : "OPENAI");
                row.put("rootCauseInput", serial == 1);
                row.put("canary", PilotPacketExportTest.CANARY);
                rows.add(row);
            }
        }
        return rows;
    }
    private PilotKey write(List<ObjectNode> rows) throws Exception {
        Path file = temp.resolve("key.jsonl");
        List<String> lines = new ArrayList<>();
        for (ObjectNode row : rows) lines.add(JSON.writeValueAsString(row));
        Files.writeString(file, String.join("\n", lines) + "\n");
        Files.writeString(file.resolveSibling("key.jsonl.sha256"), FreezeRecord.sha256(Files.readAllBytes(file)));
        return PilotKey.load(file);
    }

    @Test void exactRevision4CompositionPasses() throws Exception {
        PilotKey key = write(revision4());
        key.requireRevision4Composition();
        assertThat(key.countsBySeverity()).containsEntry(PlantedType.Severity.CRITICAL, 21L)
                .containsEntry(PlantedType.Severity.NONCRITICAL, 12L)
                .containsEntry(PlantedType.Severity.CLEAN, 93L);
        assertThat(key.packetSizes().values()).containsExactlyInAnyOrder(7, 7, 10, 24, 30, 48);
        assertThat(key.byAuthor(ModelFamily.ANTHROPIC)).hasSize(10);
    }
    @Test void everyPlantedCountIsChecked() throws Exception {
        for (PlantedType type : PlantedType.values()) {
            List<ObjectNode> rows = revision4();
            ObjectNode changed = rows.stream().filter(row -> row.path("plantedType").asText().equals(type.name()))
                    .findFirst().orElseThrow();
            PlantedType replacement = type == PlantedType.CLEAN ? PlantedType.WRONG_KEY : PlantedType.CLEAN;
            changed.put("plantedType", replacement.name()).put("severity", replacement.severity().name());
            if (replacement.severity() != PlantedType.Severity.CRITICAL) changed.put("rootCauseInput", false);
            PilotKey key = write(rows);
            assertThatThrownBy(key::requireRevision4Composition).isInstanceOf(PilotKey.InvalidPilotKey.class);
        }
    }
    @Test void packetSizesRootCauseAndBothFamiliesAreRequired() throws Exception {
        List<ObjectNode> rows = revision4();
        rows.getFirst().put("packetId", "packet-1");
        PilotKey changedSize = write(rows);
        assertThatThrownBy(changedSize::requireRevision4Composition)
                .isInstanceOf(PilotKey.InvalidPilotKey.class).hasMessageContaining("sizes");
        rows = revision4();
        rows.getFirst().put("rootCauseInput", false);
        PilotKey noInput = write(rows);
        assertThatThrownBy(noInput::requireRevision4Composition)
                .isInstanceOf(PilotKey.InvalidPilotKey.class).hasMessageContaining("root cause");
        rows = revision4();
        rows.stream().filter(row -> row.path("severity").asText().equals("CRITICAL"))
                .forEach(row -> row.put("authoringFamily", "OPENAI"));
        PilotKey oneFamily = write(rows);
        assertThatThrownBy(oneFamily::requireRevision4Composition)
                .isInstanceOf(PilotKey.InvalidPilotKey.class).hasMessageContaining("families");
        rows = revision4();
        rows.stream().filter(row -> row.path("authoringFamily").asText().equals("ANTHROPIC"))
                .findFirst().orElseThrow().put("authoringFamily", "OPENAI");
        rows.stream().filter(row -> row.path("authoringFamily").asText().equals("ANTHROPIC"))
                .findFirst().orElseThrow().put("authoringFamily", "OPENAI");
        rows.stream().filter(row -> row.path("authoringFamily").asText().equals("ANTHROPIC"))
                .findFirst().orElseThrow().put("authoringFamily", "OPENAI");
        PilotKey underEight = write(rows);
        assertThatThrownBy(underEight::requireRevision4Composition)
                .isInstanceOf(PilotKey.InvalidPilotKey.class).hasMessageContaining("families");
    }
    @Test void keyRejectsWrongFieldsEnumsAndCanaries() throws Exception {
        List<ObjectNode> rows = revision4();
        rows.getFirst().remove("id");
        List<ObjectNode> missingId = rows;
        assertThatThrownBy(() -> write(missingId)).isInstanceOf(PilotKey.InvalidPilotKey.class);
        rows = revision4();
        rows.getFirst().put("severity", "CLEAN");
        List<ObjectNode> wrongSeverity = rows;
        assertThatThrownBy(() -> write(wrongSeverity)).isInstanceOf(PilotKey.InvalidPilotKey.class);
        rows = revision4();
        rows.getFirst().put("canary", "CANARY-BAD");
        List<ObjectNode> wrongCanary = rows;
        assertThatThrownBy(() -> write(wrongCanary)).isInstanceOf(PilotKey.InvalidPilotKey.class);
        rows = revision4();
        rows.get(1).put("canary", "CANARY-AAAAAAAAAAAAAAAA");
        List<ObjectNode> mixedCanaries = rows;
        assertThatThrownBy(() -> write(mixedCanaries)).isInstanceOf(PilotKey.InvalidPilotKey.class);
        rows = revision4();
        rows.get(1).put("id", rows.getFirst().path("id").asText());
        List<ObjectNode> duplicateIds = rows;
        assertThatThrownBy(() -> write(duplicateIds)).isInstanceOf(PilotKey.InvalidPilotKey.class);
        rows = revision4();
        rows.getLast().put("rootCauseInput", true);
        List<ObjectNode> noncriticalInput = rows;
        assertThatThrownBy(() -> write(noncriticalInput)).isInstanceOf(PilotKey.InvalidPilotKey.class);
    }
}
