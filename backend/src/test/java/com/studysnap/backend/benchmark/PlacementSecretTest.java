package com.studysnap.backend.benchmark;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlacementSecretTest {
    private static final String COMMITMENT = "96338e3030a7a3c8f41ff582863afe9256a88c89821cc0669203dddd91a083e7";
    private static final long SEED = 6936037401490943588L;
    private static final String EXPORT_ID = "CLASS_B_PRIMARY";
    @TempDir Path temp;

    static PlacementSecret fixed() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        return new PlacementSecret(bytes);
    }

    @Test void knownAnswerSeparatesDomainsAndHidesRawBytes() {
        PlacementSecret secret = fixed();
        assertThat(secret.commitment()).isEqualTo(COMMITMENT);
        assertThat(secret.seedLong()).isEqualTo(SEED);
        assertThat(secret.seedLong()).isNotEqualTo(Long.parseUnsignedLong(COMMITMENT.substring(0, 16), 16));
        assertThat(secret.toString()).doesNotContain(secret.hex(), Long.toString(SEED), Long.toHexString(SEED));
    }

    @Test void operatorCreatesOnlyOutsideRepositoryAndCannotOverwrite() throws Exception {
        Path file = temp.resolve("placement-secret.bin");
        String commitment = PlacementSecret.create(file);
        assertThat(commitment).matches("[0-9a-f]{64}");
        assertThat(PlacementSecret.load(file).commitment()).isEqualTo(commitment);
        assertThat(Files.readAllBytes(file)).hasSize(32);
        try {
            assertThat(Files.getPosixFilePermissions(file)).containsExactlyInAnyOrder(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
        } catch (UnsupportedOperationException ignored) { /* Non-POSIX platform. */ }
        assertThatThrownBy(() -> PlacementSecret.create(file)).isInstanceOf(InvalidPlacementSeal.class);
        Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize());
        Path inTarget = repo.resolve("backend/target/placement-secret-refused.bin");
        assertThatThrownBy(() -> PlacementSecret.create(inTarget)).isInstanceOf(InvalidPlacementSeal.class);
        assertThat(Files.exists(inTarget)).isFalse();
        assertThat(PlacementSecret.operatorLine(EXPORT_ID, commitment)).isEqualTo(EXPORT_ID + " " + commitment);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            stream.println(PlacementSecret.operatorLine(EXPORT_ID, commitment));
        }
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo(EXPORT_ID + " " + commitment + "\n");
        assertThatThrownBy(() -> PlacementSecret.operatorLine(EXPORT_ID, "bad"))
                .isInstanceOf(InvalidPlacementSeal.class);
    }

    @Test void literalIndependentGoldenVectorsUseNameHashAndLastMacByte() throws Exception {
        PlacementSecret secret = fixed();
        List<String> ids = java.util.stream.IntStream.range(0, 10).mapToObj(i -> "case-" + i).toList();
        assertThat("NOTE_ADAPTIVE".hashCode()).isNegative();
        List<String> classBOrder = List.of("case-1", "case-8", "case-6", "case-3", "case-5",
                "case-4", "case-9", "case-2", "case-7", "case-0");
        List<Boolean> classBBits = List.of(false, true, true, true, true, false, true, true, true, true);
        List<String> classAOrder = List.of("case-6", "case-2", "case-8", "case-7", "case-9",
                "case-5", "case-3", "case-4", "case-1", "case-0");
        List<Boolean> classABits = List.of(false, true, true, true, false, false, true, false, false, true);
        assertThat(PlacementPlan.layout(ids, secret, EXPORT_ID, "STUDY_TIP").stream()
                .map(PlacementPlan.Placed::caseId)).containsExactlyElementsOf(classBOrder);
        assertThat(PlacementPlan.layout(ids, secret, EXPORT_ID, "STUDY_TIP").stream()
                .map(PlacementPlan.Placed::firstArmInA)).containsExactlyElementsOf(classBBits);
        assertThat(PlacementPlan.layout(ids, secret, "CLASS_A:BASELINE_MINI-LUNA", "NOTE_ADAPTIVE")
                .stream().map(PlacementPlan.Placed::caseId)).containsExactlyElementsOf(classAOrder);
        assertThat(PlacementPlan.layout(ids, secret, "CLASS_A:BASELINE_MINI-LUNA", "NOTE_ADAPTIVE")
                .stream().map(PlacementPlan.Placed::firstArmInA)).containsExactlyElementsOf(classABits);
        assertThat(independentBits(secret.raw(), EXPORT_ID, "STUDY_TIP", classBOrder))
                .containsExactlyElementsOf(classBBits);
        assertThat(independentBits(secret.raw(), "CLASS_A:BASELINE_MINI-LUNA", "NOTE_ADAPTIVE", classAOrder))
                .containsExactlyElementsOf(classABits);
        assertThat(PlacementPlan.order(ids, SEED, "STUDY_TIP")).containsExactlyElementsOf(classBOrder);
    }

    private static List<Boolean> independentBits(byte[] raw, String exportId, String group, List<String> ids)
            throws Exception {
        List<Boolean> bits = new java.util.ArrayList<>();
        for (String id : ids) {
            javax.crypto.Mac hmac = javax.crypto.Mac.getInstance("HmacSHA256");
            hmac.init(new javax.crypto.spec.SecretKeySpec(raw, "HmacSHA256"));
            byte[] message = (exportId + "\0" + group + "\0" + id).getBytes(StandardCharsets.UTF_8);
            bits.add((hmac.doFinal(message)[31] & 1) == 1);
        }
        return bits;
    }
}
