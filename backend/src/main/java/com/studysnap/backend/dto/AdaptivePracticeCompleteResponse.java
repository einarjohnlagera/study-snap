package com.studysnap.backend.dto;

import java.util.List;

public record AdaptivePracticeCompleteResponse(
        String message,
        boolean isFirstCompletedSessionEver,
        boolean isSecondCompletedSessionEver,
        List<String> twiceMissedConcepts
) {
}
