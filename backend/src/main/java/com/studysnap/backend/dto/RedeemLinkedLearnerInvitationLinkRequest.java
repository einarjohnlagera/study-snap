package com.studysnap.backend.dto;

public record RedeemLinkedLearnerInvitationLinkRequest(
        /** Required only when the redeemer is the learner and has no year recorded yet. */
        Integer learnerBirthYear
) {
}

