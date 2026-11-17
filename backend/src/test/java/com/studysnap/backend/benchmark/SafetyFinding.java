package com.studysnap.backend.benchmark;

import java.util.List;

/**
 * Post-lock concern only; it never changes a locked verdict, finding, or T2/T3 count. A supplied
 * caseId is checked against an OpenedKey when one is supplied; otherwise it is stored unchecked.
 * Integrity failures throw InvalidStore and mean BLOCKED; open means intact, indexed, and without
 * a semantically valid resolution.
 */
record SafetyFinding(String findingId, String recordedAtUtc, String lockSha256, String itemId,
                     String workflow, String caseId, List<String> affectedRoutes, String evidence,
                     String discoveredBy, Attribution attribution, boolean materialAbsoluteQualityConcern) {
    enum Attribution { INPUT, GENERATOR, UNDETERMINED }
    record Resolution(String findingId, String findingSha256, String resolvedAtUtc,
                      String ownerDisposition, String recordedBy, String productUxReview) {}
}
