package com.studysnap.backend.service;

import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PricingRegionResolver {
    private static final Set<String> EUROPEAN_UNION_COUNTRIES = Set.of(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
            "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
            "PL", "PT", "RO", "SK", "SI", "ES", "SE"
    );

    public String resolveRegion(String countryCode) {
        String normalized = normalizeCountryCode(countryCode);
        if (normalized == null) {
            return "PH";
        }
        return switch (normalized) {
            case "PH" -> "PH";
            case "US" -> "US";
            case "GB" -> "GB";
            case "AU" -> "AU";
            case "CA" -> "CA";
            case "SG" -> "SG";
            case "IN" -> "IN";
            default -> EUROPEAN_UNION_COUNTRIES.contains(normalized) ? "EU" : "US";
        };
    }

    public String normalizeCountryCode(String countryCode) {
        if (countryCode == null) {
            return null;
        }
        String normalized = countryCode.trim().toUpperCase();
        if (normalized.length() != 2 || "XX".equals(normalized) || "T1".equals(normalized)) {
            return null;
        }
        return normalized;
    }
}
