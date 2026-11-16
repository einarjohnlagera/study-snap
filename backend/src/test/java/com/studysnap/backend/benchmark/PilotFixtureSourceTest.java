package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PilotFixtureSourceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;
    @Test void strictWrapperEnumsAndRootCauseAreRequired() throws Exception {
        Path file = PilotPacketExportTest.fixture(temp, "first", 2);
        assertThat(PilotFixtureSource.load(file).questions()).hasSize(2);
        String valid = Files.readString(file);
        Files.writeString(file, valid.replace("\"authoringSource\":\"CODEX\",", ""));
        assertThatThrownBy(() -> PilotFixtureSource.load(file))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class);
        Files.writeString(file, valid.replace("\"rootCauseInput\":false", "\"rootCauseInput\":true"));
        assertThatThrownBy(() -> PilotFixtureSource.load(file))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class);
        Files.writeString(file, valid.replace("\"packetId\":\"first\"", "\"packetId\":\"first\",\"packetId\":\"first\""));
        assertThatThrownBy(() -> PilotFixtureSource.load(file))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class);
        Files.writeString(file, valid.replace("\"questions\":[", "\"extra\":true,\"questions\":["));
        assertThatThrownBy(() -> PilotFixtureSource.load(file))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class);
        ObjectNode empty = (ObjectNode) JSON.readTree(valid);
        empty.withArray("questions").removeAll();
        Files.writeString(file, JSON.writeValueAsString(empty));
        assertThatThrownBy(() -> PilotFixtureSource.load(file))
                .isInstanceOf(PilotFixtureSource.InvalidPilotFixture.class);
    }

    @Test void duplicateQuestionTextWithinOnePacketIsAllowed() throws Exception {
        Path file = PilotPacketExportTest.fixture(temp, "repeat", 2);
        ObjectNode root = (ObjectNode) JSON.readTree(Files.readString(file));
        ObjectNode second = (ObjectNode) root.withArray("questions").get(1).path("question");
        second.put("question", root.withArray("questions").get(0).path("question").path("question").asText());
        Files.writeString(file, JSON.writeValueAsString(root));
        assertThat(PilotFixtureSource.load(file).questions()).hasSize(2);
    }
}
