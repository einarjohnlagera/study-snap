package com.studysnap.backend.controller;

import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class XenditWebhookControllerTest {

    @Mock
    private PaymentService paymentService;

    private XenditWebhookController xenditWebhookController;

    @BeforeEach
    void setUp() {
        xenditWebhookController = new XenditWebhookController(paymentService);
    }

    @Test
    void handleWebhook_delegatesToPaymentService() {
        SimpleMessageResponse response = xenditWebhookController.handleWebhook("{\"external_id\":\"ext_123\"}", "token_123");

        verify(paymentService).handleWebhook("{\"external_id\":\"ext_123\"}", "token_123");
        assertThat(response.message()).isEqualTo("OK");
    }
}
