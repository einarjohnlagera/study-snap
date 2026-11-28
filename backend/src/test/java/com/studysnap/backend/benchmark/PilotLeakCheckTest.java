package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PilotLeakCheckTest {
    private static final String RATIONALE = "The membrane choice is supported by this source.";
    @TempDir Path temp;
    private PilotKey key() throws Exception {
        Path fixture = PilotPacketExportTest.fixture(temp.resolve("fixtures"), "alpha", 1);
        PilotPacketExport.Export exported = PilotPacketExport.export(List.of(fixture),
                temp.resolve("packets"), temp.resolve("sealed-key.jsonl"), PilotPacketExportTest.CANARY, 1);
        return PilotKey.load(exported.keyPath());
    }
    private Map<PilotLeakCheck.ArtifactClass, List<Path>> artifacts() throws Exception {
        Path prompt = temp.resolve("prompt.txt"), packet = temp.resolve("packet.txt"), output = temp.resolve("output.txt");
        Files.writeString(prompt, "Read the questions and source.");
        Files.writeString(packet, "What surrounds the cell?");
        Files.writeString(output, "The answer is the membrane.");
        return Map.of(PilotLeakCheck.ArtifactClass.PROMPT, List.of(prompt),
                PilotLeakCheck.ArtifactClass.PACKET, List.of(packet),
                PilotLeakCheck.ArtifactClass.OUTPUT, List.of(output));
    }
    private Path path(Map<PilotLeakCheck.ArtifactClass, List<Path>> artifacts,
                      PilotLeakCheck.ArtifactClass type) { return artifacts.get(type).getFirst(); }
    private List<PilotLeakCheck.Hit> scan(Map<PilotLeakCheck.ArtifactClass, List<Path>> artifacts, PilotKey key,
                                         Set<String> extras) {
        return PilotLeakCheck.scan(artifacts, key, temp.resolve("sealed-key.jsonl"), extras);
    }
    private void hit(PilotLeakCheck.ArtifactClass type, String text, String kind) throws Exception {
        PilotKey key = key();
        var artifacts = artifacts();
        Files.writeString(path(artifacts, type), text);
        assertThat(scan(artifacts, key, Set.of())).anySatisfy(found -> {
            assertThat(found.artifactClass()).isEqualTo(type);
            assertThat(found.kind()).isEqualTo(kind);
        });
    }
    @Test void cleanAllThreeClassesPass() throws Exception {
        PilotKey key = key();
        var artifacts = artifacts();
        assertThat(scan(artifacts, key, Set.of())).isEmpty();
        PilotLeakCheck.assertClean(artifacts, key, temp.resolve("sealed-key.jsonl"), Set.of());
    }
    @Test void canaryPathAuthorTypeAndStageOnePatternAreScannedEverywhere() throws Exception {
        hit(PilotLeakCheck.ArtifactClass.PACKET, PilotPacketExportTest.CANARY, "CANARY");
        hit(PilotLeakCheck.ArtifactClass.OUTPUT, PilotPacketExportTest.CANARY, "CANARY");
        hit(PilotLeakCheck.ArtifactClass.PROMPT, temp.resolve("sealed-key.jsonl").toAbsolutePath().toString(), "KEY_PATH");
        hit(PilotLeakCheck.ArtifactClass.PROMPT, "CODEX", "AUTHOR");
        hit(PilotLeakCheck.ArtifactClass.PROMPT, "OPENAI", "AUTHOR");
        hit(PilotLeakCheck.ArtifactClass.OUTPUT, "token", "STAGE1_LEAK");
        hit(PilotLeakCheck.ArtifactClass.PACKET, "WRONG_KEY", "PLANTED_TYPE");
        hit(PilotLeakCheck.ArtifactClass.PROMPT, "planted", "PLANTED_FIELD");
        hit(PilotLeakCheck.ArtifactClass.PROMPT, "sealed-key.jsonl", "KEY_NAME");
    }
    @Test void rationaleAndNormalizedCanaryAreDetected() throws Exception {
        PilotKey key = key();
        var artifacts = artifacts();
        Path template = temp.resolve("placeholder-template.txt");
        Path rationalePacket = temp.resolve("rationale-packet.jsonl");
        Files.writeString(template, "Read this packet:\n{{PACKET_JSONL}}");
        Files.writeString(rationalePacket, RATIONALE);
        ScorerPromptRender.render(template, "S", rationalePacket,
                path(artifacts, PilotLeakCheck.ArtifactClass.PROMPT));
        assertThat(scan(artifacts, key, Set.of(RATIONALE))).anySatisfy(found ->
                assertThat(found.kind()).isEqualTo("FORBIDDEN_EXTRA"));
        Files.writeString(path(artifacts, PilotLeakCheck.ArtifactClass.PROMPT), "neutral text");
        Files.writeString(path(artifacts, PilotLeakCheck.ArtifactClass.OUTPUT), "canary-0123*456789$abcdef");
        assertThat(scan(artifacts, key, Set.of())).anySatisfy(found ->
                assertThat(found.kind()).isEqualTo("CANARY"));
        assertThatThrownBy(() -> PilotLeakCheck.assertClean(artifacts, key,
                temp.resolve("sealed-key.jsonl"), Set.of()))
                .isInstanceOf(PilotLeakCheck.LeakDetected.class);
    }
    @Test void missingEmptyUnreadableAndShortExtraFailClosed() throws Exception {
        PilotKey key = key();
        var artifacts = artifacts();
        Map<PilotLeakCheck.ArtifactClass, List<Path>> missing = Map.of(
                PilotLeakCheck.ArtifactClass.PROMPT, artifacts.get(PilotLeakCheck.ArtifactClass.PROMPT),
                PilotLeakCheck.ArtifactClass.PACKET, artifacts.get(PilotLeakCheck.ArtifactClass.PACKET));
        assertThatThrownBy(() -> scan(missing, key, Set.of()))
                .isInstanceOf(PilotLeakCheck.InvalidLeakScan.class);
        Map<PilotLeakCheck.ArtifactClass, List<Path>> noPrompt = Map.of(
                PilotLeakCheck.ArtifactClass.PACKET, artifacts.get(PilotLeakCheck.ArtifactClass.PACKET),
                PilotLeakCheck.ArtifactClass.OUTPUT, artifacts.get(PilotLeakCheck.ArtifactClass.OUTPUT));
        assertThatThrownBy(() -> scan(noPrompt, key, Set.of()))
                .isInstanceOf(PilotLeakCheck.InvalidLeakScan.class);
        Map<PilotLeakCheck.ArtifactClass, List<Path>> empty = Map.of(
                PilotLeakCheck.ArtifactClass.PROMPT, List.of(),
                PilotLeakCheck.ArtifactClass.PACKET, artifacts.get(PilotLeakCheck.ArtifactClass.PACKET),
                PilotLeakCheck.ArtifactClass.OUTPUT, artifacts.get(PilotLeakCheck.ArtifactClass.OUTPUT));
        assertThatThrownBy(() -> scan(empty, key, Set.of()))
                .isInstanceOf(PilotLeakCheck.InvalidLeakScan.class);
        Map<PilotLeakCheck.ArtifactClass, List<Path>> emptyOutput = Map.of(
                PilotLeakCheck.ArtifactClass.PROMPT, artifacts.get(PilotLeakCheck.ArtifactClass.PROMPT),
                PilotLeakCheck.ArtifactClass.PACKET, artifacts.get(PilotLeakCheck.ArtifactClass.PACKET),
                PilotLeakCheck.ArtifactClass.OUTPUT, List.of());
        assertThatThrownBy(() -> scan(emptyOutput, key, Set.of()))
                .isInstanceOf(PilotLeakCheck.InvalidLeakScan.class);
        Files.delete(path(artifacts, PilotLeakCheck.ArtifactClass.OUTPUT));
        assertThatThrownBy(() -> scan(artifacts, key, Set.of()))
                .isInstanceOf(PilotLeakCheck.InvalidLeakScan.class);
        assertThatThrownBy(() -> scan(artifacts, key, Set.of("short")))
                .isInstanceOf(PilotLeakCheck.InvalidLeakScan.class);
    }
    @Test void stageOneLeakPatternIsPinned() {
        assertThat(ClassBBlindPacketExport.LEAK.pattern()).isEqualTo(
                "(?i)(?:\\bbaseline\\b|\\bcandidate\\b|\\bluna\\b|\\bsol\\b|\\bgpt(?:[-_. ]?[0-9])?\\b|"
                + "\\b(?:codex|claude|gemini|openai|model|modelName|modelId|token|tokens|tokenCount|"
                + "token_count|inputTokens|outputTokens|input_tokens|output_tokens|reasoning_tokens|usage|costUsd)\\b)");
    }
}
