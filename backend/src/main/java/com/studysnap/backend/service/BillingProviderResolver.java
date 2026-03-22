package com.studysnap.backend.service;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BillingProviderResolver {
    private final StripeBillingService stripeBillingService;
    private final PayMongoBillingService payMongoBillingService;

    public BillingService resolve(BillingProvider provider) {
        BillingProvider normalizedProvider = provider == null ? BillingProvider.STRIPE : provider;
        return switch (normalizedProvider) {
            case STRIPE -> stripeBillingService;
            case PAYMONGO -> payMongoBillingService;
            default -> throw new AppException(
                    "INVALID_BILLING_PROVIDER",
                    "Unsupported billing provider configuration.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        };
    }
}
