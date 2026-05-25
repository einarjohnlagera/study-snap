package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.RefundFailedException;
import com.studysnap.backend.exception.RefundNotEligibleException;
import com.studysnap.backend.exception.TransactionNotFoundException;
import com.studysnap.backend.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {
    private static final String REFUND_ENDPOINT = "/refunds";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String FIELD_INVOICE_ID = "invoice_id";
    private static final String FIELD_REASON = "reason";
    private static final String REFUND_REASON_OTHERS = "OTHERS";
    private static final String REFUND_CONFIRMATION_TEMPLATE = "refund-confirmation";
    private static final String BUSINESS_DAYS_LABEL = "5–10 business days";

    private final StudySnapProperties properties;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;

    @Transactional
    public RefundResult issueRefund(UUID transactionId, UUID adminUserId) {
        PaymentTransactionEntity transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(TransactionNotFoundException::new);
        validateRefundEligibility(transaction);

        submitXenditRefund(transaction);

        transaction.setStatus(PaymentTransactionStatus.REFUNDED);
        PaymentTransactionEntity savedTransaction = paymentTransactionRepository.save(transaction);
        sendRefundConfirmationEmail(savedTransaction);

        UserEntity user = savedTransaction.getUser();
        log.info(
                "billing.refund.issued transactionId={} userId={} amount={} currency={} adminUserId={}",
                savedTransaction.getId(),
                user.getId(),
                savedTransaction.getAmount(),
                savedTransaction.getCurrency(),
                adminUserId
        );
        return new RefundResult(
                savedTransaction.getId(),
                user.getEmail(),
                savedTransaction.getAmount(),
                savedTransaction.getCurrency()
        );
    }

    private void validateRefundEligibility(PaymentTransactionEntity transaction) {
        if (transaction.getProvider() != BillingProvider.XENDIT) {
            throw new RefundNotEligibleException("Only Xendit transactions can be refunded from NoteLib.");
        }
        if (transaction.getStatus() == PaymentTransactionStatus.REFUNDED) {
            throw new RefundNotEligibleException("This transaction has already been refunded.");
        }
        if (transaction.getStatus() != PaymentTransactionStatus.SUCCESS) {
            throw new RefundNotEligibleException("Only successful paid transactions can be refunded.");
        }
        if (isBlank(transaction.getProviderReferenceId())) {
            throw new RefundNotEligibleException("This transaction is missing the Xendit invoice ID.");
        }
    }

    private void submitXenditRefund(PaymentTransactionEntity transaction) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(FIELD_INVOICE_ID, transaction.getProviderReferenceId());
        payload.put(FIELD_REASON, REFUND_REASON_OTHERS);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(properties.getBilling().getXendit().getBaseUrl()) + REFUND_ENDPOINT))
                .header(HEADER_AUTHORIZATION, buildBasicAuthorizationHeader())
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn(
                        "billing.refund.xendit_error transactionId={} status={} body={}",
                        transaction.getId(),
                        response.statusCode(),
                        response.body()
                );
                throw new RefundFailedException();
            }
        } catch (IOException exception) {
            log.warn(
                    "billing.refund.xendit_error transactionId={} status={} body={}",
                    transaction.getId(),
                    "io_error",
                    exception.getMessage()
            );
            throw new RefundFailedException();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "billing.refund.xendit_error transactionId={} status={} body={}",
                    transaction.getId(),
                    "interrupted",
                    exception.getMessage()
            );
            throw new RefundFailedException();
        }
    }

    private void sendRefundConfirmationEmail(PaymentTransactionEntity transaction) {
        UserEntity user = transaction.getUser();
        try {
            EmailTemplateService.RenderedEmailTemplate rendered = emailTemplateService.render(
                    REFUND_CONFIRMATION_TEMPLATE,
                    Map.of(
                            "userName", resolveUserName(user),
                            "amount", formatAmount(transaction.getAmount()),
                            "currency", transaction.getCurrency(),
                            "planName", resolvePlanName(transaction),
                            "businessDays", BUSINESS_DAYS_LABEL
                    )
            );
            emailService.sendEmail(new EmailMessage(user.getEmail(), rendered.subject(), rendered.htmlBody(), rendered.textBody()));
        } catch (RuntimeException exception) {
            log.warn("billing.refund.email_failed transactionId={} userId={} message={}", transaction.getId(), user.getId(), exception.getMessage());
        }
    }

    private String resolveUserName(UserEntity user) {
        if (!isBlank(user.getFirstName())) {
            return user.getFirstName().trim();
        }
        if (!isBlank(user.getDisplayName())) {
            return user.getDisplayName().trim();
        }
        return "there";
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String resolvePlanName(PaymentTransactionEntity transaction) {
        return transaction.getPlanType() == null ? "paid" : transaction.getPlanType().getDisplayName();
    }

    private String buildBasicAuthorizationHeader() {
        String credentials = properties.getBilling().getXendit().getSecretKey() + ":";
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
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

    public record RefundResult(
            UUID transactionId,
            String userEmail,
            BigDecimal amount,
            String currency
    ) {
    }
}
