package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Section 5 forecast, not a provider-backed upper bound. Production omits max_output_tokens,
 * so one response can exceed this figure; the ledger can only stop before the next call. */
record ForecastReservation(long inputTokens, long outputTokens, BigDecimal priceIn,
                           BigDecimal priceOut, BigDecimal outputMultiplier) {
    ForecastReservation {
        if (inputTokens < 0 || outputTokens < 0 || Objects.requireNonNull(priceIn).signum() < 0
                || Objects.requireNonNull(priceOut).signum() < 0
                || Objects.requireNonNull(outputMultiplier).signum() <= 0)
            throw new IllegalArgumentException("Invalid forecast parameters");
    }
    BigDecimal usd() {
        return BigDecimal.valueOf(inputTokens).multiply(priceIn)
                .add(BigDecimal.valueOf(outputTokens).multiply(outputMultiplier).multiply(priceOut))
                .divide(BigDecimal.valueOf(1_000_000), 9, RoundingMode.HALF_UP);
    }
}
