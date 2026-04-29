package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingCheckoutSessionResponse;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.exception.InvalidPaymentWebhookTokenException;
import com.studysnap.backend.exception.PaymentCheckoutUnavailableException;
import com.studysnap.backend.exception.PremiumAlreadyActiveException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private static final BillingProvider BILLING_PROVIDER = BillingProvider.XENDIT;
    private static final BillingType BILLING_TYPE = BillingType.PREPAID;
    private static final PlanType PLAN_TYPE = PlanType.PREMIUM;
    private static final String INVOICE_ENDPOINT = "/v2/invoices";
    private static final String CURRENCY = "PHP";
    private static final String DESCRIPTION = "NoteLib Premium Monthly";
    private static final String SUCCESS_REDIRECT_PATH = "/billing/success";
    private static final String FAILURE_REDIRECT_PATH = "/billing/failed";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String FIELD_EXTERNAL_ID = "external_id";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_ID = "id";
    private static final String FIELD_INVOICE_URL = "invoice_url";
    private static final int INVOICE_DURATION_SECONDS = 86_400;
    private static final int PREMIUM_MONTHLY_AMOUNT_MINOR = 24_900;
    private static final BigDecimal PREMIUM_MONTHLY_AMOUNT_MAJOR = new BigDecimal("249.00");

    private final StudySnapProperties properties;
    private final UserRepository userRepository;
    private final PaymentTransactionService paymentTransactionService;
    private final SubscriptionService subscriptionService;
    private final WebhookEventService webhookEventService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BillingCheckoutSessionResponse createCheckoutSession(UUID userId) {
        userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (subscriptionService.resolvePlan(userId) == PlanType.PREMIUM) {
            throw new PremiumAlreadyActiveException();
        }

        ensureCheckoutConfigured();

        String externalId = buildExternalId(userId);
        JsonNode invoiceResponse = createInvoice(externalId);
        String checkoutUrl = readText(invoiceResponse, FIELD_INVOICE_URL);
        if (checkoutUrl == null) {
            throw new PaymentCheckoutUnavailableException("Could not start the payment checkout right now.");
        }

        Optional<PaymentTransactionEntity> pendingTransaction = paymentTransactionService.createPending(
                userId,
                BILLING_PROVIDER,
                BILLING_TYPE,
                PLAN_TYPE,
                PREMIUM_MONTHLY_AMOUNT_MAJOR,
                CURRENCY,
                externalId
        );
        if (pendingTransaction.isEmpty()) {
            throw new PaymentCheckoutUnavailableException("Could not reserve the payment transaction.");
        }

        log.info("billing.xendit.checkout created userId={} externalId={}", userId, externalId);
        return new BillingCheckoutSessionResponse(checkoutUrl);
    }

    public void handleWebhook(String payload, String callbackToken) {
        verifyWebhookToken(callbackToken);

        JsonNode webhook = parseJson(payload);
        String externalId = readText(webhook, FIELD_EXTERNAL_ID);
        String status = normalizeStatus(readText(webhook, FIELD_STATUS));
        if (externalId == null || status == null) {
            log.warn("billing.xendit.webhook ignored missingFields externalIdPresent={} statusPresent={}", externalId != null, status != null);
            return;
        }

        String providerEventId = resolveProviderEventId(webhook, externalId, status);
        Optional<com.studysnap.backend.entity.WebhookEventEntity> reservedEvent = webhookEventService.reserveEvent(
                BILLING_PROVIDER,
                providerEventId,
                status
        );
        if (reservedEvent.isEmpty()) {
            log.info("billing.xendit.webhook duplicateIgnored externalId={} status={}", externalId, status);
            return;
        }

        try {
            Optional<PaymentTransactionEntity> transaction = paymentTransactionService.findByProviderReferenceId(
                    BILLING_PROVIDER,
                    externalId
            );
            if (transaction.isEmpty()) {
                log.warn("billing.xendit.webhook missingTransaction externalId={} status={}", externalId, status);
                webhookEventService.markProcessed(reservedEvent.get().getId());
                return;
            }

            PaymentTransactionEntity paymentTransaction = transaction.get();
            log.info(
                    "billing.xendit.webhook received externalId={} status={} transactionStatus={} userId={}",
                    externalId,
                    status,
                    paymentTransaction.getStatus(),
                    paymentTransaction.getUser().getId()
            );

            if (STATUS_PAID.equals(status)) {
                handlePaidWebhook(paymentTransaction, externalId);
            } else if (STATUS_FAILED.equals(status) || STATUS_EXPIRED.equals(status)) {
                handleFailedWebhook(paymentTransaction, status);
            } else {
                log.info("billing.xendit.webhook ignored unsupportedStatus externalId={} status={}", externalId, status);
            }

            webhookEventService.markProcessed(reservedEvent.get().getId());
        } catch (RuntimeException exception) {
            webhookEventService.markFailed(reservedEvent.get().getId());
            throw exception;
        }
    }

    private void handlePaidWebhook(PaymentTransactionEntity paymentTransaction, String externalId) {
        if (paymentTransaction.getStatus() != PaymentTransactionStatus.SUCCESS) {
            paymentTransactionService.markSuccess(paymentTransaction.getId());
        }
        subscriptionService.activatePremiumSubscription(
                paymentTransaction.getUser().getId(),
                BILLING_TYPE,
                BILLING_PROVIDER,
                OffsetDateTime.now(),
                null,
                false,
                new SubscriptionService.ProviderMetadata(null, externalId)
        );
        log.info("billing.xendit.webhook premiumActivated externalId={} userId={}", externalId, paymentTransaction.getUser().getId());
    }

    private void handleFailedWebhook(PaymentTransactionEntity paymentTransaction, String status) {
        if (paymentTransaction.getStatus() != PaymentTransactionStatus.SUCCESS) {
            paymentTransactionService.markFailed(paymentTransaction.getId());
        }
        log.info("billing.xendit.webhook transactionMarkedFailed externalId={} status={} userId={}",
                paymentTransaction.getProviderReferenceId(),
                status,
                paymentTransaction.getUser().getId()
        );
    }

    private JsonNode createInvoice(String externalId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(FIELD_EXTERNAL_ID, externalId);
        payload.put("amount", PREMIUM_MONTHLY_AMOUNT_MINOR);
        payload.put("description", DESCRIPTION);
        payload.put("invoice_duration", INVOICE_DURATION_SECONDS);
        payload.put("currency", CURRENCY);
        payload.put("success_redirect_url", buildFrontendRedirectUrl(SUCCESS_REDIRECT_PATH));
        payload.put("failure_redirect_url", buildFrontendRedirectUrl(FAILURE_REDIRECT_PATH));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(properties.getBilling().getXendit().getBaseUrl()) + INVOICE_ENDPOINT))
                .header(HEADER_AUTHORIZATION, buildBasicAuthorizationHeader())
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("billing.xendit.checkout failed status={} body={}", response.statusCode(), response.body());
                throw new PaymentCheckoutUnavailableException("Could not create a hosted payment page.");
            }
            return parseJson(response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PaymentCheckoutUnavailableException("Could not reach Xendit right now.");
        }
    }

    private void ensureCheckoutConfigured() {
        if (isBlank(properties.getBilling().getXendit().getSecretKey())) {
            throw new PaymentCheckoutUnavailableException("Payments are not configured yet.");
        }
    }

    private void verifyWebhookToken(String callbackToken) {
        String expectedToken = properties.getBilling().getXendit().getWebhookToken();
        if (isBlank(expectedToken) || isBlank(callbackToken) || !expectedToken.equals(callbackToken.trim())) {
            throw new InvalidPaymentWebhookTokenException();
        }
    }

    private String buildExternalId(UUID userId) {
        return "notelib-" + userId + "-" + System.currentTimeMillis();
    }

    private String buildFrontendRedirectUrl(String path) {
        return normalizeBaseUrl(properties.getBilling().getFrontendBaseUrl()) + path;
    }

    private String buildBasicAuthorizationHeader() {
        String credentials = properties.getBilling().getXendit().getSecretKey() + ":";
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private JsonNode parseJson(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (IOException exception) {
            throw new PaymentCheckoutUnavailableException("Could not parse the payment response.");
        }
    }

    private String resolveProviderEventId(JsonNode webhook, String externalId, String status) {
        String invoiceId = readText(webhook, FIELD_ID);
        if (invoiceId != null) {
            return invoiceId + ":" + status;
        }
        return externalId + ":" + status;
    }

    private String readText(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        String value = field.asText(null);
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeStatus(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
