package com.studysnap.backend.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ExactPairedTestTest {
    private static final Rational ALPHA_FOUR_PERCENT = Rational.of(4, 100);
    private static final Rational ALPHA_INTERACTIVE_WORKFLOW = Rational.of(4, 600);

    @Test void pinnedVectorsAndOneSidedDirection() {
        vector(List.of(), 1, 0);
        vector(List.of(0, 0, 0), 1, 0);
        vector(List.of(1), 1, 1);
        vector(List.of(-1), 2, 1);
        vector(List.of(-Integer.MAX_VALUE), 2, 1);
        vector(List.of(1, 1, 1, 1, 1), 1, 5);
        vector(List.of(2, -1, 1, 0, 3), 3, 4);
        vector(repeated(1, 12), 1, 12);
        vector(List.of(3, 1, -2, 0, 4, 1, -1, 2), 17, 7);
        vector(List.of(5, 5, 5), 1, 3);
        vector(List.of(-4, -3, -2, -1, 1, 2, 3, 4), 141, 8);
        vector(repeated(2, 30), 1, 30);
        vector(List.of(7, -7, 1, 1, 1, 1, 1, 1, 1, 1), 257, 10);
    }

    @Test void dyadicEqualityAndDecimalBracketsUseExactIntegers() {
        Rational dyadic = Rational.of(1, 32);
        assertThat(ExactPairedTest.test(repeated(1, 5)).atMost(dyadic)).isTrue();
        assertThat(ExactPairedTest.test(repeated(1, 4)).atMost(dyadic)).isFalse();
        assertThat(ExactPairedTest.test(repeated(1, 5)).atMost(ALPHA_FOUR_PERCENT)).isTrue();
        assertThat(ExactPairedTest.test(repeated(1, 4)).atMost(ALPHA_FOUR_PERCENT)).isFalse();
        assertThat(ExactPairedTest.test(repeated(1, 8)).atMost(ALPHA_INTERACTIVE_WORKFLOW)).isTrue();
        assertThat(ExactPairedTest.test(repeated(1, 7)).atMost(ALPHA_INTERACTIVE_WORKFLOW)).isFalse();
        ExactPairedTest.Result underflowsAsDouble = new ExactPairedTest.Result(BigInteger.ONE, 1100);
        assertThat(underflowsAsDouble.p()).isZero();
        assertThat(underflowsAsDouble.atMost(BigInteger.ZERO, BigInteger.ONE)).isFalse();
        assertThat(new ExactPairedTest.Result(BigInteger.ONE.shiftLeft(1100), 1100).p()).isEqualTo(1);
    }

    @Test void integerIdentityMatchesThreeHundredBruteForceLists() {
        Random random = new Random(5182);
        for (int trial = 0; trial < 300; trial++) {
            int size = random.nextInt(15);
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i < size; i++) values.add(random.nextInt(11) - 5);
            ExactPairedTest.Result result = ExactPairedTest.test(values);
            List<Integer> nonzero = values.stream().filter(value -> value != 0).toList();
            long observed = values.stream().mapToLong(Integer::longValue).sum();
            int tail = 0;
            for (int mask = 0; mask < (1 << nonzero.size()); mask++) {
                long signed = 0;
                for (int i = 0; i < nonzero.size(); i++)
                    signed += ((mask & (1 << i)) == 0 ? -1 : 1) * Math.abs(nonzero.get(i));
                if (signed >= observed) tail++;
            }
            assertThat(result.tailCount()).isEqualTo(BigInteger.valueOf(tail));
            assertThat(result.nonzeroCount()).isEqualTo(nonzero.size());
        }
    }

    @Test void twoHundredCaseIdentityFinishesPromptly() {
        Random random = new Random(5200);
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < 200; i++) values.add(1 + random.nextInt(3));
        long start = System.nanoTime();
        ExactPairedTest.Result result = ExactPairedTest.test(values);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
        assertThat(result.tailCount()).isBetween(BigInteger.ZERO, BigInteger.ONE.shiftLeft(200));
    }

    @Test void increasingOneDifferenceNeverIncreasesPInTwentyThousandChecks() {
        Random random = new Random(5271);
        int violations = 0;
        for (int trial = 0; trial < 20_000; trial++) {
            List<Integer> values = new ArrayList<>();
            int size = 1 + random.nextInt(12);
            for (int i = 0; i < size; i++) values.add(random.nextInt(11) - 5);
            double original = ExactPairedTest.test(values).p();
            int position = random.nextInt(size);
            values.set(position, values.get(position) + 1);
            if (ExactPairedTest.test(values).p() > original) violations++;
        }
        assertThat(violations).as("20,000 executed checks; violations").isZero();
    }

    @Test void invalidDifferenceAndAlphaAreNamedErrors() {
        List<Integer> missing = null;
        assertThatThrownBy(() -> ExactPairedTest.test(missing)).isInstanceOf(InvalidInput.class);
        ExactPairedTest.Result result = ExactPairedTest.test(List.of(1));
        assertThatThrownBy(() -> result.atMost(BigInteger.ONE, BigInteger.ZERO))
                .isInstanceOf(InvalidInput.class);
    }

    private static void vector(List<Integer> values, long expectedTail, int expectedM) {
        ExactPairedTest.Result result = ExactPairedTest.test(values);
        assertThat(result.tailCount()).isEqualTo(BigInteger.valueOf(expectedTail));
        assertThat(result.nonzeroCount()).isEqualTo(expectedM);
        assertThat(result.p()).isEqualTo(Math.scalb((double) expectedTail, -expectedM));
    }

    private static List<Integer> repeated(int value, int count) {
        return java.util.Collections.nCopies(count, value);
    }
}
