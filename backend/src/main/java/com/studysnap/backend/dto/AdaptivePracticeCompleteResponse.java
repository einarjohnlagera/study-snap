package com.studysnap.backend.dto;

public record AdaptivePracticeCompleteResponse(
        String message,
        boolean isFirstCompletedSessionEver
) {
}
