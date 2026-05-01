package com.studysnap.backend.controller;

import com.studysnap.backend.dto.BillingCheckoutSessionRequest;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final AuthService authService;

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BillingCheckoutSessionResponse createCheckoutSession(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody(required = false) BillingCheckoutSessionRequest request,
            @RequestHeader(name = "CF-IPCountry", required = false) String cfIpCountry
    ) {
        authService.requireEmailVerified(user.userId());
        return paymentService.createCheckoutSession(
                user.userId(),
                request == null ? null : request.planType(),
                request == null ? null : request.billingCycle(),
                request == null ? null : request.returnUrl(),
                cfIpCountry
        );
    }
}
