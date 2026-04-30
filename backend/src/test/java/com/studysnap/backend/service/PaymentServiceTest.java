package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.WebhookEventEntity;
import com.studysnap.backend.exception.InvalidCheckoutReturnUrlException;
import com.studysnap.backend.exception.InvalidPaymentWebhookTokenException;
import com.studysnap.backend.exception.PremiumAlreadyActiveException;
import com.studysnap.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    private static final OffsetDateTime FIXED_TIME = OffsetDateTime.of(2026, 4, 29, 4, 0, 0, 0, ZoneOffset.UTC);
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME.toInstant(), ZoneOffset.UTC);

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

    private ObjectMapper objectMapper;
    private StudySnapProperties properties;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
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
                objectMapper,
                httpClient,
                FIXED_CLOCK
        );
    }

    @Test
    void createCheckoutSession_returnsHostedInvoiceUrlWithCorrectAmountAndRedirects() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(userId, PlanType.PREMIUM)).thenReturn(false);
        when(paymentTransactionService.findLatestPendingTransaction(userId, BillingProvider.XENDIT, PlanType.PREMIUM))
                .thenReturn(Optional.empty());
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {"invoice_url":"https://checkout.xendit.test/invoice_123","expiry_date":"2026-04-30T04:00:00Z"}
                """);
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
                any(String.class),
                eq("https://checkout.xendit.test/invoice_123"),
                eq(OffsetDateTime.parse("2026-04-30T04:00:00Z"))
        )).thenReturn(Optional.of(pendingTransaction));

        BillingCheckoutSessionResponse response = paymentService.createCheckoutSession(userId, "/notes/new");

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.xendit.test/invoice_123");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.uri().toString()).hasToString("https://api.xendit.co/v2/invoices");
        assertThat(request.headers().firstValue("Authorization"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("Basic "));
        assertThat(request.headers().firstValue("Content-Type")).contains("application/json");

        JsonNode requestBody = objectMapper.readTree(readRequestBody(request));
        assertThat(requestBody.path("amount").decimalValue()).isEqualByComparingTo(new BigDecimal("249.00"));
        assertThat(requestBody.path("success_redirect_url").asText())
                .isEqualTo("http://localhost:3000/billing/success?returnUrl=%2Fnotes%2Fnew");
        assertThat(requestBody.path("failure_redirect_url").asText())
                .isEqualTo("http://localhost:3000/billing/failed?returnUrl=%2Fnotes%2Fnew");
    }

    @Test
    void createCheckoutSession_reusesExistingPendingTransaction() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(userId, PlanType.PREMIUM)).thenReturn(false);

        PaymentTransactionEntity pendingTransaction = new PaymentTransactionEntity();
        pendingTransaction.setId(UUID.randomUUID());
        pendingTransaction.setProviderReferenceId("notelib-user-1");
        pendingTransaction.setCheckoutUrl("https://checkout.xendit.test/existing_invoice");
        pendingTransaction.setExpiresAt(FIXED_TIME.plusHours(8));
        when(paymentTransactionService.findLatestPendingTransaction(userId, BillingProvider.XENDIT, PlanType.PREMIUM))
                .thenReturn(Optional.of(pendingTransaction));

        BillingCheckoutSessionResponse response = paymentService.createCheckoutSession(userId, "/dashboard");

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.xendit.test/existing_invoice");
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(paymentTransactionService, never()).createPending(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createCheckoutSession_marksExpiredPendingAndCreatesReplacementInvoice() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(userId, PlanType.PREMIUM)).thenReturn(false);

        PaymentTransactionEntity expiredPending = new PaymentTransactionEntity();
        expiredPending.setId(UUID.randomUUID());
        expiredPending.setProviderReferenceId("notelib-old");
        expiredPending.setCheckoutUrl("https://checkout.xendit.test/expired");
        expiredPending.setExpiresAt(FIXED_TIME.minusMinutes(1));
        when(paymentTransactionService.findLatestPendingTransaction(userId, BillingProvider.XENDIT, PlanType.PREMIUM))
                .thenReturn(Optional.of(expiredPending));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {"invoice_url":"https://checkout.xendit.test/new_invoice","expiry_date":"2026-04-30T04:00:00Z"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(paymentTransactionService.createPending(
                eq(userId),
                eq(BillingProvider.XENDIT),
                eq(BillingType.PREPAID),
                eq(PlanType.PREMIUM),
                eq(new BigDecimal("249.00")),
                eq("PHP"),
                any(String.class),
                eq("https://checkout.xendit.test/new_invoice"),
                eq(OffsetDateTime.parse("2026-04-30T04:00:00Z"))
        )).thenReturn(Optional.of(new PaymentTransactionEntity()));

        BillingCheckoutSessionResponse response = paymentService.createCheckoutSession(userId, "/library");

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.xendit.test/new_invoice");
        verify(paymentTransactionService).markFailed(expiredPending.getId());
    }

    @Test
    void createCheckoutSession_rejectsAlreadyPremiumUsers() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(userId, PlanType.PREMIUM)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.createCheckoutSession(userId, "/dashboard"))
                .isInstanceOf(PremiumAlreadyActiveException.class);

        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(paymentTransactionService, never()).createPending(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createCheckoutSession_rejectsExternalReturnUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(userId, PlanType.PREMIUM)).thenReturn(false);

        assertThatThrownBy(() -> paymentService.createCheckoutSession(userId, "https://evil.example/notes/new"))
                .isInstanceOf(InvalidCheckoutReturnUrlException.class);

        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(paymentTransactionService, never()).createPending(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleWebhook_paidUpgradesUserForThirtyDays() {
        UUID userId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildTransaction(userId, PaymentTransactionStatus.PENDING, "notelib-user-1");
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        WebhookEventEntity reservedEvent = new WebhookEventEntity();
        reservedEvent.setId(UUID.randomUUID());

        when(webhookEventService.reserveEvent(BillingProvider.XENDIT, "inv_123:PAID", "PAID"))
                .thenReturn(Optional.of(reservedEvent));
        when(paymentTransactionService.findByProviderReferenceId(BillingProvider.XENDIT, "notelib-user-1"))
                .thenReturn(Optional.of(transaction));
        when(subscriptionService.activatePremiumSubscription(
                eq(userId),
                eq(BillingType.PREPAID),
                eq(BillingProvider.XENDIT),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class),
                eq(false),
                any(SubscriptionService.ProviderMetadata.class)
        )).thenReturn(subscription);

        paymentService.handleWebhook(
                "{\"id\":\"inv_123\",\"external_id\":\"notelib-user-1\",\"status\":\"PAID\"}",
                "xendit_webhook_token"
        );

        verify(paymentTransactionService).markSuccess(transaction.getId());
        ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(subscriptionService).activatePremiumSubscription(
                eq(userId),
                eq(BillingType.PREPAID),
                eq(BillingProvider.XENDIT),
                startCaptor.capture(),
                endCaptor.capture(),
                eq(false),
                any(SubscriptionService.ProviderMetadata.class)
        );
        assertThat(startCaptor.getValue()).isEqualTo(FIXED_TIME);
        assertThat(endCaptor.getValue()).isEqualTo(FIXED_TIME.plusDays(30));
        verify(paymentTransactionService).attachSubscription(transaction.getId(), subscription);
        verify(webhookEventService).markProcessed(reservedEvent.getId());
    }

    @Test
    void handleWebhook_duplicatePaidOnSuccessfulTransactionDoesNotReupgrade() {
        UUID userId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildTransaction(userId, PaymentTransactionStatus.SUCCESS, "notelib-user-1");
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

        verify(paymentTransactionService, never()).markSuccess(any());
        verify(subscriptionService, never()).activatePremiumSubscription(any(), any(), any(), any(), any(), anyBoolean(), any());
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

    private String readRequestBody(HttpRequest request) {
        HttpRequest.BodyPublisher bodyPublisher = request.bodyPublisher().orElseThrow();
        CompletableFuture<String> bodyFuture = new CompletableFuture<>();
        StringBuilder bodyBuilder = new StringBuilder();

        bodyPublisher.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                bodyBuilder.append(StandardCharsets.UTF_8.decode(item));
            }

            @Override
            public void onError(Throwable throwable) {
                bodyFuture.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                bodyFuture.complete(bodyBuilder.toString());
            }
        });

        return bodyFuture.join();
    }
}
