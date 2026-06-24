package com.studysnap.backend.controller;

import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.service.ResendWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class ResendWebhookController {
    private final ResendWebhookService resendWebhookService;

    @PostMapping("/resend")
    public SimpleMessageResponse handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "svix-id", required = false) String svixId,
            @RequestHeader(value = "svix-timestamp", required = false) String svixTimestamp,
            @RequestHeader(value = "svix-signature", required = false) String svixSignature
    ) {
        resendWebhookService.handleWebhook(payload, svixId, svixTimestamp, svixSignature);
        return new SimpleMessageResponse("OK");
    }
}
