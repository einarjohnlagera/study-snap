package com.studysnap.backend.dto;

public record AcceptLinkedLearnerRequest(
        Integer learnerBirthYear,
        boolean guardianConsentAttested
) {
}
