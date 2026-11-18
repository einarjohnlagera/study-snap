package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pins two invariants no other test reaches: secret-to-commitment binding and all-or-none seal fields. */
class PlacementBindingInvariantTest {
    private static final String HEX_A = "a".repeat(64);
    private static final String UTC = "2026-11-18T00:00:00Z";
    private static final String KEY_FILE = "arm-key.jsonl";
    private static final String EXPORT_ID = "CLASS_B_PRIMARY";
    private static final List<ArmKeySeal.PacketFile> NO_PACKETS = List.of();
    @TempDir Path temp;

    private static PlacementSecret other() {
        byte[] bytes = PlacementSecretTest.fixed().raw();
        bytes[0] ^= 1;
        return new PlacementSecret(bytes);
    }

    private Path sidecarJson(String name, PlacementSecret secret, String commitment) throws Exception {
        ObjectNode root = IntegritySupport.JSON.createObjectNode();
        root.put("schemaVersion", 1).put("kind", "CLASS_B").put("exportId", EXPORT_ID)
                .put("commitment", commitment).put("secret", secret.hex()).put("javaVersion", "test")
                .put("firstArm", "BASELINE").put("secondArm", "CANDIDATE");
        root.putArray("groups").addObject().put("groupKey", "STUDY_TIP").putArray("caseIds").add("case-1");
        root.putArray("excluded");
        Path file = temp.resolve(name);
        Files.writeString(file, root.toString());
        return file;
    }

    @Test void sidecarWithConsistentSecretAndCommitmentLoads() throws Exception {
        PlacementSecret secret = PlacementSecretTest.fixed();
        Path file = sidecarJson("consistent.json", secret, secret.commitment());
        assertThat(PlacementSidecar.load(file).commitment()).isEqualTo(secret.commitment());
    }

    @Test void sidecarWhoseSecretDoesNotMatchItsCommitmentIsRefusedOnLoad() throws Exception {
        PlacementSecret committed = PlacementSecretTest.fixed();
        Path file = sidecarJson("swapped-secret.json", other(), committed.commitment());
        assertThatThrownBy(() -> PlacementSidecar.load(file)).isInstanceOf(InvalidPlacementSeal.class);
    }

    @Test void sidecarWhoseSecretDoesNotMatchItsCommitmentIsRefusedOnWrite() {
        PlacementSecret committed = PlacementSecretTest.fixed();
        PlacementSidecar.Reveal forged = new PlacementSidecar.Reveal(PlacementSidecar.SCHEMA_VERSION,
                PlacementSidecar.CLASS_B, EXPORT_ID, committed.commitment(), other(), "test",
                "BASELINE", "CANDIDATE", List.of(new PlacementSidecar.Group("STUDY_TIP", List.of("case-1"))),
                List.of(), null);
        Path file = temp.resolve("forged-write.json");
        assertThatThrownBy(() -> PlacementSidecar.write(file, forged, PlacementSidecar.FILE_WRITER))
                .isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(file)).isFalse();
    }

    @Test void sealWithOnlySomePlacementComponentsIsRefused() {
        assertThatThrownBy(() -> new ArmKeySeal.Seal(HEX_A, KEY_FILE, 1, NO_PACKETS, UTC, HEX_A, null, null))
                .isInstanceOf(ArmKeySeal.SealBroken.class);
        assertThatThrownBy(() -> new ArmKeySeal.Seal(HEX_A, KEY_FILE, 1, NO_PACKETS, UTC, null, HEX_A, null))
                .isInstanceOf(ArmKeySeal.SealBroken.class);
        assertThatThrownBy(() -> new ArmKeySeal.Seal(HEX_A, KEY_FILE, 1, NO_PACKETS, UTC, null, null, EXPORT_ID))
                .isInstanceOf(ArmKeySeal.SealBroken.class);
        assertThatThrownBy(() -> new ArmKeySeal.Seal(HEX_A, KEY_FILE, 1, NO_PACKETS, UTC, HEX_A, HEX_A, null))
                .isInstanceOf(ArmKeySeal.SealBroken.class);
        assertThatThrownBy(() -> new ArmKeySeal.Seal(HEX_A, KEY_FILE, 1, NO_PACKETS, UTC, HEX_A, null, EXPORT_ID))
                .isInstanceOf(ArmKeySeal.SealBroken.class);
        assertThatThrownBy(() -> new ArmKeySeal.Seal(HEX_A, KEY_FILE, 1, NO_PACKETS, UTC, null, HEX_A, EXPORT_ID))
                .isInstanceOf(ArmKeySeal.SealBroken.class);
    }

    @Test void sealWithNoOrAllPlacementComponentsIsAccepted() {
        ArmKeySeal.Seal none = new ArmKeySeal.Seal(HEX_A, KEY_FILE, 1, NO_PACKETS, UTC);
        ArmKeySeal.Seal all = new ArmKeySeal.Seal(HEX_A, KEY_FILE, 1, NO_PACKETS, UTC, HEX_A, HEX_A, EXPORT_ID);
        assertThat(none.placementExportId()).isNull();
        assertThat(all.placementExportId()).isEqualTo(EXPORT_ID);
    }
}
