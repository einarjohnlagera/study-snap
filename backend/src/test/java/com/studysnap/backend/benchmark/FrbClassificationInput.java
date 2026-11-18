package com.studysnap.backend.benchmark;

import java.util.Map;
import java.util.Set;

/** Plain, route-specific owner and ledger facts; null review/cost fields mean unsupplied. */
record FrbClassificationInput(Set<String> openSafetyFindingIds, Set<String> openDisputeIds,
        Set<String> missingRequiredReviews, Set<String> costFlags,
        Set<String> establishedOperationalCaseIds, boolean ledgerFailure, boolean solRecommended,
        Set<String> ownerVetoFindingIds, Map<String, String> expansionOutcomeByWorkflow,
        Map<String, String> ledgerHeadHashes) {}
