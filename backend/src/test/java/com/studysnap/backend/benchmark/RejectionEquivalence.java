package com.studysnap.backend.benchmark;

import java.util.List;
import java.util.Objects;

/** Content-free equivalence of the rejected HTTP calls in a frozen original and recheck. */
final class RejectionEquivalence {
    enum Equivalence { EQUIVALENT, NOT_EQUIVALENT, NOT_SHOWN }
    enum Reason { MATCH, REQUEST_DIGEST_DIFFERS, REJECTION_DETAILS_DIFFER,
        EVIDENCE_NOT_CAPTURED, PROTOCOL_DEVIATION, ARM_OR_MODEL_DIFFERS }
    record Result(Equivalence equivalence, Reason reason) {}

    private RejectionEquivalence() {}

    static Result decide(Stage1Receipt original, Stage1Receipt.CaseRow row,
                         RecheckReceipt recheck, RecheckReceipt.Repeat repeat) {
        if (!Objects.equals(original.arm(), recheck.arm())
                || !Objects.equals(original.model(), recheck.model()))
            return new Result(Equivalence.NOT_SHOWN, Reason.ARM_OR_MODEL_DIFFERS);
        if (repeat.protocolDeviation() != null)
            return new Result(Equivalence.NOT_SHOWN, Reason.PROTOCOL_DEVIATION);
        if (repeat.evidence() != RouteVerdictCalculator.Evidence.SCORED)
            return new Result(Equivalence.NOT_SHOWN, Reason.EVIDENCE_NOT_CAPTURED);
        List<RecheckReceipt.RejectedCall> prior = row.llmCalls() == null ? null : row.llmCalls().stream()
                .filter(signal -> signal.httpStatus() == 400 || signal.httpStatus() == 422)
                .map(signal -> new RecheckReceipt.RejectedCall(signal.attempt(), signal.httpStatus(),
                        signal.errorCode(), signal.errorType(), signal.modelRemovedDigest()))
                .sorted(RecheckReceipt.RejectedCall.ORDER).toList();
        List<RecheckReceipt.RejectedCall> current = repeat.rejectedCalls() == null ? null
                : repeat.rejectedCalls().stream().sorted(RecheckReceipt.RejectedCall.ORDER).toList();
        if (prior == null || current == null || prior.isEmpty() || current.isEmpty()
                || prior.stream().anyMatch(RejectionEquivalence::incomplete)
                || current.stream().anyMatch(RejectionEquivalence::incomplete))
            return new Result(Equivalence.NOT_SHOWN, Reason.EVIDENCE_NOT_CAPTURED);
        boolean digestMatched = false;
        for (var left : prior) for (var right : current) {
            if (!left.modelRemovedDigest().equals(right.modelRemovedDigest())) continue;
            digestMatched = true;
            if (left.status().equals(right.status()) && left.errorCode().equals(right.errorCode())
                    && left.errorType().equals(right.errorType()))
                return new Result(Equivalence.EQUIVALENT, Reason.MATCH);
        }
        return digestMatched
                ? new Result(Equivalence.NOT_EQUIVALENT, Reason.REJECTION_DETAILS_DIFFER)
                : new Result(Equivalence.NOT_EQUIVALENT, Reason.REQUEST_DIGEST_DIFFERS);
    }

    private static boolean incomplete(RecheckReceipt.RejectedCall call) {
        return call == null || call.modelRemovedDigest() == null || call.status() == null
                || call.errorCode() == null || call.errorType() == null;
    }
}
