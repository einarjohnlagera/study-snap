package com.studysnap.backend.service;

import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.dto.CancelPremiumSubscriptionRequest;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingCycle;

import java.util.UUID;

public interface BillingService {
    BillingCheckoutSessionResponse createPremiumCheckoutSession(UUID userId, BillingCycle billingCycle);

    void cancelPremiumSubscription(UUID userId, CancelPremiumSubscriptionRequest request);

    SimpleMessageResponse handleWebhook(String payload, String signature);

    BillingProvider getProvider();
}
