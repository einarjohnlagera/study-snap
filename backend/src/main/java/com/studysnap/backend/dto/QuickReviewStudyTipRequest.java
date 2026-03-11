package com.studysnap.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QuickReviewStudyTipRequest(
        @NotNull List<@Valid QuickReviewIncorrectQuestionRequest> incorrectQuestions
) {
}
