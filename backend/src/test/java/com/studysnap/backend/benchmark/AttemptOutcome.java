package com.studysnap.backend.benchmark;

import java.math.BigDecimal;

/** Section 7 per-HTTP-attempt result; parse/schema failure is supplied by the calling adapter. */
record AttemptOutcome(String generationId, int attempt, Kind kind, boolean parseSchemaFailure,
                      BigDecimal conservativeUsd, BigDecimal billedUsd) {
    enum Kind { OK, HTTP_ERROR, TIMEOUT, EMPTY, UNMETERED }
    static AttemptOutcome from(MeteringInterceptor.Call call, boolean parseSchemaFailure) {
        Kind kind;
        if (call.timeout()) kind = Kind.TIMEOUT;
        else if (call.status() == 0 || call.status() >= 400) kind = Kind.HTTP_ERROR;
        else if (call.usage() == null || !call.usage().path("input_tokens").isIntegralNumber()
                || !call.usage().path("output_tokens").isIntegralNumber()
                || call.usage().path("input_tokens").asLong() < 0
                || call.usage().path("output_tokens").asLong() < 0
                || (!call.usage().path("output_tokens_details").path("reasoning_tokens").isMissingNode()
                    && (!call.usage().path("output_tokens_details").path("reasoning_tokens").isIntegralNumber()
                        || call.usage().path("output_tokens_details").path("reasoning_tokens").asLong() < 0)))
            kind = Kind.UNMETERED;
        else if (call.outputItems() == 0 && !call.topLevelOutputText()) kind = Kind.EMPTY;
        else kind = Kind.OK;
        return new AttemptOutcome(call.generationId(), call.attempt(), kind, parseSchemaFailure,
                call.conservativeUsd(), call.nonAdditiveUsd());
    }
    boolean clean() { return kind == Kind.OK && !parseSchemaFailure; }
}
