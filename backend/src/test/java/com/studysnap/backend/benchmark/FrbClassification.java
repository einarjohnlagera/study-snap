package com.studysnap.backend.benchmark;

import java.util.SortedSet;
import java.util.Set;

/** FRB quality classification and independent production-switch block. */
record FrbClassification(DeadlineRule.Classification classification, SortedSet<String> causes,
                         SortedSet<String> flags, boolean blocksProductionSwitch) {
    static boolean switchBlocked(DeadlineRule.Classification classification, Set<String> flags,
                                 boolean ledgerFailure) {
        return classification != DeadlineRule.Classification.BENCHMARK_PASS || ledgerFailure
                || flags.stream().anyMatch(flag -> !"SOL_RECOMMENDED".equals(flag));
    }
}
