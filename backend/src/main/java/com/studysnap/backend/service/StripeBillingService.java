package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class StripeBillingService implements BillingService {
    private static final int WEBHOOK_SIGNATURE_TOLERANCE_SECONDS = 300;
    private static final String EVENT_CHECKOUT_SESSION_COMPLETED = "checkout.session.completed";
    private static final String EVENT_INVOICE_PAID = "invoice.paid";
    private static final String EVENT_INVOICE_PAYMENT_FAILED = "invoice.payment_failed";
    private static final String EVENT_CUSTOMER_SUBSCRIPTION_UPDATED = "customer.subscription.updated";
    private static final String EVENT_CUSTOMER_SUBSCRIPTION_DELETED = "customer.subscription.deleted";
    private static final BillingProvider STRIPE_PROVIDER = BillingProvider.STRIPE;

    private final StudySnapProperties properties;
    private final SubscriptionService subscriptionService;
    private final PaymentTransactionService paymentTransactionService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private record StripeTransactionMetadata(
            BillingType billingType,
            BigDecimal amount,
            String currency
    ) {
    }

    @Override
    public BillingCheckoutSessionResponse createPremiumCheckoutSession(UUID userId) {
        ensureCheckoutConfigured();

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));
        if (subscriptionService.resolvePlan(userId) == PlanType.PREMIUM) {
            throw new AppException(
                    "PLAN_ALREADY_PREMIUM",
                    "Your Premium plan is already active.",
                    HttpStatus.CONFLICT
            );
        }

        String customerId = subscriptionService.ensureProviderCustomerId(
                user,
                STRIPE_PROVIDER,
                () -> createStripeCustomer(user)
        );

        Map<String, String> form = new LinkedHashMap<>();
        form.put("mode", "subscription");
        form.put("success_url", properties.getBilling().getStripe().getCheckoutSuccessUrl());
        form.put("cancel_url", properties.getBilling().getStripe().getCheckoutCancelUrl());
        form.put("customer", customerId);
        form.put("client_reference_id", user.getId().toString());
        form.put("line_items[0][price]", properties.getBilling().getStripe().getPremiumPriceId());
        form.put("line_items[0][quantity]", "1");

        JsonNode response = stripePostForm("/checkout/sessions", form);
        String checkoutUrl = textValue(response, "url");
        if (checkoutUrl == null) {
            throw new AppException(
                    "STRIPE_CHECKOUT_URL_MISSING",
                    "Could not start checkout. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }

        return new BillingCheckoutSessionResponse(checkoutUrl);
    }

    @Override
    public SimpleMessageResponse handleWebhook(String payload, String stripeSignatureHeader) {
        ensureWebhookConfigured();
        verifyWebhookSignature(payload, stripeSignatureHeader);

        JsonNode event = parseJson(payload);
        String eventType = textValue(event, "type");
        JsonNode eventObject = event.path("data").path("object");
        if (eventObject.isMissingNode() || eventObject.isNull()) {
            return new SimpleMessageResponse("Ignored.");
        }

        StripeTransactionMetadata transactionMetadata = resolveTransactionMetadata(eventType, eventObject);
        if (transactionMetadata == null) {
            return new SimpleMessageResponse("Ignored.");
        }

        String providerReferenceId = resolveProviderReferenceId(event, payload);
        UUID targetUserId = resolveTargetUserId(eventObject).orElse(null);
        if (targetUserId == null) {
            return new SimpleMessageResponse("Ignored.");
        }

        Optional<PaymentTransactionEntity> pending = paymentTransactionService.createPending(
                targetUserId,
                STRIPE_PROVIDER,
                transactionMetadata.billingType(),
                PlanType.PREMIUM,
                transactionMetadata.amount(),
                transactionMetadata.currency(),
                providerReferenceId
        );
        if (pending.isEmpty()) {
            return new SimpleMessageResponse("Received.");
        }

        PaymentTransactionEntity transaction = pending.get();
        try {
            applyStripeEvent(eventType, eventObject, targetUserId);
            paymentTransactionService.markSuccess(transaction.getId());
        } catch (RuntimeException ex) {
            paymentTransactionService.markFailed(transaction.getId());
            throw ex;
        }

        return new SimpleMessageResponse("Received.");
    }

    @Override
    public BillingProvider getProvider() {
        return STRIPE_PROVIDER;
    }

    private Optional<UUID> resolveTargetUserId(JsonNode eventObject) {
        UUID fromClientReference = parseUuid(textValue(eventObject, "client_reference_id"));
        if (fromClientReference != null) {
            return Optional.of(fromClientReference);
        }

        String providerSubscriptionId = textValue(eventObject, "subscription");
        if (providerSubscriptionId == null) {
            providerSubscriptionId = textValue(eventObject, "id");
        }
        if (providerSubscriptionId != null) {
            Optional<UUID> fromSubscription = subscriptionService.findUserIdByProviderSubscriptionId(
                    STRIPE_PROVIDER,
                    providerSubscriptionId
            );
            if (fromSubscription.isPresent()) {
                return fromSubscription;
            }
        }

        String providerCustomerId = textValue(eventObject, "customer");
        if (providerCustomerId == null) {
            return Optional.empty();
        }
        return subscriptionService.findUserIdByProviderCustomerId(STRIPE_PROVIDER, providerCustomerId);
    }

    private StripeTransactionMetadata resolveTransactionMetadata(String eventType, JsonNode eventObject) {
        if (EVENT_CHECKOUT_SESSION_COMPLETED.equals(eventType)) {
            if (!"subscription".equals(textValue(eventObject, "mode"))) {
                return null;
            }
            return new StripeTransactionMetadata(
                    BillingType.SUBSCRIPTION,
                    toMajorAmount(longValue(eventObject, "amount_total")),
                    textValue(eventObject, "currency")
            );
        }
        if (EVENT_INVOICE_PAID.equals(eventType)) {
            Long amountMinor = longValue(eventObject, "amount_paid");
            if (amountMinor == null) {
                amountMinor = longValue(eventObject, "amount_due");
            }
            return new StripeTransactionMetadata(
                    BillingType.SUBSCRIPTION,
                    toMajorAmount(amountMinor),
                    textValue(eventObject, "currency")
            );
        }
        if (EVENT_INVOICE_PAYMENT_FAILED.equals(eventType)) {
            return new StripeTransactionMetadata(
                    BillingType.SUBSCRIPTION,
                    toMajorAmount(longValue(eventObject, "amount_due")),
                    textValue(eventObject, "currency")
            );
        }
        if (EVENT_CUSTOMER_SUBSCRIPTION_UPDATED.equals(eventType) || EVENT_CUSTOMER_SUBSCRIPTION_DELETED.equals(eventType)) {
            return new StripeTransactionMetadata(
                    BillingType.SUBSCRIPTION,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    textValue(eventObject, "currency")
            );
        }
        return null;
    }

    private void applyStripeEvent(String eventType, JsonNode eventObject, UUID userId) {
        String customerId = textValue(eventObject, "customer");
        String providerSubscriptionId = textValue(eventObject, "subscription");
        if (providerSubscriptionId == null) {
            providerSubscriptionId = textValue(eventObject, "id");
        }
        SubscriptionService.ProviderMetadata providerMetadata = new SubscriptionService.ProviderMetadata(
                customerId,
                providerSubscriptionId
        );

        if (EVENT_CHECKOUT_SESSION_COMPLETED.equals(eventType)) {
            if (!"subscription".equals(textValue(eventObject, "mode"))) {
                return;
            }
            subscriptionService.activatePremiumSubscription(
                    userId,
                    BillingType.SUBSCRIPTION,
                    STRIPE_PROVIDER,
                    OffsetDateTime.now(),
                    null,
                    providerMetadata
            );
            return;
        }

        OffsetDateTime periodStart = toOffsetDateTime(longValue(eventObject, "current_period_start"));
        OffsetDateTime periodEnd = toOffsetDateTime(longValue(eventObject, "current_period_end"));

        if (EVENT_INVOICE_PAID.equals(eventType)) {
            subscriptionService.activatePremiumSubscription(
                    userId,
                    BillingType.SUBSCRIPTION,
                    STRIPE_PROVIDER,
                    periodStart == null ? OffsetDateTime.now() : periodStart,
                    null,
                    providerMetadata
            );
            return;
        }

        if (EVENT_INVOICE_PAYMENT_FAILED.equals(eventType)) {
            if (periodEnd != null && OffsetDateTime.now().isBefore(periodEnd)) {
                subscriptionService.activatePremiumSubscription(
                        userId,
                        BillingType.SUBSCRIPTION,
                        STRIPE_PROVIDER,
                        periodStart == null ? OffsetDateTime.now() : periodStart,
                        periodEnd,
                        providerMetadata
                );
            } else {
                subscriptionService.downgradeToFree(userId);
            }
            return;
        }

        if (EVENT_CUSTOMER_SUBSCRIPTION_UPDATED.equals(eventType)) {
            String subscriptionStatus = textValue(eventObject, "status");
            boolean cancelAtPeriodEnd = booleanValue(eventObject, "cancel_at_period_end");
            if (isPremiumActiveStatus(subscriptionStatus)) {
                subscriptionService.activatePremiumSubscription(
                        userId,
                        BillingType.SUBSCRIPTION,
                        STRIPE_PROVIDER,
                        periodStart == null ? OffsetDateTime.now() : periodStart,
                        cancelAtPeriodEnd ? periodEnd : null,
                        providerMetadata
                );
                return;
            }

            if (periodEnd != null && OffsetDateTime.now().isBefore(periodEnd)) {
                subscriptionService.activatePremiumSubscription(
                        userId,
                        BillingType.SUBSCRIPTION,
                        STRIPE_PROVIDER,
                        periodStart == null ? OffsetDateTime.now() : periodStart,
                        periodEnd,
                        providerMetadata
                );
                return;
            }
            subscriptionService.downgradeToFree(userId);
            return;
        }

        if (EVENT_CUSTOMER_SUBSCRIPTION_DELETED.equals(eventType)) {
            if (periodEnd != null && OffsetDateTime.now().isBefore(periodEnd)) {
                subscriptionService.activatePremiumSubscription(
                        userId,
                        BillingType.SUBSCRIPTION,
                        STRIPE_PROVIDER,
                        periodStart == null ? OffsetDateTime.now() : periodStart,
                        periodEnd,
                        providerMetadata
                );
                return;
            }
            subscriptionService.downgradeToFree(userId);
        }
    }

    private boolean isPremiumActiveStatus(String status) {
        return "active".equals(status) || "trialing".equals(status);
    }

    private String resolveProviderReferenceId(JsonNode event, String payload) {
        String eventId = textValue(event, "id");
        if (eventId != null) {
            return eventId;
        }

        String eventType = textValue(event, "type");
        String objectId = textValue(event.path("data").path("object"), "id");
        if (eventType != null && objectId != null) {
            return eventType + ":" + objectId;
        }

        String normalizedPayload = payload == null ? "" : payload;
        return "stripe:" + Integer.toHexString(normalizedPayload.hashCode());
    }

    private BigDecimal toMajorAmount(Long amountMinor) {
        if (amountMinor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(amountMinor)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private OffsetDateTime toOffsetDateTime(Long unixSeconds) {
        if (unixSeconds == null || unixSeconds <= 0) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(unixSeconds), ZoneOffset.UTC);
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

    private String createStripeCustomer(UserEntity user) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("email", user.getEmail());
        form.put("metadata[user_id]", user.getId().toString());

        JsonNode response = stripePostForm("/customers", form);
        String customerId = textValue(response, "id");
        if (customerId == null) {
            throw new AppException(
                    "STRIPE_CUSTOMER_CREATION_FAILED",
                    "Could not start checkout. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }
        return customerId;
    }

    private JsonNode stripePostForm(String path, Map<String, String> form) {
        String secretKey = properties.getBilling().getStripe().getSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new AppException(
                    "STRIPE_NOT_CONFIGURED",
                    "Premium checkout is not configured yet.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        String body = buildFormBody(form);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBilling().getStripe().getApiBaseUrl() + path))
                .header("Authorization", "Bearer " + secretKey.trim())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new AppException(
                    "STRIPE_NETWORK_ERROR",
                    "Could not reach billing service. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(
                    "STRIPE_NETWORK_ERROR",
                    "Could not reach billing service. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }

        JsonNode parsed = parseJson(response.body());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return parsed;
        }

        String errorMessage = textValue(parsed.path("error"), "message");
        throw new AppException(
                "STRIPE_API_ERROR",
                errorMessage == null ? "Could not start checkout. Please try again." : errorMessage,
                HttpStatus.BAD_GATEWAY
        );
    }

    private String buildFormBody(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private void verifyWebhookSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new AppException("STRIPE_INVALID_SIGNATURE", "Invalid Stripe webhook signature.", HttpStatus.BAD_REQUEST);
        }

        long timestamp = -1;
        List<String> signatures = new ArrayList<>();
        String[] parts = signatureHeader.split(",");
        for (String part : parts) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            if ("t".equals(keyValue[0])) {
                try {
                    timestamp = Long.parseLong(keyValue[1]);
                } catch (NumberFormatException ignored) {
                    timestamp = -1;
                }
            } else if ("v1".equals(keyValue[0])) {
                signatures.add(keyValue[1]);
            }
        }

        if (timestamp <= 0 || signatures.isEmpty()) {
            throw new AppException("STRIPE_INVALID_SIGNATURE", "Invalid Stripe webhook signature.", HttpStatus.BAD_REQUEST);
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > WEBHOOK_SIGNATURE_TOLERANCE_SECONDS) {
            throw new AppException("STRIPE_INVALID_SIGNATURE", "Invalid Stripe webhook signature.", HttpStatus.BAD_REQUEST);
        }

        String expectedSignature = hmacSha256(
                properties.getBilling().getStripe().getWebhookSecret(),
                timestamp + "." + payload
        );
        boolean matched = signatures.stream().anyMatch(signature -> constantTimeEquals(expectedSignature, signature));
        if (!matched) {
            throw new AppException("STRIPE_INVALID_SIGNATURE", "Invalid Stripe webhook signature.", HttpStatus.BAD_REQUEST);
        }
    }

    private String hmacSha256(String secret, String payload) {
        if (secret == null || secret.isBlank()) {
            throw new AppException(
                    "STRIPE_WEBHOOK_NOT_CONFIGURED",
                    "Billing webhook is not configured yet.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
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
                    "STRIPE_WEBHOOK_VERIFICATION_FAILED",
                    "Could not verify billing webhook.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw == null ? "" : raw);
        } catch (IOException ex) {
            throw new AppException(
                    "STRIPE_INVALID_RESPONSE",
                    "Could not process billing response.",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.asText();
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void ensureCheckoutConfigured() {
        StudySnapProperties.Stripe stripe = properties.getBilling().getStripe();
        if (stripe.getSecretKey() == null || stripe.getSecretKey().isBlank()
                || stripe.getPremiumPriceId() == null || stripe.getPremiumPriceId().isBlank()) {
            throw new AppException(
                    "STRIPE_NOT_CONFIGURED",
                    "Premium checkout is not configured yet.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private void ensureWebhookConfigured() {
        String webhookSecret = properties.getBilling().getStripe().getWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new AppException(
                    "STRIPE_WEBHOOK_NOT_CONFIGURED",
                    "Billing webhook is not configured yet.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }
}
