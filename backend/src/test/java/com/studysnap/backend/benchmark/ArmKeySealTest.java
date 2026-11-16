package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArmKeySealTest {
    @TempDir Path temp;

    @Test void realClassASealReloadsWithoutKeyRows() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        Path sealFile = ArmKeyGateFixtures.seal(temp, export.armKey());
        var seal = ArmKeySeal.load(sealFile);
        assertThat(seal.workflowStems()).containsExactly("NOTE_ADAPTIVE");
        assertThat(seal.lineCount()).isEqualTo(2);
        assertThat(Files.readString(sealFile)).doesNotContain("BASELINE", "CANDIDATE", "blind-00001");
        ArmKeySeal.verifyKeyBytes(export.armKey(), seal);
        ArmKeySeal.verifyPackets(temp.resolve("packets"), seal);
    }
    @Test void emptyClassBPacketFilesDoNotBecomeWorkflows() {
        var export = BlindReviewFixtures.classB(temp, 1);
        Path sealFile = ArmKeyGateFixtures.seal(temp, temp.resolve("arm-key.jsonl"));
        assertThat(ArmKeySeal.load(sealFile).workflowStems()).containsExactly("STUDY_TIP");
        assertThat(ArmKeySeal.load(sealFile).packetFiles()).hasSizeGreaterThan(1);
        assertThat(export.armKey()).hasSize(2);
    }
    @Test void detachedTamperRefused() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        Path file = ArmKeyGateFixtures.seal(temp, export.armKey());
        Files.writeString(IntegritySupport.detached(file), "0".repeat(64));
        assertThatThrownBy(() -> ArmKeySeal.load(file)).isInstanceOf(ArmKeySeal.SealBroken.class);
    }
    @Test void keyInsidePacketDirectoryRefused() throws Exception {
        Path packets = Files.createDirectory(temp.resolve("packets"));
        Path key = packets.resolve("key.jsonl");
        Files.writeString(key, "invalid-json\n");
        Path sealFile = temp.resolve("seal.json");
        assertThatThrownBy(() -> ArmKeySeal.seal(key, packets, sealFile, ArmKeyGateFixtures.SEALED))
                .isInstanceOf(ArmKeySeal.InvalidSeal.class);
    }
    @Test void emptyPacketDirectoryAndOnlyEmptyPacketsRefused() throws Exception {
        Path key = temp.resolve("key.jsonl");
        Files.writeString(key, "invalid-json\n");
        Path packets = Files.createDirectory(temp.resolve("packets"));
        Path seal = temp.resolve("seal.json");
        assertThatThrownBy(() -> ArmKeySeal.seal(key, packets, seal, ArmKeyGateFixtures.SEALED))
                .isInstanceOf(ArmKeySeal.InvalidSeal.class);
        Files.writeString(packets.resolve("EMPTY.jsonl"), "");
        assertThatThrownBy(() -> ArmKeySeal.seal(key, packets, seal, ArmKeyGateFixtures.SEALED))
                .isInstanceOf(ArmKeySeal.InvalidSeal.class);
    }
    @Test void overwriteAndBadTimeRefused() {
        var export = ArmKeyGateFixtures.classA(temp);
        Path file = ArmKeyGateFixtures.seal(temp, export.armKey());
        Path key = export.armKey(), packets = temp.resolve("packets");
        assertThatThrownBy(() -> ArmKeySeal.seal(key, packets, file, ArmKeyGateFixtures.SEALED))
                .isInstanceOf(ArmKeySeal.InvalidSeal.class);
        Path another = temp.resolve("bad-time.json");
        assertThatThrownBy(() -> ArmKeySeal.seal(key, packets, another, "2026-11-16T08:00:00+08:00"))
                .isInstanceOf(ArmKeySeal.InvalidSeal.class);
    }
    @Test void changedKeyAndPacketBytesRefused() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        Path sealFile = ArmKeyGateFixtures.seal(temp, export.armKey());
        var seal = ArmKeySeal.load(sealFile);
        Files.writeString(export.armKey(), Files.readString(export.armKey()) + "x");
        Path key = export.armKey();
        assertThatThrownBy(() -> ArmKeySeal.verifyKeyBytes(key, seal))
                .isInstanceOf(ArmKeySeal.SealBroken.class);
        Path packet = temp.resolve("packets/NOTE_ADAPTIVE.jsonl");
        Files.writeString(packet, Files.readString(packet) + "x");
        Path packetDirectory = temp.resolve("packets");
        assertThatThrownBy(() -> ArmKeySeal.verifyPackets(packetDirectory, seal))
                .isInstanceOf(ArmKeySeal.SealBroken.class);
    }
    @Test void addedAndRemovedPacketFilesRefused() throws Exception {
        var export = ArmKeyGateFixtures.classA(temp);
        var seal = ArmKeySeal.load(ArmKeyGateFixtures.seal(temp, export.armKey()));
        Path directory = temp.resolve("packets");
        Path extra = directory.resolve("EXTRA.jsonl");
        Files.writeString(extra, "{}\n");
        assertThatThrownBy(() -> ArmKeySeal.verifyPackets(directory, seal)).isInstanceOf(ArmKeySeal.SealBroken.class);
        Files.delete(extra);
        Files.delete(directory.resolve("NOTE_ADAPTIVE.jsonl"));
        assertThatThrownBy(() -> ArmKeySeal.verifyPackets(directory, seal)).isInstanceOf(ArmKeySeal.SealBroken.class);
    }
}
