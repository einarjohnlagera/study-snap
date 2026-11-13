package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Section 7 first/final classification, grouping parallel batches by generation ID. */
record CaseArmOutcome(String caseArmId, List<AttemptOutcome> attempts, boolean firstAttemptClean,
                      boolean finalSuccess, boolean retried, String failureClass, State state,
                      BigDecimal conservativeUsd, BigDecimal billedUsd) {
    enum State { COMPLETE, FAILED, INCONCLUSIVE }
    static CaseArmOutcome classify(String id, List<AttemptOutcome> attempts, boolean delivered,
                                   boolean stopped, String failureClass) {
        Map<String, List<AttemptOutcome>> groups = new LinkedHashMap<>();
        for (AttemptOutcome attempt : attempts)
            groups.computeIfAbsent(attempt.generationId(), ignored -> new java.util.ArrayList<>()).add(attempt);
        boolean firstClean = !groups.isEmpty() && groups.values().stream().allMatch(group -> group.getFirst().clean());
        boolean retried = groups.values().stream().anyMatch(group -> group.size() > 1);
        State state = stopped ? State.INCONCLUSIVE : delivered ? State.COMPLETE : State.FAILED;
        BigDecimal conservative = attempts.stream().map(AttemptOutcome::conservativeUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal billed = attempts.stream().map(AttemptOutcome::billedUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CaseArmOutcome(id, List.copyOf(attempts), firstClean,
                state == State.COMPLETE, retried, state == State.INCONCLUSIVE ? "INCONCLUSIVE" : failureClass,
                state, conservative, billed);
    }
}
