package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedStreamRegistryTest {
    @TempDir Path temp;
    @Test void loadsOnlyDeclaredRoundsAndPinsDetachedHash() throws Exception {
        Path file = BlindReviewFixtures.registry(temp);
        SeedStreamRegistry registry = SeedStreamRegistry.load(file);
        assertThat(registry.sha256()).isEqualTo(FreezeRecord.sha256(file));
        assertThat(registry.isDeclared(BlindReviewFixtures.WORKFLOW_A, SeedStreamRegistry.SPOT_CHECK, 2)).isTrue();
        assertThat(registry.isDeclared(BlindReviewFixtures.WORKFLOW_A, SeedStreamRegistry.SPOT_CHECK, 3)).isFalse();
    }
    @Test void missingHashAndMismatchFail() throws Exception {
        Path file = BlindReviewFixtures.registry(temp);
        Files.delete(file.resolveSibling("seeds.json.sha256"));
        assertThatThrownBy(() -> SeedStreamRegistry.load(file))
                .isInstanceOf(SeedStreamRegistry.InvalidSeedRegistry.class);
        BlindReviewFixtures.seal(file);
        Files.writeString(file, Files.readString(file) + " ");
        assertThatThrownBy(() -> SeedStreamRegistry.load(file))
                .isInstanceOf(SeedStreamRegistry.InvalidSeedRegistry.class).hasMessageContaining("SHA-256");
    }
    @Test void unknownFieldAndShortSeedFail() throws Exception {
        Path file = BlindReviewFixtures.registry(temp);
        Files.writeString(file, Files.readString(file).replace("\"streams\"", "\"other\":1,\"streams\""));
        BlindReviewFixtures.seal(file);
        assertThatThrownBy(() -> SeedStreamRegistry.load(file))
                .isInstanceOf(SeedStreamRegistry.InvalidSeedRegistry.class);
        BlindReviewFixtures.registry(temp);
        Files.writeString(file, Files.readString(file).replace(BlindReviewFixtures.SEED, "short"));
        BlindReviewFixtures.seal(file);
        assertThatThrownBy(() -> SeedStreamRegistry.load(file))
                .isInstanceOf(SeedStreamRegistry.InvalidSeedRegistry.class);
    }
    @Test void duplicateDeclarationAndDuplicateJsonFieldFail() throws Exception {
        Path file = BlindReviewFixtures.registry(temp);
        Files.writeString(file, Files.readString(file).replace("\"streams\":[",
                "\"streams\":[{\"workflow\":\"NOTE_ADAPTIVE\",\"purpose\":\"SPOT_CHECK\",\"rounds\":1},"));
        BlindReviewFixtures.seal(file);
        assertThatThrownBy(() -> SeedStreamRegistry.load(file))
                .isInstanceOf(SeedStreamRegistry.InvalidSeedRegistry.class);
        BlindReviewFixtures.registry(temp);
        Files.writeString(file, Files.readString(file).replace("\"seed\":", "\"seed\":\"duplicate-seed-value\",\"seed\":"));
        BlindReviewFixtures.seal(file);
        assertThatThrownBy(() -> SeedStreamRegistry.load(file))
                .isInstanceOf(SeedStreamRegistry.InvalidSeedRegistry.class);
    }
}
