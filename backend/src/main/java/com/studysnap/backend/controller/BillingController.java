package com.studysnap.backend.controller;

import com.studysnap.backend.dto.BillingHistoryResponse;
import com.studysnap.backend.dto.BillingPricingResponse;
import com.studysnap.backend.dto.BillingUsageSummaryResponse;
import com.studysnap.backend.dto.CancelPremiumSubscriptionRequest;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.BillingHistoryService;
import com.studysnap.backend.service.PricingService;
import com.studysnap.backend.service.BillingUsageService;
import com.studysnap.backend.service.SubscriptionService;
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

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {
    private final BillingUsageService billingUsageService;
    private final BillingHistoryService billingHistoryService;
    private final PricingService pricingService;
    private final AuthService authService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/pricing")
    public BillingPricingResponse getPricing(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "CF-IPCountry", required = false) String cfIpCountry
    ) {
        return pricingService.getPricing(user == null ? null : user.userId(), cfIpCountry);
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
    public BillingHistoryResponse getHistory(
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
        subscriptionService.scheduleCancellationAtPeriodEnd(
                user.userId(),
                payload.reason(),
                payload.feedback()
        );
        return authService.getMe(user.userId());
    }
}
