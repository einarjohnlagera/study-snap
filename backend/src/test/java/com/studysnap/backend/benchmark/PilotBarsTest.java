package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PilotBarsTest {
    private static final String L_BARS_JSON = "bars.json";
    private static final String L_BARS_JSON_SHA256 = "bars.json.sha256";
    private static final String L_BARS = "bars";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;
    private Path source() { return Path.of("src/test/java/com/studysnap/backend/benchmark/pilot-bars-test-only.json"); }
    private Path write(ObjectNode node) throws Exception {
        Path file = temp.resolve(L_BARS_JSON);
        Files.writeString(file, node.toString());
        Files.writeString(file.resolveSibling(L_BARS_JSON_SHA256), FreezeRecord.sha256(Files.readAllBytes(file)));
        return file;
    }
    private ObjectNode json() throws Exception { return (ObjectNode) JSON.readTree(Files.readAllBytes(source())); }
    @Test void detachedHashIsRequiredAndChecked() throws Exception {
        Path file = temp.resolve(L_BARS_JSON);
        Files.copy(source(), file);
        assertThatThrownBy(() -> PilotBars.load(file)).isInstanceOf(PilotBars.InvalidPilotBars.class);
        Files.writeString(file.resolveSibling(L_BARS_JSON_SHA256), "wrong");
        assertThatThrownBy(() -> PilotBars.load(file)).isInstanceOf(PilotBars.InvalidPilotBars.class);
        Files.writeString(file.resolveSibling(L_BARS_JSON_SHA256), FreezeRecord.sha256(Files.readAllBytes(file)));
        assertThat(PilotBars.load(file).sha256()).isEqualTo(FreezeRecord.sha256(Files.readAllBytes(file)));
    }
    @Test void invalidRangesAndFieldSetsAreRejected() throws Exception {
        ObjectNode root = json();
        ((ObjectNode) root.path(L_BARS)).put("minCriticalRecallCount", 99);
        Path file = write(root);
        Path outOfRange = file;
        assertThatThrownBy(() -> PilotBars.load(outOfRange)).isInstanceOf(PilotBars.InvalidPilotBars.class);
        root = json(); ((ObjectNode) root.path(L_BARS)).remove("criticalTotal"); file = write(root);
        Path missing = file;
        assertThatThrownBy(() -> PilotBars.load(missing)).isInstanceOf(PilotBars.InvalidPilotBars.class);
        root = json(); root.put("unknown", true); file = write(root); Path unknown = file;
        assertThatThrownBy(() -> PilotBars.load(unknown)).isInstanceOf(PilotBars.InvalidPilotBars.class);
        root = json(); ((ObjectNode) root.path("limits").path("P")).put("tokenStop", -1); file = write(root);
        Path negative = file;
        assertThatThrownBy(() -> PilotBars.load(negative)).isInstanceOf(PilotBars.InvalidPilotBars.class);
    }
    @Test void duplicateJsonFieldsAreRejected() throws Exception {
        Path file = temp.resolve(L_BARS_JSON);
        String raw = Files.readString(source()).replaceFirst("\\\"criticalTotal\\\": 21,", "\\\"criticalTotal\\\": 21, \\\"criticalTotal\\\": 21,");
        Files.writeString(file, raw);
        Files.writeString(file.resolveSibling(L_BARS_JSON_SHA256), FreezeRecord.sha256(Files.readAllBytes(file)));
        assertThatThrownBy(() -> PilotBars.load(file)).isInstanceOf(PilotBars.InvalidPilotBars.class);
    }
}
