package com.studysnap.backend.config;

import com.studysnap.backend.service.BillingProviderResolver;
import com.studysnap.backend.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@RequiredArgsConstructor
public class BillingServiceConfig {
    private final StudySnapProperties properties;
    private final BillingProviderResolver billingProviderResolver;

    @Bean
    @Primary
    public BillingService activeBillingService() {
        return billingProviderResolver.resolve(properties.getBilling().getProvider());
    }
}
