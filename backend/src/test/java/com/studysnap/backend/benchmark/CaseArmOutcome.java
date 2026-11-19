package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Section 7 first/final classification, grouping parallel batches by generation ID. */
record CaseArmOutcome(String caseArmId, List<AttemptOutcome> attempts, boolean firstAttemptClean,
                      boolean finalSuccess, boolean retried, String failureClass, State state,
                      BigDecimal conservativeUsd, BigDecimal billedUsd, Integer deliveredQuestionCount) {
    enum State { COMPLETE, FAILED, INCONCLUSIVE }
    CaseArmOutcome(String caseArmId, List<AttemptOutcome> attempts, boolean firstAttemptClean,
                   boolean finalSuccess, boolean retried, String failureClass, State state,
                   BigDecimal conservativeUsd, BigDecimal billedUsd) {
        this(caseArmId, attempts, firstAttemptClean, finalSuccess, retried, failureClass, state,
                conservativeUsd, billedUsd, null);
    }
    static CaseArmOutcome classify(String id, List<AttemptOutcome> attempts, boolean delivered,
                                   boolean stopped, String failureClass) {
        return classify(id, attempts, delivered, stopped, failureClass, null);
    }
    /** A stopped case is inconclusive even when the adapter observed a partial delivery. */
    static CaseArmOutcome classify(String id, List<AttemptOutcome> attempts, boolean delivered,
                                   boolean stopped, String failureClass, Integer deliveredQuestionCount) {
        Map<String, List<AttemptOutcome>> groups = new LinkedHashMap<>();
        for (AttemptOutcome attempt : attempts)
            groups.computeIfAbsent(attempt.generationId(), ignored -> new java.util.ArrayList<>()).add(attempt);
        boolean firstClean = !stopped && failureClass == null && !groups.isEmpty()
                && groups.values().stream().allMatch(group -> group.getFirst().clean());
        boolean retried = groups.values().stream().anyMatch(group -> group.size() > 1);
        State state = stopped ? State.INCONCLUSIVE : delivered ? State.COMPLETE : State.FAILED;
        BigDecimal conservative = attempts.stream().map(AttemptOutcome::conservativeUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal billed = attempts.stream().map(AttemptOutcome::billedUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CaseArmOutcome(id, List.copyOf(attempts), firstClean,
                state == State.COMPLETE, retried, state == State.INCONCLUSIVE
                        ? failureClass == null ? "INCONCLUSIVE" : failureClass : failureClass,
                state, conservative, billed,
                state == State.FAILED && "SHORT_DELIVERY".equals(failureClass)
                        ? deliveredQuestionCount : null);
    }
}
