package com.studysnap.backend.service;

import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PayMongoBillingService implements BillingService {
    @Override
    public BillingCheckoutSessionResponse createPremiumCheckoutSession(UUID userId) {
        throw new AppException(
                "PAYMONGO_NOT_IMPLEMENTED",
                "PayMongo billing is not configured yet.",
                HttpStatus.NOT_IMPLEMENTED
        );
    }

    @Override
    public SimpleMessageResponse handleWebhook(String payload, String signature) {
        throw new AppException(
                "PAYMONGO_NOT_IMPLEMENTED",
                "PayMongo billing is not configured yet.",
                HttpStatus.NOT_IMPLEMENTED
        );
    }

    @Override
    public BillingProvider getProvider() {
        return BillingProvider.PAYMONGO;
    }
}
