package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/** Frozen provider limits. Every input token occupies at least one request byte. */
record ProviderBoundReservation(Map<String, ModelPrice> models, long longContextThreshold,
                                long framingMargin, LocalDate documentationReadDate) {
    static final String TABLE_REQUIRED = "PROVIDER_BOUND_TABLE_REQUIRED";
    record ModelPrice(long maximumOutputTokens, BigDecimal inputPerMillion,
                      BigDecimal outputPerMillion) {
        ModelPrice {
            if (maximumOutputTokens <= 0 || inputPerMillion == null || outputPerMillion == null
                    || inputPerMillion.signum() <= 0 || outputPerMillion.signum() <= 0)
                throw new StageCapLedger.SpendStop("INVALID_PROVIDER_BOUND_PRICE");
        }
    }

    ProviderBoundReservation {
        if (models == null || models.isEmpty() || models.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null)
                || longContextThreshold <= 0
                || framingMargin < 0 || framingMargin >= longContextThreshold
                || documentationReadDate == null)
            throw new StageCapLedger.SpendStop("INVALID_PROVIDER_BOUND_TABLE");
        models = Map.copyOf(models);
    }

    ModelPrice requireModel(String modelId) {
        ModelPrice row = models.get(modelId);
        if (row == null) throw new StageCapLedger.SpendStop("UNKNOWN_PROVIDER_BOUND_MODEL");
        return row;
    }

    BigDecimal usd(String modelId, byte[] requestBodyBytes) {
        ModelPrice row = requireModel(modelId);
        if (requestBodyBytes == null || requestBodyBytes.length > longContextThreshold)
            throw new StageCapLedger.SpendStop("PROVIDER_BOUND_INPUT_TOO_LARGE");
        return BigDecimal.valueOf(requestBodyBytes.length).multiply(row.inputPerMillion())
                .add(BigDecimal.valueOf(row.maximumOutputTokens()).multiply(row.outputPerMillion()))
                .divide(BigDecimal.valueOf(1_000_000), 9, RoundingMode.UP);
    }

    static boolean stageFeasible(BigDecimal cap, BigDecimal scheduledWorstHigh,
                                 BigDecimal worstCall, int concurrency) {
        if (cap == null || scheduledWorstHigh == null || worstCall == null || concurrency < 0)
            throw new StageCapLedger.SpendStop("INVALID_FEASIBILITY_INPUT");
        return cap.compareTo(scheduledWorstHigh.add(worstCall.multiply(BigDecimal.valueOf(concurrency)))) >= 0;
    }
}
