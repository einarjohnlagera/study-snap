package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
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
    private PricingService pricingService;
    @Mock
    private WebhookEventService webhookEventService;
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
                objectMapper,
                httpClient,
                FIXED_CLOCK
        );
    }

    @Test
    void createCheckoutSession_returnsHostedMonthlyInvoiceUrlUsingConfiguredMonthlySelection() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(pricingService.resolveCheckoutSelection(userId, BillingCycle.MONTHLY, null, "PH"))
                .thenReturn(buildSelection(BillingCycle.MONTHLY, "249.00", "249.00", "0.00", null));
        when(paymentTransactionService.findPendingTransactions(userId, BillingProvider.XENDIT, PlanType.PREMIUM))
                .thenReturn(List.of());
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {"invoice_url":"https://checkout.xendit.test/invoice_monthly","expiry_date":"2026-04-30T04:00:00Z"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(paymentTransactionService.createPending(any())).thenReturn(Optional.of(new PaymentTransactionEntity()));

        BillingCheckoutSessionResponse response = paymentService.createCheckoutSession(
                userId,
                BillingCycle.MONTHLY,
                "/notes/new",
                "PH"
        );

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.xendit.test/invoice_monthly");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        JsonNode requestBody = objectMapper.readTree(readRequestBody(request));
        assertThat(requestBody.path("amount").decimalValue()).isEqualByComparingTo("249.00");
        assertThat(requestBody.path("description").asText()).isEqualTo("NoteLib Premium Monthly");
        assertThat(requestBody.path("currency").asText()).isEqualTo("PHP");
        assertThat(requestBody.path("success_redirect_url").asText())
                .isEqualTo("http://localhost:3000/billing/success?returnUrl=%2Fnotes%2Fnew");
    }

    @Test
    void createCheckoutSession_usesAnnualConfiguredAmount() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(pricingService.resolveCheckoutSelection(userId, BillingCycle.YEARLY, null, "PH"))
                .thenReturn(buildSelection(BillingCycle.YEARLY, "1999.00", "1999.00", "0.00", null));
        when(paymentTransactionService.findPendingTransactions(userId, BillingProvider.XENDIT, PlanType.PREMIUM))
                .thenReturn(List.of());
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {"invoice_url":"https://checkout.xendit.test/invoice_yearly","expiry_date":"2026-04-30T04:00:00Z"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(paymentTransactionService.createPending(any())).thenReturn(Optional.of(new PaymentTransactionEntity()));

        BillingCheckoutSessionResponse response = paymentService.createCheckoutSession(
                userId,
                BillingCycle.YEARLY,
                "/pricing",
                "PH"
        );

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.xendit.test/invoice_yearly");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        JsonNode requestBody = objectMapper.readTree(readRequestBody(requestCaptor.getValue()));
        assertThat(requestBody.path("amount").decimalValue()).isEqualByComparingTo("1999.00");
        assertThat(requestBody.path("description").asText()).isEqualTo("NoteLib Premium Annual");
    }

    @Test
    void createCheckoutSession_usesDiscountedFinalAmountAndStoresVoucherState() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID voucherId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(pricingService.resolveCheckoutSelection(userId, BillingCycle.MONTHLY, null, "PH"))
                .thenReturn(buildSelection(BillingCycle.MONTHLY, "249.00", "199.00", "50.00", voucherId));
        when(paymentTransactionService.findPendingTransactions(userId, BillingProvider.XENDIT, PlanType.PREMIUM))
                .thenReturn(List.of());
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {"invoice_url":"https://checkout.xendit.test/invoice_intro","expiry_date":"2026-04-30T04:00:00Z"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(paymentTransactionService.createPending(any())).thenReturn(Optional.of(new PaymentTransactionEntity()));

        paymentService.createCheckoutSession(userId, BillingCycle.MONTHLY, "/pricing", "PH");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        JsonNode requestBody = objectMapper.readTree(readRequestBody(requestCaptor.getValue()));
        assertThat(requestBody.path("amount").decimalValue()).isEqualByComparingTo("199.00");
        assertThat(requestBody.path("description").asText()).isEqualTo("NoteLib Premium Monthly - Intro offer applied");

        ArgumentCaptor<PaymentTransactionService.PendingPaymentTransactionRequest> pendingCaptor =
                ArgumentCaptor.forClass(PaymentTransactionService.PendingPaymentTransactionRequest.class);
        verify(paymentTransactionService).createPending(pendingCaptor.capture());
        PaymentTransactionService.PendingPaymentTransactionRequest pendingRequest = pendingCaptor.getValue();
        assertThat(pendingRequest.billingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(pendingRequest.originalAmount()).isEqualByComparingTo("249.00");
        assertThat(pendingRequest.discountAmount()).isEqualByComparingTo("50.00");
        assertThat(pendingRequest.finalAmount()).isEqualByComparingTo("199.00");
        assertThat(pendingRequest.voucherId()).isEqualTo(voucherId);
    }

    @Test
    void createCheckoutSession_reusesExistingPendingTransactionWhenCycleAmountAndVoucherMatch() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID voucherId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(pricingService.resolveCheckoutSelection(userId, BillingCycle.MONTHLY, null, "PH"))
                .thenReturn(buildSelection(BillingCycle.MONTHLY, "249.00", "199.00", "50.00", voucherId));

        PaymentTransactionEntity pendingTransaction = buildPendingTransaction(
                userId,
                BillingCycle.MONTHLY,
                "249.00",
                "50.00",
                "199.00",
                voucherId,
                FIXED_TIME.plusHours(8)
        );
        when(paymentTransactionService.findPendingTransactions(userId, BillingProvider.XENDIT, PlanType.PREMIUM))
                .thenReturn(List.of(pendingTransaction));

        BillingCheckoutSessionResponse response = paymentService.createCheckoutSession(
                userId,
                BillingCycle.MONTHLY,
                "/dashboard",
                "PH"
        );

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.xendit.test/existing_invoice");
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(paymentTransactionService, never()).createPending(any());
        verify(paymentTransactionService, never()).markFailed(any());
    }

    @Test
    void createCheckoutSession_marksSameCyclePendingFailedWhenPricingChanges() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(pricingService.resolveCheckoutSelection(userId, BillingCycle.MONTHLY, null, "PH"))
                .thenReturn(buildSelection(BillingCycle.MONTHLY, "249.00", "249.00", "0.00", null));

        PaymentTransactionEntity stalePending = buildPendingTransaction(
                userId,
                BillingCycle.MONTHLY,
                "249.00",
                "50.00",
                "199.00",
                UUID.randomUUID(),
                FIXED_TIME.plusHours(8)
        );
        when(paymentTransactionService.findPendingTransactions(userId, BillingProvider.XENDIT, PlanType.PREMIUM))
                .thenReturn(List.of(stalePending));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {"invoice_url":"https://checkout.xendit.test/new_invoice","expiry_date":"2026-04-30T04:00:00Z"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(paymentTransactionService.createPending(any())).thenReturn(Optional.of(new PaymentTransactionEntity()));

        BillingCheckoutSessionResponse response = paymentService.createCheckoutSession(
                userId,
                BillingCycle.MONTHLY,
                "/library",
                "PH"
        );

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.xendit.test/new_invoice");
        verify(paymentTransactionService).markFailed(stalePending.getId());
    }

    @Test
    void createCheckoutSession_doesNotReuseMonthlyPendingForAnnualCheckout() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(pricingService.resolveCheckoutSelection(userId, BillingCycle.YEARLY, null, "PH"))
                .thenReturn(buildSelection(BillingCycle.YEARLY, "1999.00", "1999.00", "0.00", null));

        PaymentTransactionEntity monthlyPending = buildPendingTransaction(
                userId,
                BillingCycle.MONTHLY,
                "249.00",
                "0.00",
                "249.00",
                null,
                FIXED_TIME.plusHours(8)
        );
        when(paymentTransactionService.findPendingTransactions(userId, BillingProvider.XENDIT, PlanType.PREMIUM))
                .thenReturn(List.of(monthlyPending));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("""
                {"invoice_url":"https://checkout.xendit.test/annual_invoice","expiry_date":"2026-04-30T04:00:00Z"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(paymentTransactionService.createPending(any())).thenReturn(Optional.of(new PaymentTransactionEntity()));

        BillingCheckoutSessionResponse response = paymentService.createCheckoutSession(
                userId,
                BillingCycle.YEARLY,
                "/pricing",
                "PH"
        );

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.xendit.test/annual_invoice");
        verify(paymentTransactionService, never()).markFailed(monthlyPending.getId());
    }

    @Test
    void createCheckoutSession_rejectsExternalReturnUrl() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));

        assertThatThrownBy(() -> paymentService.createCheckoutSession(
                userId,
                BillingCycle.MONTHLY,
                "https://evil.example/notes/new",
                "PH"
        )).isInstanceOf(InvalidCheckoutReturnUrlException.class);

        verifyNoInteractions(httpClient);
        verify(paymentTransactionService, never()).createPending(any());
    }

    @Test
    void handleWebhook_paidMonthlyUpgradesUserForThirtyDaysAndRecordsVoucherRedemption() {
        UUID userId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildWebhookTransaction(
                userId,
                PaymentTransactionStatus.PENDING,
                BillingCycle.MONTHLY,
                "notelib-user-1"
        );
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
        verify(pricingService).recordVoucherRedemption(subscription, transaction);
        verify(webhookEventService).markProcessed(reservedEvent.getId());
    }

    @Test
    void handleWebhook_paidYearlyUpgradesUserForThreeHundredSixtyFiveDays() {
        UUID userId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildWebhookTransaction(
                userId,
                PaymentTransactionStatus.PENDING,
                BillingCycle.YEARLY,
                "notelib-user-yearly"
        );
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        WebhookEventEntity reservedEvent = new WebhookEventEntity();
        reservedEvent.setId(UUID.randomUUID());

        when(webhookEventService.reserveEvent(BillingProvider.XENDIT, "inv_yearly:PAID", "PAID"))
                .thenReturn(Optional.of(reservedEvent));
        when(paymentTransactionService.findByProviderReferenceId(BillingProvider.XENDIT, "notelib-user-yearly"))
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
                "{\"id\":\"inv_yearly\",\"external_id\":\"notelib-user-yearly\",\"status\":\"PAID\"}",
                "xendit_webhook_token"
        );

        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(subscriptionService).activatePremiumSubscription(
                eq(userId),
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
    void handleWebhook_duplicatePaidOnSuccessfulTransactionDoesNotReupgrade() {
        UUID userId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildWebhookTransaction(
                userId,
                PaymentTransactionStatus.SUCCESS,
                BillingCycle.MONTHLY,
                "notelib-user-1"
        );
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
        verify(pricingService, never()).recordVoucherRedemption(any(), any());
        verify(webhookEventService).markProcessed(reservedEvent.getId());
    }

    @Test
    void handleWebhook_failedDoesNotUpgradeUserOrRedeemVoucher() {
        UUID userId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildWebhookTransaction(
                userId,
                PaymentTransactionStatus.PENDING,
                BillingCycle.MONTHLY,
                "notelib-user-2"
        );
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
            BillingCycle billingCycle,
            String basePrice,
            String effectivePrice,
            String discountAmount,
            UUID voucherId
    ) {
        return new PricingService.CheckoutSelection(
                "PH",
                "PH",
                "PHP",
                billingCycle,
                "xendit_premium_checkout",
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
                billingCycle,
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
            BillingCycle billingCycle,
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
        transaction.setPlanType(PlanType.PREMIUM);
        transaction.setBillingCycle(billingCycle);
        transaction.setOriginalAmount(new BigDecimal("249.00"));
        transaction.setDiscountAmount(BigDecimal.ZERO);
        transaction.setAmount(new BigDecimal("249.00"));
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
