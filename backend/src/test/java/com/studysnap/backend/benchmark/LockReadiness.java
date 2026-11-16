package com.studysnap.backend.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Required human adjudication must finish before the workflow's final LockRecord is written. */
final class LockReadiness {
    private LockReadiness() {}

    static void check(Map<String, QuestionStatus> statuses, Set<String> reviewRequiredPairs) {
        if (statuses == null || reviewRequiredPairs == null) throw new InvalidEvidence("Lock inputs required");
        List<String> pending = new ArrayList<>();
        statuses.values().stream().filter(s -> s.state() == QuestionStatus.State.PENDING_REVIEW)
                .map(QuestionStatus::itemId).sorted().forEach(pending::add);
        if (!pending.isEmpty()) throw new InvalidEvidence("PENDING_REVIEW questions: " + pending);
    }

    static LockRecord.FindingsValidator asValidator(Supplier<Map<String, QuestionStatus>> statuses,
                                                     Supplier<Set<String>> reviewRequiredPairs) {
        if (statuses == null || reviewRequiredPairs == null) throw new InvalidEvidence("Lock suppliers required");
        return ignored -> check(statuses.get(), reviewRequiredPairs.get());
    }
}
