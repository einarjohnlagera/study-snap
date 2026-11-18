package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RejectionEquivalenceTest {
    private static final String DIGEST = "digest";
    private static final String CODE = "C";
    private static final String TYPE = "T";
    private static final String ARM = "LUNA";
    private static final String MODEL = "model";
    private static final String HTTP_ERROR = "HTTP_ERROR";
    private static final String CASE = "case";
    private static final String HEAD = "head";
    private static final String FREEZE = "freeze";
    private static final String SET = "set";
    private static final String OTHER = "other";

    private static Stage1Receipt.Signal signal(int status, String digest, String code, String type) {
        return new Stage1Receipt.Signal(HTTP_ERROR, 1, 1, status, "group", code, type, digest);
    }

    private static RecheckReceipt.RejectedCall call(Integer status, String digest, String code, String type) {
        return new RecheckReceipt.RejectedCall(2, status, code, type, digest);
    }

    private static Stage1Receipt original(String arm, String model, List<Stage1Receipt.Signal> signals) {
        Stage1Receipt.CaseRow row = new Stage1Receipt.CaseRow(CASE, "workflow", "hash",
                CaseArmOutcome.State.FAILED, false, false, HTTP_ERROR, BigDecimal.ZERO, BigDecimal.ZERO,
                signals, List.of(DIGEST), null);
        return new Stage1Receipt(HEAD, FREEZE, arm, model, StageCapLedger.Stage.STAGE_1,
                Instant.EPOCH, Instant.EPOCH, 0, false, SET, List.of(row), Map.of(), null);
    }

    private static RecheckReceipt.Repeat repeat(List<RecheckReceipt.RejectedCall> calls, String deviation,
                                                  RouteVerdictCalculator.Evidence evidence) {
        return new RecheckReceipt.Repeat(1, HTTP_ERROR, evidence, deviation, null, null, true, calls);
    }

    private static RecheckReceipt receipt(String arm, String model, RecheckReceipt.Repeat repeat) {
        return new RecheckReceipt(HEAD, FREEZE, arm, model, Set.of(RecheckPlan.PROVIDER_REJECTION),
                List.of(new RecheckReceipt.Case(CASE, HTTP_ERROR, List.of(repeat,
                        new RecheckReceipt.Repeat(2, HTTP_ERROR, RouteVerdictCalculator.Evidence.SCORED,
                                null, null, null, true)))), SET);
    }

    private static RejectionEquivalence.Result decide(List<Stage1Receipt.Signal> signals,
                                                       List<RecheckReceipt.RejectedCall> calls) {
        Stage1Receipt original = original(ARM, MODEL, signals);
        RecheckReceipt.Repeat repeat = repeat(calls, null, RouteVerdictCalculator.Evidence.SCORED);
        return RejectionEquivalence.decide(original, original.cases().getFirst(),
                receipt(ARM, MODEL, repeat), repeat);
    }

    @Test void matchingPairAndOrderIndependence() {
        var left = List.of(signal(400, OTHER, "X", TYPE), signal(400, DIGEST, CODE, TYPE));
        var right = List.of(call(422, OTHER, CODE, TYPE), call(400, DIGEST, CODE, TYPE));
        assertThat(decide(left, right)).isEqualTo(new RejectionEquivalence.Result(
                RejectionEquivalence.Equivalence.EQUIVALENT, RejectionEquivalence.Reason.MATCH));
        assertThat(decide(left.reversed(), right.reversed())).isEqualTo(decide(left, right));
    }

    @Test void capturedDifferencesCannotMatch() {
        var original = List.of(signal(400, DIGEST, CODE, TYPE));
        assertThat(decide(original, List.of(call(400, OTHER, CODE, TYPE))).reason())
                .isEqualTo(RejectionEquivalence.Reason.REQUEST_DIGEST_DIFFERS);
        for (var different : List.of(call(422, DIGEST, CODE, TYPE), call(400, DIGEST, "X", TYPE),
                call(400, DIGEST, CODE, "X"))) {
            assertThat(decide(original, List.of(different))).isEqualTo(new RejectionEquivalence.Result(
                    RejectionEquivalence.Equivalence.NOT_EQUIVALENT,
                    RejectionEquivalence.Reason.REJECTION_DETAILS_DIFFER));
        }
    }

    @Test void missingEvidenceNeverInventsAMatch() {
        var current = List.of(call(400, DIGEST, CODE, TYPE));
        assertThat(decide(List.of(signal(400, DIGEST, null, TYPE)), current).equivalence())
                .isEqualTo(RejectionEquivalence.Equivalence.NOT_SHOWN);
        assertThat(decide(List.of(signal(400, DIGEST, CODE, TYPE)), null).equivalence())
                .isEqualTo(RejectionEquivalence.Equivalence.NOT_SHOWN);
        assertThat(decide(List.of(signal(400, null, null, null)),
                List.of(call(400, null, null, null))).equivalence())
                .isEqualTo(RejectionEquivalence.Equivalence.NOT_SHOWN);
        assertThat(decide(List.of(signal(400, DIGEST, CODE, TYPE)),
                List.of(call(400, DIGEST, null, TYPE))).equivalence())
                .isEqualTo(RejectionEquivalence.Equivalence.NOT_SHOWN);
        assertThat(decide(List.of(signal(400, DIGEST, CODE, TYPE)),
                List.of(call(null, DIGEST, CODE, TYPE))).equivalence())
                .isEqualTo(RejectionEquivalence.Equivalence.NOT_SHOWN);
        assertThat(decide(List.of(signal(400, DIGEST, "", "")),
                List.of(call(400, DIGEST, "", ""))).equivalence())
                .isEqualTo(RejectionEquivalence.Equivalence.EQUIVALENT);
        assertThat(decide(List.of(signal(400, DIGEST, "", "")), current).equivalence())
                .isEqualTo(RejectionEquivalence.Equivalence.NOT_EQUIVALENT);
    }

    @Test void armModelProtocolAndScoreMustBeShown() {
        Stage1Receipt original = original(ARM, MODEL, List.of(signal(400, DIGEST, CODE, TYPE)));
        var repeat = repeat(List.of(call(400, DIGEST, CODE, TYPE)), null,
                RouteVerdictCalculator.Evidence.SCORED);
        assertThat(RejectionEquivalence.decide(original, original.cases().getFirst(),
                receipt("OTHER", MODEL, repeat), repeat).reason())
                .isEqualTo(RejectionEquivalence.Reason.ARM_OR_MODEL_DIFFERS);
        assertThat(RejectionEquivalence.decide(original, original.cases().getFirst(),
                receipt(ARM, OTHER, repeat), repeat).reason())
                .isEqualTo(RejectionEquivalence.Reason.ARM_OR_MODEL_DIFFERS);
        var deviated = repeat(repeat.rejectedCalls(), "different request",
                RouteVerdictCalculator.Evidence.SCORED);
        assertThat(RejectionEquivalence.decide(original, original.cases().getFirst(),
                receipt(ARM, MODEL, deviated), deviated).reason())
                .isEqualTo(RejectionEquivalence.Reason.PROTOCOL_DEVIATION);
        var missing = repeat(repeat.rejectedCalls(), null, RouteVerdictCalculator.Evidence.MISSING);
        assertThat(RejectionEquivalence.decide(original, original.cases().getFirst(),
                receipt(ARM, MODEL, missing), missing).reason())
                .isEqualTo(RejectionEquivalence.Reason.EVIDENCE_NOT_CAPTURED);
    }
}
