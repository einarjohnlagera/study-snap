package com.studysnap.backend.benchmark;

import java.util.List;

/**
 * A disputed confirmed critical leaves locked evidence unchanged. A refused dispute must be refiled
 * as a safety finding, never dropped. DISPUTE_ACCEPTED_FOR_OWNER_DECISION remains blocking until
 * separately resolved; only UPHELD_LOCKED_EVIDENCE closes it. Product UX must confirm this choice.
 * A supplied caseId is checked against an OpenedKey when one is supplied; otherwise stored unchecked.
 * Integrity failures throw InvalidStore and mean BLOCKED; open means intact, indexed, and without
 * a semantically closing resolution.
 */
record EvidenceDispute(String disputeId, String recordedAtUtc, String lockSha256, String workflow,
                       String itemId, String caseId, String disputedBy, String reason,
                       List<String> affectedRoutes) {
    enum Outcome { UPHELD_LOCKED_EVIDENCE, DISPUTE_ACCEPTED_FOR_OWNER_DECISION }
    record Resolution(String disputeId, String disputeSha256, String resolvedAtUtc,
                      Outcome resolution, String text, String recordedBy) {}
}
