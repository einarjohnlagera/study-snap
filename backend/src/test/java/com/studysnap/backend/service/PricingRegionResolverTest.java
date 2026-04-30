package com.studysnap.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PricingRegionResolverTest {

    private final PricingRegionResolver resolver = new PricingRegionResolver();

    @Test
    void resolveRegion_mapsExplicitCountriesAndDefaults() {
        assertThat(resolver.resolveRegion("PH")).isEqualTo("PH");
        assertThat(resolver.resolveRegion("us")).isEqualTo("US");
        assertThat(resolver.resolveRegion("GB")).isEqualTo("GB");
        assertThat(resolver.resolveRegion("AU")).isEqualTo("AU");
        assertThat(resolver.resolveRegion("CA")).isEqualTo("CA");
        assertThat(resolver.resolveRegion("SG")).isEqualTo("SG");
        assertThat(resolver.resolveRegion("IN")).isEqualTo("IN");
        assertThat(resolver.resolveRegion("DE")).isEqualTo("EU");
        assertThat(resolver.resolveRegion("BR")).isEqualTo("US");
        assertThat(resolver.resolveRegion(null)).isEqualTo("PH");
    }

    @Test
    void normalizeCountryCode_rejectsCloudflareUnknownMarkers() {
        assertThat(resolver.normalizeCountryCode(" ph ")).isEqualTo("PH");
        assertThat(resolver.normalizeCountryCode("XX")).isNull();
        assertThat(resolver.normalizeCountryCode("T1")).isNull();
        assertThat(resolver.normalizeCountryCode("USA")).isNull();
    }
}
