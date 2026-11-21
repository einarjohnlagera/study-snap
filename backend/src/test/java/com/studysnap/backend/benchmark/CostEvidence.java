package com.studysnap.backend.benchmark;

import java.math.BigDecimal;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/** Immutable replay result and its exact source digest. */
record CostEvidence(SortedSet<String> flags,
        Map<StageCapLedger.Stage, BigDecimal> capByStage,
        Map<StageCapLedger.Stage, BigDecimal> spentByStage,
        Map<StageCapLedger.Stage, BigDecimal> billedByStage,
        int recordCount, String ledgerSha256) {
    CostEvidence {
        flags = java.util.Collections.unmodifiableSortedSet(new TreeSet<>(flags));
        capByStage = Map.copyOf(capByStage);
        spentByStage = Map.copyOf(spentByStage);
        billedByStage = Map.copyOf(billedByStage);
    }
}
