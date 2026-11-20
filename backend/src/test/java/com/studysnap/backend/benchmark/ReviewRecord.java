package com.studysnap.backend.benchmark;

/**
 * A review claim on one locked run. The signer is a recorded claim, not authentication; the
 * external head anchor and attended owner save are the control. The evidence digest is recorded
 * but never checked against a file. A review can predate the results it claims to cover because
 * its time only has to follow the lock. An owner COMPLETE names one earlier blocking concern
 * through supersedes and records a different resolution-document digest. The store cannot verify
 * that document or whether its digest adequately documents resolution (Product UX question 1).
 * Product UX concerns can be cleared only by such owner records; N open concerns need N owner
 * records. A 5f-2a record without the supersedes JSON key is refused by the strict field set.
 * Removed suffixes remain undetectable without an externally noted head (Brief 5f-2b). An open
 * concern outside the caller's required subjects does not block; Product UX has not ruled on that.
 */
record ReviewRecord(String reviewId, String recordedAtUtc, String lockSha256, Kind kind,
                    String workflow, String subjectId, Outcome outcome, String signer,
                    SignerRole signerRole, String evidenceSha256, String supersedes) {
    ReviewRecord(String reviewId, String recordedAtUtc, String lockSha256, Kind kind,
                 String workflow, String subjectId, Outcome outcome, String signer,
                 SignerRole signerRole, String evidenceSha256) {
        this(reviewId, recordedAtUtc, lockSha256, kind, workflow, subjectId, outcome,
                signer, signerRole, evidenceSha256, null);
    }
    enum Kind { CLASS_B_WORKFLOW, KEY_DEFECT, ABSOLUTE_QUALITY }
    enum Outcome { COMPLETE, BLOCKING_CONCERN }
    enum SignerRole { OWNER, PRODUCT_UX }
}
