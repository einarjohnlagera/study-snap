package com.studysnap.backend.controller;

import com.studysnap.backend.dto.BillingCheckoutSessionRequest;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.dto.BillingHistoryResponse;
import com.studysnap.backend.dto.BillingHistoryItemResponse;
import com.studysnap.backend.dto.BillingPricingResponse;
import com.studysnap.backend.dto.CancelPremiumSubscriptionRequest;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.SubscriptionPlanStatusResponse;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.entity.SubscriptionCancellationReason;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.BillingHistoryService;
import com.studysnap.backend.service.BillingService;
import com.studysnap.backend.service.BillingUsageService;
import com.studysnap.backend.service.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

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
    private BillingHistoryService billingHistoryService;
    @Mock
    private AuthService authService;
    @Mock
    private PricingService pricingService;

    private BillingController billingController;

    @BeforeEach
    void setUp() {
        billingController = new BillingController(
                billingService,
                billingUsageService,
                billingHistoryService,
                pricingService,
                authService
        );
    }

    @Test
    void createCheckoutSession_requiresEmailVerification() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        BillingCheckoutSessionResponse expected = new BillingCheckoutSessionResponse("https://checkout.example");
        when(billingService.createPremiumCheckoutSession(userId, BillingCycle.MONTHLY, null, "PH")).thenReturn(expected);

        BillingCheckoutSessionResponse response = billingController.createCheckoutSession(user, null, "PH");

        verify(authService).requireEmailVerified(userId);
        verify(billingService).createPremiumCheckoutSession(userId, BillingCycle.MONTHLY, null, "PH");
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void createCheckoutSession_usesRequestedBillingCycle() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        BillingCheckoutSessionResponse expected = new BillingCheckoutSessionResponse("https://checkout.example/yearly");
        BillingCheckoutSessionRequest request = new BillingCheckoutSessionRequest(BillingCycle.YEARLY, "SAVE10");
        when(billingService.createPremiumCheckoutSession(userId, BillingCycle.YEARLY, "SAVE10", "US"))
                .thenReturn(expected);

        BillingCheckoutSessionResponse response = billingController.createCheckoutSession(user, request, "US");

        verify(authService).requireEmailVerified(userId);
        verify(billingService).createPremiumCheckoutSession(userId, BillingCycle.YEARLY, "SAVE10", "US");
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

        assertThatThrownBy(() -> billingController.createCheckoutSession(user, null, null))
                .isSameAs(verificationError);

        verify(billingService, never()).createPremiumCheckoutSession(any(UUID.class), any(BillingCycle.class), any(), any());
    }

    @Test
    void getPricing_delegatesToPricingService() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        BillingPricingResponse expected = new BillingPricingResponse(
                "PH",
                "PHP",
                new BigDecimal("249.00"),
                new BigDecimal("1999.00"),
                new BigDecimal("199.00"),
                true,
                true
        );
        when(pricingService.getPricing(userId, "PH")).thenReturn(expected);

        BillingPricingResponse response = billingController.getPricing(user, "PH");

        assertThat(response).isEqualTo(expected);
        verify(pricingService).getPricing(userId, "PH");
    }

    @Test
    void getHistory_returnsBillingHistory() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        BillingHistoryResponse expected = new BillingHistoryResponse(
                PlanType.PREMIUM,
                SubscriptionStatus.ACTIVE,
                BillingCycle.MONTHLY,
                OffsetDateTime.parse("2026-03-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                false,
                null,
                List.of(
                        new BillingHistoryItemResponse(
                                UUID.randomUUID(),
                                OffsetDateTime.now(),
                                "Premium Monthly",
                                new BigDecimal("4.99"),
                                "USD",
                                PaymentTransactionStatus.SUCCESS,
                                BillingProvider.PAYMONGO,
                                "evt_123"
                        )
                )
        );
        when(billingHistoryService.getHistory(userId)).thenReturn(expected);

        BillingHistoryResponse response = billingController.getHistory(user);

        assertThat(response).isEqualTo(expected);
        verify(billingHistoryService).getHistory(userId);
    }

    @Test
    void cancelPremiumSubscription_returnsUpdatedProfile() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        CancelPremiumSubscriptionRequest request = new CancelPremiumSubscriptionRequest(
                SubscriptionCancellationReason.TOO_EXPENSIVE,
                "Need a lower price point."
        );
        MeResponse expected = new MeResponse(
                userId.toString(),
                "[email protected]",
                "Note",
                null,
                "Note",
                null,
                ProfileType.STUDENT,
                EngagementMode.FOCUSED,
                OffsetDateTime.parse("2026-03-20T00:00:00Z"),
                OffsetDateTime.parse("2026-03-21T00:00:00Z"),
                UserRole.USER,
                UserStatus.ACTIVE,
                PlanType.PREMIUM,
                new SubscriptionPlanStatusResponse(
                        true,
                        OffsetDateTime.parse("2026-04-20T00:00:00Z"),
                        OffsetDateTime.parse("2026-03-23T00:00:00Z")
                )
        );
        when(authService.getMe(userId)).thenReturn(expected);

        MeResponse response = billingController.cancelPremiumSubscription(user, request);

        verify(billingService).cancelPremiumSubscription(userId, request);
        verify(authService).getMe(userId);
        assertThat(response).isEqualTo(expected);
    }
}
