package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.RefundFailedException;
import com.studysnap.backend.exception.RefundNotEligibleException;
import com.studysnap.backend.exception.TransactionNotFoundException;
import com.studysnap.backend.repository.PaymentTransactionRepository;
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
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<String> httpResponse;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private EmailService emailService;

    private ObjectMapper objectMapper;
    private RefundService refundService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        StudySnapProperties properties = new StudySnapProperties();
        properties.getBilling().getXendit().setBaseUrl("https://api.xendit.co");
        properties.getBilling().getXendit().setSecretKey("xnd_development_secret");
        refundService = new RefundService(
                properties,
                paymentTransactionRepository,
                objectMapper,
                httpClient,
                emailTemplateService,
                emailService
        );
    }

    @Test
    void issueRefund_postsInvoiceRefundMarksTransactionRefundedAndSendsEmail() throws Exception {
        UUID transactionId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildTransaction(transactionId, BillingProvider.XENDIT, PaymentTransactionStatus.SUCCESS);
        when(paymentTransactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(paymentTransactionRepository.save(transaction)).thenReturn(transaction);
        when(emailTemplateService.render(any(), any())).thenReturn(new EmailTemplateService.RenderedEmailTemplate(
                "Your NoteLib refund has been processed",
                "<p>Refunded</p>",
                "Refunded"
        ));

        RefundService.RefundResult result = refundService.issueRefund(transactionId, adminUserId);

        assertThat(result.transactionId()).isEqualTo(transactionId);
        assertThat(result.userEmail()).isEqualTo("learner@notelib.app");
        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.REFUNDED);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.uri().toString()).isEqualTo("https://api.xendit.co/refunds");
        assertThat(request.headers().firstValue("Authorization")).contains("Basic eG5kX2RldmVsb3BtZW50X3NlY3JldDo=");
        JsonNode requestBody = objectMapper.readTree(readRequestBody(request));
        assertThat(requestBody.path("invoice_id").asText()).isEqualTo("inv_test_123");
        assertThat(requestBody.path("reason").asText()).isEqualTo("OTHERS");

        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).sendEmail(emailCaptor.capture());
        assertThat(emailCaptor.getValue().to()).isEqualTo("learner@notelib.app");
    }

    @Test
    void issueRefund_missingTransactionThrowsNotFound() {
        UUID transactionId = UUID.randomUUID();
        when(paymentTransactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        UUID adminUserId = UUID.randomUUID();
        assertThatThrownBy(() -> refundService.issueRefund(transactionId, adminUserId))
                .isInstanceOf(TransactionNotFoundException.class);

        verifyNoInteractions(httpClient);
    }

    @Test
    void issueRefund_alreadyRefundedTransactionThrowsConflict() {
        UUID transactionId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildTransaction(transactionId, BillingProvider.XENDIT, PaymentTransactionStatus.REFUNDED);
        when(paymentTransactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        UUID adminUserId = UUID.randomUUID();
        assertThatThrownBy(() -> refundService.issueRefund(transactionId, adminUserId))
                .isInstanceOf(RefundNotEligibleException.class)
                .hasMessage("This transaction has already been refunded.");

        verifyNoInteractions(httpClient);
    }

    @Test
    void issueRefund_nonXenditTransactionThrowsConflict() {
        UUID transactionId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildTransaction(transactionId, BillingProvider.INTERNAL_MIGRATION, PaymentTransactionStatus.SUCCESS);
        when(paymentTransactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        UUID adminUserId = UUID.randomUUID();
        assertThatThrownBy(() -> refundService.issueRefund(transactionId, adminUserId))
                .isInstanceOf(RefundNotEligibleException.class)
                .hasMessage("Only Xendit transactions can be refunded from NoteLib.");

        verifyNoInteractions(httpClient);
    }

    @Test
    void issueRefund_xenditErrorThrowsRefundFailedWithoutChangingStatus() throws Exception {
        UUID transactionId = UUID.randomUUID();
        PaymentTransactionEntity transaction = buildTransaction(transactionId, BillingProvider.XENDIT, PaymentTransactionStatus.SUCCESS);
        when(paymentTransactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("{\"message\":\"not refundable\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        UUID adminUserId = UUID.randomUUID();
        assertThatThrownBy(() -> refundService.issueRefund(transactionId, adminUserId))
                .isInstanceOf(RefundFailedException.class);

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        verify(paymentTransactionRepository, never()).save(any());
        verifyNoInteractions(emailTemplateService, emailService);
    }

    private PaymentTransactionEntity buildTransaction(
            UUID transactionId,
            BillingProvider provider,
            PaymentTransactionStatus status
    ) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("learner@notelib.app");
        user.setFirstName("Ari");
        user.setUsername("ari");

        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.setId(transactionId);
        transaction.setUser(user);
        transaction.setProvider(provider);
        transaction.setBillingType(BillingType.PREPAID);
        transaction.setPlanType(PlanType.PRO);
        transaction.setBillingCycle(BillingCycle.MONTHLY);
        transaction.setAccessDurationDays(30);
        transaction.setOriginalAmount(new BigDecimal("249.00"));
        transaction.setDiscountAmount(BigDecimal.ZERO);
        transaction.setAmount(new BigDecimal("249.00"));
        transaction.setCurrency("PHP");
        transaction.setStatus(status);
        transaction.setProviderReferenceId("inv_test_123");
        transaction.setCreatedAt(OffsetDateTime.parse("2026-05-25T00:00:00Z"));
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
