package com.studysnap.backend.benchmark;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.List;

/** Exact one-sided paired sign-flip test, with integer-only rejection decisions. */
final class ExactPairedTest {
    private ExactPairedTest() {}

    record Result(BigInteger tailCount, int nonzeroCount) {
        Result {
            if (tailCount == null || nonzeroCount < 0 || tailCount.signum() < 0
                    || tailCount.compareTo(BigInteger.ONE.shiftLeft(nonzeroCount)) > 0)
                throw new InvalidInput("Invalid exact-test result");
        }

        double p() {
            if (nonzeroCount <= 1022) return Math.scalb(tailCount.doubleValue(), -nonzeroCount);
            return new BigDecimal(tailCount).divide(new BigDecimal(BigInteger.ONE.shiftLeft(nonzeroCount)))
                    .doubleValue();
        }

        boolean atMost(BigInteger alphaNumerator, BigInteger alphaDenominator) {
            if (alphaNumerator == null || alphaDenominator == null || alphaNumerator.signum() < 0
                    || alphaDenominator.signum() <= 0 || alphaNumerator.compareTo(alphaDenominator) > 0)
                throw new InvalidInput("Invalid alpha");
            return tailCount.multiply(alphaDenominator)
                    .compareTo(alphaNumerator.shiftLeft(nonzeroCount)) <= 0;
        }

        boolean atMost(Rational alpha) {
            if (alpha == null) throw new InvalidInput("Missing alpha");
            return atMost(alpha.num(), alpha.den());
        }
    }

    static Result test(List<Integer> differences) {
        if (differences == null) throw new InvalidInput("Missing differences");
        int m = 0;
        long sum = 0;
        long absoluteSum = 0;
        for (Integer value : differences) {
            if (value == null || value == Integer.MIN_VALUE) throw new InvalidInput("Invalid difference");
            sum += value;
            if (value != 0) {
                m++;
                absoluteSum += Math.abs((long) value);
            }
        }
        BigInteger total = BigInteger.ONE.shiftLeft(m);
        long k = (sum + absoluteSum) / 2;
        if (m == 0 || k <= 0) return new Result(total, m);
        if (k > absoluteSum) return new Result(BigInteger.ZERO, m);
        int baseBits = m + 2;
        if (absoluteSum > (Integer.MAX_VALUE - 1L) / baseBits)
            throw new InvalidInput("Exact-test polynomial exceeds supported bit index");
        BigInteger polynomial = BigInteger.ONE;
        for (Integer value : differences) {
            if (value != 0) polynomial = polynomial.add(polynomial.shiftLeft(baseBits * Math.abs(value)));
        }
        BigInteger modulus = BigInteger.ONE.shiftLeft(baseBits).subtract(BigInteger.ONE);
        return new Result(polynomial.shiftRight((int) (baseBits * k)).mod(modulus), m);
    }
}
