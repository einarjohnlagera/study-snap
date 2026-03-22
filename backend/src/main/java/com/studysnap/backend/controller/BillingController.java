package com.studysnap.backend.controller;

import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.dto.BillingUsageSummaryResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.BillingService;
import com.studysnap.backend.service.BillingUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {
    private final BillingService billingService;
    private final BillingUsageService billingUsageService;
    private final AuthService authService;

    @PostMapping({"/checkout-session", "/checkout/premium"})
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BillingCheckoutSessionResponse createCheckoutSession(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        authService.requireEmailVerified(user.userId());
        return billingService.createPremiumCheckoutSession(user.userId());
    }

    @GetMapping({"/usage-summary", "/usage"})
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BillingUsageSummaryResponse getUsageSummary(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return billingUsageService.getMonthlyUsageSummary(user.userId());
    }

    @PostMapping("/webhook")
    public SimpleMessageResponse handleBillingWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Billing-Signature", required = false) String billingSignature,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature
    ) {
        String signature = billingSignature == null || billingSignature.isBlank()
                ? stripeSignature
                : billingSignature;
        return billingService.handleWebhook(payload, signature);
    }
}
