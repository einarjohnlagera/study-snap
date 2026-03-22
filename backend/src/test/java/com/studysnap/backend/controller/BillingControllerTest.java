package com.studysnap.backend.controller;

import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.BillingService;
import com.studysnap.backend.service.BillingUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingControllerTest {

    @Mock
    private BillingService billingService;
    @Mock
    private BillingUsageService billingUsageService;
    @Mock
    private AuthService authService;

    private BillingController billingController;

    @BeforeEach
    void setUp() {
        billingController = new BillingController(billingService, billingUsageService, authService);
    }

    @Test
    void createCheckoutSession_requiresEmailVerification() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        BillingCheckoutSessionResponse expected = new BillingCheckoutSessionResponse("https://checkout.example");
        when(billingService.createPremiumCheckoutSession(userId)).thenReturn(expected);

        BillingCheckoutSessionResponse response = billingController.createCheckoutSession(user);

        verify(authService).requireEmailVerified(userId);
        verify(billingService).createPremiumCheckoutSession(userId);
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

        assertThatThrownBy(() -> billingController.createCheckoutSession(user))
                .isSameAs(verificationError);

        verify(billingService, never()).createPremiumCheckoutSession(any(UUID.class));
    }
}
