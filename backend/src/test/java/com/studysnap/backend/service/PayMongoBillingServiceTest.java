package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.WebhookEventEntity;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayMongoBillingServiceTest {

    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private PaymentTransactionService paymentTransactionService;
    @Mock
    private WebhookEventService webhookEventService;
    @Mock
    private UserRepository userRepository;

    private ObjectMapper objectMapper;
    private StudySnapProperties properties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new StudySnapProperties();
        properties.getBilling().setProvider(BillingProvider.PAYMONGO);
        properties.getBilling().getPaymongo().setSecretKey("sk_test_123");
        properties.getBilling().getPaymongo().setMonthlyPlanId("plan_monthly");
        properties.getBilling().getPaymongo().setYearlyPlanId("plan_yearly");
        properties.getBilling().getPaymongo().setCheckoutSuccessUrl("https://app.notelib.test/settings?checkout=success");
        properties.getBilling().getPaymongo().setCheckoutCancelUrl("https://app.notelib.test/settings?checkout=cancel");
    }

    @Test
    void createPremiumCheckoutSession_usesYearlyPlanAndReturnsCheckoutUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setFirstName("Note");
        user.setLastName("Learner");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionService.resolvePlan(userId)).thenReturn(PlanType.FREE);
        when(subscriptionService.ensureProviderCustomerId(eq(user), eq(BillingProvider.PAYMONGO), any()))
                .thenReturn("cus_existing");

        PayMongoBillingService service = spy(new PayMongoBillingService(
                properties,
                subscriptionService,
                paymentTransactionService,
                webhookEventService,
                userRepository,
                objectMapper
        ));
        JsonNode subscriptionResponse = objectMapper.readTree("""
                {
                  "data": {
                    "id": "sub_123",
                    "attributes": {
                      "checkout_url": "https://checkout.paymongo.test/sub_123"
                    }
                  }
                }
                """);
        doReturn(subscriptionResponse).when(service).payMongoPostJson(eq("/subscriptions"), any(JsonNode.class));

        BillingCheckoutSessionResponse response = service.createPremiumCheckoutSession(userId, BillingCycle.YEARLY);

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.paymongo.test/sub_123");
        ArgumentCaptor<JsonNode> payloadCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(service).payMongoPostJson(eq("/subscriptions"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().at("/data/attributes/plan").asText()).isEqualTo("plan_yearly");
    }

    @Test
    void handleWebhook_subscriptionActivated_activatesPremiumAndMarksSuccess() {
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.setId(transactionId);
        when(paymentTransactionService.createPending(
                eq(userId),
                eq(BillingProvider.PAYMONGO),
                eq(BillingType.SUBSCRIPTION),
                eq(PlanType.PREMIUM),
                any(),
                any(),
                eq("evt_activated_1")
        )).thenReturn(Optional.of(transaction));

        PayMongoBillingService service = new PayMongoBillingService(
                properties,
                subscriptionService,
                paymentTransactionService,
                webhookEventService,
                userRepository,
                objectMapper
        );
        WebhookEventEntity webhookEvent = new WebhookEventEntity();
        webhookEvent.setId(UUID.randomUUID());
        when(webhookEventService.reserveEvent(eq(BillingProvider.PAYMONGO), eq("evt_activated_1"), eq("subscription.activated")))
                .thenReturn(Optional.of(webhookEvent));
        String payload = """
                {
                  "data": {
                    "id": "evt_activated_1",
                    "attributes": {
                      "type": "subscription.activated",
                      "data": {
                        "id": "sub_abc",
                        "type": "subscription",
                        "attributes": {
                          "customer": "cus_abc",
                          "currency": "USD",
                          "amount": 499,
                          "current_period_start": 1760000000,
                          "current_period_end": 1762592000,
                          "metadata": {
                            "user_id": "%s"
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(userId);

        SimpleMessageResponse response = service.handleWebhook(payload, null);

        assertThat(response.message()).isEqualTo("Received.");
        verify(subscriptionService).activatePremiumSubscription(
                eq(userId),
                eq(BillingType.SUBSCRIPTION),
                eq(BillingProvider.PAYMONGO),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class),
                eq(new SubscriptionService.ProviderMetadata("cus_abc", "sub_abc"))
        );
        verify(paymentTransactionService).markSuccess(transactionId);
        verify(paymentTransactionService, never()).markFailed(transactionId);
    }

    @Test
    void handleWebhook_duplicateEvent_doesNotReprocess() {
        UUID userId = UUID.randomUUID();
        when(paymentTransactionService.createPending(
                eq(userId),
                eq(BillingProvider.PAYMONGO),
                eq(BillingType.SUBSCRIPTION),
                eq(PlanType.PREMIUM),
                any(),
                any(),
                eq("evt_paid_1")
        )).thenReturn(Optional.empty());

        PayMongoBillingService service = new PayMongoBillingService(
                properties,
                subscriptionService,
                paymentTransactionService,
                webhookEventService,
                userRepository,
                objectMapper
        );
        WebhookEventEntity webhookEvent = new WebhookEventEntity();
        webhookEvent.setId(UUID.randomUUID());
        when(webhookEventService.reserveEvent(eq(BillingProvider.PAYMONGO), eq("evt_paid_1"), eq("subscription.invoice.paid")))
                .thenReturn(Optional.of(webhookEvent));
        String payload = """
                {
                  "data": {
                    "id": "evt_paid_1",
                    "attributes": {
                      "type": "subscription.invoice.paid",
                      "data": {
                        "id": "inv_123",
                        "type": "invoice",
                        "attributes": {
                          "subscription_id": "sub_123",
                          "customer_id": "cus_123",
                          "amount_paid": 499,
                          "currency": "USD",
                          "metadata": {
                            "user_id": "%s"
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(userId);

        SimpleMessageResponse response = service.handleWebhook(payload, null);

        assertThat(response.message()).isEqualTo("Received.");
        verify(subscriptionService, never()).activatePremiumSubscription(any(), any(), any(), any(), any(), any());
        verify(paymentTransactionService, never()).markSuccess(any());
        verify(paymentTransactionService, never()).markFailed(any());
    }

    @Test
    void handleWebhook_duplicateByWebhookEventStore_doesNotReprocess() {
        UUID userId = UUID.randomUUID();
        when(webhookEventService.reserveEvent(eq(BillingProvider.PAYMONGO), eq("evt_paid_2"), eq("subscription.invoice.paid")))
                .thenReturn(Optional.empty());

        PayMongoBillingService service = new PayMongoBillingService(
                properties,
                subscriptionService,
                paymentTransactionService,
                webhookEventService,
                userRepository,
                objectMapper
        );
        String payload = """
                {
                  "data": {
                    "id": "evt_paid_2",
                    "attributes": {
                      "type": "subscription.invoice.paid",
                      "data": {
                        "id": "inv_999",
                        "type": "invoice",
                        "attributes": {
                          "subscription_id": "sub_999",
                          "customer_id": "cus_999",
                          "amount_paid": 499,
                          "currency": "USD",
                          "metadata": {
                            "user_id": "%s"
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(userId);

        SimpleMessageResponse response = service.handleWebhook(payload, null);

        assertThat(response.message()).isEqualTo("Received.");
        verify(paymentTransactionService, never()).createPending(any(), any(), any(), any(), any(), any(), any());
        verify(subscriptionService, never()).activatePremiumSubscription(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleWebhook_unpaidWithoutGracePeriod_downgradesAndMarksFailed() {
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.setId(transactionId);
        when(paymentTransactionService.createPending(
                eq(userId),
                eq(BillingProvider.PAYMONGO),
                eq(BillingType.SUBSCRIPTION),
                eq(PlanType.PREMIUM),
                any(),
                any(),
                eq("evt_unpaid_1")
        )).thenReturn(Optional.of(transaction));

        PayMongoBillingService service = new PayMongoBillingService(
                properties,
                subscriptionService,
                paymentTransactionService,
                webhookEventService,
                userRepository,
                objectMapper
        );
        WebhookEventEntity webhookEvent = new WebhookEventEntity();
        webhookEvent.setId(UUID.randomUUID());
        when(webhookEventService.reserveEvent(eq(BillingProvider.PAYMONGO), eq("evt_unpaid_1"), eq("subscription.unpaid")))
                .thenReturn(Optional.of(webhookEvent));
        String payload = """
                {
                  "data": {
                    "id": "evt_unpaid_1",
                    "attributes": {
                      "type": "subscription.unpaid",
                      "data": {
                        "id": "sub_unpaid",
                        "type": "subscription",
                        "attributes": {
                          "customer": "cus_unpaid",
                          "amount_due": 499,
                          "currency": "USD",
                          "metadata": {
                            "user_id": "%s"
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(userId);

        service.handleWebhook(payload, null);

        verify(subscriptionService).downgradeToFree(userId);
        verify(paymentTransactionService).markFailed(transactionId);
    }

    @Test
    void handleWebhook_paymentFailedWithinGracePeriod_keepsPremiumAndMarksFailed() {
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.setId(transactionId);
        when(paymentTransactionService.createPending(
                eq(userId),
                eq(BillingProvider.PAYMONGO),
                eq(BillingType.SUBSCRIPTION),
                eq(PlanType.PREMIUM),
                any(),
                any(),
                eq("evt_failed_1")
        )).thenReturn(Optional.of(transaction));

        PayMongoBillingService service = new PayMongoBillingService(
                properties,
                subscriptionService,
                paymentTransactionService,
                webhookEventService,
                userRepository,
                objectMapper
        );
        WebhookEventEntity webhookEvent = new WebhookEventEntity();
        webhookEvent.setId(UUID.randomUUID());
        when(webhookEventService.reserveEvent(eq(BillingProvider.PAYMONGO), eq("evt_failed_1"), eq("subscription.invoice.payment_failed")))
                .thenReturn(Optional.of(webhookEvent));
        long futurePeriodEnd = OffsetDateTime.now().plusDays(7).toEpochSecond();
        String payload = """
                {
                  "data": {
                    "id": "evt_failed_1",
                    "attributes": {
                      "type": "subscription.invoice.payment_failed",
                      "data": {
                        "id": "inv_failed",
                        "type": "invoice",
                        "attributes": {
                          "subscription_id": "sub_failed",
                          "customer_id": "cus_failed",
                          "amount_due": 499,
                          "currency": "USD",
                          "current_period_end": %s,
                          "metadata": {
                            "user_id": "%s"
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(futurePeriodEnd, userId);

        service.handleWebhook(payload, null);

        verify(subscriptionService).activatePremiumSubscription(
                eq(userId),
                eq(BillingType.SUBSCRIPTION),
                eq(BillingProvider.PAYMONGO),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class),
                eq(new SubscriptionService.ProviderMetadata("cus_failed", "sub_failed"))
        );
        verify(paymentTransactionService).markFailed(transactionId);
        verify(subscriptionService, never()).downgradeToFree(any());
    }

    @Test
    void handleWebhook_updatedCancelled_downgradesWithoutTransactionInsert() {
        UUID userId = UUID.randomUUID();
        PayMongoBillingService service = new PayMongoBillingService(
                properties,
                subscriptionService,
                paymentTransactionService,
                webhookEventService,
                userRepository,
                objectMapper
        );
        WebhookEventEntity webhookEvent = new WebhookEventEntity();
        webhookEvent.setId(UUID.randomUUID());
        when(webhookEventService.reserveEvent(eq(BillingProvider.PAYMONGO), eq("evt_updated_1"), eq("subscription.updated")))
                .thenReturn(Optional.of(webhookEvent));
        String payload = """
                {
                  "data": {
                    "id": "evt_updated_1",
                    "attributes": {
                      "type": "subscription.updated",
                      "data": {
                        "id": "sub_cancelled",
                        "type": "subscription",
                        "attributes": {
                          "status": "cancelled",
                          "customer": "cus_cancelled",
                          "metadata": {
                            "user_id": "%s"
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(userId);

        service.handleWebhook(payload, null);

        verify(subscriptionService).downgradeToFree(userId);
        verify(paymentTransactionService, never()).createPending(any(), any(), any(), any(), any(), any(), any());
    }
}
