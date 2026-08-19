package com.studysnap.backend.dto;

import jakarta.validation.constraints.AssertTrue;

public record GuardianConsentRequest(
        @AssertTrue(message = "Guardian consent attestation is required.")
        boolean attested
) {
}
