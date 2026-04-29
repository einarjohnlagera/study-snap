package com.studysnap.backend.controller;

import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class XenditWebhookController {
    private final PaymentService paymentService;

    @PostMapping("/xendit")
    public SimpleMessageResponse handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "x-callback-token", required = false) String callbackToken
    ) {
        paymentService.handleWebhook(payload, callbackToken);
        return new SimpleMessageResponse("OK");
    }
}
