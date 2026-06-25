package com.studysnap.backend.controller;

import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.service.ResendWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResendWebhookControllerTest {
    @Mock
    private ResendWebhookService resendWebhookService;

    private ResendWebhookController resendWebhookController;

    @BeforeEach
    void setUp() {
        resendWebhookController = new ResendWebhookController(resendWebhookService);
    }

    @Test
    void handleWebhook_delegatesRawPayloadAndSvixHeaders() {
        SimpleMessageResponse response = resendWebhookController.handleWebhook(
                "{\"type\":\"email.bounced\"}",
                "msg_123",
                "1731705121",
                "v1,signature"
        );

        verify(resendWebhookService).handleWebhook(
                "{\"type\":\"email.bounced\"}",
                "msg_123",
                "1731705121",
                "v1,signature"
        );
        assertThat(response.message()).isEqualTo("OK");
    }
}
