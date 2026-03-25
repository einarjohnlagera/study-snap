package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.dto.CancelPremiumSubscriptionRequest;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.WebhookEventEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class PayMongoBillingService implements BillingService {
    private static final BillingProvider PAYMONGO_PROVIDER = BillingProvider.PAYMONGO;
    private static final String EVENT_SUBSCRIPTION_ACTIVATED = "subscription.activated";
    private static final String EVENT_SUBSCRIPTION_INVOICE_PAID = "subscription.invoice.paid";
    private static final String EVENT_SUBSCRIPTION_INVOICE_PAYMENT_FAILED = "subscription.invoice.payment_failed";
    private static final String EVENT_SUBSCRIPTION_PAST_DUE = "subscription.past_due";
    private static final String EVENT_SUBSCRIPTION_UNPAID = "subscription.unpaid";
    private static final String EVENT_SUBSCRIPTION_UPDATED = "subscription.updated";

    private final StudySnapProperties properties;
    private final SubscriptionService subscriptionService;
    private final PaymentTransactionService paymentTransactionService;
    private final WebhookEventService webhookEventService;
    private final PricingService pricingService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private record PayMongoTransactionMetadata(
            BillingType billingType,
            BigDecimal amount,
            String currency
    ) {
    }

    @Override
    public BillingCheckoutSessionResponse createPremiumCheckoutSession(
            UUID userId,
            BillingCycle billingCycle,
            String voucherCode,
            String cfIpCountry
    ) {
        ensureCheckoutConfigured();

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        if (subscriptionService.resolvePlan(userId) == PlanType.PREMIUM) {
            throw new AppException(
                    "PLAN_ALREADY_PREMIUM",
                    "Your Premium plan is already active.",
                    HttpStatus.CONFLICT
            );
        }

        String customerId = subscriptionService.ensureProviderCustomerId(
                user,
                PAYMONGO_PROVIDER,
                () -> createPayMongoCustomer(user)
        );
        PricingService.CheckoutSelection checkoutSelection = pricingService.resolveCheckoutSelection(
                userId,
                billingCycle,
                voucherCode,
                cfIpCountry
        );

        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode data = root.putObject("data");
        ObjectNode attributes = data.putObject("attributes");
        attributes.put("customer", customerId);
        attributes.put("plan", checkoutSelection.planId());
        attributes.put("return_url", properties.getBilling().getPaymongo().getCheckoutSuccessUrl());
        attributes.put("cancel_url", properties.getBilling().getPaymongo().getCheckoutCancelUrl());
        ObjectNode metadata = attributes.putObject("metadata");
        metadata.put("user_id", user.getId().toString());
        metadata.put("billing_cycle", checkoutSelection.billingCycle().name());
        metadata.put("pricing_region", checkoutSelection.region());
        metadata.put("pricing_currency", checkoutSelection.currency());
        if (checkoutSelection.countryCode() != null) {
            metadata.put("country_code", checkoutSelection.countryCode());
        }
        if (checkoutSelection.voucherId() != null) {
            metadata.put("voucher_id", checkoutSelection.voucherId().toString());
        }
        if (checkoutSelection.voucherCode() != null) {
            metadata.put("voucher_code", checkoutSelection.voucherCode());
        }
        if (checkoutSelection.effectivePrice() != null) {
            metadata.put("effective_price", checkoutSelection.effectivePrice().toPlainString());
        }

        JsonNode response = payMongoPostJson("/subscriptions", root);
        JsonNode subscriptionObject = response.path("data");
        String checkoutUrl = resolveCheckoutUrl(subscriptionObject);
        if (checkoutUrl == null) {
            throw new AppException(
                    "PAYMONGO_CHECKOUT_URL_MISSING",
                    "Could not start checkout. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }

        return new BillingCheckoutSessionResponse(checkoutUrl);
    }

    @Override
    public void cancelPremiumSubscription(UUID userId, CancelPremiumSubscriptionRequest request) {
        subscriptionService.scheduleCancellationAtPeriodEnd(
                userId,
                request == null ? null : request.reason(),
                request == null ? null : request.feedback()
        );
    }

    @Override
    public SimpleMessageResponse handleWebhook(String payload, String signatureHeader) {
        verifyWebhookSignatureIfConfigured(payload, signatureHeader);
        JsonNode event = parseJson(payload);
        String eventType = resolveEventType(event);
        if (!isSupportedEvent(eventType)) {
            log.info("billing.paymongo.webhook ignored unsupported eventType={}", eventType);
            return new SimpleMessageResponse("Ignored.");
        }

        JsonNode eventResource = resolveEventResource(event);
        String providerReferenceId = resolveProviderReferenceId(event, payload, eventType, eventResource);
        Optional<WebhookEventEntity> reservedWebhookEvent = webhookEventService.reserveEvent(
                PAYMONGO_PROVIDER,
                providerReferenceId,
                eventType
        );
        if (reservedWebhookEvent.isEmpty()) {
            log.info("billing.paymongo.webhook duplicate eventId={} eventType={}", providerReferenceId, eventType);
            return new SimpleMessageResponse("Received.");
        }

        String providerSubscriptionId = resolveProviderSubscriptionId(eventResource);
        String providerCustomerId = resolveProviderCustomerId(eventResource);
        UUID targetUserId = resolveTargetUserId(eventResource).orElse(null);
        if (targetUserId == null) {
            webhookEventService.markProcessed(reservedWebhookEvent.get().getId());
            log.warn(
                    "billing.paymongo.webhook ignored unresolved user eventType={} providerReferenceId={} providerSubscriptionId={} providerCustomerId={}",
                    eventType,
                    providerReferenceId,
                    providerSubscriptionId,
                    providerCustomerId
            );
            return new SimpleMessageResponse("Ignored.");
        }
        log.info(
                "billing.paymongo.webhook received eventType={} providerReferenceId={} userId={} providerSubscriptionId={} providerCustomerId={}",
                eventType,
                providerReferenceId,
                targetUserId,
                providerSubscriptionId,
                providerCustomerId
        );

        Optional<PaymentTransactionEntity> pendingTransaction = Optional.empty();
        if (shouldTrackTransaction(eventType)) {
            PayMongoTransactionMetadata transactionMetadata = resolveTransactionMetadata(eventType, eventResource);
            pendingTransaction = paymentTransactionService.createPending(
                    targetUserId,
                    PAYMONGO_PROVIDER,
                    transactionMetadata.billingType(),
                    PlanType.PREMIUM,
                    transactionMetadata.amount(),
                    transactionMetadata.currency(),
                    providerReferenceId
            );
            if (pendingTransaction.isEmpty()) {
                webhookEventService.markProcessed(reservedWebhookEvent.get().getId());
                log.info("billing.paymongo.webhook duplicate ignored providerReferenceId={} eventType={}", providerReferenceId, eventType);
                return new SimpleMessageResponse("Received.");
            }
        }

        try {
            SubscriptionEntity activatedSubscription = applyPayMongoEvent(eventType, eventResource, targetUserId);
            pendingTransaction.ifPresent(transaction -> {
                if (isFailureEvent(eventType)) {
                    paymentTransactionService.markFailed(transaction.getId());
                } else {
                    paymentTransactionService.markSuccess(transaction.getId());
                }
            });
            if (activatedSubscription != null) {
                pricingService.recordVoucherRedemption(
                        resolveVoucherId(eventResource),
                        targetUserId,
                        activatedSubscription,
                        pendingTransaction.orElse(null)
                );
            }
            webhookEventService.markProcessed(reservedWebhookEvent.get().getId());
        } catch (RuntimeException ex) {
            pendingTransaction.ifPresent(transaction -> paymentTransactionService.markFailed(transaction.getId()));
            webhookEventService.markFailed(reservedWebhookEvent.get().getId());
            throw ex;
        }

        return new SimpleMessageResponse("Received.");
    }

    @Override
    public BillingProvider getProvider() {
        return PAYMONGO_PROVIDER;
    }

    private SubscriptionEntity applyPayMongoEvent(String eventType, JsonNode eventResource, UUID userId) {
        String providerCustomerId = resolveProviderCustomerId(eventResource);
        String providerSubscriptionId = resolveProviderSubscriptionId(eventResource);
        SubscriptionService.ProviderMetadata providerMetadata = new SubscriptionService.ProviderMetadata(
                providerCustomerId,
                providerSubscriptionId
        );

        OffsetDateTime periodStart = resolvePeriodStart(eventResource);
        OffsetDateTime periodEnd = resolvePeriodEnd(eventResource);

        if (EVENT_SUBSCRIPTION_ACTIVATED.equals(eventType) || EVENT_SUBSCRIPTION_INVOICE_PAID.equals(eventType)) {
            log.info(
                    "billing.paymongo.subscription activating userId={} eventType={} providerSubscriptionId={} periodEnd={}",
                    userId,
                    eventType,
                    providerSubscriptionId,
                    periodEnd
            );
            return subscriptionService.activatePremiumSubscription(
                    userId,
                    BillingType.SUBSCRIPTION,
                    PAYMONGO_PROVIDER,
                    periodStart == null ? OffsetDateTime.now() : periodStart,
                    periodEnd,
                    false,
                    providerMetadata
            );
            
        }

        if (EVENT_SUBSCRIPTION_INVOICE_PAYMENT_FAILED.equals(eventType)
                || EVENT_SUBSCRIPTION_PAST_DUE.equals(eventType)
                || EVENT_SUBSCRIPTION_UNPAID.equals(eventType)) {
            if (periodEnd != null && OffsetDateTime.now().isBefore(periodEnd)) {
                log.info(
                        "billing.paymongo.subscription keep-active-during-grace userId={} eventType={} providerSubscriptionId={} periodEnd={}",
                        userId,
                        eventType,
                        providerSubscriptionId,
                        periodEnd
                );
                return subscriptionService.activatePremiumSubscription(
                        userId,
                        BillingType.SUBSCRIPTION,
                        PAYMONGO_PROVIDER,
                        periodStart == null ? OffsetDateTime.now() : periodStart,
                        periodEnd,
                        false,
                        providerMetadata
                );
                
            }
            log.info(
                    "billing.paymongo.subscription downgrading userId={} eventType={} providerSubscriptionId={}",
                    userId,
                    eventType,
                    providerSubscriptionId
            );
            subscriptionService.downgradeToFree(userId);
            return null;
        }

        if (EVENT_SUBSCRIPTION_UPDATED.equals(eventType)) {
            String status = textValue(eventResource.path("attributes"), "status");
            boolean cancelAtPeriodEnd = booleanValue(eventResource.path("attributes"), "cancel_at_period_end");
            if (isCanceledStatus(status)) {
                if (periodEnd != null && OffsetDateTime.now().isBefore(periodEnd)) {
                    log.info(
                            "billing.paymongo.subscription canceled-at-period-end userId={} status={} providerSubscriptionId={} periodEnd={}",
                            userId,
                            status,
                            providerSubscriptionId,
                            periodEnd
                    );
                    return subscriptionService.activatePremiumSubscription(
                            userId,
                            BillingType.SUBSCRIPTION,
                            PAYMONGO_PROVIDER,
                            periodStart == null ? OffsetDateTime.now() : periodStart,
                            periodEnd,
                            true,
                            providerMetadata
                    );
                    
                }
                log.info(
                        "billing.paymongo.subscription canceled-downgrade userId={} status={} providerSubscriptionId={}",
                        userId,
                        status,
                        providerSubscriptionId
                );
                subscriptionService.downgradeToFree(userId);
                return null;
            }

            if (isActiveStatus(status)) {
                log.info(
                        "billing.paymongo.subscription updated-active userId={} status={} providerSubscriptionId={} periodEnd={} cancelAtPeriodEnd={}",
                        userId,
                        status,
                        providerSubscriptionId,
                        periodEnd,
                        cancelAtPeriodEnd
                );
                return subscriptionService.activatePremiumSubscription(
                        userId,
                        BillingType.SUBSCRIPTION,
                        PAYMONGO_PROVIDER,
                        periodStart == null ? OffsetDateTime.now() : periodStart,
                        periodEnd,
                        cancelAtPeriodEnd,
                        providerMetadata
                );
                
            }

            if (periodEnd != null && OffsetDateTime.now().isBefore(periodEnd)) {
                log.info(
                        "billing.paymongo.subscription updated-grace userId={} status={} providerSubscriptionId={} periodEnd={}",
                        userId,
                        status,
                        providerSubscriptionId,
                        periodEnd
                );
                return subscriptionService.activatePremiumSubscription(
                        userId,
                        BillingType.SUBSCRIPTION,
                        PAYMONGO_PROVIDER,
                        periodStart == null ? OffsetDateTime.now() : periodStart,
                        periodEnd,
                        cancelAtPeriodEnd,
                        providerMetadata
                );
                
            }
            log.info(
                    "billing.paymongo.subscription updated-downgrade userId={} status={} providerSubscriptionId={}",
                    userId,
                    status,
                    providerSubscriptionId
            );
            subscriptionService.downgradeToFree(userId);
            return null;
        }
        return null;
    }

    private Optional<UUID> resolveTargetUserId(JsonNode eventResource) {
        UUID fromMetadata = parseUuid(textValue(eventResource.path("attributes").path("metadata"), "user_id"));
        if (fromMetadata != null) {
            return Optional.of(fromMetadata);
        }

        String providerSubscriptionId = resolveProviderSubscriptionId(eventResource);
        if (providerSubscriptionId != null) {
            Optional<UUID> fromSubscription = subscriptionService.findUserIdByProviderSubscriptionId(
                    PAYMONGO_PROVIDER,
                    providerSubscriptionId
            );
            if (fromSubscription.isPresent()) {
                return fromSubscription;
            }
        }

        String providerCustomerId = resolveProviderCustomerId(eventResource);
        if (providerCustomerId == null) {
            return Optional.empty();
        }
        return subscriptionService.findUserIdByProviderCustomerId(PAYMONGO_PROVIDER, providerCustomerId);
    }

    private boolean shouldTrackTransaction(String eventType) {
        return EVENT_SUBSCRIPTION_ACTIVATED.equals(eventType)
                || EVENT_SUBSCRIPTION_INVOICE_PAID.equals(eventType)
                || EVENT_SUBSCRIPTION_INVOICE_PAYMENT_FAILED.equals(eventType)
                || EVENT_SUBSCRIPTION_PAST_DUE.equals(eventType)
                || EVENT_SUBSCRIPTION_UNPAID.equals(eventType);
    }

    private boolean isFailureEvent(String eventType) {
        return EVENT_SUBSCRIPTION_INVOICE_PAYMENT_FAILED.equals(eventType)
                || EVENT_SUBSCRIPTION_PAST_DUE.equals(eventType)
                || EVENT_SUBSCRIPTION_UNPAID.equals(eventType);
    }

    private PayMongoTransactionMetadata resolveTransactionMetadata(String eventType, JsonNode eventResource) {
        JsonNode attributes = eventResource.path("attributes");
        if (EVENT_SUBSCRIPTION_ACTIVATED.equals(eventType)) {
            return new PayMongoTransactionMetadata(
                    BillingType.SUBSCRIPTION,
                    toMajorAmount(longValue(attributes, "amount")),
                    textValue(attributes, "currency")
            );
        }
        if (EVENT_SUBSCRIPTION_INVOICE_PAID.equals(eventType)) {
            Long amountMinor = longValue(attributes, "amount_paid");
            if (amountMinor == null) {
                amountMinor = longValue(attributes, "amount");
            }
            if (amountMinor == null) {
                amountMinor = longValue(attributes, "amount_due");
            }
            return new PayMongoTransactionMetadata(
                    BillingType.SUBSCRIPTION,
                    toMajorAmount(amountMinor),
                    textValue(attributes, "currency")
            );
        }
        Long amountMinor = longValue(attributes, "amount_due");
        if (amountMinor == null) {
            amountMinor = longValue(attributes, "amount");
        }
        return new PayMongoTransactionMetadata(
                BillingType.SUBSCRIPTION,
                toMajorAmount(amountMinor),
                textValue(attributes, "currency")
        );
    }

    private BillingCycle normalizeCycle(BillingCycle billingCycle) {
        return billingCycle == null ? BillingCycle.MONTHLY : billingCycle;
    }

    private String createPayMongoCustomer(UserEntity user) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode data = root.putObject("data");
        ObjectNode attributes = data.putObject("attributes");
        attributes.put("email", user.getEmail());
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            attributes.put("first_name", user.getFirstName());
        }
        if (user.getLastName() != null && !user.getLastName().isBlank()) {
            attributes.put("last_name", user.getLastName());
        }
        ObjectNode metadata = attributes.putObject("metadata");
        metadata.put("user_id", user.getId().toString());

        JsonNode response = payMongoPostJson("/customers", root);
        String customerId = textValue(response.path("data"), "id");
        if (customerId == null) {
            throw new AppException(
                    "PAYMONGO_CUSTOMER_CREATION_FAILED",
                    "Could not start checkout. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }
        return customerId;
    }

    JsonNode payMongoPostJson(String path, JsonNode payload) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBilling().getPaymongo().getApiBaseUrl() + path))
                .header("Authorization", "Basic " + buildBasicAuthorization(properties.getBilling().getPaymongo().getSecretKey()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new AppException(
                    "PAYMONGO_NETWORK_ERROR",
                    "Could not reach billing service. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(
                    "PAYMONGO_NETWORK_ERROR",
                    "Could not reach billing service. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }

        JsonNode parsed = parseJson(response.body());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return parsed;
        }
        String message = firstNonBlank(
                normalizeText(parsed.at("/errors/0/detail").asText()),
                normalizeText(parsed.at("/errors/0/code").asText())
        );
        throw new AppException(
                "PAYMONGO_API_ERROR",
                message == null ? "Could not start checkout. Please try again." : message,
                HttpStatus.BAD_GATEWAY
        );
    }

    private String resolveCheckoutUrl(JsonNode subscriptionObject) {
        List<String> pointers = List.of(
                "/attributes/next_action/redirect/url",
                "/attributes/latest_invoice/payment_intent/attributes/next_action/redirect/url",
                "/attributes/latest_invoice/payment_intent/next_action/redirect/url",
                "/attributes/latest_invoice/payment_url",
                "/attributes/checkout_url",
                "/attributes/authorization_url",
                "/attributes/hosted_url",
                "/attributes/invoice_url"
        );
        for (String pointer : pointers) {
            String value = normalizeText(subscriptionObject.at(pointer).asText());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String resolveEventType(JsonNode event) {
        String[] pointers = {"/data/attributes/type", "/type", "/data/type"};
        for (String pointer : pointers) {
            String value = normalizeText(event.at(pointer).asText());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private JsonNode resolveEventResource(JsonNode event) {
        JsonNode resource = event.at("/data/attributes/data");
        if (!resource.isMissingNode() && !resource.isNull()) {
            return resource;
        }
        JsonNode fallback = event.path("data");
        if (!fallback.isMissingNode() && !fallback.isNull()) {
            return fallback;
        }
        return objectMapper.createObjectNode();
    }

    private boolean isSupportedEvent(String eventType) {
        return EVENT_SUBSCRIPTION_ACTIVATED.equals(eventType)
                || EVENT_SUBSCRIPTION_INVOICE_PAID.equals(eventType)
                || EVENT_SUBSCRIPTION_INVOICE_PAYMENT_FAILED.equals(eventType)
                || EVENT_SUBSCRIPTION_PAST_DUE.equals(eventType)
                || EVENT_SUBSCRIPTION_UNPAID.equals(eventType)
                || EVENT_SUBSCRIPTION_UPDATED.equals(eventType);
    }

    private String resolveProviderReferenceId(JsonNode event, String payload, String eventType, JsonNode eventResource) {
        String eventId = firstNonBlank(textValue(event.path("data"), "id"), textValue(event, "id"));
        if (eventId != null) {
            return eventId;
        }
        String resourceId = textValue(eventResource, "id");
        if (eventType != null && resourceId != null) {
            return eventType + ":" + resourceId;
        }
        return "paymongo:" + Integer.toHexString((payload == null ? "" : payload).hashCode());
    }

    private String resolveProviderCustomerId(JsonNode eventResource) {
        JsonNode attributes = eventResource.path("attributes");
        return firstNonBlank(
                textValue(attributes, "customer"),
                textValue(attributes, "customer_id"),
                textValue(attributes.path("customer"), "id")
        );
    }

    private String resolveProviderSubscriptionId(JsonNode eventResource) {
        JsonNode attributes = eventResource.path("attributes");
        String fromAttributes = firstNonBlank(
                textValue(attributes, "subscription"),
                textValue(attributes, "subscription_id"),
                textValue(attributes.path("subscription"), "id")
        );
        if (fromAttributes != null) {
            return fromAttributes;
        }
        String type = textValue(eventResource, "type");
        if (type != null && type.contains("subscription")) {
            return textValue(eventResource, "id");
        }
        return null;
    }

    private OffsetDateTime resolvePeriodStart(JsonNode eventResource) {
        JsonNode attributes = eventResource.path("attributes");
        return firstPresent(
                () -> toOffsetDateTime(longValue(attributes, "current_period_start")),
                () -> parseOffsetDateTime(textValue(attributes, "current_period_start_at")),
                () -> parseOffsetDateTime(textValue(attributes, "current_period_start"))
        );
    }

    private OffsetDateTime resolvePeriodEnd(JsonNode eventResource) {
        JsonNode attributes = eventResource.path("attributes");
        return firstPresent(
                () -> toOffsetDateTime(longValue(attributes, "current_period_end")),
                () -> parseOffsetDateTime(textValue(attributes, "current_period_end_at")),
                () -> parseOffsetDateTime(textValue(attributes, "current_period_end"))
        );
    }

    @SafeVarargs
    private <T> T firstPresent(Supplier<T>... suppliers) {
        for (Supplier<T> supplier : suppliers) {
            T value = supplier.get();
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean isCanceledStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toLowerCase();
        return "canceled".equals(normalized)
                || "cancelled".equals(normalized)
                || "incomplete_cancelled".equals(normalized)
                || "incomplete_canceled".equals(normalized)
                || "terminated".equals(normalized);
    }

    private boolean isActiveStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toLowerCase();
        return "active".equals(normalized) || "trialing".equals(normalized);
    }

    private String buildBasicAuthorization(String secretKey) {
        String normalized = normalizeText(secretKey);
        if (normalized == null) {
            throw new AppException(
                    "PAYMONGO_NOT_CONFIGURED",
                    "Premium checkout is not configured yet.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        return Base64.getEncoder().encodeToString((normalized + ":").getBytes(StandardCharsets.UTF_8));
    }

    private void verifyWebhookSignatureIfConfigured(String payload, String signatureHeader) {
        String webhookSecret = normalizeText(properties.getBilling().getPaymongo().getWebhookSecret());
        if (webhookSecret == null) {
            return;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new AppException("PAYMONGO_INVALID_SIGNATURE", "Invalid PayMongo webhook signature.", HttpStatus.BAD_REQUEST);
        }

        long timestamp = -1L;
        List<String> signatures = new ArrayList<>();
        String[] parts = signatureHeader.split(",");
        for (String rawPart : parts) {
            String[] keyValue = rawPart.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            String key = keyValue[0].trim().toLowerCase();
            String value = keyValue[1].trim();
            if (value.isEmpty()) {
                continue;
            }
            if ("t".equals(key) || "timestamp".equals(key)) {
                try {
                    timestamp = Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                    timestamp = -1L;
                }
                continue;
            }
            if ("v1".equals(key) || "signature".equals(key) || "sig".equals(key) || "te".equals(key) || "li".equals(key)) {
                signatures.add(value);
            }
        }

        List<String> expectedSignatures = new ArrayList<>();
        expectedSignatures.add(hmacSha256(webhookSecret, payload == null ? "" : payload));
        if (timestamp > 0) {
            expectedSignatures.add(hmacSha256(webhookSecret, timestamp + "." + (payload == null ? "" : payload)));
        }
        boolean matched = signatures.stream().anyMatch(sig -> expectedSignatures.stream().anyMatch(exp -> constantTimeEquals(exp, sig)));
        if (!matched) {
            throw new AppException("PAYMONGO_INVALID_SIGNATURE", "Invalid PayMongo webhook signature.", HttpStatus.BAD_REQUEST);
        }
    }

    private String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (GeneralSecurityException ex) {
            throw new AppException(
                    "PAYMONGO_WEBHOOK_VERIFICATION_FAILED",
                    "Could not verify billing webhook.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw == null ? "" : raw);
        } catch (IOException ex) {
            throw new AppException(
                    "PAYMONGO_INVALID_PAYLOAD",
                    "Could not process billing webhook payload.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        return normalizeText(valueNode.asText());
    }

    private String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalizeText(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private Long longValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isNumber()) {
            return valueNode.longValue();
        }
        try {
            return Long.parseLong(valueNode.asText().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal toMajorAmount(Long amountMinor) {
        if (amountMinor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(amountMinor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private OffsetDateTime toOffsetDateTime(Long unixSeconds) {
        if (unixSeconds == null || unixSeconds <= 0) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(unixSeconds), ZoneOffset.UTC);
    }

    private OffsetDateTime parseOffsetDateTime(String rawDateTime) {
        String normalized = normalizeText(rawDateTime);
        if (normalized == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(normalized);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private UUID parseUuid(String rawUuid) {
        String normalized = normalizeText(rawUuid);
        if (normalized == null) {
            return null;
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean booleanValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return false;
        }
        if (valueNode.isBoolean()) {
            return valueNode.booleanValue();
        }
        return Boolean.parseBoolean(valueNode.asText().trim());
    }

    private UUID resolveVoucherId(JsonNode eventResource) {
        JsonNode metadata = eventResource.path("attributes").path("metadata");
        if (metadata.isMissingNode() || metadata.isNull()) {
            return null;
        }
        return parseUuid(textValue(metadata, "voucher_id"));
    }

    private void ensureCheckoutConfigured() {
        StudySnapProperties.Paymongo paymongo = properties.getBilling().getPaymongo();
        if (normalizeText(paymongo.getSecretKey()) == null) {
            throw new AppException(
                    "PAYMONGO_NOT_CONFIGURED",
                    "Premium checkout is not configured yet.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }
}
