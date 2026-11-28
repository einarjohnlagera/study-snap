package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PilotFamiliesTest {
    @TempDir Path temp;
    private static final String FILE = "families.json";
    private static final String OPENAI = "OPENAI";
    private static final String ANTHROPIC = "ANTHROPIC";
    private static final String P = "P";
    private static final String S = "S";
    private static final String PACKETS = "packets";
    private static final String SCORERS = "scorers";

    static ObjectNode valid(String pModel, String sModel) {
        ObjectNode root = IntegritySupport.JSON.createObjectNode();
        root.put("schema", 1);
        ObjectNode packets = root.putObject(PACKETS);
        for (int n = 1; n <= 6; n++) {
            boolean openai = n == 1 || n == 2 || n == 6;
            ObjectNode packet = packets.putObject("pilot-%02d".formatted(n));
            packet.put("authorFamily", openai ? OPENAI : ANTHROPIC)
                    .put("authorTool", "runner-a").put("authorModelId", "author-model")
                    .put("authorSessionId", openai ? "author-openai" : "author-anthropic")
                    .put("verifierFamily", openai ? ANTHROPIC : OPENAI)
                    .put("verifierTool", "runner-b").put("verifierModelId", "verifier-model")
                    .put("verifierSessionId", openai ? "verifier-anthropic" : "verifier-openai");
        }
        ObjectNode scorers = root.putObject(SCORERS);
        scorers.putObject(P).put("family", OPENAI).put("tool", "scorer-p").put("modelId", pModel);
        scorers.putObject(S).put("family", ANTHROPIC).put("tool", "scorer-s").put("modelId", sModel);
        return root;
    }
    static Path write(Path directory, ObjectNode node) throws Exception {
        Path file = directory.resolve(FILE);
        Files.writeString(file, node.toString());
        Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(Files.readAllBytes(file)) + "\n");
        return file;
    }
    private ObjectNode valid() { return valid("p-model", "s-model"); }
    private Path write(ObjectNode node) throws Exception { return write(temp, node); }
    private ObjectNode packet(ObjectNode root, int n) {
        return (ObjectNode) root.path(PACKETS).path("pilot-%02d".formatted(n));
    }
    private ObjectNode scorer(ObjectNode root, String role) { return (ObjectNode) root.path(SCORERS).path(role); }
    private void rejected(ObjectNode root, String message) throws Exception {
        Path file = write(root);
        assertThatThrownBy(() -> PilotFamilies.load(file))
                .isInstanceOf(PilotFamilies.InvalidPilotFamilies.class).hasMessageContaining(message);
    }
    @Test void validRecordExposesEveryFieldAndStableRawDigest() throws Exception {
        Path file = write(valid());
        PilotFamilies one = PilotFamilies.load(file), two = PilotFamilies.load(file);
        assertThat(one.sha256OfBytes()).isEqualTo(two.sha256OfBytes()).isEqualTo(FreezeRecord.sha256(Files.readAllBytes(file)));
        assertThat(one.packets()).hasSize(6);
        assertThat(one.scorers()).hasSize(2);
        var first = one.packets().get("pilot-01");
        assertThat(first.authorFamily()).isEqualTo(ModelFamily.OPENAI);
        assertThat(first.authorTool()).isEqualTo("runner-a");
        assertThat(first.authorModelId()).isEqualTo("author-model");
        assertThat(first.authorSessionId()).isEqualTo("author-openai");
        assertThat(first.verifierFamily()).isEqualTo(ModelFamily.ANTHROPIC);
        assertThat(first.verifierTool()).isEqualTo("runner-b");
        assertThat(first.verifierModelId()).isEqualTo("verifier-model");
        assertThat(first.verifierSessionId()).isEqualTo("verifier-anthropic");
        assertThat(one.scorers().get(P)).isEqualTo(new PilotFamilies.Scorer(ModelFamily.OPENAI, "scorer-p", "p-model"));
    }
    @Test void detachedDigestMissingAndTamperedAreRefused() throws Exception {
        Path file = write(valid());
        Files.delete(IntegritySupport.detached(file));
        assertThatThrownBy(() -> PilotFamilies.load(file)).isInstanceOf(PilotFamilies.InvalidPilotFamilies.class)
                .hasMessageContaining("detached SHA-256");
        write(valid());
        Files.writeString(file, Files.readString(file) + " ");
        assertThatThrownBy(() -> PilotFamilies.load(file)).isInstanceOf(PilotFamilies.InvalidPilotFamilies.class)
                .hasMessageContaining("detached SHA-256");
    }
    @Test void duplicateAndUnknownFieldsAtEveryLevelAreRefused() throws Exception {
        Path file = write(valid());
        Files.writeString(file, Files.readString(file).replaceFirst("\\\"schema\\\":1", "\\\"schema\\\":1,\\\"schema\\\":1"));
        Files.writeString(IntegritySupport.detached(file), FreezeRecord.sha256(Files.readAllBytes(file)));
        assertThatThrownBy(() -> PilotFamilies.load(file)).isInstanceOf(PilotFamilies.InvalidPilotFamilies.class)
                .hasMessageContaining("duplicate");
        ObjectNode root = valid(); root.put("extra", true); rejected(root, "root");
        root = valid(); ((ObjectNode) root.path(PACKETS)).put("extra", true); rejected(root, PACKETS);
        root = valid(); packet(root, 1).put("extra", true); rejected(root, "pilot-01");
        root = valid(); ((ObjectNode) root.path(SCORERS)).put("extra", true); rejected(root, SCORERS);
        root = valid(); scorer(root, P).put("extra", true); rejected(root, P);
    }
    @Test void exactSixPacketsAndClosedFamilies() throws Exception {
        ObjectNode root = valid(); ((ObjectNode) root.path(PACKETS)).remove("pilot-06"); rejected(root, PACKETS);
        root = valid(); ((ObjectNode) root.path(PACKETS)).set("pilot-07", packet(root, 1).deepCopy()); rejected(root, PACKETS);
        root = valid(); packet(root, 1).put("authorFamily", "GOOGLE"); rejected(root, "Unknown family");
    }
    @Test void textBoundsAndControlCharacters() throws Exception {
        ObjectNode root = valid(); packet(root, 1).put("authorTool", " "); rejected(root, "authorTool");
        root = valid(); packet(root, 1).put("authorTool", "x".repeat(201)); rejected(root, "authorTool");
        root = valid(); packet(root, 1).put("authorTool", "x".repeat(200));
        assertThat(PilotFamilies.load(write(root)).packets().get("pilot-01").authorTool()).hasSize(200);
        root = valid(); packet(root, 1).put("authorTool", "bad\ncontrol"); rejected(root, "authorTool");
    }
    @Test void allocationVerificationAndScorerRolesFailInOrder() throws Exception {
        ObjectNode root = valid(); packet(root, 1).put("authorFamily", ANTHROPIC); rejected(root, "Author family allocation");
        root = valid(); packet(root, 1).put("verifierFamily", OPENAI); rejected(root, "Verifier family");
        root = valid(); scorer(root, P).put("family", ANTHROPIC); rejected(root, "Scorer family");
        root = valid(); scorer(root, S).put("family", OPENAI); rejected(root, "Scorer family");
    }
    @Test void sessionMayRepeatWithinRoleButNotAcrossRoles() throws Exception {
        ObjectNode root = valid();
        PilotFamilies valid = PilotFamilies.load(write(root));
        assertThat(valid.packets().get("pilot-02").authorSessionId()).isEqualTo("author-openai");
        assertThat(valid.packets().get("pilot-06").authorSessionId()).isEqualTo("author-openai");
        packet(root, 3).put("verifierSessionId", "author-openai");
        rejected(root, "Session shared");
        root = valid(); packet(root, 3).put("verifierSessionId", "separate-verification-invocation");
        assertThat(PilotFamilies.load(write(root)).packets().get("pilot-03").verifierSessionId())
                .isEqualTo("separate-verification-invocation");
    }
    @Test void toolDeclarationChecksAreCaseInsensitiveAndApplyToSlots() throws Exception {
        for (String tool : List.of("Codex", "cOdEx", "a codex helper")) {
            ObjectNode root = valid(); packet(root, 1).put("authorTool", tool); rejected(root, "Codex");
            root = valid(); packet(root, 1).put("verifierTool", tool); rejected(root, "Codex");
        }
        for (String tool : List.of("owner", "OWNER", "Human", "human")) {
            ObjectNode root = valid(); packet(root, 1).put("authorTool", tool); rejected(root, "Human tool");
            root = valid(); packet(root, 1).put("verifierTool", tool); rejected(root, "Human tool");
            root = valid(); scorer(root, P).put("tool", tool); rejected(root, "Human tool");
        }
    }
}
