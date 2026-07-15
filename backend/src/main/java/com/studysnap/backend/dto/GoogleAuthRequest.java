package com.studysnap.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleAuthRequest(
        @NotBlank(message = "Google authorization code is required.")
        String code,
        Boolean keepSignedIn,
        @Size(max = 255, message = "UTM source is too long.")
        String utmSource,
        @Size(max = 255, message = "UTM medium is too long.")
        String utmMedium,
        @Size(max = 255, message = "UTM campaign is too long.")
        String utmCampaign,
        @Size(max = 255, message = "UTM content is too long.")
        String utmContent,
        @Size(max = 255, message = "UTM term is too long.")
        String utmTerm,
        @Size(max = 2048, message = "Referrer is too long.")
        String referrer
) {
    public GoogleAuthRequest(String code, Boolean keepSignedIn) {
        this(code, keepSignedIn, null, null, null, null, null, null);
    }
}
