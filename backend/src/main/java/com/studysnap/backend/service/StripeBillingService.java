package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.PlanType;
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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class StripeBillingService {
    private static final int WEBHOOK_SIGNATURE_TOLERANCE_SECONDS = 300;
    private static final String EVENT_CHECKOUT_SESSION_COMPLETED = "checkout.session.completed";
    private static final String EVENT_INVOICE_PAID = "invoice.paid";
    private static final String EVENT_INVOICE_PAYMENT_FAILED = "invoice.payment_failed";
    private static final String EVENT_CUSTOMER_SUBSCRIPTION_UPDATED = "customer.subscription.updated";
    private static final String EVENT_CUSTOMER_SUBSCRIPTION_DELETED = "customer.subscription.deleted";

    private final StudySnapProperties properties;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

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

        String customerId = subscriptionService.ensureStripeCustomerId(user, () -> createStripeCustomer(user));

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

    public SimpleMessageResponse handleWebhook(String payload, String stripeSignatureHeader) {
        ensureWebhookConfigured();
        verifyWebhookSignature(payload, stripeSignatureHeader);

        JsonNode event = parseJson(payload);
        String eventType = textValue(event, "type");
        JsonNode eventObject = event.path("data").path("object");
        if (eventObject.isMissingNode() || eventObject.isNull()) {
            return new SimpleMessageResponse("Ignored.");
        }

        if (EVENT_CHECKOUT_SESSION_COMPLETED.equals(eventType)) {
            handleCheckoutSessionCompleted(eventObject);
        } else if (EVENT_INVOICE_PAID.equals(eventType)) {
            handleInvoicePaid(eventObject);
        } else if (EVENT_INVOICE_PAYMENT_FAILED.equals(eventType)) {
            handleInvoicePaymentFailed(eventObject);
        } else if (EVENT_CUSTOMER_SUBSCRIPTION_UPDATED.equals(eventType)) {
            handleCustomerSubscriptionUpdated(eventObject);
        } else if (EVENT_CUSTOMER_SUBSCRIPTION_DELETED.equals(eventType)) {
            handleCustomerSubscriptionDeleted(eventObject);
        }

        return new SimpleMessageResponse("Received.");
    }

    private void handleCheckoutSessionCompleted(JsonNode eventObject) {
        String mode = textValue(eventObject, "mode");
        if (!"subscription".equals(mode)) {
            return;
        }

        String customerId = textValue(eventObject, "customer");
        if (customerId == null) {
            return;
        }

        String stripeSubscriptionId = textValue(eventObject, "subscription");
        String clientReferenceId = textValue(eventObject, "client_reference_id");
        UUID userId = parseUuid(clientReferenceId);
        if (userId != null) {
            subscriptionService.activatePremium(userId, customerId, stripeSubscriptionId);
            return;
        }
        subscriptionService.activatePremiumByStripeCustomer(customerId, stripeSubscriptionId);
    }

    private void handleInvoicePaid(JsonNode eventObject) {
        String customerId = textValue(eventObject, "customer");
        if (customerId == null) {
            return;
        }
        String stripeSubscriptionId = textValue(eventObject, "subscription");
        subscriptionService.activatePremiumByStripeCustomer(customerId, stripeSubscriptionId);
    }

    private void handleInvoicePaymentFailed(JsonNode eventObject) {
        String customerId = textValue(eventObject, "customer");
        if (customerId == null) {
            return;
        }
        subscriptionService.revertToFreeByStripeCustomer(customerId);
    }

    private void handleCustomerSubscriptionUpdated(JsonNode eventObject) {
        String customerId = textValue(eventObject, "customer");
        if (customerId == null) {
            return;
        }
        String stripeSubscriptionId = textValue(eventObject, "id");
        String subscriptionStatus = textValue(eventObject, "status");

        if (isPremiumActiveStatus(subscriptionStatus)) {
            subscriptionService.activatePremiumByStripeCustomer(customerId, stripeSubscriptionId);
            return;
        }
        subscriptionService.revertToFreeByStripeCustomer(customerId);
    }

    private void handleCustomerSubscriptionDeleted(JsonNode eventObject) {
        String customerId = textValue(eventObject, "customer");
        if (customerId == null) {
            return;
        }
        subscriptionService.revertToFreeByStripeCustomer(customerId);
    }

    private boolean isPremiumActiveStatus(String status) {
        return "active".equals(status) || "trialing".equals(status);
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
