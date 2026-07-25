package com.studysnap.backend.dto;

import java.util.List;

public record ChallengeQuizStartRequest(
        String mode,
        List<String> additionalStudyPackIds
) {
}
