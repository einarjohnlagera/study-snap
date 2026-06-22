package com.studysnap.backend.controller;

import com.studysnap.backend.dto.EmailUnsubscribeResponse;
import com.studysnap.backend.service.EmailUnsubscribeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
public class EmailUnsubscribeController {
    private final EmailUnsubscribeService emailUnsubscribeService;

    @PostMapping("/unsubscribe")
    public EmailUnsubscribeResponse unsubscribe(@RequestParam("token") String token) {
        EmailUnsubscribeService.UnsubscribeResult result = emailUnsubscribeService.unsubscribe(token);
        return new EmailUnsubscribeResponse(
                result.category(),
                result.displayName(),
                "You've been unsubscribed from " + result.displayName() + " emails."
        );
    }
}
