package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.DiscountVoucherEntity;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.WebhookEventEntity;
import com.studysnap.backend.exception.InvalidCheckoutReturnUrlException;
import com.studysnap.backend.exception.InvalidPaymentWebhookTokenException;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    private PricingService pricingService;
    @Mock
    private WebhookEventService webhookEventService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<String> httpResponse;

    private ObjectMapper objectMapper;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        StudySnapProperties properties = new StudySnapProperties();
        properties.getBilling().setFrontendBaseUrl("http://localhost:3000");
        properties.getBilling().getXendit().setBaseUrl("https://api.xendit.co");
        properties.getBilling().getXendit().setSecretKey("xnd_development_secret");
        properties.getBilling().getXendit().setWebhookToken("xendit_webhook_token");
        paymentService = new PaymentService(
                properties,
                userRepository,
                paymentTransactionService,
                subscriptionService,
                pricingService,
                webhookEventService,
                analyticsService,
                objectMapper,
                httpClient,
                FIXED_CLOCK
        );
    }

    @Test
    void createCheckoutSession_createsProMonthlyInvoiceUsingConfiguredAmount() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        PaymentTransactionEntity pendingTransaction = new PaymentTransactionEntity();
        pendingTransaction.setId(transactionId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(pricingService.resolveCheckoutSelection(userId, PlanType.PRO, BillingCycle.MONTHLY, null, "PH"))
                .thenReturn(buildSelection(PlanType.PRO, BillingCycle.MONTHLY, 30, "249.00", "249.00", "0.00", null));
        when(paymentTransactionService.findPendingTransactions(userId, BillingProvider.XENDIT, PlanType.PRO))
                .thenReturn(List.of());
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {"invoice_url":"https://checkout.xendit.test/invoice_pro_monthly","expiry_date":"2026-04-30T04:00:00Z"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(paymentTransactionService.createPending(any())).thenReturn(Optional.of(pendingTransaction));

        BillingCheckoutSessionResponse response = paymentService.createCheckoutSession(
                userId,
                PlanType.PRO,
                BillingCycle.MONTHLY,
                "/notes/new",
                "PH"
        );

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.xendit.test/invoice_pro_monthly");
        verify(analyticsService).trackEvent(
                eq(userId),
                eq(AnalyticsEventType.CHECKOUT_INITIATED),
                eq(transactionId),
                eq(Map.of("planType", "PRO", "billingCycle", "MONTHLY"))
        );

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        JsonNode requestBody = objectMapper.readTree(readRequestBody(requestCaptor.getValue()));
        assertThat(requestBody.path("amount").decimalValue()).isEqualByComparingTo("249.00");
        assertThat(requestBody.path("description").asText()).isEqualTo("NoteLib Pro Monthly");
        assertThat(requestBody.path("success_redirect_url").asText())
                .isEqualTo("http://localhost:3000/billing/success?plan=PRO&returnUrl=%2Fnotes%2Fnew");
    }

    @Test
    void createCheckoutSession_usesPlusIntroAmountAndDescription() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID voucherId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(pricingService.resolveCheckoutSelection(userId, PlanType.PLUS, BillingCycle.MONTHLY, null, "PH"))
                .thenReturn(buildSelection(PlanType.PLUS, BillingCycle.MONTHLY, 30, "179.00", "149.00", "30.00", voucherId));
        when(paymentTransactionService.findPendingTransactions(userId, BillingProvider.XENDIT, PlanType.PLUS))
                .thenReturn(List.of());
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {"invoice_url":"https://checkout.xendit.test/invoice_plus_intro","expiry_date":"2026-04-30T04:00:00Z"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(paymentTransactionService.createPending(any())).thenReturn(Optional.of(new PaymentTransactionEntity()));

        paymentService.createCheckoutSession(userId, PlanType.PLUS, BillingCycle.MONTHLY, "/pricing", "PH");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        JsonNode requestBody = objectMapper.readTree(readRequestBody(requestCaptor.getValue()));
        assertThat(requestBody.path("amount").decimalValue()).isEqualByComparingTo("149.00");
        assertThat(requestBody.path("description").asText()).isEqualTo("NoteLib Plus Monthly - Intro offer applied");

        ArgumentCaptor<PaymentTransactionService.PendingPaymentTransactionRequest> pendingCaptor =
                ArgumentCaptor.forClass(PaymentTransactionService.PendingPaymentTransactionRequest.class);
        verify(paymentTransactionService).createPending(pendingCaptor.capture());
        PaymentTransactionService.PendingPaymentTransactionRequest pendingRequest = pendingCaptor.getValue();
        assertThat(pendingRequest.planType()).isEqualTo(PlanType.PLUS);
        assertThat(pendingRequest.accessDurationDays()).isEqualTo(30);
        assertThat(pendingRequest.originalAmount()).isEqualByComparingTo("179.00");
        assertThat(pendingRequest.discountAmount()).isEqualByComparingTo("30.00");
        assertThat(pendingRequest.finalAmount()).isEqualByComparingTo("149.00");
        assertThat(pendingRequest.voucherId()).isEqualTo(voucherId);
    }

    @Test
    void createCheckoutSession_reusesPendingCheckoutOnlyForMatchingPlan() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID voucherId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(pricingService.resolveCheckoutSelection(userId, PlanType.PLUS, BillingCycle.MONTHLY, null, "PH"))
                .thenReturn(buildSelection(PlanType.PLUS, BillingCycle.MONTHLY, 30, "179.00", "149.00", "30.00", voucherId));

        PaymentTransactionEntity plusPending = buildPendingTransaction(
                userId,
                PlanType.PLUS,
                BillingCycle.MONTHLY,
                "179.00",
                "30.00",
                "149.00",
                voucherId,
                FIXED_TIME.plusHours(8)
        );
        when(paymentTransactionService.findPendingTransactions(userId, BillingProvider.XENDIT, PlanType.PLUS))
                .thenReturn(List.of(plusPending));

        BillingCheckoutSessionResponse response = paymentService.createCheckoutSession(
                userId,
                PlanType.PLUS,
                BillingCycle.MONTHLY,
                "/dashboard",
                "PH"
        );

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.xendit.test/existing_invoice");
        verify(analyticsService).trackEvent(
                userId,
                AnalyticsEventType.CHECKOUT_INITIATED,
                plusPending.getId(),
                Map.of("planType", "PLUS", "billingCycle", "MONTHLY")
        );
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(paymentTransactionService, never()).createPending(any());
        verify(paymentTransactionService, never()).markFailed(any());
    }

    @Test
    void createCheckoutSession_rejectsExternalReturnUrl() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));

        assertThatThrownBy(() -> paymentService.createCheckoutSession(
                userId,
                PlanType.PRO,
                BillingCycle.MONTHLY,
                "https://evil.example/notes/new",
                "PH"
        )).isInstanceOf(InvalidCheckoutReturnUrlException.class);

        verifyNoInteractions(httpClient);
        verify(paymentTransactionService, never()).createPending(any());
        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
    }

    @Test
    void createCheckoutSession_doesNotTrackCheckoutWhenInvoiceHasNoUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(pricingService.resolveCheckoutSelection(userId, PlanType.PRO, BillingCycle.MONTHLY, null, "PH"))
                .thenReturn(buildSelection(PlanType.PRO, BillingCycle.MONTHLY, 30, "249.00", "249.00", "0.00", null));
        when(paymentTransactionService.findPendingTransactions(userId, BillingProvider.XENDIT, PlanType.PRO))
                .thenReturn(List.of());
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {"expiry_date":"2026-04-30T04:00:00Z"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        assertThatThrownBy(() -> paymentService.createCheckoutSession(
                userId,
                PlanType.PRO,
                BillingCycle.MONTHLY,
                "/dashboard",
                "PH"
        )).isInstanceOf(com.studysnap.backend.exception.PaymentCheckoutUnavailableException.class);

        verify(paymentTransactionService, never()).createPending(any());
        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
    }

    @Test
    void handleWebhook_paidMonthlyActivatesSelectedPlusPlanAndRecordsVoucherRedemption() {
        UUID userId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildWebhookTransaction(
                userId,
                PaymentTransactionStatus.PENDING,
                PlanType.PLUS,
                BillingCycle.MONTHLY,
                30,
                "notelib-user-plus"
        );
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        WebhookEventEntity reservedEvent = new WebhookEventEntity();
        reservedEvent.setId(UUID.randomUUID());

        when(webhookEventService.reserveEvent(BillingProvider.XENDIT, "inv_plus:PAID", "PAID"))
                .thenReturn(Optional.of(reservedEvent));
        when(paymentTransactionService.findByProviderReferenceId(BillingProvider.XENDIT, "notelib-user-plus"))
                .thenReturn(Optional.of(transaction));
        when(subscriptionService.activatePaidSubscription(
                eq(userId),
                eq(PlanType.PLUS),
                eq(BillingType.PREPAID),
                eq(BillingProvider.XENDIT),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class),
                eq(false),
                any(SubscriptionService.ProviderMetadata.class)
        )).thenReturn(subscription);

        paymentService.handleWebhook(
                "{\"id\":\"inv_plus\",\"external_id\":\"notelib-user-plus\",\"status\":\"PAID\"}",
                "xendit_webhook_token"
        );

        verify(paymentTransactionService).markSuccess(transaction.getId());
        ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(subscriptionService).activatePaidSubscription(
                eq(userId),
                eq(PlanType.PLUS),
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
        verify(pricingService).recordVoucherRedemption(subscription, transaction);
        verify(webhookEventService).markProcessed(reservedEvent.getId());
    }

    @Test
    void handleWebhook_paidYearlyActivatesSelectedProPlanForConfiguredDuration() {
        UUID userId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildWebhookTransaction(
                userId,
                PaymentTransactionStatus.PENDING,
                PlanType.PRO,
                BillingCycle.YEARLY,
                365,
                "notelib-user-pro-yearly"
        );
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        WebhookEventEntity reservedEvent = new WebhookEventEntity();
        reservedEvent.setId(UUID.randomUUID());

        when(webhookEventService.reserveEvent(BillingProvider.XENDIT, "inv_pro_yearly:PAID", "PAID"))
                .thenReturn(Optional.of(reservedEvent));
        when(paymentTransactionService.findByProviderReferenceId(BillingProvider.XENDIT, "notelib-user-pro-yearly"))
                .thenReturn(Optional.of(transaction));
        when(subscriptionService.activatePaidSubscription(
                eq(userId),
                eq(PlanType.PRO),
                eq(BillingType.PREPAID),
                eq(BillingProvider.XENDIT),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class),
                eq(false),
                any(SubscriptionService.ProviderMetadata.class)
        )).thenReturn(subscription);

        paymentService.handleWebhook(
                "{\"id\":\"inv_pro_yearly\",\"external_id\":\"notelib-user-pro-yearly\",\"status\":\"PAID\"}",
                "xendit_webhook_token"
        );

        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(subscriptionService).activatePaidSubscription(
                eq(userId),
                eq(PlanType.PRO),
                eq(BillingType.PREPAID),
                eq(BillingProvider.XENDIT),
                any(OffsetDateTime.class),
                endCaptor.capture(),
                eq(false),
                any(SubscriptionService.ProviderMetadata.class)
        );
        assertThat(endCaptor.getValue()).isEqualTo(FIXED_TIME.plusDays(365));
    }

    @Test
    void handleWebhook_duplicatePaidOnSuccessfulTransactionDoesNotReapplySubscription() {
        UUID userId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildWebhookTransaction(
                userId,
                PaymentTransactionStatus.SUCCESS,
                PlanType.PRO,
                BillingCycle.MONTHLY,
                30,
                "notelib-user-pro"
        );
        WebhookEventEntity reservedEvent = new WebhookEventEntity();
        reservedEvent.setId(UUID.randomUUID());

        when(webhookEventService.reserveEvent(BillingProvider.XENDIT, "inv_dup:PAID", "PAID"))
                .thenReturn(Optional.of(reservedEvent));
        when(paymentTransactionService.findByProviderReferenceId(BillingProvider.XENDIT, "notelib-user-pro"))
                .thenReturn(Optional.of(transaction));

        paymentService.handleWebhook(
                "{\"id\":\"inv_dup\",\"external_id\":\"notelib-user-pro\",\"status\":\"PAID\"}",
                "xendit_webhook_token"
        );

        verify(paymentTransactionService, never()).markSuccess(any());
        verify(subscriptionService, never()).activatePaidSubscription(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(pricingService, never()).recordVoucherRedemption(any(), any());
        verify(webhookEventService).markProcessed(reservedEvent.getId());
    }

    @Test
    void handleWebhook_failedDoesNotActivateSubscription() {
        UUID userId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildWebhookTransaction(
                userId,
                PaymentTransactionStatus.PENDING,
                PlanType.PRO,
                BillingCycle.MONTHLY,
                30,
                "notelib-user-failed"
        );
        WebhookEventEntity reservedEvent = new WebhookEventEntity();
        reservedEvent.setId(UUID.randomUUID());

        when(webhookEventService.reserveEvent(BillingProvider.XENDIT, "inv_failed:FAILED", "FAILED"))
                .thenReturn(Optional.of(reservedEvent));
        when(paymentTransactionService.findByProviderReferenceId(BillingProvider.XENDIT, "notelib-user-failed"))
                .thenReturn(Optional.of(transaction));

        paymentService.handleWebhook(
                "{\"id\":\"inv_failed\",\"external_id\":\"notelib-user-failed\",\"status\":\"FAILED\"}",
                "xendit_webhook_token"
        );

        verify(paymentTransactionService).markFailed(transaction.getId());
        verify(subscriptionService, never()).activatePaidSubscription(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(pricingService, never()).recordVoucherRedemption(any(), any());
        verify(webhookEventService).markProcessed(reservedEvent.getId());
    }

    @Test
    void handleWebhook_invalidTokenIsRejected() {
        assertThatThrownBy(() -> paymentService.handleWebhook("{\"external_id\":\"ext_123\",\"status\":\"PAID\"}", "wrong_token"))
                .isInstanceOf(InvalidPaymentWebhookTokenException.class);

        verify(webhookEventService, never()).reserveEvent(any(), any(), any());
        verify(paymentTransactionService, never()).findByProviderReferenceId(any(), any());
    }

    private PricingService.CheckoutSelection buildSelection(
            PlanType planType,
            BillingCycle billingCycle,
            int accessDurationDays,
            String basePrice,
            String effectivePrice,
            String discountAmount,
            UUID voucherId
    ) {
        return new PricingService.CheckoutSelection(
                "PH",
                "PH",
                "PHP",
                planType,
                planType.getDisplayName(),
                billingCycle,
                accessDurationDays,
                new BigDecimal(basePrice),
                new BigDecimal(discountAmount),
                voucherId,
                voucherId == null ? null : "INTRO-PH-MONTHLY",
                new BigDecimal(effectivePrice)
        );
    }

    private UserEntity buildUser(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        return user;
    }

    private PaymentTransactionEntity buildPendingTransaction(
            UUID userId,
            PlanType planType,
            BillingCycle billingCycle,
            String originalAmount,
            String discountAmount,
            String finalAmount,
            UUID voucherId,
            OffsetDateTime expiresAt
    ) {
        PaymentTransactionEntity transaction = buildWebhookTransaction(
                userId,
                PaymentTransactionStatus.PENDING,
                planType,
                billingCycle,
                billingCycle == BillingCycle.YEARLY ? 365 : 30,
                "existing-external-id"
        );
        transaction.setOriginalAmount(new BigDecimal(originalAmount));
        transaction.setDiscountAmount(new BigDecimal(discountAmount));
        transaction.setAmount(new BigDecimal(finalAmount));
        transaction.setCurrency("PHP");
        transaction.setCheckoutUrl("https://checkout.xendit.test/existing_invoice");
        transaction.setExpiresAt(expiresAt);
        if (voucherId != null) {
            DiscountVoucherEntity voucher = new DiscountVoucherEntity();
            voucher.setId(voucherId);
            transaction.setVoucher(voucher);
        }
        return transaction;
    }

    private PaymentTransactionEntity buildWebhookTransaction(
            UUID userId,
            PaymentTransactionStatus status,
            PlanType planType,
            BillingCycle billingCycle,
            int accessDurationDays,
            String providerReferenceId
    ) {
        UserEntity user = new UserEntity();
        user.setId(userId);

        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.setId(UUID.randomUUID());
        transaction.setUser(user);
        transaction.setStatus(status);
        transaction.setProvider(BillingProvider.XENDIT);
        transaction.setBillingType(BillingType.PREPAID);
        transaction.setPlanType(planType);
        transaction.setBillingCycle(billingCycle);
        transaction.setAccessDurationDays(accessDurationDays);
        transaction.setOriginalAmount(new BigDecimal(planType == PlanType.PLUS ? "179.00" : "249.00"));
        transaction.setDiscountAmount(BigDecimal.ZERO);
        transaction.setAmount(new BigDecimal(planType == PlanType.PLUS ? "179.00" : "249.00"));
        transaction.setCurrency("PHP");
        transaction.setProviderReferenceId(providerReferenceId);
        transaction.setCreatedAt(FIXED_TIME.minusHours(1));
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
