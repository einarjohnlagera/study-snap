package com.studysnap.backend.controller;

import com.studysnap.backend.dto.BillingCheckoutSessionRequest;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.dto.BillingHistoryItemResponse;
import com.studysnap.backend.dto.BillingUsageSummaryResponse;
import com.studysnap.backend.dto.CancelPremiumSubscriptionRequest;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.BillingHistoryService;
import com.studysnap.backend.service.BillingService;
import com.studysnap.backend.service.BillingUsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {
    private final BillingService billingService;
    private final BillingUsageService billingUsageService;
    private final BillingHistoryService billingHistoryService;
    private final AuthService authService;

    @PostMapping({"/checkout-session", "/checkout/premium"})
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BillingCheckoutSessionResponse createCheckoutSession(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody(required = false) BillingCheckoutSessionRequest request
    ) {
        authService.requireEmailVerified(user.userId());
        BillingCycle billingCycle = request == null || request.billingCycle() == null
                ? BillingCycle.MONTHLY
                : request.billingCycle();
        return billingService.createPremiumCheckoutSession(user.userId(), billingCycle);
    }

    @GetMapping({"/usage-summary", "/usage"})
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BillingUsageSummaryResponse getUsageSummary(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return billingUsageService.getMonthlyUsageSummary(user.userId());
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<BillingHistoryItemResponse> getHistory(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return billingHistoryService.getHistory(user.userId());
    }

    @PostMapping("/subscription/cancel")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public MeResponse cancelPremiumSubscription(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody(required = false) CancelPremiumSubscriptionRequest request
    ) {
        CancelPremiumSubscriptionRequest payload = request == null
                ? new CancelPremiumSubscriptionRequest(null, null)
                : request;
        billingService.cancelPremiumSubscription(user.userId(), payload);
        return authService.getMe(user.userId());
    }

    @PostMapping("/webhook")
    public SimpleMessageResponse handleBillingWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Billing-Signature", required = false) String billingSignature,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
            @RequestHeader(value = "Paymongo-Signature", required = false) String payMongoSignature,
            @RequestHeader(value = "X-Paymongo-Signature", required = false) String payMongoSignatureAlt
    ) {
        String signature = resolveSignature(billingSignature, stripeSignature, payMongoSignature, payMongoSignatureAlt);
        return billingService.handleWebhook(payload, signature);
    }

    private String resolveSignature(
            String billingSignature,
            String stripeSignature,
            String payMongoSignature,
            String payMongoSignatureAlt
    ) {
        if (billingSignature != null && !billingSignature.isBlank()) {
            return billingSignature;
        }
        if (stripeSignature != null && !stripeSignature.isBlank()) {
            return stripeSignature;
        }
        if (payMongoSignature != null && !payMongoSignature.isBlank()) {
            return payMongoSignature;
        }
        return payMongoSignatureAlt;
    }
}
