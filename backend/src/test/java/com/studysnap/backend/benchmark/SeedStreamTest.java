package com.studysnap.backend.benchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedStreamTest {
    @TempDir Path temp;
    private SeedStream stream() throws Exception {
        return SeedStream.of(SeedStreamRegistry.load(BlindReviewFixtures.registry(temp)),
                BlindReviewFixtures.WORKFLOW_A, SeedStreamRegistry.SPOT_CHECK, 1);
    }
    @Test void pinnedCounterModeVectors() throws Exception {
        assertThat(stream().firstBlockSha256())
                .isEqualTo("46bfc388c227c0a886cda621fb00516b3460c23101213c5e982469f191cf5aab");
        SeedStream sequence = stream();
        List<Integer> actual = new ArrayList<>();
        for (int i = 0; i < 10; i++) actual.add(sequence.nextInt(1000));
        assertThat(actual).containsExactly(552, 128, 353, 59, 401, 358, 249, 483, 415, 798);
        List<Integer> shuffled = new ArrayList<>();
        for (int i = 0; i < 20; i++) shuffled.add(i);
        stream().shuffle(shuffled);
        assertThat(shuffled).containsExactly(16, 18, 19, 9, 4, 17, 0, 13, 15, 2, 6, 11, 5, 3, 8, 1, 14, 7, 10, 12);
    }
    @Test void streamIdentityIncludesWorkflowPurposeAndRound() throws Exception {
        SeedStreamRegistry registry = SeedStreamRegistry.load(BlindReviewFixtures.registry(temp));
        String first = stream().firstBlockSha256();
        assertThat(SeedStream.of(registry, BlindReviewFixtures.WORKFLOW_B, SeedStreamRegistry.SPOT_CHECK, 1).firstBlockSha256())
                .isNotEqualTo(first);
        assertThat(SeedStream.of(registry, BlindReviewFixtures.WORKFLOW_A, SeedStreamRegistry.EXPANSION, 1).firstBlockSha256())
                .isNotEqualTo(first);
        assertThat(SeedStream.of(registry, BlindReviewFixtures.WORKFLOW_A, SeedStreamRegistry.SPOT_CHECK, 2).firstBlockSha256())
                .isNotEqualTo(first);
    }
    @Test void undeclaredAndOutOfRangeRoundsFail() throws Exception {
        SeedStreamRegistry registry = SeedStreamRegistry.load(BlindReviewFixtures.registry(temp));
        assertThatThrownBy(() -> SeedStream.of(registry, "UNKNOWN", SeedStreamRegistry.SPOT_CHECK, 1))
                .isInstanceOf(SeedStream.UndeclaredStream.class);
        assertThatThrownBy(() -> SeedStream.of(registry, BlindReviewFixtures.WORKFLOW_A, SeedStreamRegistry.SPOT_CHECK, 3))
                .isInstanceOf(SeedStream.UndeclaredStream.class);
    }
    @Test void boundedDrawsAreInRangeAndBalanced() throws Exception {
        SeedStream source = stream();
        int[] counts = new int[10];
        for (int i = 0; i < 100_000; i++) {
            int value = source.nextInt(10);
            assertThat(value).isBetween(0, 9);
            counts[value]++;
        }
        for (int count : counts) assertThat(count).isBetween(9_500, 10_500);
    }
    @Test void largeBoundPinsRejectionInsteadOfModulo() throws Exception {
        SeedStream source = stream();
        List<Integer> draws = new ArrayList<>();
        for (int i = 0; i < 8; i++) draws.add(source.nextInt(1_500_000_000));
        assertThat(draws).containsExactly(1_186_972_552, 761_624_353, 878_756_401, 18_955_358,
                1_052_523_249, 946_285_483, 520_971_798, 725_635_462);
    }
    @Test void shuffleIsAPermutation() throws Exception {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < 20; i++) values.add(i);
        stream().shuffle(values);
        assertThat(values).containsExactlyInAnyOrderElementsOf(java.util.stream.IntStream.range(0, 20).boxed().toList());
    }
}
