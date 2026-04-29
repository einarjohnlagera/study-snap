package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.WebhookEventEntity;
import com.studysnap.backend.exception.InvalidPaymentWebhookTokenException;
import com.studysnap.backend.exception.PremiumAlreadyActiveException;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentTransactionService paymentTransactionService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private WebhookEventService webhookEventService;
    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<String> httpResponse;

    private StudySnapProperties properties;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        properties = new StudySnapProperties();
        properties.getBilling().setFrontendBaseUrl("http://localhost:3000");
        properties.getBilling().getXendit().setBaseUrl("https://api.xendit.co");
        properties.getBilling().getXendit().setSecretKey("xnd_development_secret");
        properties.getBilling().getXendit().setWebhookToken("xendit_webhook_token");
        paymentService = new PaymentService(
                properties,
                userRepository,
                paymentTransactionService,
                subscriptionService,
                webhookEventService,
                new ObjectMapper(),
                httpClient
        );
    }

    @Test
    void createCheckoutSession_returnsHostedInvoiceUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"invoice_url\":\"https://checkout.xendit.test/invoice_123\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        PaymentTransactionEntity pendingTransaction = new PaymentTransactionEntity();
        pendingTransaction.setId(UUID.randomUUID());
        when(paymentTransactionService.createPending(
                eq(userId),
                eq(BillingProvider.XENDIT),
                eq(BillingType.PREPAID),
                eq(PlanType.PREMIUM),
                eq(new BigDecimal("249.00")),
                eq("PHP"),
                any(String.class)
        )).thenReturn(Optional.of(pendingTransaction));

        BillingCheckoutSessionResponse response = paymentService.createCheckoutSession(userId);

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.xendit.test/invoice_123");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.uri().toString()).hasToString("https://api.xendit.co/v2/invoices");
        assertThat(request.headers().firstValue("Authorization"))
            .hasValueSatisfying(value -> assertThat(value).startsWith("Basic "));
        assertThat(request.headers().firstValue("Content-Type")).contains("application/json");
    }

    @Test
    void createCheckoutSession_rejectsAlreadyPremiumUsers() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.PREMIUM);

        assertThatThrownBy(() -> paymentService.createCheckoutSession(userId))
                .isInstanceOf(PremiumAlreadyActiveException.class);

        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(paymentTransactionService, never()).createPending(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleWebhook_paidUpgradesUser() {
        UUID userId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildTransaction(userId, PaymentTransactionStatus.PENDING, "notelib-user-1");
        WebhookEventEntity reservedEvent = new WebhookEventEntity();
        reservedEvent.setId(UUID.randomUUID());

        when(webhookEventService.reserveEvent(BillingProvider.XENDIT, "inv_123:PAID", "PAID"))
                .thenReturn(Optional.of(reservedEvent));
        when(paymentTransactionService.findByProviderReferenceId(BillingProvider.XENDIT, "notelib-user-1"))
                .thenReturn(Optional.of(transaction));

        paymentService.handleWebhook(
                "{\"id\":\"inv_123\",\"external_id\":\"notelib-user-1\",\"status\":\"PAID\"}",
                "xendit_webhook_token"
        );

        verify(paymentTransactionService).markSuccess(transaction.getId());
        verify(subscriptionService).activatePremiumSubscription(
                eq(userId),
                eq(BillingType.PREPAID),
                eq(BillingProvider.XENDIT),
                any(OffsetDateTime.class),
                eq(null),
                eq(false),
                any(SubscriptionService.ProviderMetadata.class)
        );
        verify(webhookEventService).markProcessed(reservedEvent.getId());
    }

    @Test
    void handleWebhook_failedDoesNotUpgradeUser() {
        UUID userId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildTransaction(userId, PaymentTransactionStatus.PENDING, "notelib-user-2");
        WebhookEventEntity reservedEvent = new WebhookEventEntity();
        reservedEvent.setId(UUID.randomUUID());

        when(webhookEventService.reserveEvent(BillingProvider.XENDIT, "inv_456:FAILED", "FAILED"))
                .thenReturn(Optional.of(reservedEvent));
        when(paymentTransactionService.findByProviderReferenceId(BillingProvider.XENDIT, "notelib-user-2"))
                .thenReturn(Optional.of(transaction));

        paymentService.handleWebhook(
                "{\"id\":\"inv_456\",\"external_id\":\"notelib-user-2\",\"status\":\"FAILED\"}",
                "xendit_webhook_token"
        );

        verify(paymentTransactionService).markFailed(transaction.getId());
        verify(subscriptionService, never()).activatePremiumSubscription(any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(webhookEventService).markProcessed(reservedEvent.getId());
    }

    @Test
    void handleWebhook_invalidTokenIsRejected() {
        assertThatThrownBy(() -> paymentService.handleWebhook("{\"external_id\":\"ext_123\",\"status\":\"PAID\"}", "wrong_token"))
                .isInstanceOf(InvalidPaymentWebhookTokenException.class);

        verify(webhookEventService, never()).reserveEvent(any(), any(), any());
        verify(paymentTransactionService, never()).findByProviderReferenceId(any(), any());
    }

    private PaymentTransactionEntity buildTransaction(UUID userId, PaymentTransactionStatus status, String providerReferenceId) {
        UserEntity user = new UserEntity();
        user.setId(userId);

        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.setId(UUID.randomUUID());
        transaction.setUser(user);
        transaction.setStatus(status);
        transaction.setProviderReferenceId(providerReferenceId);
        return transaction;
    }
}
