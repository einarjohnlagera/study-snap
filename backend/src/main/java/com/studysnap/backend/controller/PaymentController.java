package com.studysnap.backend.controller;

import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
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
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        authService.requireEmailVerified(user.userId());
        return paymentService.createCheckoutSession(user.userId());
    }
}
