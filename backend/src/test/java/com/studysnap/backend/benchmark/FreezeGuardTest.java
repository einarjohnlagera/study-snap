package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Section 10 exact-byte and pre-ledger freeze refusal mutants. */
class FreezeGuardTest {
    private static final String HEAD = "a".repeat(40);
    private static final String MODEL = "gpt-4.1-mini";
    private static final String ARM = "BASELINE_MINI";
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String PREREG = "docs/claude-plans/2026-11-12-v0182-llm-migration-preregistration.md";
    private static final String PARAMETERS = "backend/src/test/java/com/studysnap/backend/benchmark/frozen-route-gates.json";
    private static final String PROMPT = "backend/src/main/resources/prompts/study-pack-v1/system.txt";
    private static final String CHANGED = "changed";
    private static final String SHA = "SHA-256";
    private static final String ROW = "synthetic-row";
    private static final String HASH_FILE = "freeze.json.sha256";
    private static final String MANIFEST_KEY = "classA.primary";
    @TempDir Path temp;

    private record Fixture(Path root, Path prereg, Path parameters, Path prompt, Path manifest,
                           Path record, Path ledger) {}

    private Fixture fixture() throws Exception {
        Path root = temp.resolve("repo");
        Path prereg = write(root.resolve(PREREG), "draft");
        Path parameters = write(root.resolve(PARAMETERS), "gates");
        Path prompt = write(root.resolve(PROMPT), "system");
        Path manifest = write(temp.resolve("manifest.jsonl"), ROW);
        var freeze = new FreezeRecord(HEAD, FreezeRecord.sha256(prereg), FreezeRecord.sha256(parameters),
                Map.of(manifest.toString(), FreezeRecord.sha256(manifest)),
                Map.of(PROMPT, FreezeRecord.sha256(prompt)),
                Map.of(ARM, new FreezeRecord.Arm(MODEL, List.of(WORKFLOW))),
                Map.of("INTERACTIVE_QUIZ", MODEL), LocalDate.of(2026, 11, 14));
        Path file = temp.resolve("freeze.json");
        byte[] bytes = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(freeze);
        Files.write(file, bytes);
        Files.writeString(temp.resolve(HASH_FILE), FreezeRecord.sha256(bytes));
        return new Fixture(root, prereg, parameters, prompt, manifest, file, temp.resolve("ledger.jsonl"));
    }

