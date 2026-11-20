package com.studysnap.backend.benchmark;

import java.util.Map;
import java.util.Set;

/** Appendix A's closed FRB classification table. Unknown causes fail closed. */
final class CauseTable {
    enum Kind { FALLBACK, CONDITIONAL_FALLBACK, RETURN_TO_OWNER }
    private static final Map<String, Kind> ROWS = Map.ofEntries(
            Map.entry("T3_ONLY", Kind.FALLBACK),
            Map.entry("MISSING_RUN_DATA", Kind.CONDITIONAL_FALLBACK),
            Map.entry("SUBGROUP_SHORTFALL", Kind.CONDITIONAL_FALLBACK));
    private static final Set<String> OWNER = Set.of(
            "T1", "T2", "OWNER_VETO", "UNDISPOSED_CANDIDATE_CRITICAL",
            "OPEN_SAFETY_FINDING", "OPEN_EVIDENCE_DISPUTE", "DISPUTE_ACCEPTED_FOR_OWNER_DECISION",
            "REVIEW_RECORD_MISSING", "INCONCLUSIVE_SCORER_UNRELIABLE",
            "INCONCLUSIVE_WORKLOAD_EXHAUSTED", "INCONCLUSIVE_UNRESOLVED_FINDING",
            "HUMAN_ADJUDICATION_INCOMPLETE", "UNMETERED", "UNSCORABLE",
            "INCOMPLETE_RECHECK", "RECHECK_MIXED", "RECHECK_UNRESOLVED",
            "DRAWN_RESERVE_WITH_UNRESOLVED_PRIMARY", "RESERVE_RECEIPT_GAP", "RESERVE_NOT_SCORED",
            "RESERVE_EVIDENCE_UNRESOLVED", "DELIVERED_WITH_PROVIDER_REJECTION_UNRECHECKED",
            "REJECTION_REPRODUCED_WITH_DELIVERED_RECOVERY", "REJECTION_REPRODUCED_THEN_RETRIED",
            "HARNESS_REGRESSION", "CONFIRMED_T1", "MISSING", "LEDGER_INTEGRITY_FAILURE",
            "LEDGER_NOT_SUPPLIED", "SUITE_NOT_EXECUTED", "UNATTRIBUTED_INCONCLUSIVE",
            "EXPANSION_OUTCOME_NOT_SUPPLIED", "INCOMPLETE_DOUBLE_MISS_EXPANSION",
            "PENDING_REVIEW_ITEMS", "KEY_DEFECT_REVIEW_WORKLOAD_EXCEEDS_LIMIT");

    private CauseTable() {}

    static boolean known(String cause) {
        return cause != null && (OWNER.contains(cause) || ROWS.containsKey(cause)
                || cause.startsWith("SHORT_DELIVERY_")
                    && cause.length() > "SHORT_DELIVERY_".length());
    }

    static Kind kind(String cause) {
        if (cause == null) return Kind.RETURN_TO_OWNER;
        if (cause.startsWith("SHORT_DELIVERY_") && cause.length() > "SHORT_DELIVERY_".length())
            return Kind.RETURN_TO_OWNER;
        if (OWNER.contains(cause)) return Kind.RETURN_TO_OWNER;
        return ROWS.getOrDefault(cause, Kind.RETURN_TO_OWNER);
    }
}
