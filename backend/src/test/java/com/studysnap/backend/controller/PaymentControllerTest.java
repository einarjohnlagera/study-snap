package com.studysnap.backend.controller;

import com.studysnap.backend.dto.BillingCheckoutSessionRequest;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.PaymentService;
import com.studysnap.backend.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;
    @Mock
    private AuthService authService;

    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        paymentController = new PaymentController(paymentService, authService);
    }

    @Test
    void createCheckoutSession_requiresVerifiedEmailAndDelegates() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        BillingCheckoutSessionResponse expected = new BillingCheckoutSessionResponse("https://checkout.xendit.test/invoice_123");
        when(paymentService.createCheckoutSession(userId, PlanType.PRO, BillingCycle.MONTHLY, "/notes/new", "PH")).thenReturn(expected);

        BillingCheckoutSessionResponse response = paymentController.createCheckoutSession(
                user,
                new BillingCheckoutSessionRequest(PlanType.PRO, BillingCycle.MONTHLY, "/notes/new"),
                "PH"
        );

        verify(authService).requireEmailVerified(userId);
        verify(paymentService).createCheckoutSession(userId, PlanType.PRO, BillingCycle.MONTHLY, "/notes/new", "PH");
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void createCheckoutSession_blocksUnverifiedUsers() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, false, 1);
        AppException verificationError = new AppException(
                "EMAIL_VERIFICATION_REQUIRED",
                "Email verification required.",
                HttpStatus.FORBIDDEN
        );
        doThrow(verificationError).when(authService).requireEmailVerified(userId);

        assertThatThrownBy(() -> paymentController.createCheckoutSession(user, null, null))
                .isSameAs(verificationError);

        verify(paymentService, never()).createCheckoutSession(userId, null, null, null, null);
    }
}