    private static Path write(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value);
        return path;
    }

    private FreezeGuard.Verified verify(Fixture f) {
        return verify(f, f.manifest(), List.of(WORKFLOW), MODEL, HEAD);
    }

    private FreezeGuard.Verified verify(Fixture f, Path manifest, List<String> workflows,
                                        String model, String head) {
        return FreezeGuard.verify(f.record(), f.root(), ARM, model,
                Map.of(MANIFEST_KEY, manifest), workflows, head);
    }

    @Test void verifiedBytesPermitTheFrozenArm() throws Exception {
        Fixture f = fixture();
        assertThat(verify(f).head()).isEqualTo(HEAD);
        assertThat(verify(f).real()).isFalse();
    }

    @Test void missingOrDetachedHashMismatchRefusesBeforeLedger() throws Exception {
        Fixture f = fixture();
        Files.writeString(temp.resolve(HASH_FILE), "0".repeat(64));
        assertThatThrownBy(() -> verify(f)).isInstanceOf(FreezeRecord.InvalidFreeze.class);
        assertThat(Files.exists(f.ledger())).isFalse();
        Files.delete(f.record());
        assertThatThrownBy(() -> verify(f)).isInstanceOf(FreezeRecord.InvalidFreeze.class);
    }

    @Test void changedPreregistrationRefusesBeforeLedger() throws Exception {
        Fixture f = fixture();
        Files.writeString(f.prereg(), CHANGED);
        assertThatThrownBy(() -> verify(f)).isInstanceOf(FreezeRecord.InvalidFreeze.class)
                .hasMessageContaining(PREREG).hasMessageContaining(SHA);
        assertThat(Files.exists(f.ledger())).isFalse();
    }

    @Test void changedParametersRefusesBeforeLedger() throws Exception {
        Fixture f = fixture();
        Files.writeString(f.parameters(), CHANGED);
        assertThatThrownBy(() -> verify(f)).isInstanceOf(FreezeRecord.InvalidFreeze.class)
                .hasMessageContaining(PARAMETERS).hasMessageContaining(SHA);
        assertThat(Files.exists(f.ledger())).isFalse();
    }

    @Test void changedManifestRefusesBeforeLedger() throws Exception {
        Fixture f = fixture();
        Files.writeString(f.manifest(), CHANGED);
        assertThatThrownBy(() -> verify(f)).isInstanceOf(FreezeRecord.InvalidFreeze.class)
                .hasMessageContaining(f.manifest().toString()).hasMessageContaining(SHA);
        assertThat(Files.exists(f.ledger())).isFalse();
    }

    @Test void changedPromptRefusesBeforeLedger() throws Exception {
        Fixture f = fixture();
        Files.writeString(f.prompt(), CHANGED);
        assertThatThrownBy(() -> verify(f)).isInstanceOf(FreezeRecord.InvalidFreeze.class)
                .hasMessageContaining(PROMPT).hasMessageContaining(SHA);
        assertThat(Files.exists(f.ledger())).isFalse();
    }

    @Test void unknownFreezeFieldIsMalformedEvenWithValidDetachedHash() throws Exception {
        Fixture f = fixture();
        String content = Files.readString(f.record());
        byte[] mutated = (content.substring(0, content.length() - 1) + ",\"unknown\":true}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(f.record(), mutated);
        Files.writeString(temp.resolve(HASH_FILE), FreezeRecord.sha256(mutated));
        assertThatThrownBy(() -> verify(f)).isInstanceOf(FreezeRecord.InvalidFreeze.class);
        assertThat(Files.exists(f.ledger())).isFalse();
    }

    @Test void unpinnedPromptFileIsRefused() throws Exception {
        Fixture f = fixture();
        write(f.root().resolve("backend/src/main/resources/prompts/study-pack-v1/new.txt"), "new");
        assertThatThrownBy(() -> verify(f)).isInstanceOf(FreezeRecord.InvalidFreeze.class)
                .hasMessageContaining("Prompt absent");
    }

    @Test void headMismatchRefusesBeforeLedger() throws Exception {
        Fixture f = fixture();
        Path manifest = f.manifest();
        List<String> workflows = List.of(WORKFLOW);
        String wrongHead = "b".repeat(40);
        assertThatThrownBy(() -> verify(f, manifest, workflows, MODEL, wrongHead))
                .isInstanceOf(FreezeRecord.InvalidFreeze.class).hasMessageContaining("HEAD mismatch");
        assertThat(Files.exists(f.ledger())).isFalse();
    }

    @Test void unlistedWorkflowOrManifestRefuses() throws Exception {
        Fixture f = fixture();
        Path absent = write(temp.resolve("other.jsonl"), ROW);
        List<String> workflow = List.of(WORKFLOW);
        List<String> unlisted = List.of("TEACHER_QUIZ");
        Path manifest = f.manifest();
        assertThatThrownBy(() -> verify(f, absent, workflow, MODEL, HEAD))
                .isInstanceOf(FreezeRecord.InvalidFreeze.class).hasMessageContaining("Manifest absent");
        assertThatThrownBy(() -> verify(f, manifest, unlisted, MODEL, HEAD))
                .isInstanceOf(FreezeRecord.InvalidFreeze.class).hasMessageContaining("Workflow absent");
    }

    @Test void armModelMismatchRefusesBeforeLedger() throws Exception {
        Fixture f = fixture();
        Path manifest = f.manifest();
        List<String> workflows = List.of(WORKFLOW);
        assertThatThrownBy(() -> verify(f, manifest, workflows, "wrong-model", HEAD))
                .isInstanceOf(FreezeRecord.InvalidFreeze.class).hasMessageContaining("Arm/model");
        assertThat(Files.exists(f.ledger())).isFalse();
    }
}
