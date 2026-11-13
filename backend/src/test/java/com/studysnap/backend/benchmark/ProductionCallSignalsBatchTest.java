package com.studysnap.backend.benchmark;

import com.studysnap.backend.service.impl.OpenAiLlmStudyPackService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/** Cross-thread signal alignment for preregistration sections 5 and 7. */
class ProductionCallSignalsBatchTest {
    private static final String CASE_ID = "alignment";
    private static final String BATCH_ONE = "-batch-1";
    private static final String BATCH_TWO = "-batch-2";
    private static final String SUCCESS = "SUCCESS";
    private static final String PREFIX = "action=llm_call operation=note_lib_long_exam route=INTERACTIVE_QUIZ "
            + "model=gpt-6-luna attempt=1 outcome=";

    @Test void alignsSignalWithinBatchEvenWhenGlobalOrderIsReversed() {
        try (var signals = new ProductionCallSignals()) {
            emit(CASE_ID + BATCH_ONE, "SCHEMA_OR_PARSE");
            emit(CASE_ID + BATCH_TWO, SUCCESS);
            var result = signals.result(CASE_ID, true, null, List.of(
                    receipt(CASE_ID + BATCH_TWO), receipt(CASE_ID + BATCH_ONE)));
            assertThat(result.callGroupIds()).containsExactly(CASE_ID + BATCH_TWO, CASE_ID + BATCH_ONE);
            assertThat(result.parseFailureCallIndices()).isEqualTo(Set.of(1));
            assertThat(result.failureClass()).isNull();
        }
    }

    @Test void countMismatchInsideBatchIsInconclusiveEvenWhenTotalsMatch() {
        try (var signals = new ProductionCallSignals()) {
            emit(CASE_ID + BATCH_ONE, SUCCESS);
            emit(CASE_ID + BATCH_ONE, SUCCESS);
            var result = signals.result(CASE_ID, true, null, List.of(
                    receipt(CASE_ID + BATCH_ONE), receipt(CASE_ID + BATCH_TWO)));
            assertThat(result.delivered()).isFalse();
            assertThat(result.failureClass()).isEqualTo(ProductionCallSignals.SIGNAL_MISMATCH);
        }
    }

    private static void emit(String batch, String outcome) {
        MDC.put(BatchTaggingExecutor.MDC_KEY, batch);
        try { LoggerFactory.getLogger(OpenAiLlmStudyPackService.class).info(PREFIX + outcome); }
        finally { MDC.remove(BatchTaggingExecutor.MDC_KEY); }
    }

    private static ArmAssertingInterceptor.CallReceipt receipt(String generationId) {
        var call = new MeteringInterceptor.Call(generationId, "LONG_EXAM:synthetic", 1, 1, 200,
                null, 0, List.of(), 0, true, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null, false);
        return new ArmAssertingInterceptor.CallReceipt(call, 10, "digest");
    }
}
