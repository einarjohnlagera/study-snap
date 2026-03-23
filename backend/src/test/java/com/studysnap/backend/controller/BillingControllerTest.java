package com.studysnap.backend.controller;

import com.studysnap.backend.dto.BillingCheckoutSessionRequest;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.dto.BillingHistoryItemResponse;
import com.studysnap.backend.dto.CancelPremiumSubscriptionRequest;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.SubscriptionPlanStatusResponse;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.entity.SubscriptionCancellationReason;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.BillingHistoryService;
import com.studysnap.backend.service.BillingService;
import com.studysnap.backend.service.BillingUsageService;
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

    private BillingController billingController;

    @BeforeEach
    void setUp() {
        billingController = new BillingController(billingService, billingUsageService, billingHistoryService, authService);
    }

    @Test
    void createCheckoutSession_requiresEmailVerification() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        BillingCheckoutSessionResponse expected = new BillingCheckoutSessionResponse("https://checkout.example");
        when(billingService.createPremiumCheckoutSession(userId, BillingCycle.MONTHLY)).thenReturn(expected);

        BillingCheckoutSessionResponse response = billingController.createCheckoutSession(user, null);

        verify(authService).requireEmailVerified(userId);
        verify(billingService).createPremiumCheckoutSession(userId, BillingCycle.MONTHLY);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void createCheckoutSession_usesRequestedBillingCycle() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        BillingCheckoutSessionResponse expected = new BillingCheckoutSessionResponse("https://checkout.example/yearly");
        BillingCheckoutSessionRequest request = new BillingCheckoutSessionRequest(BillingCycle.YEARLY);
        when(billingService.createPremiumCheckoutSession(userId, BillingCycle.YEARLY)).thenReturn(expected);

        BillingCheckoutSessionResponse response = billingController.createCheckoutSession(user, request);

        verify(authService).requireEmailVerified(userId);
        verify(billingService).createPremiumCheckoutSession(userId, BillingCycle.YEARLY);
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

        assertThatThrownBy(() -> billingController.createCheckoutSession(user, null))
                .isSameAs(verificationError);

        verify(billingService, never()).createPremiumCheckoutSession(any(UUID.class), any(BillingCycle.class));
    }

    @Test
    void getHistory_returnsBillingHistory() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        List<BillingHistoryItemResponse> expected = List.of(
                new BillingHistoryItemResponse(
                        OffsetDateTime.now(),
                        "Premium Monthly",
                        new BigDecimal("4.99"),
                        "USD",
                        PaymentTransactionStatus.SUCCESS,
                        BillingProvider.PAYMONGO,
                        "evt_123"
                )
        );
        when(billingHistoryService.getHistory(userId)).thenReturn(expected);

        List<BillingHistoryItemResponse> response = billingController.getHistory(user);

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
