package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * A recipient's shared-quiz submission.
 *
 * <p>{@code answers} carries one entry per question and stays required, so a partial submission is
 * still rejected on size. Its entries are nullable: a MULTI_SELECT question has no single index, and
 * its selections arrive in the index-aligned {@code multiAnswers} instead.
 *
 * <p>{@code multiAnswers} is optional so a recipient whose browser still holds the pre-fix bundle keeps
 * submitting successfully.
 */
public record SharedQuizResultsRequest(
        @NotNull List<Integer> answers,
        List<List<Integer>> multiAnswers
) {
}
