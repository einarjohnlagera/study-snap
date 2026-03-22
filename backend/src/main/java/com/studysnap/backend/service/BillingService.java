package com.studysnap.backend.service;

import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.BillingProvider;

import java.util.UUID;

public interface BillingService {
    BillingCheckoutSessionResponse createPremiumCheckoutSession(UUID userId);

    SimpleMessageResponse handleWebhook(String payload, String signature);

    BillingProvider getProvider();
}
