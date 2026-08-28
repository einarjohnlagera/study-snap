package com.studysnap.backend.dto;

import com.studysnap.backend.entity.LinkedLearnerSide;
import jakarta.validation.constraints.NotNull;

public record CreateLinkedLearnerInvitationLinkRequest(
        @NotNull(message = "Choose whether you are the supporter or learner.")
        LinkedLearnerSide creatorRole,
        /** Required only when the creator is the learner and has no year recorded yet. */
        Integer learnerBirthYear
) {
}

