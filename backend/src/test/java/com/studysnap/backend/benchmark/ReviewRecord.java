package com.studysnap.backend.benchmark;

/**
 * A review claim on one locked run. The signer is a recorded claim, not authentication; the
 * external head anchor and attended owner save are the control. The evidence digest is recorded
 * but never checked against a file. A review can predate the results it claims to cover because
 * its time only has to follow the lock. An owner COMPLETE names one earlier blocking concern
 * through supersedes, records a different resolution-document digest, and carries a bounded
 * resolution code. The store cannot verify that document or whether it adequately documents
 * resolution.
 * Product UX concerns can be cleared only by such owner records; N open concerns need N owner
 * records. A record without the supersedes or resolutionCode JSON key is refused by the strict
 * field set. Removed suffixes remain undetectable without an externally noted head (Brief 5f-2b).
 */
record ReviewRecord(String reviewId, String recordedAtUtc, String lockSha256, Kind kind,
                    String workflow, String subjectId, Outcome outcome, String signer,
                    SignerRole signerRole, String evidenceSha256, String supersedes,
                    ResolutionCode resolutionCode) {
    ReviewRecord(String reviewId, String recordedAtUtc, String lockSha256, Kind kind,
                 String workflow, String subjectId, Outcome outcome, String signer,
                 SignerRole signerRole, String evidenceSha256) {
        this(reviewId, recordedAtUtc, lockSha256, kind, workflow, subjectId, outcome,
                signer, signerRole, evidenceSha256, null, null);
    }
    enum Kind { CLASS_B_WORKFLOW, KEY_DEFECT, ABSOLUTE_QUALITY, NO_VETO, OWNER_VETO, OPERATIONAL_CAUSE }
    enum Outcome { COMPLETE, BLOCKING_CONCERN }
    enum SignerRole { OWNER, PRODUCT_UX }
    enum ResolutionCode { FIXED, ACCEPTED_WITH_RATIONALE, NOT_APPLICABLE }
}
