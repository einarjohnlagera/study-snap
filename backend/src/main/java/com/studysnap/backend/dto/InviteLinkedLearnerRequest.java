package com.studysnap.backend.dto;

import com.studysnap.backend.entity.LinkedLearnerSide;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteLinkedLearnerRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        String email,
        @NotNull(message = "Choose whether you are the supporter or learner.")
        LinkedLearnerSide inviterRole
) {
}
