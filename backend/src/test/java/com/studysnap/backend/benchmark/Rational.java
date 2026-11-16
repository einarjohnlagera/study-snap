package com.studysnap.backend.benchmark;

import java.math.BigInteger;

record Rational(BigInteger num, BigInteger den) {
    Rational {
        if (num == null || den == null || num.signum() < 0 || den.signum() <= 0 || num.compareTo(den) > 0)
            throw new InvalidInput("Invalid probability");
    }

    Rational dividedBy(int divisor) {
        if (divisor <= 0) throw new InvalidInput("Nonpositive divisor");
        return new Rational(num, den.multiply(BigInteger.valueOf(divisor)));
    }

    static Rational of(long numerator, long denominator) {
        return new Rational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }
}
