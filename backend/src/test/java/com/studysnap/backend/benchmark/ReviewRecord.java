package com.studysnap.backend.benchmark;

/**
 * A review claim on one locked run. The signer is a recorded claim, not authentication; the
 * external head anchor and attended owner save are the control. The evidence digest is recorded
 * but never checked against a file. A review can predate the results it claims to cover because
 * its time only has to follow the lock. A later owner completion can supersede a Product UX
 * concern without a hash link to that concern. Product UX must rule on that policy.
 */
record ReviewRecord(String reviewId, String recordedAtUtc, String lockSha256, Kind kind,
                    String workflow, String subjectId, Outcome outcome, String signer,
                    SignerRole signerRole, String evidenceSha256) {
    enum Kind { CLASS_B_WORKFLOW, KEY_DEFECT, ABSOLUTE_QUALITY }
    enum Outcome { COMPLETE, BLOCKING_CONCERN }
    enum SignerRole { OWNER, PRODUCT_UX }
}
