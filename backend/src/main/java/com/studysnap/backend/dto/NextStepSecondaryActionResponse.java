package com.studysnap.backend.dto;

public record NextStepSecondaryActionResponse(
        String actionLabel,
        String actionHref,
        boolean adaptivePractice
) {
}
